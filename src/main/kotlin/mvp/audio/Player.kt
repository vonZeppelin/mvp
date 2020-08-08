package mvp.audio

import com.sun.jna.Pointer
import javafx.application.Platform
import java.util.concurrent.CompletableFuture
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import kotlin.concurrent.timer
import mvp.nativelibs.DeviceInfo
import mvp.nativelibs.LibBASS
import mvp.nativelibs.NULL_PTR
import mvp.nativelibs.SYNCPROC

object Player {
    data class Device(val name: String, val flags: Int)
    enum class Status { ERROR, LOADING, PLAYING, STANDBY }

    private val _devicesProperty = ReadOnlyObjectWrapper(this, "device", emptySet<Device>())
    val devicesProperty: ReadOnlyObjectProperty<Set<Device>> = _devicesProperty.readOnlyProperty

    private val _statusProperty = ReadOnlyObjectWrapper(this, "status", Status.STANDBY)
    val statusProperty: ReadOnlyObjectProperty<Status> = _statusProperty.readOnlyProperty
    val status: Status
        get() = statusProperty.get()

    private val _trackProperty = ReadOnlyObjectWrapper(this, "track", UNKNOWN_TRACK)
    val trackProperty: ReadOnlyObjectProperty<Track> = _trackProperty.readOnlyProperty
    val track: Track
        get() = trackProperty.get()

    private var stream: CompletableFuture<Pointer> = CompletableFuture.completedFuture(NULL_PTR)

    private val metaSync = object : SYNCPROC {
        override fun callback(handle: Pointer, channel: Pointer, data: Int, userData: Pointer?) {
            println("meta")
        }
    }

    private val stallSync = object : SYNCPROC {
        override fun callback(handle: Pointer, channel: Pointer, data: Int, userData: Pointer?) {
            println("stall")
        }
    }

    init {
        timer(initialDelay = 100, period = 100, daemon = true) {
            val devices = sequence {
                var i = 1
                val info = DeviceInfo()
                while(LibBASS.BASS_GetDeviceInfo(i++, info)) {
                    yield(Device(info.name, info.flags))
                }
            }
            .filter { (it.flags and LibBASS.BASS_DEVICE_ENABLED) != 0 }
            .toSet()
            Platform.runLater {
                _devicesProperty.set(devices)
            }
        }
    }

    fun play(track: Track) {
        stop()
        stream = CompletableFuture
            .supplyAsync {
                Platform.runLater {
                    _trackProperty.set(track)
                    _statusProperty.set(Status.LOADING)
                }
                val stream = LibBASS.BASS_StreamCreateURL(
                    track.url.toASCIIString(),
                    0,
                    LibBASS.BASS_STREAM_BLOCK or LibBASS.BASS_STREAM_STATUS or LibBASS.BASS_STREAM_AUTOFREE
                )
                check(stream != NULL_PTR) {
                    "Couldn't create stream from ${track.url}, error code ${LibBASS.BASS_ErrorGetCode()}"
                }
                LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_META, 0, metaSync)
                LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_OGG_CHANGE, 0, metaSync)
                LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_HLS_SEGMENT, 0, metaSync)
                LibBASS.BASS_ChannelSetSync(stream, LibBASS.BASS_SYNC_STALL, 0, stallSync)
                check(LibBASS.BASS_ChannelPlay(stream, true)) {
                    "Couldn't play channel, error code ${LibBASS.BASS_ErrorGetCode()}"
                }
                Platform.runLater {
                    _statusProperty.set(Status.PLAYING)
                }
                stream
            }
            .exceptionally {
                Platform.runLater {
                    _statusProperty.set(Status.ERROR)
                }
                NULL_PTR
            }
    }

    fun stop() {
        stream
            .thenAccept {
                Platform.runLater {
                    _statusProperty.set(Status.STANDBY)
                }
                LibBASS.BASS_StreamFree(it)
            }
    }

    fun destroy() {
        LibBASS.BASS_Free()
    }
}
