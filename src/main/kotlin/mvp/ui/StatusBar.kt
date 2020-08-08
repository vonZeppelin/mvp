package mvp.ui

import com.sun.jna.Callback
import com.sun.jna.Pointer
import javafx.application.Platform.runLater
import javafx.geometry.Point2D
import kotlin.random.Random
import mvp.nativelibs.NSApp
import mvp.nativelibs.NSRect
import mvp.nativelibs.NSSize
import mvp.nativelibs.NULL_PTR
import mvp.nativelibs.OBJC
import mvp.nativelibs.autoreleasepool
import mvp.nativelibs.get
import mvp.nativelibs.javaString
import mvp.nativelibs.msgSend
import mvp.nativelibs.msgSendS
import mvp.nativelibs.nsArrayOf
import mvp.nativelibs.nsClass
import mvp.nativelibs.nsSelector
import mvp.nativelibs.nsString
import mvp.nativelibs.performInMainThread

data class StatusBarEvent(val id: String, val location: Point2D, val isRightButton: Boolean)

typealias EventListener = (StatusBarEvent) -> Unit

@Suppress("unused", "UNUSED_PARAMETER")
class StatusBar(eventListener: EventListener) {
    private object AddItem : Callback {
        fun callback(self: Pointer, cmd: Pointer, args: Pointer) {
            autoreleasepool {
                val itemId = args[0]
                val imageData = args[1]

                val statusBar = "NSStatusBar".nsClass()
                    .msgSend<Pointer>("systemStatusBar")

                val imageSize = statusBar.msgSend<Double>("thickness") * 0.75
                val image = "NSImage".nsClass()
                    .msgSend<Pointer>("alloc")
                    .msgSend<Pointer>("initWithData:", imageData)
                image.msgSend<Pointer>("setTemplate:", true)
                image.msgSend<Pointer>("setSize:", NSSize.ByValue(imageSize, imageSize))

                val itemButton = statusBar
                    .msgSend<Pointer>("statusItemWithLength:", -1.0)
                    .msgSend<Pointer>("retain")
                    .msgSend<Pointer>("button")
                itemButton.msgSend<Pointer>("setImage:", image)
                itemButton.msgSend<Pointer>("setTarget:", self)
                // action mask is (NSEventMaskLeftMouseDown | NSEventMaskRightMouseDown)
                itemButton.msgSend<Pointer>("sendActionOn:", (1 shl 1) or (1 shl 3))
                itemButton.msgSend<Pointer>("setAction:", "itemClicked:".nsSelector())
                // hack: use <NSString* itemId> as button's tag
                itemButton.msgSend<Pointer>("setTag:", itemId.msgSend<Pointer>("retain"))
            }
        }
    }
    private class ItemClicked(private val eventListener: EventListener) : Callback {
        fun callback(self: Pointer, cmd: Pointer, source: Pointer) {
            autoreleasepool {
                // TODO Multi-monitor support?
                val screenSize = "NSScreen".nsClass()
                    .msgSend<Pointer>("mainScreen")
                    .msgSendS("frame", NSRect())
                    .size
                val itemButtonLocation = source.msgSend<Pointer>("window")
                    .msgSendS("frame", NSRect())
                val buttonNumber = NSApp.msgSend<Pointer>("currentEvent")
                    .msgSend<Long>("buttonNumber")
                runLater {
                    eventListener(
                        StatusBarEvent(
                            id = source.msgSend<Pointer>("tag").javaString(),
                            location = Point2D(
                                itemButtonLocation.origin.x + itemButtonLocation.size.width / 2,
                                screenSize.height - itemButtonLocation.origin.y
                            ),
                            isRightButton = buttonNumber != 0L
                        )
                    )
                }
            }
        }
    }
    private object RemoveItem : Callback {
        fun callback(self: Pointer, cmd: Pointer, args: Pointer) {
            autoreleasepool {
                val itemId = args[0]
                removeStatusItems {
                    it.msgSend<Pointer>("button")
                        .msgSend<Pointer>("tag")
                        .msgSend("isEqualToString:", itemId)
                }
            }
        }
    }
    private object Start : Callback {
        fun callback(self: Pointer, cmd: Pointer) {
            autoreleasepool {
                NSApp.msgSend<Pointer>("run")
            }
        }
    }
    private object Stop : Callback {
        fun callback(self: Pointer, cmd: Pointer) {
            autoreleasepool {
                removeStatusItems()
                self.msgSend<Pointer>("release")
            }
        }
    }

    // To be called, ItemClicked callback must be strongly reachable
    private val itemClickedCallback: ItemClicked = ItemClicked(eventListener)
    private val appDelegateClass: Pointer =
        OBJC.objc_allocateClassPair("NSObject".nsClass(), "AppDelegate${Random.nextInt(999)}", 0).apply {
            OBJC.class_addMethod(this, "addItem:".nsSelector(), AddItem, "v@:@:@")
            OBJC.class_addMethod(this, "itemClicked:".nsSelector(), itemClickedCallback, "v@:@:@")
            OBJC.class_addMethod(this, "removeItem:".nsSelector(), RemoveItem, "v@:@:@")
            OBJC.class_addMethod(this, "start".nsSelector(), Start, "v@:@")
            OBJC.class_addMethod(this, "stop".nsSelector(), Stop, "v@:@")
            OBJC.objc_registerClassPair(this)
        }
    private val appDelegate: Pointer = autoreleasepool {
        appDelegateClass
            .msgSend<Pointer>("alloc")
            .msgSend<Pointer>("init")
            .msgSend<Pointer>("retain")
            .apply { performInMainThread("start") }
    }

    fun destroy() {
        autoreleasepool {
            appDelegate.performInMainThread("stop")
            // TODO OBJC.objc_disposeClassPair(appDelegateClass)
        }
    }

    fun addIcon(id: String, icon: ByteArray) {
        autoreleasepool {
            val imageData = "NSData".nsClass()
                .msgSend<Pointer>("dataWithBytes:length:", icon, icon.size)
            appDelegate.performInMainThread("addItem:", nsArrayOf(id.nsString(), imageData))
        }
    }

    fun removeIcon(id: String) {
        autoreleasepool {
            appDelegate.performInMainThread("removeItem:", nsArrayOf(id.nsString()))
        }
    }
}

private fun removeStatusItems(predicate: (Pointer) -> Boolean = { true }) {
    val statusBarWindowClass = "NSStatusBarWindow".nsClass()
    val statusBar = "NSStatusBar".nsClass()
        .msgSend<Pointer>("systemStatusBar")
    val windowsEnum = NSApp
        .msgSend<Pointer>("windows")
        .msgSend<Pointer>("objectEnumerator")
    generateSequence { windowsEnum.msgSend<Pointer>("nextObject").takeIf { it != NULL_PTR } }
        .filter { it.msgSend("isKindOfClass:", statusBarWindowClass) }
        .map { it.msgSend<Pointer>("statusItem") }
        .filter(predicate)
        .forEach {
            it.msgSend<Pointer>("button")
                .msgSend<Pointer>("tag")
                .msgSend<Pointer>("release")
            statusBar.msgSend<Pointer>("removeStatusItem:", it)
            it.msgSend<Pointer>("release")
        }
}
