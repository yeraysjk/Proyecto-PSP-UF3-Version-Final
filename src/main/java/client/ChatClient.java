package client;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import server.ChatServer;
import server.Logger;

/**
 * Clase principal del cliente de chat.
 * Maneja la conexión con el servidor y la interfaz de usuario.
 */
public class ChatClient extends Application {
    // Configuración de conexión
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    
    // Componentes de conexión
    private Socket socket;                    // Socket para la conexión con el servidor
    private BufferedReader in;                // Lector de entrada del servidor
    private PrintWriter out;                  // Escritor de salida al servidor
    private String username;                  // Nombre de usuario actual
    private String password;                  // Contraseña del usuario
    private ExecutorService executorService;  // Servicio para manejar hilos
    private ChatWindowController chatController; // Controlador de la ventana de chat
    private ObservableList<String> userList = FXCollections.observableArrayList(); // Lista de usuarios
    private Stage primaryStage;               // Ventana principal
    private boolean isConnected = false;      // Estado de la conexión
    private ChatServer server;                // Instancia del servidor
    private Thread serverThread;              // Hilo del servidor

    /**
     * Inicializa la aplicación y el servidor
     */
    @Override
    public void init() throws Exception {
        // Iniciar el servidor en un hilo separado
        server = new ChatServer();
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error iniciando el servidor: " + e.getMessage()));
            }
        });
        serverThread.setDaemon(true); // El hilo se cerrará cuando la aplicación se cierre
        serverThread.start();
        
        // Esperar a que el servidor esté listo
        Thread.sleep(1000);
    }

    /**
     * Inicia la aplicación y muestra la ventana de login
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            this.primaryStage = primaryStage;
            primaryStage.setOnCloseRequest(event -> {
                event.consume();
                disconnect();
                Platform.exit();
            });

            boolean logged = showLoginDialog();
            if (logged) {
                showChatWindow();
            } else {
                Platform.exit();
            }
        } catch (Exception e) {
            System.err.println("Error al iniciar la aplicación: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    /**
     * Cierra la aplicación y desconecta del servidor
     */
    @Override
    public void stop() {
        try {
            if (server != null) {
                server.shutdown();
            }
            disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Muestra el diálogo de inicio de sesión
     * @return true si el login fue exitoso
     */
    private boolean showLoginDialog() {
        try {
            FXMLLoader loginLoader = new FXMLLoader(ChatClient.class.getResource("/fxml/LoginDialog.fxml"));
            Parent loginRoot = loginLoader.load();
            LoginDialogController loginController = loginLoader.getController();
            
            Stage loginStage = new Stage();
            loginStage.setTitle("Iniciar Sesión");
            loginStage.setScene(new Scene(loginRoot));
            loginStage.initModality(Modality.APPLICATION_MODAL);
            loginStage.initOwner(primaryStage);
            loginController.setDialogStage(loginStage);
            
            loginStage.showAndWait();
            
            if (loginController.getUsername() != null) {
                this.username = loginController.getUsername();
                this.password = loginController.getPassword();
                connectToServer();
                return true;
            }
        } catch (IOException e) {
            showError("Error al cargar el diálogo de login: " + e.getMessage());
        }
        return false;
    }

    /**
     * Registra un nuevo usuario en el servidor
     * @param username Nombre de usuario
     * @param password Contraseña
     * @return true si el registro fue exitoso
     */
    public boolean registerUser(String username, String password) {
        try (Socket regSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter regOut = new PrintWriter(regSocket.getOutputStream(), true);
             BufferedReader regIn = new BufferedReader(new InputStreamReader(regSocket.getInputStream()))) {
            regOut.println("REGISTER:" + username + ":" + password);
            String response = regIn.readLine();
            return response != null && response.startsWith("OK:");
        } catch (IOException e) {
            showError("Error de conexión al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Muestra un mensaje informativo
     */
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Información");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Establece la conexión con el servidor
     */
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            executorService = Executors.newFixedThreadPool(2);
            
            // Enviar el nombre de usuario y contraseña al servidor
            out.println("LOGIN:" + username + ":" + password);
            isConnected = true;
            
            // Solicitar la lista de usuarios tras iniciar sesión
            out.println("GET_USERS");
            
            // Iniciar el hilo de escucha
            startMessageListener();
        } catch (IOException e) {
            showError("Error al conectar con el servidor: " + e.getMessage());
            Platform.exit();
        }
    }

    /**
     * Muestra la ventana principal del chat
     */
    private void showChatWindow() {
        try {
            java.net.URL fxmlUrl = ChatClient.class.getResource("/fxml/ChatWindow.fxml");
            FXMLLoader chatLoader = new FXMLLoader(fxmlUrl);
            Parent chatRoot = chatLoader.load();
            chatController = chatLoader.getController();
            
            chatController.setChatClient(this);
            chatController.setUsername(username);
            chatController.setStage(primaryStage);
            
            primaryStage.setTitle("Chat App - " + username);
            primaryStage.setScene(new Scene(chatRoot));
            primaryStage.setOnCloseRequest(event -> {
                event.consume();
                disconnect();
                Platform.exit();
            });
            
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error al cargar la ventana de chat: " + e.getMessage());
            Platform.exit();
        }
    }
    
    /**
     * Inicia el hilo que escucha mensajes del servidor
     */
    private void startMessageListener() {
        executorService.execute(() -> {
            try {
                while (isConnected && !socket.isClosed()) {
                    String message = in.readLine();
                    if (message != null) {
                        handleServerMessage(message);
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                if (isConnected && !socket.isClosed()) {
                    Platform.runLater(() -> showError("Error de conexión: " + e.getMessage()));
                }
            } finally {
                disconnect();
            }
        });
    }
    
    /**
     * Procesa los mensajes recibidos del servidor
     * @param message Mensaje recibido
     */
    private void handleServerMessage(String message) {
        Platform.runLater(() -> {
            try {
                if (message.startsWith("USERLIST:")) {
                    String[] users = message.substring(9).split(",");
                    userList.clear();
                    userList.addAll(users);
                    if (chatController != null) {
                        chatController.updateUserList(userList);
                    }
                } else if (message.startsWith("HISTORIAL:")) {
                    String historialMsg = message.substring(10);
                    if (chatController != null) {
                        chatController.setHistorial(historialMsg);
                    }
                } else if (message.startsWith("HISTORIAL_PRIVADO:")) {
                    String historialMsg = message.substring("HISTORIAL_PRIVADO:".length());
                    if (chatController != null) {
                        chatController.setHistorial(historialMsg);
                    }
                } else if (message.startsWith("ERROR:")) {
                    showError(message.substring(6));
                } else {
                    // Mensaje individual
                    if (chatController != null) {
                        chatController.appendMessage(message);
                    }
                }
            } catch (Exception e) {
                Logger.error("Error procesando mensaje del servidor", e);
                if (chatController != null) {
                    chatController.showError("Error procesando mensaje: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Envía un mensaje al servidor
     * @param message Contenido del mensaje
     * @param recipient Destinatario (vacío para mensaje general)
     */
    public void sendMessage(String message, String recipient) {
        if (!isConnected) {
            showError("No estás conectado al servidor");
            return;
        }
        
        try {
            String formattedMessage;
            if (recipient.isEmpty()) {
                formattedMessage = "MESSAGE:" + message;
            } else {
                formattedMessage = "PRIVATE:" + recipient + ":" + message;
            }
            // Enviar el mensaje sin cifrar
            out.println(formattedMessage);
        } catch (Exception e) {
            showError("Error al enviar mensaje: " + e.getMessage());
        }
    }
    
    /**
     * Desconecta del servidor y limpia recursos
     */
    public void disconnect() {
        isConnected = false;
        try {
            if (out != null) {
                out.println("LOGOUT:" + username);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
        } catch (IOException e) {
            showError("Error al cerrar conexión: " + e.getMessage());
        }
    }
    
    /**
     * Muestra un mensaje de error
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            try {
                if (chatController != null) {
                    chatController.showError(message);
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    if (primaryStage != null && primaryStage.isShowing()) {
                        alert.initOwner(primaryStage);
                    }
                    alert.showAndWait();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Solicita el historial de mensajes privados
     */
    public void requestPrivateHistory(String otherUser) {
        if (out != null) {
            out.println("GET_PRIVATE_HISTORY:" + otherUser);
        }
    }
    
    /**
     * Solicita el historial de mensajes generales
     */
    public void requestGeneralHistory() {
        if (out != null) {
            out.println("GET_GENERAL_HISTORY");
        }
    }
    
    /**
     * Limpia el chat general
     */
    public void clearGeneralChat() {
        if (!isConnected) {
            showError("No estás conectado al servidor");
            return;
        }
        out.println("CLEAR_GENERAL");
    }

    /**
     * Limpia el chat privado con un usuario
     */
    public void clearPrivateChat(String username) {
        if (!isConnected) {
            showError("No estás conectado al servidor");
            return;
        }
        out.println("CLEAR_PRIVATE:" + username);
    }
    
    /**
     * Punto de entrada principal de la aplicación
     */
    public static void main(String[] args) {
        launch(args);
    }
} 