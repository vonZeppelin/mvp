package mvp.ui

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder
import com.jfoenix.controls.cells.editors.base.GenericEditableTreeTableCell
import com.jfoenix.validation.RequiredFieldValidator
import com.jfoenix.validation.base.ValidatorBase
import java.net.URI
import java.util.concurrent.Callable
import javafx.application.Platform.runLater
import javafx.beans.binding.Bindings
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.TextInputControl
import javafx.scene.control.TreeTableColumn.CellDataFeatures
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.Callback
import mvp.audio.Player
import mvp.audio.Player.Status
import mvp.audio.Track

val StatusCellFactory = Callback<CellDataFeatures<Track, out Node>, ObservableValue<out Node?>> { cellDataFeatures ->
    Bindings.createObjectBinding(
        Callable {
            when {
                cellDataFeatures.value.value != Player.track -> null
                Player.status == Status.ERROR -> ImageView("/images/error.png")
                Player.status == Status.LOADING -> ImageView("/images/loading.png")
                Player.status == Status.PLAYING -> ImageView("/images/play-cell.png")
                else -> null
            }
        },
        Player.statusProperty, Player.trackProperty
    )
}

class TrackCell : GenericEditableTreeTableCell<Track, String>(null) {
    private inner class TrackEditorBuilder : EditorNodeBuilder<Any> {
        private var nameField: JFXTextField? = null
        private var urlField: JFXTextField? = null

        override fun createNode(
            value: Any,
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

        override fun setValue(value: Any) {
            with(treeTableRow.item) {
                nameField?.text = name
                urlField?.text = url.toASCIIString()
            }
        }

        override fun validateValue() {
            if (listOfNotNull(nameField, urlField).all(JFXTextField::validate)) {
                // track's name and URL are updated after a successful validation
                // as a workaround to edit multiple values via EditorNodeBuilder
                with(treeTableRow.item) {
                    name = nameField!!.text
                    url = URI(urlField!!.text)
                }
            } else {
                error("Invalid track name or URL")
            }
        }

        override fun cancelEdit() {
            treeTableView.refresh()
        }

        override fun startEdit() {
            // runLater() required for name field to get focus
            runLater {
                nameField?.selectAll()
                nameField?.requestFocus()
            }
        }

        override fun getValue(): Any? = nameField?.text

        override fun nullEditorNode() {
            nameField = null
            urlField = null
        }

        override fun updateItem(item: Any?, empty: Boolean) {
            startEdit()
        }
    }

    init {
        builder = TrackEditorBuilder()
    }

    override fun getValue(): Any {
        val deleteButton = JFXButton(null, ImageView(deleteIcon)).apply {
            isDisableVisualFocus = true
            onAction = EventHandler {
                treeTableView.root.children -= treeTableRow.treeItem
                treeTableView.refresh()
            }
            visibleProperty().bind(treeTableRow.hoverProperty())
        }
        val spacer = Region().apply {
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        return HBox(Label(item), spacer, deleteButton).apply {
            alignment = Pos.CENTER
        }
    }

    private companion object {
        val deleteIcon = Image("/images/delete.png", 16.0, 16.0, true, false)
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
