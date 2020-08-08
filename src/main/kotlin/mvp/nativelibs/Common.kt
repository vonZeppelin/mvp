package mvp.nativelibs

import com.sun.jna.FunctionMapper
import com.sun.jna.Pointer

val NULL_PTR: Pointer = Pointer.createConstant(0L)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Fn(val name: String)

val FN_FUNCTION_MAPPER = FunctionMapper { _, method ->
    method.getAnnotation(Fn::class.java)?.name ?: method.name
}
