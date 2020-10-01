module mvp {
    requires com.jfoenix;
    requires com.sun.jna;
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;

    opens images;
    opens mvp to javafx.graphics;
    opens mvp.nativelibs to com.sun.jna;
    opens mvp.ui to javafx.fxml, com.sun.jna;
}
