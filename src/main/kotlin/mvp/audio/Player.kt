package mvp.audio

import com.jfoenix.utils.JFXUtilities.runInFXAndWait
import com.sun.jna.Pointer
import java.nio.charset.Charset
import java.util.Locale
import java.util.Timer
import javafx.application.Platform.runLater
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.concurrent.Service
import javafx.concurrent.Task
import kotlin.concurrent.timer
import mvp.nativelibs.DeviceInfo
import mvp.nativelibs.LibBASS
import mvp.nativelibs.NULL_PTR
import mvp.nativelibs.SYNCPROC

private const val OUTPUT_SAMPLE_RATE = 48000

private data class Device(val id: Int, val name: String, val flags: Int) {
    companion object Utils {
        fun getDevice(deviceId: Int): Device? =
            if (deviceId < 0)
                null
            else
                DeviceInfo()
                    .takeIf { LibBASS.BASS_GetDeviceInfo(deviceId, it) }
                    ?.let { Device(deviceId, it.name, it.flags) }

        fun getDevice(channel: Pointer): Device? = getDevice(LibBASS.BASS_ChannelGetDevice(channel))
    }
}

private fun setStreamVolume(stream: Pointer, volume: Double) {
    LibBASS.BASS_ChannelSetAttribute(stream, LibBASS.BASS_ATTRIB_VOL, (volume / 100.0).toFloat())
}

private fun toStringSequence(strings: Pointer, charset: Charset = Charsets.UTF_8): Sequence<String> =
    sequence {
        var offset = 0L
        while (true) {
            val str = strings.getString(offset, charset.name())
            if (str.isNullOrBlank()) {
                break
            } else {
                yield(str)
                offset += str.toByteArray(charset).size + 1
            }
        }
    }

object Player {
    enum class Status { ERROR, LOADING, PLAYING, STANDBY }

    val instaPauseProperty: BooleanProperty = SimpleBooleanProperty(this, this::instaPause.name, true)
    val instaPause: Boolean
        get() = instaPauseProperty.get()

    private val _statusProperty = ReadOnlyObjectWrapper(this, this::status.name, Status.STANDBY)
    val statusProperty: ReadOnlyObjectProperty<Status> = _statusProperty.readOnlyProperty
    val status: Status
        get() = statusProperty.get()

    private val _statusMessageProperty = ReadOnlyStringWrapper(this, this::statusMessage.name, "")
    val statusMessageProperty: ReadOnlyStringProperty = _statusMessageProperty.readOnlyProperty
    val statusMessage: String
        get() = statusMessageProperty.get()

    private val _trackProperty = ReadOnlyObjectWrapper(this, this::track.name, UNKNOWN_TRACK)
    val trackProperty: ReadOnlyObjectProperty<Track> = _trackProperty.readOnlyProperty
    val track: Track
        get() = trackProperty.get()

    val volumeProperty: DoubleProperty = SimpleDoubleProperty(this, this::volume.name, 100.0)
    val volume: Double
        get() = volumeProperty.get()

    private val endSync: SYNCPROC = object : SYNCPROC {
        override fun callback(handle: Int, channel: Pointer, data: Int, userData: Pointer?) {
            runLater(::stop)
        }
    }
    private val metaSync: SYNCPROC = object : SYNCPROC {
        override fun callback(handle: Int, channel: Pointer, data: Int, userData: Pointer?) {
            var msg = ""
            LibBASS.BASS_ChannelGetTags(channel, LibBASS.BASS_TAG_META)?.let { meta ->
                // metadata is a string
                msg = meta.getString(0, Charsets.UTF_8.name())
                    .substringAfter("StreamTitle='")
                    .substringBefore("';")
            }
            LibBASS.BASS_ChannelGetTags(channel, LibBASS.BASS_TAG_OGG)?.let { comments ->
                // OGG comments is a series of null-terminated UTF-8 strings
                val tags = toStringSequence(comments)
                    .mapNotNull { comment -> comment.split("=", limit = 2).takeIf { it.size == 2 } }
                    .associateBy({ it[0].toLowerCase(Locale.ENGLISH) }, { it[1] })
                msg = listOfNotNull(tags["artist"], tags["title"]).joinToString(" - ")
            }

            runLater { _statusMessageProperty.set(msg) }
        }
    }
    private val stallSync: SYNCPROC = object : SYNCPROC {
        override fun callback(handle: Int, channel: Pointer, data: Int, userData: Pointer?) {
            val newStatus = when (data) {
                0 -> Status.LOADING
                1 -> Status.PLAYING
                else -> return
            }
            runLater { _statusProperty.set(newStatus) }
        }
    }

