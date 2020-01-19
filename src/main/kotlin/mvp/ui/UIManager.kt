package mvp.ui

import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Point2D
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.stage.Screen
import kotlin.math.max
import kotlin.math.min

import mvp.App
import mvp.nativelibs.StatusBar
import java.io.InputStream

private const val ARROW_SIZE = 10.0
private const val SCENE_WIDTH = 350.0 + ARROW_SIZE * 2
private const val SCENE_HEIGHT = 200.0 + ARROW_SIZE * 2

class UIManager(private val app: App) {

    val showAuxIcons: BooleanProperty = SimpleBooleanProperty(this, "showAuxIcons", false)

    fun init() {
        val ui: Parent = FXMLLoader(app).load(
            "/ui.fxml", "INSETS" to (1.5 * ARROW_SIZE)
        )
        showAuxIcons.addListener { _, _, _ ->
            hideTrayIcons()
            showTrayIcons()
        }
        app.primaryStage.scene = Scene(ui, SCENE_WIDTH, SCENE_HEIGHT, Color.TRANSPARENT)
        StatusBar.start {
            showUI(it.location)
        }
        showTrayIcons()
    }

    private fun showTrayIcons() {
        if (showAuxIcons.value) {
            StatusBar.addIcon("prev", loadIcon("/prev.png"))
        }
        StatusBar.addIcon("play", loadIcon("/play.png"))
        if (showAuxIcons.value) {
            StatusBar.addIcon("next", loadIcon("/next.png"))
        }
    }

    private fun hideTrayIcons(): Unit = listOf("prev", "play", "next").forEach(StatusBar::removeIcon)

    private fun loadIcon(iconPath: String): ByteArray = javaClass.getResourceAsStream(iconPath).use(InputStream::readBytes)

    // TODO Drop shadow
    // TODO Proper positioning, East-West support?
    private fun showUI(click: Point2D) {
        fun Double.clamp(min: Double, max: Double) = max(min, min(this, max))

        val stage = app.primaryStage
        if (stage.isShowing) {
            return
        }

        val screenBounds = Screen.getScreensForRectangle(click.x, click.y, 1.0, 1.0)
            .single()
            .visualBounds
        val arrowheadX = click.x.clamp(screenBounds.minX, screenBounds.maxX)
        val arrowheadY = click.y.clamp(screenBounds.minY, screenBounds.maxY)
        with(stage.scene) {
            stage.x = arrowheadX - width / 2
            stage.y = arrowheadY

            root.clip = Shape.union(
                Rectangle(ARROW_SIZE, ARROW_SIZE, width - ARROW_SIZE * 2, height - ARROW_SIZE * 2).apply {
                    arcWidth = 10.0
                    arcHeight = 10.0
                },
                Polygon(
                    width / 2 - ARROW_SIZE, ARROW_SIZE,
                    width / 2, 0.0,
                    width / 2 + ARROW_SIZE, ARROW_SIZE
                )
            )
        }
        Platform.runLater(stage::show)
    }
}
