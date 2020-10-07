package mvp.ui

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.util.Duration
import mvp.audio.Player
import mvp.audio.Player.Status.PLAYING

class TrackInfo : Label(), EventHandler<ActionEvent> {
    init {
        alignment = Pos.BASELINE_CENTER

        val rate = Duration.seconds(5.0)
        Timeline(KeyFrame(rate, this)).apply {
            cycleCount = Timeline.INDEFINITE

            Player.statusProperty.addListener { _, _, newValue ->
                if (newValue == PLAYING) {
                    // play with a delay of 5% of the rate
                    playFrom(rate.multiply(0.95))
                } else {
                    stop()
                    text = ""
                }
            }
        }
    }

    override fun handle(event: ActionEvent) {
        Player.trackInfo()?.let {
            text = "${it.codec} ${it.bitrate}kbps"
        }
    }
}
