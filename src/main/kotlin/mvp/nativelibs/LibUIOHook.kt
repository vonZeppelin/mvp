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

class EventData : Union() {
    @Structure.FieldOrder("button", "clicks", "x", "y")
    class MouseEventData : Structure() {
        @JvmField var button: Short = 0
        @JvmField var clicks: Short = 0
        @JvmField var x: Short = 0
        @JvmField var y: Short = 0
    }

    @Structure.FieldOrder("keycode", "rawcode", "keychar")
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

interface LoggerProc : Callback {
    fun callback(level: Int, message: String, args: Pointer): Boolean
}

interface DispatchProc : Callback {
    fun callback(event: UIOHookEvent)
}

object LibUIOHook {
    @JvmStatic external fun hook_run(): Int
    @JvmStatic external fun hook_set_dispatch_proc(proc: DispatchProc)
    @JvmStatic external fun hook_set_logger_proc(proc: LoggerProc)
    @JvmStatic external fun hook_stop(): Int

    val NOOP_LOGGER: LoggerProc = object : LoggerProc {
        override fun callback(level: Int, message: String, args: Pointer): Boolean = true
    }

    init {
        Native.register("uiohook")

        // by default, silence lib's chatty log output
        hook_set_logger_proc(NOOP_LOGGER)
    }
}
