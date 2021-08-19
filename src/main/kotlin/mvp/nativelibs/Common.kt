package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.FunctionMapper
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.lang.System.mapLibraryName
import java.nio.file.Files
import java.nio.file.Path

val NULL_PTR: Pointer = Pointer.createConstant(0L)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Fn(val name: String)

val FN_FUNCTION_MAPPER = FunctionMapper { _, method ->
    method.getAnnotation(Fn::class.java)?.name ?: method.name
}

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

fun interface Callback0<R> : Callback {
    fun callback(): R
}

fun interface Callback1<V, R> : Callback {
    fun callback(arg: V): R
}

fun interface Callback2<V1, V2, R> : Callback {
    fun callback(arg1: V1, arg2: V2): R
}

fun interface Callback3<V1, V2, V3, R> : Callback {
    fun callback(arg1: V1, arg2: V2, arg3: V3): R
}

fun interface Callback4<V1, V2, V3, V4, R> : Callback {
    fun callback(arg1: V1, arg2: V2, arg3: V3, arg4: V4): R
}
