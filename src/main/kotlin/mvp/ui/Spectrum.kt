package mvp.ui

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color.BLACK
import javafx.util.Duration
import kotlin.random.Random
import mvp.audio.Player

private const val BARS_COUNT = 25
private const val BARS_GAP = 2

class Spectrum : Canvas(), EventHandler<ActionEvent> {
    private val timeline = Timeline(KeyFrame(Duration.millis(45.0), this)).apply {
        cycleCount = Timeline.INDEFINITE
        isAutoReverse = true
    }

    init {
        graphicsContext2D.fill = BLACK

        Player.statusProperty.addListener { _, _, newValue ->
            if (newValue == Player.Status.PLAYING) timeline.play() else timeline.stop()
        }
    }

    override fun maxHeight(width: Double): Double = Double.POSITIVE_INFINITY
    override fun minHeight(width: Double): Double = 1.0

    override fun maxWidth(width: Double): Double = Double.POSITIVE_INFINITY
    override fun minWidth(width: Double): Double = 1.0

    override fun isResizable(): Boolean = true
    override fun resize(width: Double, height: Double) {
        this.width = width
        this.height = height
    }

    override fun handle(event: ActionEvent) {
        if (width == 0.0 || height == 0.0) return

        val fft = List(BARS_COUNT) { Random.nextDouble(0.0, height) }
        val barWidth = (width - BARS_GAP * (BARS_COUNT - 1)) / BARS_COUNT

        with(graphicsContext2D) {
            clearRect(0.0, 0.0, width, height)

            fft.forEachIndexed { index, value ->
                fillRect((barWidth + BARS_GAP) * index, value, barWidth, height)
            }
        }
    }
}
