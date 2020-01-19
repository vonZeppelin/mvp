package mvp.ui

import javafx.beans.binding.Bindings
import javafx.beans.value.WritableValue
import javafx.css.PseudoClass
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.ContextMenu
import javafx.scene.control.TableCell
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import mvp.Track
import java.net.URI

enum class Status { PLAYING, STOPPED }

class StatusCell(status: WritableValue<Pair<Track, Status>>) : TableCell<Track, Status>() {
    init {
        onMouseClicked = EventHandler {
            if (!isEmpty && it.clickCount == 2) {
                status.value = tableRow.item to Status.PLAYING
            }
        }
    }

    override fun updateItem(item: Status?, empty: Boolean) {
        super.updateItem(item, empty)
        graphic = when (item) {
            Status.STOPPED -> ImageView(STOP_ICON)
            Status.PLAYING -> ImageView(PLAY_ICON)
            else -> null
        }
    }

    private companion object {
        private val STOP_ICON = loadImage("/stop.png")
        private val PLAY_ICON = loadImage("/play.png")

        private fun loadImage(path: String): Image = Image(StatusCell::class.java.getResource(path).toExternalForm(), true)
    }
}

class TrackTableCell(contextMenu: ContextMenu) : TableCell<Track, Track>() {

    private val nameField: TextField = TextField()
    private val urlField: TextField = TextField()

    init {
        fun String?.isValidURI(): Boolean = runCatching { URI(this!!) }.isSuccess

        styleClass += CELL_CSS_CLASS
        val onActionHandler = EventHandler<ActionEvent> { evt ->
            val pseudoClasses = nameField.pseudoClassStates + urlField.pseudoClassStates
            if (INVALID_CLASS !in pseudoClasses) {
                item.name = nameField.text
                item.url = URI.create(urlField.text)
                commitEdit(item)
                evt.consume()
            }
        }
        val onKeyReleasedHandler = EventHandler<KeyEvent> { evt ->
            if (evt.code == KeyCode.ESCAPE) {
                cancelEdit();
                evt.consume();
            }
        }

        with(nameField) {
            promptText = "Track name"
            onAction = onActionHandler
            onKeyReleased = onKeyReleasedHandler
            textProperty().addListener { _, _, newValue ->
                pseudoClassStateChanged(INVALID_CLASS, newValue.isNullOrBlank())
            }
        }
        with(urlField) {
            promptText = "Track URL"
            onAction = onActionHandler
            onKeyReleased = onKeyReleasedHandler
            textProperty().addListener { _, _, newValue ->
                pseudoClassStateChanged(INVALID_CLASS, !newValue.isValidURI())
            }
        }

        contextMenuProperty().bind(
            Bindings.`when`(emptyProperty()).then(null as ContextMenu?).otherwise(contextMenu)
        )
    }

    override fun startEdit() {
        super.startEdit()
        if (isEditing) {
            nameField.text = item?.name
            urlField.text = item?.url?.toASCIIString()
            text = null
            graphic = VBox(5.0, nameField, urlField)

            nameField.selectAll()
            nameField.requestFocus()
        }
    }

    override fun cancelEdit() {
        super.cancelEdit()
        text = item?.name
        graphic = null
    }

    override fun updateItem(item: Track?, empty: Boolean) {
        super.updateItem(item, empty)

        when {
            empty -> {
                text = null
                graphic = null
            }
            isEditing -> {
                nameField.text = item?.name
                urlField.text = item?.url?.toASCIIString()
                text = null
                graphic = VBox(5.0, nameField, urlField)
            }
            else -> {
                text = item?.name
                graphic = null
            }
        }
    }

    private companion object {
        private const val CELL_CSS_CLASS = "track-table-cell"
        private val INVALID_CLASS = PseudoClass.getPseudoClass("invalid")
    }
}
