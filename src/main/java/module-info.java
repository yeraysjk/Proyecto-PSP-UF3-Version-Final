module yeray.priede.projecte_uf3_psp_final {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;

    opens client to javafx.fxml;
    exports client;
}