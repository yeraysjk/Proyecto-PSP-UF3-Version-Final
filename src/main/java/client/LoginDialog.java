package client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;

public class LoginDialog {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button cancelButton;
    @FXML private Button exitButton;
    @FXML private GridPane mainGrid;
    @FXML private HBox buttonBox;
    
    private Stage dialogStage;
    private String username = null;
    private String password = null;
    private boolean isRegistering = false;
    
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        
        // Configurar el comportamiento de la ventana
        dialogStage.setOnCloseRequest(event -> {
            username = null;
            password = null;
            dialogStage.close();
        });
        
        // Configurar el comportamiento de las teclas
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancel();
            }
        });
    }
    
    @FXML
    private void initialize() {
        // Configurar el diseño
        mainGrid.setHgap(10);
        mainGrid.setVgap(10);
        mainGrid.setPadding(new Insets(10));
        
        // Configurar los botones
        loginButton.setOnAction(e -> login());
        registerButton.setOnAction(e -> register());
        cancelButton.setOnAction(e -> cancel());
        exitButton.setOnAction(e -> System.exit(0));
        
        // Configurar los campos
        usernameField.setPromptText("Usuario");
        passwordField.setPromptText("Contraseña");
    }
    
    private void login() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        isRegistering = false;
        dialogStage.close();
    }
    
    private void register() {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        isRegistering = true;
        dialogStage.close();
    }
    
    private void cancel() {
        username = null;
        password = null;
        dialogStage.close();
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public boolean isRegistering() {
        return isRegistering;
    }
} 