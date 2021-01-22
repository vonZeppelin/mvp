package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import kotlin.text.Charsets.UTF_8

@Structure.FieldOrder("width", "height")
open class NSSize(@JvmField var width: Double = 0.0, @JvmField var height: Double = 0.0) : Structure() {
    class ByValue(width: Double = 0.0, height: Double = 0.0) : NSSize(width, height), Structure.ByValue
}

@Structure.FieldOrder("x", "y")
class NSPoint(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0) : Structure()

@Structure.FieldOrder("origin", "size")
class NSRect(@JvmField var origin: NSPoint = NSPoint(), @JvmField var size: NSSize = NSSize()) : Structure()

interface ObjC : Library {
    fun class_addMethod(cls: Pointer, sel: Pointer, imp: Callback, types: String): Boolean
    fun objc_allocateClassPair(supercls: Pointer, name: String, extraBytes: Int): Pointer
    fun objc_disposeClassPair(cls: Pointer)
    fun objc_getClass(className: String): Pointer
    @Fn("objc_msgSend") fun objc_msgSend_dret(receiver: Pointer, sel: Pointer, vararg args: Any?): Double
    @Fn("objc_msgSend") fun objc_msgSend_lret(receiver: Pointer, sel: Pointer, vararg args: Any?): Long
    @Fn("objc_msgSend") fun objc_msgSend_pret(receiver: Pointer, sel: Pointer, vararg args: Any?): Pointer?
    fun objc_registerClassPair(cls: Pointer)
    fun sel_registerName(name: String): Pointer
}

val OBJC: ObjC = Native.load(
    "objc.A", ObjC::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to FN_FUNCTION_MAPPER)
)

val NSApp: Pointer
    get() = "NSApplication".nsClass().msgSend("sharedApplication")

fun String.nsClass(): Pointer = OBJC.objc_getClass(this)

fun String.nsSelector(): Pointer = OBJC.sel_registerName(this)

inline fun <reified T> Pointer.msgSend(sel: String, vararg args: Any?): T =
    when (val clazz = T::class) {
        Boolean::class -> (OBJC.objc_msgSend_lret(this, sel.nsSelector(), *args) == 1L) as T
        Double::class -> OBJC.objc_msgSend_dret(this, sel.nsSelector(), *args) as T
        Long::class -> OBJC.objc_msgSend_lret(this, sel.nsSelector(), *args) as T
        Pointer::class -> (OBJC.objc_msgSend_pret(this, sel.nsSelector(), *args) ?: NULL_PTR) as T
        else -> throw UnsupportedOperationException("Unsupported return type $clazz")
    }

fun <T: Structure> Pointer.msgSendS(sel: String, ret: T): T {
    val selector = sel.nsSelector()
    val thisClass = this.msgSend<Pointer>("class")
    val nsInvocation = "NSInvocation".nsClass()
        .msgSend<Pointer>(
            "invocationWithMethodSignature:",
            thisClass.msgSend<Pointer>(
                // check if extension receiver is a NSClass
                if (this == thisClass) "methodSignatureForSelector:" else "instanceMethodSignatureForSelector:",
                selector
            )
        )
    nsInvocation.msgSend<Pointer>("setSelector:", selector)
    nsInvocation.msgSend<Pointer>("invokeWithTarget:", this)
    return ret.apply {
        nsInvocation.msgSend<Pointer>("getReturnValue:", this)
    }
}

fun Pointer.performInMainThread(sel: String, obj: Any = NULL_PTR, wait: Boolean = false) {
    msgSend<Pointer>("performSelectorOnMainThread:withObject:waitUntilDone:", sel.nsSelector(), obj, wait)
}

fun String.nsString(): Pointer {
    val nsStringClass = "NSString".nsClass()
    return if (isEmpty()) {
        nsStringClass.msgSend("string")
    } else {
        val nsUTF8StringEncoding = 4
        val utfBytes = toByteArray(charset = UTF_8)
        nsStringClass
            .msgSend<Pointer>("alloc")
            .msgSend("initWithBytes:length:encoding:", utfBytes, utfBytes.size, nsUTF8StringEncoding)
    }
}

fun Pointer.javaString(): String = this.msgSend<Pointer>("UTF8String").getString(0, UTF_8.name())

operator fun Pointer.get(index: Int): Pointer = msgSend("objectAtIndex:", index)

fun Pointer.asSequence(): Sequence<Pointer> =
    msgSend<Pointer>("objectEnumerator").let { enum ->
        generateSequence { enum.msgSend<Pointer>("nextObject").takeIf { it != NULL_PTR } }
    }

fun nsArrayOf(vararg args: Pointer): Pointer {
    val nsArrayClass = "NSArray".nsClass()
    return if (args.isEmpty()) {
        nsArrayClass.msgSend("array")
    } else {
        nsArrayClass.msgSend("arrayWithObjects:count:", args, args.size)
    }
}

fun <T> autoreleasepool(block: () -> T): T {
    val pool = "NSAutoreleasePool".nsClass()
        .msgSend<Pointer>("alloc")
        .msgSend<Pointer>("init")
    try {
        return block()
    } finally {
        pool.msgSend<Pointer>("drain")
    }
}
