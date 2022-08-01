package mvp.nativelibs

import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.Path

val NULL_PTR: Pointer = Pointer.createConstant(0L)

fun loadLibraries(vararg libraries: String, block: (String, Path) -> Unit) {
    val tempDir = Files.createTempDirectory("mvp")
    libraries
        // eagerly unpack all libs to the same folder and load them with block()
        .map { library ->
            val libraryResource = mapLibraryName(library).let {
                if (Platform.isMac()) it.replace("jnilib", "dylib") else it
            }
            val libraryPath = tempDir.resolve(libraryResource)

            block.javaClass.getResourceAsStream("/libs/$libraryResource").use {
                Files.copy(it, libraryPath)
            }
            NativeLibrary.addSearchPath(library, tempDir.toString())
            block(library, libraryPath)

            libraryPath
        }
        // delete all files once they're loaded, won't work on Win 🤷
        .forEach(Files::delete)
    // delete the temp dir as it's empty by now
    Files.delete(tempDir)
}
