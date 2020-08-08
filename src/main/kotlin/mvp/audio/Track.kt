package mvp.audio

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.io.File
import java.net.URI
import java.nio.charset.Charset.defaultCharset

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

fun readM3U(file: File): List<Track> {
    val charset =
        if (file.extension.equals("m3u8", ignoreCase = true))
            Charsets.UTF_8
        else
            defaultCharset()

    return file.useLines(charset) {
        val lines = it.iterator()
        when {
            !lines.hasNext() -> emptyList()
            lines.next() != M3U_HEADER -> error("$file is not a M3U playlist")
            else -> mutableListOf<Track>().apply {
                while (lines.hasNext()) {
                    val line = lines.next()
                    add(
                        if (line.startsWith(M3U_INFO_PREFIX))
                            Track(
                                line.substringAfter(','),
                                if (lines.hasNext())
                                    lines.next()
                                else
                                    error("$file is not a valid M3U playlist")
                            )
                        else
                            Track("Unknown track", line)
                    )
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
