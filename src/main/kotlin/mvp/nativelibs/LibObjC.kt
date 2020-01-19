package mvp.nativelibs

import com.sun.jna.Callback
import com.sun.jna.CallbackThreadInitializer
import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import javafx.geometry.Point2D
import mvp.nativelibs.ObjC.Companion.NULL
import mvp.nativelibs.ObjC.Companion.javaString
import mvp.nativelibs.ObjC.Companion.msgSendD
import mvp.nativelibs.ObjC.Companion.msgSendL
import mvp.nativelibs.ObjC.Companion.msgSendP
import mvp.nativelibs.ObjC.Companion.msgSendS
import mvp.nativelibs.ObjC.Companion.nsArray
import mvp.nativelibs.ObjC.Companion.nsClass
import mvp.nativelibs.ObjC.Companion.nsSelector
import mvp.nativelibs.ObjC.Companion.nsString
import mvp.nativelibs.ObjC.NSPoint
import mvp.nativelibs.ObjC.NSRect
import mvp.nativelibs.ObjC.NSSize
import kotlin.reflect.full.createInstance

data class Event(val id: String, val location: Point2D, val isRightButton: Boolean)

val NOOP_EVENT_LISTENER: (Event) -> Unit = {}

object StatusBar {
    private object AddItem : Callback {
        fun callback(self: Pointer, cmd: Pointer, args: Pointer) {
            val itemId = args.msgSendP("objectAtIndex:", 0)
            val imageData = args.msgSendP("objectAtIndex:", 1)

            val statusBar = "NSStatusBar".nsClass()
                .msgSendP("systemStatusBar")
            val statusBarThickness = statusBar.msgSendD("thickness")

            val image = "NSImage".nsClass()
                .msgSendP("alloc")
                .msgSendP("initWithData:", imageData)
            image.msgSendP("setTemplate:", true)
            image.msgSendP("setSize:", NSSize.ByValue(statusBarThickness, statusBarThickness))

            val itemButton = statusBar
                .msgSendP("statusItemWithLength:", -1.0)
                .msgSendP("retain")
                .msgSendP("button")
            itemButton.msgSendP("setImage:", image)
            itemButton.msgSendP("setTarget:", self)
            itemButton.msgSendP("sendActionOn:", 10)
            itemButton.msgSendP("setAction:", "itemClicked:".nsSelector())
            itemButton.msgSendP("setTag:", itemId.msgSendP("retain"))
        }
    }
    private object RemoveItem : Callback {
        fun callback(self: Pointer, cmd: Pointer, args: Pointer) {
            val itemId = args.msgSendP("objectAtIndex:", 0)
            val statusBar = "NSStatusBar".nsClass()
                .msgSendP("systemStatusBar")
            val nsStatusBarWindowClass = "NSStatusBarWindow".nsClass()
            val windowsEnum = "NSApplication".nsClass()
                .msgSendP("sharedApplication")
                .msgSendP("windows")
                .msgSendP("objectEnumerator")
            generateSequence { windowsEnum.msgSendP("nextObject") }
                .filter { it.msgSendL("isKindOfClass:", nsStatusBarWindowClass) == 1L }
                .map { it.msgSendP("statusItem") }
                .filter {
                    it.msgSendP("button")
                        .msgSendP("tag")
                        .msgSendL("isEqualToString:", itemId) == 1L
                }
                .forEach {
                    it.msgSendP("button")
                        .msgSendP("tag")
                        .msgSendP("release")
                    it.msgSendP("release")
                    statusBar.msgSendP("removeStatusItem:", it)
                }
        }
    }
    private object Start : Callback {
        fun callback(self: Pointer, cmd: Pointer) {
            "NSApplication".nsClass()
                .msgSendP("sharedApplication")
                .msgSendP("run")
        }
    }
    private object ItemClicked : Callback {
        fun callback(self: Pointer, cmd: Pointer, source: Pointer) {
            val mouseLocation = "NSEvent".nsClass()
                .msgSendS<NSPoint>("mouseLocation")
            val currentEvent = "NSApplication".nsClass()
                .msgSendP("sharedApplication")
                .msgSendP("currentEvent")
            val screenSize = "NSScreen".nsClass()
                .msgSendP("mainScreen")
                .msgSendS<NSRect>("frame")
            eventListener(
                Event(
                    id = source.msgSendP("tag").javaString(),
                    location = Point2D(mouseLocation.x, screenSize.size.height - mouseLocation.y),
                    isRightButton = currentEvent.msgSendL("buttonNumber") != 0L
                )
            )
        }
    }
    private val appDelegateClass: Pointer
    @Volatile private var appDelegate: Pointer = NULL
    @Volatile private var eventListener: (Event) -> Unit = NOOP_EVENT_LISTENER

    init {
        CallbackThreadInitializer(true, false).run {
            Native.setCallbackThreadInitializer(AddItem, this)
            Native.setCallbackThreadInitializer(ItemClicked, this)
            Native.setCallbackThreadInitializer(RemoveItem, this)
            Native.setCallbackThreadInitializer(Start, this)
        }
        appDelegateClass = with(ObjC.INSTANCE) {
            objc_allocateClassPair("NSObject".nsClass(), "AppDelegate", 0).apply {
                class_addMethod(this, "addItem:".nsSelector(), AddItem, "v@:@:@")
                class_addMethod(this, "itemClicked:".nsSelector(), ItemClicked, "v@:@:@")
                class_addMethod(this, "removeItem:".nsSelector(), RemoveItem, "v@:@:@")
                class_addMethod(this, "start".nsSelector(), Start, "v@:@")
                objc_registerClassPair(this)
            }
        }
    }

