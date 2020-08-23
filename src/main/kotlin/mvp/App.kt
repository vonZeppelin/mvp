package mvp

import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Callback
import mvp.ui.ARROW_HEIGHT
import mvp.ui.Controller

class App : Application() {
    private lateinit var uiHooks: UIHooks

    override fun start(primaryStage: Stage) {
        with(primaryStage) {
            initStyle(StageStyle.TRANSPARENT)
            val (root, controller) = "/ui.fxml".loadFXML<Parent, Controller>(this)
            scene = Scene(root, SCENE_WIDTH, SCENE_HEIGHT, Color.TRANSPARENT)
            uiHooks = UIHooks(this, controller)
        }
    }

    override fun stop() {
        uiHooks.dispose()
    }
}

fun main(args: Array<String>) {
    Platform.setImplicitExit(false)
    Application.launch(App::class.java, *args)
}

private const val SCENE_WIDTH = 350.0 + ARROW_HEIGHT * 2
private const val SCENE_HEIGHT = 200.0 + ARROW_HEIGHT * 2

private fun <ROOT : Parent, CONTROLLER> String.loadFXML(stage: Stage): Pair<ROOT, CONTROLLER> =
    FXMLLoader(App::class.java.getResource(this))
        .apply {
            controllerFactory = Callback {
                when (it) {
                    Controller::class.java -> Controller(stage)
                    else -> it.getDeclaredConstructor().newInstance()
                }
            }
        }
        .let { it.load<ROOT>() to it.getController<CONTROLLER>() }
