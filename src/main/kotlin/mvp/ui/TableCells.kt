package mvp.ui

import com.jfoenix.controls.JFXTextField
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder
import com.jfoenix.controls.cells.editors.base.GenericEditableTreeTableCell
import com.jfoenix.validation.RequiredFieldValidator
import com.jfoenix.validation.base.ValidatorBase
import java.net.URI
import javafx.application.Platform.runLater
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.scene.control.TextInputControl
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class TrackCell : GenericEditableTreeTableCell<PlaylistItem, String>(null) {
    private inner class TrackEditorBuilder : EditorNodeBuilder<String> {
        private var nameField: JFXTextField? = null
        private var urlField: JFXTextField? = null

        override fun createNode(
            value: String,
            keyEventsHandler: EventHandler<KeyEvent>,
            focusChangeListener: ChangeListener<Boolean>
        ): Region {
            nameField = JFXTextField().apply {
                promptText = "Track name"
                styleClass += "track-name-input"
                validators += requiredFieldValidator
            }
            urlField = JFXTextField().apply {
                promptText = "Track URL"
                styleClass += "track-url-input"
                validators += listOf(requiredFieldValidator, urlFieldValidator)
            }

            setValue(value)

            return VBox(nameField, urlField).apply {
                focusedProperty().addListener(focusChangeListener)
                onKeyPressed = EventHandler { event ->
                    // runLater() required for edit commit?
                    runLater { keyEventsHandler.handle(event) }
                }
                styleClass += "track-table-cell"
            }
        }

        override fun setValue(value: String) {
            with(treeTableRow.item.track) {
                nameField?.text = name
                urlField?.text = url.toASCIIString()
            }
        }

        override fun validateValue() {
            if (listOfNotNull(nameField, urlField).all(JFXTextField::validate)) {
                // track's name and URL are updated after a successful validation
                // as a workaround to edit multiple values via EditorNodeBuilder
                with(treeTableRow.item.track) {
                    name = nameField!!.text
                    url = URI(urlField!!.text)
                }
            } else {
                error("Invalid track name or URL")
            }
        }

        override fun cancelEdit() {
            // nothing to do
        }

        override fun startEdit() {
            // runLater() required for name field to get focus
            runLater {
                nameField?.selectAll()
                nameField?.requestFocus()
            }
        }

        override fun getValue(): String? = nameField?.text

        override fun nullEditorNode() {
            nameField = null
            urlField = null
        }

        override fun updateItem(item: String?, empty: Boolean) {
            startEdit()
        }
    }

    init {
        builder = TrackEditorBuilder()
    }

    private companion object {
        val requiredFieldValidator = RequiredFieldValidator("Required field")
        val urlFieldValidator = object : ValidatorBase("Invalid URL") {
            override fun eval() {
                with(getSrcControl() as TextInputControl) {
                    hasErrors.set(runCatching { URI(text) }.isFailure)
                }
            }
        }
    }
}
