package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import mvp.Track

import javafx.collections.FXCollections
import javafx.collections.ObservableSet
import java.util.concurrent.CompletableFuture

interface Player {
    val track: Track

    fun play()

    fun stop()
}

object LibBASS {
    init {
        Native.register("bass")

        BASS_Init(-1, 48000, 0)

        listOf("bassflac", "basshls", "bassopus").forEach { plugin ->
            Native.extractFromResourcePath(plugin).run {
                check(BASS_PluginLoad(absolutePath, 0) != 0) {
                    "Couldn't load '$plugin' plugin"
                }
            }
        }
    }

    fun listDevices(): ObservableSet<Device> = FXCollections.emptyObservableSet()

    fun createPlayer(track: Track): Player =
        object : Player {
            override val track: Track = track

            private val stream: CompletableFuture<Pointer> = CompletableFuture.supplyAsync {
                BASS_StreamCreateURL(track.url.toASCIIString(), 0, 0).apply {
                    check(Pointer.nativeValue(this) != 0L) {
                        "Couldn't create stream from ${track.url}, error code ${BASS_ErrorGetCode()}"
                    }
                }
            }

            override fun play() {
                stream.thenAccept {
                    check(BASS_ChannelPlay(it, true)) {
                        "Couldn't play channel, error code ${BASS_ErrorGetCode()}"
                    }
                }
            }

            override fun stop() {
                stream.thenAccept {
                    check(BASS_ChannelStop(it)) {
                        "Couldn't stop channel, error code ${BASS_ErrorGetCode()}"
                    }
                    check(BASS_StreamFree(it)) {
                        "Couldn't free channel, error code ${BASS_ErrorGetCode()}"
                    }
                }
            }
        }

    data class Device(val name: String, val flags: Int)

    @Structure.FieldOrder("name", "driver", "flags")
    class DeviceInfo(
        @JvmField var name: String,
        @JvmField var driver: String,
        @JvmField var flags: Int
    ) : Structure()

    @JvmStatic private external fun BASS_GetDeviceInfo(device: Int, info: DeviceInfo): Boolean
    @JvmStatic private external fun BASS_ErrorGetCode(): Int
    @JvmStatic private external fun BASS_Free()
    @JvmStatic private external fun BASS_Init(device: Int, freq: Int, flags: Int, win: Long = 0, clsid: Pointer? = Pointer.NULL): Boolean
    @JvmStatic private external fun BASS_PluginLoad(file: String, flags: Int): Int
    @JvmStatic private external fun BASS_ChannelPlay(handle: Pointer, restart: Boolean): Boolean
    @JvmStatic private external fun BASS_ChannelStop(handle: Pointer): Boolean
    @JvmStatic private external fun BASS_StreamCreateURL(url: String, offset: Int, flags: Int, proc: Callback? = null, user: Pointer? = Pointer.NULL): Pointer
    @JvmStatic private external fun BASS_StreamFree(stream: Pointer): Boolean
}
