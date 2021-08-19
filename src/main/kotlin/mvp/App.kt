package mvp

import com.jfoenix.assets.JFoenixResources
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Screen
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
            val (sceneWidth, sceneHeight) = Screen.getPrimary().bounds.run {
                fun scale(dimension: Double): Double = dimension / 4.5 + ARROW_HEIGHT * 2

                scale(width) to scale(height)
            }

            scene = Scene(root, sceneWidth, sceneHeight, Color.TRANSPARENT).apply {
                stylesheets += listOf(
                    JFoenixResources.load("css/jfoenix-fonts.css").toExternalForm(),
                    JFoenixResources.load("css/jfoenix-design.css").toExternalForm()
                )
            }
            uiHooks = UIHooks(this, controller)
        }
    }

    override fun stop() {
        uiHooks.dispose()
    }
}

fun main(args: Array<String>) {
    System.setProperty("prism.lcdtext", "false");
    Platform.setImplicitExit(false)
    Application.launch(App::class.java, *args)
}

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
        .let { it.load<ROOT>() to it.getController() }
