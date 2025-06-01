module org.example.servmanagment {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires jsch;
    requires org.apache.commons.io;

    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    opens org.example.servmanagment to javafx.fxml;
    exports org.example.servmanagment;
}