    private val streamService: Service<Pointer> = object : Service<Pointer>() {
        override fun createTask(): Task<Pointer> {
            val trackUrl = track.url
            return object : Task<Pointer>() {
                override fun call(): Pointer? {
                    check(LibBASS.BASS_Init(-1, OUTPUT_SAMPLE_RATE, 0)) {
                        "Couldn't init output device, error code ${LibBASS.BASS_ErrorGetCode()}"
                    }

                    val stream = LibBASS.BASS_StreamCreateURL(
                        trackUrl.toASCIIString(),
                        0,
                        LibBASS.BASS_STREAM_BLOCK or LibBASS.BASS_STREAM_STATUS or LibBASS.BASS_STREAM_AUTOFREE
                    )

                    return when {
                        NULL_PTR == stream -> error(
                            "Couldn't create stream from $trackUrl, error code ${LibBASS.BASS_ErrorGetCode()}"
                        )
                        isCancelled -> {
                            LibBASS.BASS_StreamFree(stream)
                            null
                        }
                        else -> {
                            setStreamVolume(stream, volume)

                            LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_META, 0, metaSync)
                            LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_OGG_CHANGE, 0, metaSync)
                            LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_END, 0, endSync)
                            LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_STALL, 0, stallSync)

                            if (!LibBASS.BASS_ChannelPlay(stream, true)) {
                                LibBASS.BASS_StreamFree(stream)
                                error("Couldn't play stream, error code ${LibBASS.BASS_ErrorGetCode()}")
                            }

                            stream
                        }
                    }
                }
            }
        }

        override fun cancelled() {
            ready()
        }

        override fun failed() {
            _statusProperty.set(Status.ERROR)
            _statusMessageProperty.set(exception.message)
        }

        override fun ready() {
            _statusProperty.set(Status.STANDBY)
            _statusMessageProperty.set("")
        }

        override fun running() {
            _statusProperty.set(Status.LOADING)
            _statusMessageProperty.set("")
        }

        override fun succeeded() {
            _statusProperty.set(Status.PLAYING)
            _statusMessageProperty.set("")
        }
    }

    private val instaPauseTimer: Timer = timer(daemon = true, initialDelay = 100, period = 500) {
        var maybeStream: Pointer? = null
        runInFXAndWait {
            maybeStream = streamService.value
        }
        val stream = maybeStream ?: return@timer

        val currentDevice = Device.getDevice(stream) ?: return@timer
        val defaultDevice = generateSequence(1, Int::inc)
            .mapNotNull(Device.Utils::getDevice)
            .find { it.flags and LibBASS.BASS_DEVICE_DEFAULT != 0 }
            ?: return@timer

        if (defaultDevice != currentDevice) {
            var reassignDevices = false
            runInFXAndWait {
                reassignDevices = when {
                    status != Status.PLAYING -> false
                    instaPause -> {
                        stop()
                        false
                    }
                    else -> true
                }
            }
            if (reassignDevices) {
                LibBASS.BASS_Init(defaultDevice.id, OUTPUT_SAMPLE_RATE, 0)
                LibBASS.BASS_ChannelSetDevice(stream, defaultDevice.id)
                LibBASS.BASS_ChannelPlay(stream, true)
                LibBASS.BASS_SetDevice(currentDevice.id)
                LibBASS.BASS_Free()
            }
        }
    }

    init {
        LibBASS.BASS_SetConfig(LibBASS.BASS_CONFIG_NET_PREBUF_WAIT, 0)

        volumeProperty.addListener { _, _, volume -> streamService.value?.let { setStreamVolume(it, volume.toDouble()) } }
    }

    fun play(track: Track) {
        stop()

        _trackProperty.set(track)
        streamService.restart()
    }

    fun stop() {
        streamService.value
            ?.let(LibBASS::BASS_StreamFree)
            ?: streamService.cancel()
        LibBASS.BASS_Free()
        streamService.reset()
    }

    fun destroy() {
        stop()
        instaPauseTimer.cancel()
    }
}
