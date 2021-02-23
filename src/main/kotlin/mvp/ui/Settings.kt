package mvp.ui

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import java.util.prefs.Preferences

object Settings {
    private val prefs: Preferences = Preferences.userNodeForPackage(Settings::class.java)

    fun bind(property: BooleanProperty, key: String, default: Boolean) {
        property.value = prefs.getBoolean(key, default)
        property.addListener { _, _, newValue -> prefs.putBoolean(key, newValue) }
    }

    fun bind(property: DoubleProperty, key: String, default: Double) {
        property.value = prefs.getDouble(key, default)
        property.addListener { _, _, newValue -> prefs.putDouble(key, newValue.toDouble()) }
    }
}
