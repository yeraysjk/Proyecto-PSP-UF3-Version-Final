package client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import java.util.Optional;
import javafx.scene.control.ListCell;
import javafx.util.Callback;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.layout.HBox;
import java.util.HashSet;
import java.util.Set;

public class ChatWindowController {
    @FXML private ListView<String> userListView;
    @FXML private ListView<MensajeChat> chatListView;
    @FXML private TextField messageField;
    @FXML private TextField recipientField;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Label statusLabel;
    @FXML private BorderPane mainContainer;

    private ChatClient chatClient;
    private String username;
    private Stage stage;
    private ObservableList<String> userList = FXCollections.observableArrayList();
    private ObservableList<MensajeChat> mensajes = FXCollections.observableArrayList();
    private String currentSelectedUser = null;
    private java.util.Timer historyTimer = null;
    private LocalDate lastDateHeader = null;
    private Set<String> mensajeIds = new HashSet<>();

    @FXML
    public void initialize() {
        // Añadir usuario especial 'General'
        userList.add("General");
        userListView.setItems(userList);
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                recipientField.setText(newVal.equals("General") ? "" : newVal);
                currentSelectedUser = newVal;
                mensajes.clear(); // Limpiar mensajes al cambiar de usuario
                mensajeIds.clear(); // Limpiar IDs de mensajes
                lastDateHeader = null; // Resetear el encabezado de fecha
                chatListView.setItems(mensajes);
                // Solicitar historial adecuado
                if (chatClient != null) {
                    if (newVal.equals("General")) {
                        chatClient.requestGeneralHistory(); // Usar método específico para general
                    } else {
                        chatClient.requestPrivateHistory(newVal);
                    }
                    startHistoryTimer();
                }
            } else {
                recipientField.clear();
                currentSelectedUser = null;
                stopHistoryTimer();
            }
        });

        // Configurar celdas personalizadas para el chat
        chatListView.setCellFactory(new Callback<ListView<MensajeChat>, ListCell<MensajeChat>>() {
            @Override
            public ListCell<MensajeChat> call(ListView<MensajeChat> listView) {
                return new ListCell<MensajeChat>() {
                    @Override
                    protected void updateItem(MensajeChat msg, boolean empty) {
                        super.updateItem(msg, empty);
                        if (empty || msg == null) {
                            setText(null);
                            setGraphic(null);
                            setStyle("");
                        } else if (msg.getTexto().startsWith("--- ")) {
                            // Encabezado de día
                            Label header = new Label(msg.getTexto());
                            header.getStyleClass().add("header-dia");
                            setGraphic(header);
                            setText(null);
                            setStyle("-fx-alignment: center;");
                        } else {
                            String hora = msg.getHora().format(DateTimeFormatter.ofPattern("HH:mm"));
                            String texto = msg.getTexto();
                            
                            Label bubble = new Label(texto + "  ");
                            Label horaLabel = new Label(hora);
                            horaLabel.getStyleClass().add("hora-label");
                            
                            if (msg.isEnviado()) {
                                bubble.getStyleClass().add("bubble-enviado");
                                setStyle("-fx-alignment: CENTER_RIGHT;");
                            } else {
                                bubble.getStyleClass().add("bubble-recibido");
                                setStyle("-fx-alignment: CENTER_LEFT;");
                            }
                            
                            HBox hbox = new HBox(bubble, horaLabel);
                            hbox.setSpacing(4);
                            hbox.setStyle(getStyle());
                            setGraphic(hbox);
                            setText(null);
                        }
                    }
                };
            }
        });

        // Configurar los botones
        sendButton.setOnAction(e -> sendMessage());
        clearButton.setOnAction(e -> clearChat());

        // Configurar el campo de mensaje
        messageField.setOnAction(e -> sendMessage());
        
        // Configurar el campo de destinatario
        recipientField.setPromptText("Todos (dejar vacío para mensaje general)");
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
        stopHistoryTimer();
    }

    public void setUsername(String username) {
        this.username = username;
        updateStatus("Conectado como: " + username);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void updateUserList(ObservableList<String> users) {
        userList.clear();
        userList.add("General"); // Siempre primero
        userList.addAll(users);
        userList.remove(username); // No mostrar el usuario actual en la lista
        updateStatus("Usuarios conectados: " + users.size());
    }

    public void appendMessage(String message) {
        MensajeChat msg = parseMensaje(message);
        if (msg != null) {
            appendMessage(msg);
        }
    }

    public void appendMessage(MensajeChat msg) {
        if (msg != null) {
            String id = msg.getFecha() + "_" + msg.getHora() + "_" + msg.getTexto();
            if (!mensajeIds.contains(id)) {
                if (lastDateHeader == null || !msg.getFecha().equals(lastDateHeader)) {
                    lastDateHeader = msg.getFecha();
                    MensajeChat header = new MensajeChat("--- " + lastDateHeader.toString() + " ---", "", false, lastDateHeader, msg.getHora());
                    mensajes.add(header);
                }
                mensajes.add(msg);
                mensajeIds.add(id);
                
                Platform.runLater(() -> {
                    chatListView.scrollTo(mensajes.size() - 1);
                });
            }
        }
    }

    private MensajeChat parseMensaje(String message) {
        try {
            if (!message.startsWith("[")) return null;
            int closeBracket = message.indexOf("]");
            String datetime = message.substring(1, closeBracket);
            String rest = message.substring(closeBracket + 2);
            String date = datetime.split(" ")[0];
            String hour = datetime.split(" ")[1].substring(0,5);
            String texto = rest;
            String emisor = rest.contains(":") ? rest.split(":")[0] : "";
            boolean enviado = rest.startsWith("Tú");
            
            // Determinar si es un mensaje general o privado
            boolean esGeneral = !rest.contains("->");
            
            MensajeChat msg = new MensajeChat(texto, emisor, enviado, LocalDate.parse(date), LocalTime.parse(hour));
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    public void showError(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(error);
        alert.showAndWait();
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String recipient = recipientField.getText().trim();
            // Si el usuario seleccionado es "General" o el campo está vacío, enviar mensaje general
            if (currentSelectedUser != null && currentSelectedUser.equals("General")) {
                recipient = "";
            }
            // Validar destinatario solo si no es mensaje general
            if (!recipient.isEmpty() && !userList.contains(recipient)) {
                showError("El usuario seleccionado no es válido.");
                return;
            }
            chatClient.sendMessage(message, recipient);
            
            // Mostrar el mensaje localmente
            MensajeChat msg = new MensajeChat(message, "Tú", true, LocalDate.now(), LocalTime.now());
            appendMessage(msg);
            
            messageField.clear();
            messageField.requestFocus();
        }
    }

    public void clearChat() {
        if (chatClient != null) {
            if (currentSelectedUser != null && currentSelectedUser.equals("General")) {
                chatClient.clearGeneralChat();
            } else if (currentSelectedUser != null) {
                chatClient.clearPrivateChat(currentSelectedUser);
            }
        }
        mensajes.clear();
        mensajeIds.clear();
        lastDateHeader = null;
    }
    
    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private void startHistoryTimer() {
        stopHistoryTimer();
        if (currentSelectedUser != null && chatClient != null) {
            historyTimer = new java.util.Timer();
            historyTimer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (currentSelectedUser != null) {
                        if (currentSelectedUser.equals("General")) {
                            chatClient.requestPrivateHistory("");
                        } else {
                            chatClient.requestPrivateHistory(currentSelectedUser);
                        }
                    }
                }
            }, 5000, 5000); // cada 5 segundos
        }
    }

    private void stopHistoryTimer() {
        if (historyTimer != null) {
            historyTimer.cancel();
            historyTimer = null;
        }
    }

    public void setHistorial(String historial) {
        if (historial == null || historial.trim().isEmpty()) {
            return;
        }
        
        // Limpiar mensajes solo si es la primera carga
        if (mensajes.isEmpty()) {
            mensajes.clear();
            mensajeIds.clear();
            lastDateHeader = null;
        }
        
        String[] lines = historial.split("\\n|\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            MensajeChat msg = parseMensaje(line);
            if (msg != null) {
                String id = msg.getFecha() + "_" + msg.getHora() + "_" + msg.getTexto();
                if (!mensajeIds.contains(id)) {
                    // Encabezado de día si cambia la fecha
                    if (lastDateHeader == null || !msg.getFecha().equals(lastDateHeader)) {
                        lastDateHeader = msg.getFecha();
                        MensajeChat header = new MensajeChat("--- " + lastDateHeader.toString() + " ---", "", false, lastDateHeader, msg.getHora());
                        mensajes.add(header);
                    }
                    mensajes.add(msg);
                    mensajeIds.add(id);
                }
            }
        }
        
        // Scroll automático al final del historial
        if (!mensajes.isEmpty()) {
            Platform.runLater(() -> {
                chatListView.scrollTo(mensajes.size() - 1);
            });
        }
    }
} 