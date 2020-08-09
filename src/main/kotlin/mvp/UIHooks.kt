package mvp

import javafx.application.Platform.isNestedLoopRunning
import javafx.application.Platform.runLater
import javafx.geometry.Rectangle2D
import javafx.stage.Screen
import javafx.stage.Stage
import kotlin.concurrent.thread
import mvp.nativelibs.DispatchProc
import mvp.nativelibs.EVENT_MOUSE_PRESSED
import mvp.nativelibs.EventData
import mvp.nativelibs.LibUIOHook
import mvp.nativelibs.UIOHookEvent

class UIHooks(private val stage: Stage) {
    private val uiHookDispatch: DispatchProc = object : DispatchProc {
        override fun callback(event: UIOHookEvent) {
            when (event.eventType) {
                EVENT_MOUSE_PRESSED -> maybeHideStage(event.data.mouse)
            }
        }
    }

    init {
        thread(isDaemon = true) {
            LibUIOHook.hook_set_dispatch_proc(uiHookDispatch)
            LibUIOHook.hook_run()
        }
    }

    fun dispose() {
        LibUIOHook.hook_stop()
    }

    private fun maybeHideStage(event: EventData.MouseEventData) {
        val mouseX = event.x.toDouble()
        val mouseY = event.y.toDouble()
        // TODO East-West support?
        val screenBounds = Screen.getScreensForRectangle(mouseX, mouseY, 0.0, 0.0)
            .single()
            .visualBounds
        // consider mouse clicks "inside" screen only
        if (screenBounds.contains(mouseX, mouseY)) {
            runLater {
                if (stage.isShowing && !isNestedLoopRunning()) {
                    val stageRect = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
                    if (!stageRect.contains(mouseX, mouseY)) {
                        stage.hide()
                    }
                }
            }
        }
    }
}