    fun start(listener: (Event) -> Unit) {
        eventListener = listener
        appDelegate = appDelegateClass.msgSendP("alloc").msgSendP("init")
        appDelegate.performInMainThread("start")
    }

    fun stop() {
        eventListener = NOOP_EVENT_LISTENER
        appDelegate = NULL
        "NSApplication".nsClass()
            .msgSendP("sharedApplication")
            .msgSendP("terminate:", NULL)
    }

    fun addIcon(id: String, icon: ByteArray) {
        val imageData = "NSData".nsClass().msgSendP("dataWithBytes:length:", icon, icon.size)
        appDelegate.performInMainThread("addItem:", nsArray(id.nsString(), imageData))
    }

    fun removeIcon(id: String) {
        appDelegate.performInMainThread("removeItem:", nsArray(id.nsString()))
    }

    private fun Pointer.performInMainThread(sel: String, obj: Any? = NULL) {
        msgSendP("performSelectorOnMainThread:withObject:waitUntilDone:", sel.nsSelector(), obj, false)
    }
}

private interface ObjC : Library {
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Fn(val value: String)

    @Structure.FieldOrder("width", "height")
    open class NSSize(@JvmField var width: Double, @JvmField var height: Double) : Structure() {
        constructor() : this(0.0, 0.0)
        class ByValue(width: Double, height: Double) : NSSize(width, height), Structure.ByValue
    }

    @Structure.FieldOrder("x", "y")
    class NSPoint : Structure() {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
    }

    @Structure.FieldOrder("origin", "size")
    class NSRect : Structure() {
        @JvmField var origin: NSPoint = NSPoint()
        @JvmField var size: NSSize = NSSize()
    }

    fun class_addMethod(cls: Pointer, sel: Pointer, imp: Callback, types: String): Boolean
    fun objc_allocateClassPair(supercls: Pointer, name: String, extraBytes: Int): Pointer
    fun objc_getClass(className: String): Pointer
    @Fn("objc_msgSend")
    fun objc_msgSend_pret(receiver: Pointer, sel: Pointer, vararg args: Any?): Pointer
    @Fn("objc_msgSend")
    fun objc_msgSend_lret(receiver: Pointer, sel: Pointer, vararg args: Any?): Long
    @Fn("objc_msgSend")
    fun objc_msgSend_dret(receiver: Pointer, sel: Pointer, vararg args: Any?): Double
    fun objc_registerClassPair(cls: Pointer)
    fun sel_registerName(name: String): Pointer

    companion object {
        val NULL: Pointer = Pointer.createConstant(0)
        val INSTANCE: ObjC = Native.load(
            "objc.A",
            ObjC::class.java,
            mapOf(
                Library.OPTION_FUNCTION_MAPPER to FunctionMapper { _, method ->
                    method.getAnnotation(Fn::class.java)?.value ?: method.name
                }
            )
        )

        fun String.nsClass(): Pointer = INSTANCE.objc_getClass(this)

        fun String.nsSelector(): Pointer = INSTANCE.sel_registerName(this)

        fun String.nsString(): Pointer {
            val nsStringClass = "NSString".nsClass()
            return if (isEmpty()) {
                nsStringClass.msgSendP("string")
            } else {
                val utfBytes = toByteArray(charset = Charsets.UTF_8)
                nsStringClass
                    .msgSendP("alloc")
                    .msgSendP("initWithBytes:length:encoding:", utfBytes, utfBytes.size, 4)
            }
        }

        fun Pointer.msgSendP(sel: String, vararg args: Any?): Pointer =
            INSTANCE.objc_msgSend_pret(this, sel.nsSelector(), *args)

        fun Pointer.msgSendL(sel: String, vararg args: Any?): Long =
            INSTANCE.objc_msgSend_lret(this, sel.nsSelector(), *args)

        fun Pointer.msgSendD(sel: String, vararg args: Any?): Double =
            INSTANCE.objc_msgSend_dret(this, sel.nsSelector(), *args)

        inline fun <reified T: Structure> Pointer.msgSendS(sel: String): T {
            val selector = sel.nsSelector()
            val thisClass = this.msgSendP("class")
            val nsInvocation = "NSInvocation".nsClass()
                .msgSendP(
                    "invocationWithMethodSignature:",
                    thisClass.msgSendP(
                        if (this == thisClass)
                            "methodSignatureForSelector:"
                        else
                            "instanceMethodSignatureForSelector:",
                        selector
                    )
                )
            nsInvocation.msgSendP("setSelector:", selector)
            nsInvocation.msgSendP("invokeWithTarget:", this)
            return T::class.createInstance().apply {
                nsInvocation.msgSendP("getReturnValue:", this)
            }
        }

        fun Pointer.javaString(): String = this.msgSendP("UTF8String").getString(0, "UTF-8")

        fun autoreleasepool(block: () -> Unit) {
            val pool = "NSAutoreleasePool".nsClass()
                .msgSendP("alloc")
                .msgSendP("init")
            try {
                block()
            } finally {
                pool.msgSendP("drain")
            }
        }

        fun nsArray(vararg args: Pointer): Pointer {
            val nsArrayClass = "NSArray".nsClass()
            return if (args.isEmpty()) {
                nsArrayClass.msgSendP("array")
            } else {
                nsArrayClass.msgSendP("arrayWithObjects:count:", args, args.size)
            }
        }
    }
}
