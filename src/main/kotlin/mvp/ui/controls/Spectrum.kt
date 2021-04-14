package mvp.ui.controls

import javafx.beans.binding.Bindings.selectBoolean
import javafx.beans.binding.BooleanBinding
import javafx.concurrent.ScheduledService
import javafx.concurrent.Task
import javafx.scene.canvas.Canvas
import javafx.util.Duration
import mvp.audio.Player
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

private const val BANDS_COUNT = 25
private const val BAND_GAP = 2

class Spectrum : Canvas() {
    private val spectrumService: ScheduledService<DoubleArray> = object : ScheduledService<DoubleArray>() {
        init { period = Duration.millis(55.0) }

        override fun createTask(): Task<DoubleArray> =
            object : Task<DoubleArray>() {
                override fun call(): DoubleArray {
                    val fft = Player.spectrum()

                    // algo taken from the Bass lib 'spectrum.c' example
                    val powerMul = log2(fft.size.toDouble())
                    var b0 = 0
                    return DoubleArray(BANDS_COUNT) { x ->
                        var b1 = 2.0.pow(x * powerMul / (BANDS_COUNT - 1)).toInt()
                        if (b1 > fft.size - 1) b1 = fft.size - 1
                        if (b1 <= b0) b1 = b0 + 1
                        var peak = 0f
                        while (b0 < b1) {
                            if (peak < fft[b0 + 1]) peak = fft[b0 + 1]
                            b0++
                        }
                        var y = sqrt(peak) * 2 * height
                        if (y > height) y = height
                        y
                    }
                }
            }

        override fun succeeded() {
            super.succeeded()
            drawSpectrum(lastValue)
        }

        override fun cancelled() {
            super.cancelled()
            drawNoSound()
        }
    }

    private val shouldDrawSpectrum: BooleanBinding =
        Player.statusProperty.isEqualTo(Player.Status.PLAYING).and(selectBoolean(sceneProperty(), "window", "showing"))
            .apply {
                addListener { _, _, shouldDraw ->
                    if (shouldDraw) spectrumService.restart() else spectrumService.cancel()
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

        if (Player.status != Player.Status.PLAYING) drawNoSound()
    }

    private fun drawNoSound() {
        drawSpectrum(DoubleArray(BANDS_COUNT) { 1.0 })
    }

    private fun drawSpectrum(bands: DoubleArray) {
        if (width == 0.0 || height == 0.0) return

        val bandWidth = (width - BAND_GAP * (BANDS_COUNT - 1)) / BANDS_COUNT
        with(graphicsContext2D) {
            clearRect(0.0, 0.0, width, height)
            bands.forEachIndexed { index, value ->
                fillRect((bandWidth + BAND_GAP) * index, height - value, bandWidth, height)
            }
        }
    }
}
