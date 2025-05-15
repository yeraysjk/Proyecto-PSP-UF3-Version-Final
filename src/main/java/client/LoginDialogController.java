package client;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class LoginDialogController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label errorLabel;

    private String username = null;
    private String password = null;
    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        dialogStage.setOnCloseRequest(event -> {
            event.consume();
            username = null;
            password = null;
            dialogStage.close();
        });
    }

    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> openRegisterDialog());
        usernameField.setPromptText("Usuario");
        passwordField.setPromptText("Contraseña");
    }

    private void handleLogin() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void openRegisterDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RegisterDialog.fxml"));
            Parent root = loader.load();
            RegisterDialogController controller = loader.getController();
            Stage regStage = new Stage();
            regStage.setTitle("Registro de Usuario");
            regStage.setScene(new Scene(root));
            regStage.initModality(Modality.APPLICATION_MODAL);
            regStage.initOwner(dialogStage);
            controller.setDialogStage(regStage);
            regStage.showAndWait();
            if (controller.isRegistered()) {
                usernameField.setText(controller.getUsername());
                passwordField.setText(controller.getPassword());
                errorLabel.setText("Usuario listo para registrar. Pulsa Iniciar sesión.");
                errorLabel.setStyle("-fx-text-fill: #00bcd4;");
                errorLabel.setVisible(true);
            }
        } catch (Exception ex) {
            errorLabel.setText("Error abriendo registro: " + ex.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ff5252;");
            errorLabel.setVisible(true);
        }
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
} 