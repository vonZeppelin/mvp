package mvp.ui

import java.nio.file.Paths
import javafx.application.Platform
import javafx.beans.binding.Bindings.`when` as whenever
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Slider
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeTableColumn
import javafx.scene.control.TreeTableView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Callback
import mvp.audio.Player
import mvp.audio.Player.Status
import mvp.audio.Track
import mvp.audio.readM3U
import mvp.audio.writeM3U
import mvp.ui.controls.AboutDialog
import mvp.ui.controls.StatusCellFactory
import mvp.ui.controls.TrackCell
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

const val ARROW_HEIGHT = 10.0
@JvmField val ROOT_PADDING: Insets = Insets(ARROW_HEIGHT * 2, ARROW_HEIGHT, ARROW_HEIGHT, ARROW_HEIGHT)

class Controller(private val stage: Stage) {
    @FXML private lateinit var appMenu: ContextMenu
    @FXML private lateinit var autohide: CheckMenuItem
    @FXML private lateinit var instaPause: CheckMenuItem
    @FXML private lateinit var volume: Slider
    @FXML private lateinit var playlist: TreeTableView<Track>
    @FXML private lateinit var statusCol: TreeTableColumn<Track, out Node>
    @FXML private lateinit var trackCol: TreeTableColumn<Track, String>

    private lateinit var trayIcon: TrayIcon

    @FXML fun aboutApp() {
        AboutDialog().showAndWait()
    }

    @FXML fun addTrack() {
        playlist.root.children += TreeItem(Track("New track", ""))
        playlist.refresh()
    }

    @FXML fun exitApp() {
        Player.destroy()
        writeM3U(playlist.root.children.map { it.value }, mvpPlaylist)
        exitProcess(0)
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
        // hide track controls on Escape
        playlist.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ESCAPE && playlist.editingCell == null) {
                playlist.selectionModel.clearSelection()
            }
        }
        playlist.root = TreeItem<Track>().apply {
            if (mvpPlaylist.exists()) {
                children += readM3U(mvpPlaylist).map(::TreeItem)
            }
        }
        statusCol.cellValueFactory = StatusCellFactory
        trackCol.cellFactory = Callback { TrackCell() }
        trackCol.cellValueFactory = Callback {
            val track = it.value.value
            whenever(Player.trackProperty.isEqualTo(track).and(Player.statusMessageProperty.isNotEmpty))
                .then(Player.statusMessageProperty)
                .otherwise(track.nameProperty)
        }

        with(instaPause.selectedProperty()) {
            Player.instaPauseProperty.bind(this)
            Settings.bind(this, "instapause", true)
        }
        with(autohide.selectedProperty()) {
            Settings.bind(this, "autohide", true)
        }

        with(volume.valueProperty()) {
            Player.volumeProperty.bind(this)
            Settings.bind(this, "volume", 100.0)
        }

        Player.statusProperty.addListener { _, _, newValue ->
            val newIcon = when (newValue) {
                Status.LOADING, Status.PLAYING -> "stop"
                Status.ERROR, Status.STANDBY -> "play"
                else -> return@addListener
            }
            SwingUtilities.invokeLater { trayIcon.image = loadIcon(newIcon) }
        }
        Player.statusMessageProperty.addListener { _, _, newValue ->
            SwingUtilities.invokeLater { trayIcon.toolTip = newValue }
        }

        SwingUtilities.invokeAndWait {
            trayIcon = TrayIcon(loadIcon("play"))
            trayIcon.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    Platform.runLater {
                        when {
                            SwingUtilities.isRightMouseButton(e) ->
                                if (stage.isShowing) stage.hide() else showUI(e.x.toDouble(), e.y.toDouble())
                            else -> toggleTrack()
                        }
                    }
                }
            })
        }
        SystemTray.getSystemTray().add(trayIcon)
    }

    val isAutohideEnabled: Boolean
        get() = autohide.isSelected

    fun toggleTrack() {
        when (Player.status) {
            Status.LOADING, Status.PLAYING -> Player.stop()
            else ->
                (playlist.selectionModel.selectedItem ?: playlist.root.children.firstOrNull())
                    ?.let { Player.play(it.value) }
        }
    }

    fun playNextTrack(reverse: Boolean = false) {
        val siblingProvider: (TreeItem<Track>) -> TreeItem<Track>? =
            if (reverse) TreeItem<Track>::previousSibling else TreeItem<Track>::nextSibling

        playlist.root.children.find { it.value == Player.track }
            ?.let(siblingProvider)
            ?.run { Player.play(value) }
    }

    // TODO Proper positioning, East-West support?
    private fun showUI(x: Double, y: Double) {
        val scene = if (stage.isShowing) return else stage.scene

        val screenBounds = Screen.getScreensForRectangle(x, y, 1.0, 1.0)
            .single()
            .visualBounds
        val arrowheadX = x.coerceIn(screenBounds.minX, screenBounds.maxX)
        val arrowheadY = y.coerceIn(screenBounds.minY, screenBounds.maxY)

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

private fun loadIcon(icon: String): Image =
    Controller::class.java.getResourceAsStream("/images/$icon.png").use(ImageIO::read)

private val mvpPlaylist = Paths.get(System.getProperty("user.home"), "mvp.m3u8").toFile()
