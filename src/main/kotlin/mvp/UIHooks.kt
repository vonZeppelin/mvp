package mvp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import javafx.application.Platform.isNestedLoopRunning
import javafx.application.Platform.runLater
import javafx.geometry.Rectangle2D
import javafx.stage.Screen
import javafx.stage.Stage
import kotlin.concurrent.thread
import mvp.nativelibs.DispatchProc
import mvp.nativelibs.EVENT_KEY_RELEASED
import mvp.nativelibs.EVENT_MOUSE_PRESSED
import mvp.nativelibs.EventData
import mvp.nativelibs.LibUIOHook
import mvp.nativelibs.VC_MEDIA_NEXT
import mvp.nativelibs.VC_MEDIA_PLAY
import mvp.nativelibs.VC_MEDIA_PREVIOUS
import mvp.ui.Controller

class UIHooks(private val stage: Stage, private val controller: Controller) {
    private val uiHookDispatch: DispatchProc = DispatchProc { event ->
        when (event.eventType) {
            EVENT_MOUSE_PRESSED -> if (controller.isAutohideEnabled) maybeHideStage(event.data.mouse)
            EVENT_KEY_RELEASED -> handleMediaKeys(event.data.keyboard)
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
        debounceScheduler.shutdownNow()
    }

    private fun maybeHideStage(event: EventData.MouseEventData) {
        val mouseX = event.x.toDouble()
        val mouseY = event.y.toDouble()
        // TODO East-West support?
        val screenBounds = Screen.getScreensForRectangle(mouseX, mouseY, 1.0, 1.0)
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

    private fun handleMediaKeys(event: EventData.KeyboardEventData) {
        val action = when (event.keycode) {
            VC_MEDIA_PLAY -> Runnable { controller.toggleTrack() }
            VC_MEDIA_NEXT -> Runnable { controller.playNextTrack() }
            VC_MEDIA_PREVIOUS -> Runnable { controller.playNextTrack(reverse = true) }
            else -> return
        }
        // libuiohook sends doubled media keys events
        debounce(event.keycode, millis = 150) { runLater(action) }
    }

    private companion object {
        private val debounceScheduler = Executors.newSingleThreadScheduledExecutor()
        private val delayedBlocks = ConcurrentHashMap<Any, Future<*>>()

        fun debounce(key: Any, millis: Long, block: () -> Unit) {
            delayedBlocks.computeIfAbsent(key) {
                runCatching(block)

                debounceScheduler.schedule({ delayedBlocks -= key }, millis, MILLISECONDS)
            }
        }
    }
}
