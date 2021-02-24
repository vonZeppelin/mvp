package mvp.ui.controls

import javafx.concurrent.ScheduledService
import javafx.concurrent.Task
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.util.Duration
import mvp.audio.Player
import mvp.audio.Player.Status.PLAYING
import mvp.audio.TrackInfo as Info

class TrackInfo : Label() {
    init {
        alignment = Pos.BASELINE_CENTER

        val trackInfoService = object : ScheduledService<Info?>() {
            init { period = Duration.seconds(5.0) }

            override fun createTask(): Task<Info?> =
                object : Task<Info?>() {
                    override fun call(): Info? = Player.trackInfo()
                }

            override fun succeeded() {
                super.succeeded()
                lastValue?.let {
                    text = "${it.codec} ${it.bitrate}kbps"
                }
            }

            override fun cancelled() {
                super.cancelled()
                text = ""
            }
        }
        Player.statusProperty.addListener { _, _, newValue ->
            if (newValue == PLAYING) trackInfoService.restart() else trackInfoService.cancel()
        }
    }
}
