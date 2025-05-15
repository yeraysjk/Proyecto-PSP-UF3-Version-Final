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
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.nio.file.Files;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.FileChooser;
import java.io.ByteArrayInputStream;
import javafx.stage.Modality;

public class ChatWindowController {
    @FXML private ListView<String> userListView;
    @FXML private ListView<MensajeChat> chatListView;
    @FXML private TextField messageField;
    @FXML private TextField recipientField;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Label statusLabel;
    @FXML private BorderPane mainContainer;
    @FXML private Button uploadButton;

    private ChatClient chatClient;
    private String username;
    private Stage stage;
    private ObservableList<String> userList = FXCollections.observableArrayList();
    private ObservableList<MensajeChat> mensajes = FXCollections.observableArrayList();
    private String currentSelectedUser = null;
    private java.util.Timer historyTimer = null;
    private LocalDate lastDateHeader = null;
    private Set<String> mensajeIds = new HashSet<>();
    private FileChooser fileChooser;
    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

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
                        chatClient.requestPrivateHistory(""); // Usar vacío para general
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
                            
                            if (msg.isImagen()) {
                                // Crear un botón para la imagen
                                Button imageButton = new Button("📷 Ver imagen");
                                imageButton.getStyleClass().add("chat-image");
                                imageButton.setOnAction(e -> showImage(texto));
                                
                                Label horaLabel = new Label(hora);
                                horaLabel.getStyleClass().add("hora-label");
                                
                                HBox hbox = new HBox(imageButton, horaLabel);
                                hbox.setSpacing(4);
                                
                                if (msg.isEnviado()) {
                                    hbox.setStyle("-fx-alignment: CENTER_RIGHT;");
                                } else {
                                    hbox.setStyle("-fx-alignment: CENTER_LEFT;");
                                }
                                
                                setGraphic(hbox);
                                setText(null);
                            } else {
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

        // Configurar el FileChooser
        fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        // Configurar el botón de subir
        uploadButton.setOnAction(e -> handleImageUpload());
        
        // Configurar el ListView para manejar clics en imágenes
        chatListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                MensajeChat selectedItem = chatListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.isImagen()) {
                    showImage(selectedItem.getTexto());
                }
            }
        });
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
            
            MensajeChat msg = new MensajeChat(texto, emisor, enviado, LocalDate.parse(date), LocalTime.parse(hour));
            
            // Verificar si es una imagen
            if (texto.startsWith("IMAGE:")) {
                msg.setImagen(true);
                msg.setTexto(texto.substring(6)); // Quitar el prefijo "IMAGE:"
            }
            
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

    private void handleImageUpload() {
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            if (file.length() > MAX_IMAGE_SIZE) {
                showError("La imagen es demasiado grande. El tamaño máximo es 5MB.");
                return;
            }
            
            try {
                // Generar un nombre único para la imagen
                String imageName = System.currentTimeMillis() + "_" + file.getName();
                String imagePath = "src/main/resources/images/" + imageName;
                
                // Asegurarse de que el directorio existe
                File imageDir = new File("src/main/resources/images");
                if (!imageDir.exists()) {
                    imageDir.mkdirs();
                }
                
                // Copiar la imagen a la carpeta del proyecto
                Files.copy(file.toPath(), new File(imagePath).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // Crear el mensaje con la ruta de la imagen
                String message = "IMAGE:" + imagePath;
                String recipient = recipientField.getText().trim();
                
                // Si es chat general, enviar a todos
                if (currentSelectedUser != null && currentSelectedUser.equals("General")) {
                    recipient = "";
                }
                
                chatClient.sendMessage(message, recipient);
                
                // Mostrar la imagen localmente
                MensajeChat msg = new MensajeChat(imagePath, "Tú", true, LocalDate.now(), LocalTime.now());
                msg.setImagen(true);
                appendMessage(msg);
                
            } catch (IOException ex) {
                showError("Error al procesar la imagen: " + ex.getMessage());
            }
        }
    }

    private void showImage(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                showError("No se puede encontrar la imagen");
                return;
            }
            
            Platform.runLater(() -> {
                try {
                    Image image = new Image(imageFile.toURI().toString());
                    Stage imageStage = new Stage();
                    imageStage.initModality(Modality.NONE);
                    imageStage.initOwner(stage);
                    
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(800);
                    imageView.setFitHeight(600);
                    imageView.setPreserveRatio(true);
                    
                    ScrollPane scrollPane = new ScrollPane(imageView);
                    scrollPane.setFitToWidth(true);
                    scrollPane.setFitToHeight(true);
                    
                    Scene scene = new Scene(scrollPane);
                    imageStage.setTitle("Imagen");
                    imageStage.setScene(scene);
                    
                    // Manejar el cierre de la ventana
                    imageStage.setOnCloseRequest(event -> {
                        event.consume();
                        imageStage.hide();
                    });
                    
                    imageStage.show();
                } catch (Exception ex) {
                    showError("Error al mostrar la imagen: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            showError("Error al cargar la imagen: " + ex.getMessage());
        }
    }
} 