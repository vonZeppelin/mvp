package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

interface SYNCPROC : Callback {
    fun callback(handle: Pointer, channel: Pointer, data: Int, userData: Pointer?)
}

@Structure.FieldOrder("name", "driver", "flags")
class DeviceInfo : Structure() {
    @JvmField var name: String = ""
    @JvmField var driver: String = ""
    @JvmField var flags: Int = 0
}

object LibBASS {
    @JvmStatic external fun BASS_ChannelGetDevice(handle: Pointer): Int
    @JvmStatic external fun BASS_ChannelGetTags(handle: Pointer, tags: Int): String
    @JvmStatic external fun BASS_ChannelPlay(handle: Pointer, restart: Boolean): Boolean
    @JvmStatic external fun BASS_ChannelSetAttribute(handle: Pointer, attribute: Int, volume: Float)
    @JvmStatic external fun BASS_ChannelSetDevice(handle: Pointer, device: Int): Boolean
    @JvmStatic external fun BASS_ChannelSetSync(handle: Pointer, type: Int, param: Long, proc: SYNCPROC, userData: Pointer = NULL_PTR): Pointer
    @JvmStatic external fun BASS_ErrorGetCode(): Int
    @JvmStatic external fun BASS_Free(): Boolean
    @JvmStatic external fun BASS_GetDeviceInfo(device: Int, info: DeviceInfo): Boolean
    @JvmStatic external fun BASS_Init(device: Int, freq: Int, flags: Int, win: Pointer = NULL_PTR, clsid: Pointer = NULL_PTR): Boolean
    @JvmStatic external fun BASS_PluginLoad(file: String, flags: Int): Int
    @JvmStatic external fun BASS_SetConfig(option: Int, value: Int): Boolean
    @JvmStatic external fun BASS_SetDevice(device: Int): Boolean
    @JvmStatic external fun BASS_StreamCreateURL(url: String, offset: Int, flags: Int, proc: Callback? = null, userData: Pointer = NULL_PTR): Pointer
    @JvmStatic external fun BASS_StreamFree(stream: Pointer): Boolean

    const val BASS_CONFIG_DEV_DEFAULT = 36
    const val BASS_CONFIG_NET_PREBUF_WAIT = 60

    const val BASS_DEVICE_ENABLED = 1
    const val BASS_DEVICE_DEFAULT = 2

    const val BASS_STREAM_AUTOFREE = 0x40000
    const val BASS_STREAM_BLOCK = 0x100000
    const val BASS_STREAM_STATUS = 0x800000

    const val BASS_ATTRIB_VOL = 2

    const val BASS_SYNC_HLS_SEGMENT = 0x10300
    const val BASS_SYNC_END = 2
    const val BASS_SYNC_META = 4
    const val BASS_SYNC_STALL = 6
    const val BASS_SYNC_OGG_CHANGE = 12

    init {
        loadLibraries("bass", "bassflac", "basshls", "bassopus") { name, path ->
            if (name == "bass") {
                Native.register(name)
            } else {
                check(BASS_PluginLoad(path.toString(), 1) != 0) {
                    "Couldn't load '$name' plugin from file '$path', error code ${BASS_ErrorGetCode()}"
                }
            }
        }
    }
}
