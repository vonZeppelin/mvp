package mvp.audio

import java.io.File
import java.net.URI
import java.nio.charset.Charset.defaultCharset
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty

class Track(name: String, url: URI) {
    constructor(name: String, url: String) : this(name, URI(url))

    val nameProperty: StringProperty = SimpleStringProperty(this, this::name.name, name)
    val urlProperty: ObjectProperty<URI> = SimpleObjectProperty(this, this::url.name, url)

    var name: String
        get() = nameProperty.get()
        set(value) = nameProperty.set(value)

    var url: URI
        get() = urlProperty.get()
        set(value) = urlProperty.set(value)
}

fun readM3U(file: File): Sequence<Track> {
    val charset =
        if (file.extension.equals("m3u8", ignoreCase = true))
            Charsets.UTF_8
        else
            defaultCharset()

    return sequence {
        file.useLines(charset) { lines ->
            var trackTitle: String? = null
            for (line in lines) {
                if (!line.startsWith('#')) {
                    yield(Track(trackTitle ?: line, line))
                    trackTitle = null
                } else if (line.startsWith(M3U_INFO_PREFIX)) {
                    trackTitle = line.substringAfter(',')
                }
            }
        }
    }
}

fun writeM3U(tracks: List<Track>, file: File) {
    file.printWriter().use { playlist ->
        playlist.println(M3U_HEADER)
        for (track in tracks) {
            playlist.println("$M3U_INFO_PREFIX-1,${track.name}")
            playlist.println(track.url.toASCIIString())
        }
    }
}

val UNKNOWN_TRACK = Track("N/A", "N/A")

private const val M3U_HEADER = "#EXTM3U"
private const val M3U_INFO_PREFIX = "#EXTINF:"
