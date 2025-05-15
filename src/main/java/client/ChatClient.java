package client;

import java.io.*;
import java.net.Socket;
import java.util.Base64;
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

public class ChatClient extends Application {
    // Cambiar a la IP del servidor cuando se conecte desde otro ordenador
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private ExecutorService executorService;
    private ChatWindowController chatController;
    private ObservableList<String> userList = FXCollections.observableArrayList();
    private Stage primaryStage;
    private boolean isConnected = false;
    private ChatServer server;
    private Thread serverThread;

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
                connectToServer();
                return true;
            }
        } catch (IOException e) {
            showError("Error al cargar el diálogo de login: " + e.getMessage());
        }
        return false;
    }

    private boolean registerUser(String username, String password) {
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

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Información");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            executorService = Executors.newFixedThreadPool(2);
            
            // Enviar el nombre de usuario al servidor
            out.println("LOGIN:" + username);
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

    private void showChatWindow() {
        try {
            java.net.URL fxmlUrl = ChatClient.class.getResource("/fxml/ChatWindow.fxml");
            System.out.println("Ruta FXML encontrada: " + fxmlUrl);
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
                } else if (message.startsWith("FILE:")) {
                    handleFileMessage(message);
                } else if (message.startsWith("ERROR:")) {
                    showError(message.substring(6));
                } else {
                    // Mensaje individual
                    if (chatController != null) {
                        chatController.appendMessage(message);
                    }
                }
            } catch (Exception e) {
                if (chatController != null) {
                    chatController.appendMessage(message);
                }
            }
        });
    }
    
    private void handleFileMessage(String message) {
        // Implementación del manejo de archivos
        String[] parts = message.split(":", 4);
        if (parts.length >= 4) {
            String sender = parts[1];
            String fileName = parts[2];
            chatController.appendMessage("Archivo recibido de " + sender + ": " + fileName);
        }
    }
    
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
    
    public void sendFile(File file, String recipient) {
        if (!isConnected) {
            showError("No estás conectado al servidor");
            return;
        }
        
        executorService.execute(() -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                String header;
                if (recipient.isEmpty()) {
                    header = "FILE:" + file.getName() + ":" + file.length();
                } else {
                    header = "PRIVATE_FILE:" + recipient + ":" + file.getName() + ":" + file.length();
                }
                out.println(header);
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    String chunk = Base64.getEncoder().encodeToString(buffer);
                    out.println(chunk);
                }
                
                out.println("END_FILE");
                Platform.runLater(() -> chatController.appendMessage("Archivo enviado: " + file.getName()));
            } catch (IOException e) {
                Platform.runLater(() -> showError("Error al enviar archivo: " + e.getMessage()));
            }
        });
    }
    
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
                System.err.println("Error al mostrar mensaje de error: " + message);
                e.printStackTrace();
            }
        });
    }
    
    public void requestPrivateHistory(String otherUser) {
        if (out != null) {
            out.println("GET_PRIVATE_HISTORY:" + otherUser);
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
} 