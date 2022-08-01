package mvp.ui.controls

import com.jfoenix.animation.alert.JFXAlertAnimation
import com.jfoenix.controls.JFXAlert
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialogLayout
import javafx.scene.control.Label
import javafx.scene.text.Text
import mvp.cssStyles

class AboutDialog : JFXAlert<Unit>() {
    init {
        animation = JFXAlertAnimation.NO_ANIMATION
        dialogPane.stylesheets += cssStyles

        setContent(
            JFXDialogLayout().apply {
                setActions(
                    JFXButton("Close").apply {
                        setOnAction { close() }
                    }
                )
                setBody(Text(ABOUT_APP))
                setHeading(Label("About MVP"))
            }
        )
    }
}

private val ABOUT_APP = """
    Minimal Viable Player - lives in the taskbar and plays streaming audio.
    
    2020-2022, Leonid Bogdanov
""".trimIndent()
