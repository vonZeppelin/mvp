package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Union

const val EVENT_KEY_TYPED = 3
const val EVENT_KEY_PRESSED = 4
const val EVENT_KEY_RELEASED = 5
const val EVENT_MOUSE_CLICKED = 6
const val EVENT_MOUSE_PRESSED = 7
const val EVENT_MOUSE_RELEASED = 8
const val EVENT_MOUSE_MOVED = 9
const val EVENT_MOUSE_DRAGGED = 10
const val EVENT_MOUSE_WHEEL = 11

const val VC_MEDIA_PREVIOUS = 0xE010.toShort()
const val VC_MEDIA_NEXT = 0xE019.toShort()
const val VC_MEDIA_PLAY = 0xE022.toShort()

class EventData : Union() {
    @FieldOrder("button", "clicks", "x", "y")
    class MouseEventData : Structure() {
        @JvmField var button: Short = 0
        @JvmField var clicks: Short = 0
        @JvmField var x: Short = 0
        @JvmField var y: Short = 0
    }

    @FieldOrder("keycode", "rawcode", "keychar")
    class KeyboardEventData : Structure() {
        @JvmField var keycode: Short = 0
        @JvmField var rawcode: Short = 0
        @JvmField var keychar: Short = 0
    }

    @JvmField var mouse: MouseEventData = MouseEventData()
    @JvmField var keyboard: KeyboardEventData = KeyboardEventData()
}

@Structure.FieldOrder("eventType", "time", "mask", "reserved", "data")
class UIOHookEvent : Structure() {
    @JvmField var eventType: Int = 0
    @JvmField var time: Long = 0
    @JvmField var mask: Short = 0
    @JvmField var reserved: Short = 0
    @JvmField var data: EventData = EventData()

    override fun read() {
        super.read()
        data.setType(
            when (eventType) {
                in EVENT_KEY_TYPED..EVENT_KEY_RELEASED -> EventData.KeyboardEventData::class.java
                in EVENT_MOUSE_CLICKED..EVENT_MOUSE_WHEEL -> EventData.MouseEventData::class.java
                else -> return
            }
        )
        data.read()
    }
}

fun interface LoggerProc : Callback {
    fun callback(level: Int, message: String, args: Pointer?): Boolean
}

fun interface DispatchProc : Callback {
    fun callback(event: UIOHookEvent)
}

object LibUIOHook {
    @JvmStatic external fun hook_run(): Int
    @JvmStatic external fun hook_set_dispatch_proc(proc: DispatchProc)
    @JvmStatic external fun hook_set_logger_proc(proc: LoggerProc)
    @JvmStatic external fun hook_stop(): Int

    val NOOP_LOGGER: LoggerProc = LoggerProc { _, _, _ -> true }

    init {
        loadLibraries("uiohook") { name, _ -> Native.register(name) }

        // by default, silence lib's chatty log output
        hook_set_logger_proc(NOOP_LOGGER)
    }
}
