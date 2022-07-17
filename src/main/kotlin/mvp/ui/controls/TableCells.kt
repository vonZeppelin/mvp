package mvp.ui.controls

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXTextField
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder
import com.jfoenix.controls.cells.editors.base.GenericEditableTreeTableCell
import com.jfoenix.validation.RequiredFieldValidator
import com.jfoenix.validation.base.ValidatorBase
import java.net.URI
import javafx.application.Platform.runLater
import javafx.beans.binding.Bindings.createObjectBinding
import javafx.beans.binding.Bindings.`when` as whenever
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.TextInputControl
import javafx.scene.control.TreeTableColumn.CellDataFeatures
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

private fun loadImage(image: String): ImageView =
    ImageView("/images/$image.png").apply {
        fitHeight = 21.0
        fitWidth = 21.0
    }

val StatusCellFactory = Callback<CellDataFeatures<Track, out Node>, ObservableValue<out Node?>> { cellDataFeatures ->
    createObjectBinding(
        {
            when {
                cellDataFeatures.value.value != Player.track -> null
                Player.status == Status.ERROR -> loadImage("error")
                Player.status == Status.LOADING -> loadImage("loading")
                Player.status == Status.PLAYING -> loadImage("play-cell")
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
                setOnKeyPressed { event ->
                    // runLater() required for edit commit?
                    runLater { keyEventsHandler.handle(event) }
                }
                styleClass += "track-table-editor"
            }
        }

        override fun setValue(value: Any) {
            with(tableRow.item) {
                nameField?.text = name
                urlField?.text = url.toASCIIString()
            }
        }

        override fun validateValue() {
            if (listOfNotNull(nameField, urlField).all(JFXTextField::validate)) {
                // track's name and URL are updated after a successful validation
                // as a workaround to edit multiple values via EditorNodeBuilder
                with(tableRow.item) {
                    name = nameField!!.text
                    url = URI(urlField!!.text)
                }
            } else {
                error("Invalid track name or URL")
            }
        }

        override fun cancelEdit() {
            treeTableView.isEditable = false
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
            treeTableView.isEditable = false
        }

        override fun updateItem(item: Any?, empty: Boolean) {
            startEdit()
        }
    }

    init {
        builder = TrackEditorBuilder()
        styleClass += "track-table-cell"
    }

    override fun getValue(): Any {
        val controlsPanel = HBox(
            // play/stop button
            JFXButton().apply {
                val track = tableRow.item
                val isPlayingThisTrack = Player.trackProperty.isEqualTo(track).and(Player.statusProperty.isEqualTo(Status.PLAYING))
                graphicProperty().bind(
                    whenever(isPlayingThisTrack)
                        .then(loadImage("stop"))
                        .otherwise(loadImage("play"))
                )
                textProperty().bind(
                    whenever(isPlayingThisTrack)
                        .then("Stop")
                        .otherwise("Play")
                )
                setOnAction { if (isPlayingThisTrack.value) Player.stop() else Player.play(track) }
            },
            // edit button
            JFXButton("Edit", loadImage("edit")).apply {
                setOnAction {
                    // workaround for annoying default behaviour of editable (Tree)TableView:
                    // enable its editing on button click (edit start) and disable on edit cancel/commit
                    treeTableView.isEditable = true
                    treeTableView.edit(tableRow.index, tableColumn)
                }
            },
            // horizontal glue
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            // delete button
            JFXButton("Delete", loadImage("delete")).apply {
                setOnAction {
                    treeTableView.root.children -= tableRow.treeItem
                    treeTableView.selectionModel.clearSelection()
                    treeTableView.refresh()
                }
            }
        )

        return Label(item, controlsPanel).apply {
            contentDisplayProperty().bind(
                whenever(tableRow.selectedProperty())
                    .then(ContentDisplay.GRAPHIC_ONLY)
                    .otherwise(ContentDisplay.TEXT_ONLY)
            )
            prefWidth = Double.POSITIVE_INFINITY
        }
    }

    private companion object {
        val requiredFieldValidator = RequiredFieldValidator("Required field")
        val urlFieldValidator = object : ValidatorBase("Invalid URL") {
            private val acceptedProtocols = setOf("http", "https")

            override fun eval() {
                with(getSrcControl() as TextInputControl) {
                    hasErrors.set(
                        runCatching { URI(text).scheme }
                            .map { it.lowercase() !in acceptedProtocols }
                            .getOrDefault(true)
                    )
                }
            }
        }
    }
}
