package mvp

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import javafx.stage.StageStyle
import mvp.ui.UIManager

class App : Application() {
    val uiManager = UIManager(this)
    lateinit var primaryStage: Stage

    override fun start(primaryStage: Stage) {
        this.primaryStage = primaryStage.apply { initStyle(StageStyle.TRANSPARENT) }
        uiManager.init()
    }
}

fun main(args: Array<String>) {
    Platform.setImplicitExit(false)
    Application.launch(App::class.java, *args)
}
