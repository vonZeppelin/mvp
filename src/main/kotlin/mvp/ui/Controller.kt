package mvp.ui

import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener.Change as ListChange
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.geometry.Side
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.stage.FileChooser
import javafx.util.Callback
import java.net.URI
import java.nio.file.Paths

import mvp.App
import mvp.Track
import mvp.nativelibs.LibBASS
import mvp.nativelibs.Player
import mvp.nativelibs.StatusBar
import mvp.readM3U
import mvp.writeM3U
import java.util.concurrent.Callable
import kotlin.system.exitProcess

class Controller(private val app: App) {
    @FXML private lateinit var appMenu: ContextMenu
    @FXML private lateinit var trackMenu: ContextMenu
    @FXML private lateinit var showAuxIcons: CheckMenuItem
    @FXML private lateinit var playlist: TableView<Track>

    private val status = SimpleObjectProperty<Pair<Track, Status>>(this, "status", null).apply {
        var player: Player? = null
        addListener {_, _, (track, status) ->
            when (status) {
                Status.PLAYING ->
                    if (player?.track == track) {
                        player?.play()
                    } else {
                        player?.stop()
                        player = LibBASS.createPlayer(track).apply { play() }
                    }
                Status.STOPPED -> player?.stop()
            }
        }
    }

    @FXML fun aboutApp() {
        Alert(AlertType.INFORMATION, aboutApp, ButtonType.CLOSE)
            .apply {
                headerText = null
                title = "About MVP"
            }
            .showAndWait()
    }

    @FXML fun addTrack() {
        playlist.items.add(Track("New track", URI.create("")))
    }

    @FXML fun deleteTrack() {
        playlist.items.remove(playlist.selectionModel.selectedItem)
    }

    @Suppress("UNCHECKED_CAST")
    @FXML fun editTrack() {
        with(playlist) {
            val selectedCell = selectionModel.selectedCells.single()
            edit(selectedCell.row, selectedCell.tableColumn as TableColumn<Track, *>?)
        }
    }

    @FXML fun playTrack() {
        status.set(playlist.selectionModel.selectedItem to Status.PLAYING)
    }

    @FXML fun toggleTrack() {
        TODO()
    }

    @FXML fun exitApp() {
        StatusBar.stop()
        exitProcess(0)
    }

    @FXML fun importPlaylist() {
        val playlists = FileChooser()
            .apply { extensionFilters += FileChooser.ExtensionFilter("Playlists", "*.m3u", "*.m3u8") }
            .showOpenMultipleDialog(app.primaryStage)
            ?: emptyList()
        playlist.items.addAll(playlists.flatMap(::readM3U))
    }

    @FXML fun showAppMenu(evt: ActionEvent): Unit = appMenu.show(evt.source as Node, Side.LEFT, 0.0, 0.0)

    @FXML private fun initialize() {
        app.uiManager.showAuxIcons.bind(showAuxIcons.selectedProperty())

        val mvpPlaylist = Paths.get(System.getProperty("user.home"), "mvp.m3u8").toFile()
        val tracks = if (mvpPlaylist.exists()) readM3U(mvpPlaylist) else arrayListOf()
        playlist.items = FXCollections.observableList(tracks) { arrayOf(it.nameProperty, it.urlProperty) }.apply {
            addListener { change: ListChange<out Track> -> writeM3U(change.list, mvpPlaylist) }
        }

        val (statusCol, trackCol) = playlist.columns
        statusCol.cellValueFactory = Callback { cell ->
            Bindings.createObjectBinding(
                Callable {
                    val status = status.value
                    when {
                        status == null -> null
                        status.first == cell.value-> status.second
                        else -> null
                    }
                },
                status
            )
        }
        statusCol.cellFactory = Callback { StatusCell(status) }
        trackCol.cellValueFactory = Callback { cell -> ReadOnlyObjectWrapper(cell.value) }
        trackCol.cellFactory = Callback { TrackTableCell(trackMenu) }
    }
}

private const val aboutApp = "Minimal Viable Player - lives in system tray and plays streaming audio.\n\n\u00a9 2019, Leonid Bogdanov"
