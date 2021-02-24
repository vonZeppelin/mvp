package mvp.ui.controls

import com.jfoenix.animation.alert.JFXAlertAnimation
import com.jfoenix.controls.JFXAlert
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialogLayout
import javafx.scene.control.Label
import javafx.scene.text.Text

class AboutDialog : JFXAlert<Unit>() {
    init {
        animation = JFXAlertAnimation.NO_ANIMATION
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

private const val ABOUT_APP = "Minimal Viable Player - lives in the taskbar and plays streaming audio.\n\n\u00a9 2021, Leonid Bogdanov"
