package mvp.ui

import com.jfoenix.animation.alert.JFXAlertAnimation
import com.jfoenix.controls.JFXAlert
import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialogLayout
import com.sun.jna.Pointer
import java.io.InputStream
import java.nio.file.Paths
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeTableColumn
import javafx.scene.control.TreeTableView
import javafx.scene.layout.Region
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Callback
import mvp.audio.Player
import mvp.audio.Player.Status
import mvp.audio.Track
import mvp.audio.readM3U
import mvp.audio.writeM3U
import mvp.nativelibs.NSApp
import mvp.nativelibs.javaString
import mvp.nativelibs.msgSend

const val ARROW_HEIGHT = 10.0
@JvmField val ROOT_PADDING: Insets = Insets(ARROW_HEIGHT * 2, ARROW_HEIGHT, ARROW_HEIGHT, ARROW_HEIGHT)

class Controller(private val stage: Stage) {
    @FXML private lateinit var appMenu: ContextMenu
    @FXML private lateinit var instaPause: CheckMenuItem
    @FXML private lateinit var showAuxIcons: CheckMenuItem
    @FXML private lateinit var playlist: TreeTableView<Track>
    @FXML private lateinit var statusCol: TreeTableColumn<Track, out Node>
    @FXML private lateinit var trackCol: TreeTableColumn<Track, String>

    private val statusBar: StatusBar = StatusBar { (id, location, isRightButton) ->
        when {
            isRightButton -> if (stage.isShowing) stage.hide() else showUI(location)
            id == PLAY_ICON_ID -> toggleTrack()
            id == PREVIOUS_ICON_ID -> playNextTrack(reverse = true)
            id == NEXT_ICON_ID -> playNextTrack()
        }
    }
    private val isDarkTheme: Boolean by lazy {
        NSApp.msgSend<Pointer>("effectiveAppearance")
            .msgSend<Pointer>("name")
            .javaString()
            .contains("dark", ignoreCase = true)
    }

    @FXML fun aboutApp() {
        JFXAlert<Unit>()
            .apply {
                animation = JFXAlertAnimation.NO_ANIMATION
                setContent(
                    JFXDialogLayout().apply {
                        setHeading(Label("About MVP"))
                        setBody(Text(ABOUT_APP))
                        setActions(
                            JFXButton("Close").apply {
                                setOnAction { close() }
                            }
                        )
                    }
                )
            }
            .showAndWait()
    }

    @FXML fun addTrack() {
        playlist.root.children += TreeItem(Track("New track", ""))
        playlist.refresh()
    }

    @FXML fun editTrack() {
        val selectedCell = playlist.selectionModel.selectedCells.single()
        playlist.edit(selectedCell.row, selectedCell.tableColumn)
    }

    @FXML fun exitApp() {
        Player.destroy()
        statusBar.destroy()
        writeM3U(playlist.root.children.map { it.value }, mvpPlaylist)
        Platform.exit()
    }

    @FXML fun openPlaylist() {
        FileChooser()
            .apply { extensionFilters += FileChooser.ExtensionFilter("Playlists", "*.m3u", "*.m3u8") }
            .showOpenMultipleDialog(stage)
            ?.flatMap(::readM3U)
            ?.let { playlist.root.children += it.map(::TreeItem) }
    }

    @FXML fun showAppMenu(evt: ActionEvent) {
        appMenu.show(evt.source as Node, Side.LEFT, 0.0, 0.0)
    }

    @FXML fun initialize() {
        playlist.root = TreeItem<Track>().apply {
            if (mvpPlaylist.exists()) {
                children += readM3U(mvpPlaylist).map(::TreeItem)
            }
        }
        statusCol.cellValueFactory = StatusCellFactory
        trackCol.cellFactory = Callback { TrackCell() }
        trackCol.cellValueFactory = Callback { it.value.value.nameProperty }

        Player.instaPauseProperty.bind(instaPause.selectedProperty())
        Player.statusProperty.addListener { _, _, newValue ->
            val newIcon = when (newValue) {
                Status.LOADING, Status.PLAYING -> "/stop.png"
                Status.ERROR, Status.STANDBY -> "/play.png"
                else -> return@addListener
            }
            statusBar.updateIcon(PLAY_ICON_ID, loadIcon(newIcon))
        }
        showAuxIcons.selectedProperty().addListener { _, _, newValue ->
            hideTrayIcons()
            showTrayIcons(newValue)
        }
        showTrayIcons()
    }

    fun toggleTrack() {
        when (Player.status) {
            Status.LOADING, Status.PLAYING -> Player.stop()
            else -> (playlist.selectionModel.selectedItem ?: playlist.root.children.firstOrNull())?.let { Player.play(it.value) }
        }
    }

    fun playNextTrack(reverse: Boolean = false) {
        val siblingProvider: (TreeItem<Track>) -> TreeItem<Track>? =
            if (reverse) TreeItem<Track>::previousSibling else TreeItem<Track>::nextSibling

        playlist.root.children.find { it.value == Player.track }
            ?.let(siblingProvider)
            ?.run { Player.play(value) }
    }

    private fun showTrayIcons(showAuxIcons: Boolean = false) {
        if (showAuxIcons) {
            statusBar.addIcon(NEXT_ICON_ID, loadIcon("/next.png"))
        }
        statusBar.addIcon(
            PLAY_ICON_ID,
            loadIcon(
                if (Player.status == Status.PLAYING) "/stop.png" else "/play.png"
            )
        )
        if (showAuxIcons) {
            statusBar.addIcon(PREVIOUS_ICON_ID, loadIcon("/previous.png"))
        }
    }

    private fun hideTrayIcons() {
        listOf(PREVIOUS_ICON_ID, PLAY_ICON_ID, NEXT_ICON_ID).forEach(statusBar::removeIcon)
    }

    // TODO Proper positioning, East-West support?
    private fun showUI(click: Point2D) {
        val scene = if (stage.isShowing) return else stage.scene

        val screenBounds = Screen.getScreensForRectangle(click.x, click.y, 1.0, 1.0)
            .single()
            .visualBounds
        val arrowheadX = click.x.coerceIn(screenBounds.minX, screenBounds.maxX)
        val arrowheadY = click.y.coerceIn(screenBounds.minY, screenBounds.maxY)

        stage.x = arrowheadX - scene.width / 2
        stage.y = arrowheadY

        (scene.root as Region).shape = Shape.union(
            Rectangle(ARROW_HEIGHT, ARROW_HEIGHT, scene.width - ARROW_HEIGHT * 2, scene.height - ARROW_HEIGHT * 2).apply {
                arcWidth = 10.0
                arcHeight = 10.0
            },
            Polygon(
                scene.width / 2 - ARROW_HEIGHT, ARROW_HEIGHT,
                scene.width / 2, 0.0,
                scene.width / 2 + ARROW_HEIGHT, ARROW_HEIGHT
            )
        )

        stage.show()
        stage.toFront()
    }
}

private fun loadIcon(iconPath: String): ByteArray =
    Controller::class.java.getResourceAsStream(iconPath).use(InputStream::readBytes)

private const val NEXT_ICON_ID = "next"
private const val PLAY_ICON_ID = "play/stop"
private const val PREVIOUS_ICON_ID = "previous"
private const val ABOUT_APP = "Minimal Viable Player - lives in system tray and plays streaming audio.\n\n\u00a9 2020, Leonid Bogdanov"
private val mvpPlaylist = Paths.get(System.getProperty("user.home"), "mvp.m3u8").toFile()
