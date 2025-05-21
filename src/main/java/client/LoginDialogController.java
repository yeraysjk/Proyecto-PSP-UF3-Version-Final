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

/**
 * Controlador para la ventana de inicio de sesión.
 * Maneja la autenticación de usuarios y el registro de nuevos usuarios.
 */
public class LoginDialogController {
    // Componentes de la interfaz gráfica
    @FXML private TextField usernameField;     // Campo para el nombre de usuario
    @FXML private PasswordField passwordField; // Campo para la contraseña
    @FXML private Button loginButton;          // Botón de inicio de sesión
    @FXML private Button registerButton;       // Botón de registro
    @FXML private Label errorLabel;            // Etiqueta para mensajes de error

    // Variables de control
    private String username = null;            // Nombre de usuario ingresado
    private String password = null;            // Contraseña ingresada
    private Stage dialogStage;                 // Ventana del diálogo

    /**
     * Establece la ventana del diálogo y configura el evento de cierre
     * @param dialogStage Ventana del diálogo
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        dialogStage.setOnCloseRequest(event -> {
            event.consume();
            username = null;
            password = null;
            dialogStage.close();
        });
    }

    /**
     * Inicializa los componentes de la interfaz y configura los eventos
     */
    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> openRegisterDialog());
        usernameField.setPromptText("Usuario");
        passwordField.setPromptText("Contraseña");
    }

    /**
     * Maneja el evento de inicio de sesión
     */
    private void handleLogin() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Abre la ventana de registro de nuevos usuarios
     */
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
                errorLabel.setText("¡Usuario registrado! Haz clic en Iniciar sesión para continuar.");
                errorLabel.setStyle("-fx-text-fill: #4caf50;");
                errorLabel.setVisible(true);
            }
        } catch (Exception ex) {
            errorLabel.setText("Error abriendo registro: " + ex.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ff5252;");
            errorLabel.setVisible(true);
        }
    }

    /**
     * Obtiene el nombre de usuario ingresado
     * @return Nombre de usuario
     */
    public String getUsername() { return username; }

    /**
     * Obtiene la contraseña ingresada
     * @return Contraseña
     */
    public String getPassword() { return password; }
} 