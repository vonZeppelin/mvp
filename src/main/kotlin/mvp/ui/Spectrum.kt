package mvp.ui

import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color

class Spectrum : Canvas() {

    override fun isResizable(): Boolean = true

    override fun maxHeight(width: Double): Double = Double.POSITIVE_INFINITY
    override fun minHeight(width: Double): Double = 1.0
    override fun maxWidth(width: Double): Double = Double.POSITIVE_INFINITY
    override fun minWidth(width: Double): Double = 1.0

    override fun resize(width: Double, height: Double) {
        this.width = width
        this.height = height

        with(graphicsContext2D) {
            stroke = Color.AQUAMARINE
            strokeRoundRect(1.0, 1.0, width - 1, height - 1, 1.0, 1.0)
        }
    }

}
