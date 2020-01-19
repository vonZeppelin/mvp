package mvp.ui

import javafx.util.Callback

import mvp.App

class FXMLLoader(private val app: App) {
    fun <T> load(resource: String, vararg vals: Pair<String, Any>): T =
        javafx.fxml.FXMLLoader(javaClass.getResource(resource))
            .apply {
                controllerFactory = Callback { clazz ->
                    when (clazz) {
                        Controller::class.java -> Controller(app)
                        else -> clazz.getDeclaredConstructor().newInstance()
                    }
                }
                namespace += vals
            }
            .load()
}
