package client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;

/**
 * Controlador para la ventana de registro de usuarios.
 * Maneja el proceso de registro de nuevos usuarios en el sistema.
 */
public class RegisterDialogController {
    // Configuración de conexión
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    
    // Componentes de la interfaz gráfica
    @FXML private TextField usernameField;         // Campo para el nombre de usuario
    @FXML private PasswordField passwordField;     // Campo para la contraseña
    @FXML private PasswordField repeatPasswordField; // Campo para repetir la contraseña
    @FXML private Label errorLabel;                // Etiqueta para mensajes de error/éxito
    @FXML private Button registerButton;           // Botón de registro
    @FXML private Button backButton;               // Botón para volver

    // Variables de control
    private Stage dialogStage;                     // Ventana del diálogo
    private String username;                       // Nombre de usuario registrado
    private String password;                       // Contraseña registrada
    private boolean registered = false;            // Estado del registro

    /**
     * Establece la ventana del diálogo
     * @param dialogStage Ventana del diálogo
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Inicializa los componentes de la interfaz y configura los eventos
     */
    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
        registerButton.setOnAction(e -> handleRegister());
        backButton.setOnAction(e -> handleBack());
    }

    /**
     * Maneja el evento de registro de usuario
     * Valida los campos y envía la solicitud al servidor
     */
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
    
    /**
     * Envía la solicitud de registro al servidor
     * @param username Nombre de usuario
     * @param password Contraseña
     * @return true si el registro fue exitoso
     */
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

    /**
     * Maneja el evento de volver al login
     */
    private void handleBack() {
        this.username = null;
        this.password = null;
        this.registered = false;
        dialogStage.close();
    }

    /**
     * Muestra un mensaje de error
     * @param msg Mensaje de error
     */
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #ff5252;");
        errorLabel.setVisible(true);
    }
    
    /**
     * Muestra un mensaje de éxito
     * @param msg Mensaje de éxito
     */
    private void showSuccess(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #4caf50;");
        errorLabel.setVisible(true);
    }

    /**
     * Obtiene el nombre de usuario registrado
     * @return Nombre de usuario
     */
    public String getUsername() { return username; }

    /**
     * Obtiene la contraseña registrada
     * @return Contraseña
     */
    public String getPassword() { return password; }

    /**
     * Verifica si el registro fue exitoso
     * @return true si el registro fue exitoso
     */
    public boolean isRegistered() { return registered; }
} 