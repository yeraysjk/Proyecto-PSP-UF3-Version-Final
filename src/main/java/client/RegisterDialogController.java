package client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;

public class RegisterDialogController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField repeatPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;
    @FXML private Button backButton;

    private Stage dialogStage;
    private String username;
    private String password;
    private boolean registered = false;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
        registerButton.setOnAction(e -> handleRegister());
        backButton.setOnAction(e -> handleBack());
    }

    private void handleRegister() {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();
        String repeat = repeatPasswordField.getText();
        
        // Validar campos
        if (user.isEmpty() || pass.isEmpty() || repeat.isEmpty()) {
            showError("Todos los campos son obligatorios");
            return;
        }
        
        if (user.length() < 3) {
            showError("El nombre de usuario debe tener al menos 3 caracteres");
            return;
        }
        
        if (pass.length() < 4) {
            showError("La contraseña debe tener al menos 4 caracteres");
            return;
        }
        
        if (!pass.equals(repeat)) {
            showError("Las contraseñas no coinciden");
            return;
        }
        
        // Enviar solicitud de registro al servidor
        if (registerUser(user, pass)) {
            this.username = user;
            this.password = pass;
            this.registered = true;
            dialogStage.close();
        }
    }
    
    private boolean registerUser(String username, String password) {
        try {
            // Crear una conexión independiente para el registro
            Socket regSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            PrintWriter regOut = new PrintWriter(regSocket.getOutputStream(), true);
            BufferedReader regIn = new BufferedReader(new InputStreamReader(regSocket.getInputStream()));
            
            // Enviar el comando de registro
            regOut.println("REGISTER:" + username + ":" + password);
            
            // Esperar respuesta del servidor (timeout después de 5 segundos)
            regSocket.setSoTimeout(5000);
            String response = regIn.readLine();
            
            // Cerrar la conexión
            regSocket.close();
            
            if (response != null && response.startsWith("OK:")) {
                showSuccess("Usuario registrado correctamente");
                return true;
            } else {
                String errorMsg = "Error desconocido";
                if (response != null) {
                    if (response.contains(":")) {
                        errorMsg = response.substring(response.indexOf(":") + 1);
                    } else {
                        errorMsg = response;
                    }
                }
                showError("Error al registrar usuario: " + errorMsg);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error de conexión al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    private void handleBack() {
        this.username = null;
        this.password = null;
        this.registered = false;
        dialogStage.close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #ff5252;");
        errorLabel.setVisible(true);
    }
    
    private void showSuccess(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #4caf50;");
        errorLabel.setVisible(true);
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isRegistered() { return registered; }
} 