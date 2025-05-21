package server;

import java.io.*;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import server.UserManager;
import server.MessageManager;
import server.DatabaseConfig;

/**
 * Manejador de cliente individual.
 * Gestiona la comunicación con un cliente específico, incluyendo autenticación,
 * registro y procesamiento de mensajes.
 */
public class ClientHandler implements Runnable {
    // Componentes de conexión
    private final Socket socket;              // Socket de conexión con el cliente
    private final PrintWriter out;            // Escritor de salida al cliente
    private final BufferedReader in;          // Lector de entrada del cliente
    private String username;                  // Nombre de usuario del cliente
    private final AtomicBoolean isRunning;    // Estado de la conexión
    private final ChatServer server;          // Referencia al servidor
    private final boolean isSecure;           // Indica si la conexión es segura (SSL)

    /**
     * Constructor del manejador de cliente
     * @param socket Socket de conexión
     * @param server Referencia al servidor
     * @throws IOException Si hay error al crear los streams
     */
    public ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.isSecure = socket instanceof SSLSocket;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.isRunning = new AtomicBoolean(true);
    }

    /**
     * Método principal que maneja la conexión del cliente
     */
    @Override
    public void run() {
        try {
            handleClient();
        } catch (IOException e) {
            Logger.error("Error en la conexión con el cliente", e);
        } finally {
            disconnect();
        }
    }

    /**
     * Maneja la conexión inicial del cliente y el proceso de autenticación
     * @throws IOException Si hay error en la comunicación
     */
    private void handleClient() throws IOException {
        // Esperar login o registro
        String initialMessage = in.readLine();
        
        // Permitir registro antes del login
        if (initialMessage != null && initialMessage.startsWith("REGISTER:")) {
            String registerData = initialMessage.substring(9);
            handleRegister(registerData);
            return;
        }
        
        // Continuar con el proceso de login
        if (initialMessage == null || !initialMessage.startsWith("LOGIN:")) {
            sendMessage("ERROR: Formato de login inválido");
            return;
        }

        // Obtener username y password del mensaje de login (formato LOGIN:username:password)
        String[] loginParts = initialMessage.substring(6).split(":", 2);
        if (loginParts.length != 2) {
            sendMessage("ERROR: Formato de login inválido (debe incluir usuario y contraseña)");
            return;
        }
        
        Logger.log("Procesando mensaje: " + loginParts[0]);

        username = loginParts[0];
        String password = loginParts[1];
        
        // Verificar credenciales usando UserManager
        if (!UserManager.authenticateUser(username, password)) {
            sendMessage("ERROR: Usuario o contraseña incorrectos");
            return;
        }
        
        if (!server.addClient(username, this)) {
            sendMessage("ERROR: Usuario ya conectado");
            return;
        }

        sendMessage("OK: Conectado como " + username);
        Logger.log("Cliente conectado: " + username + (isSecure ? " (conexión segura)" : ""));

        // Enviar historial de mensajes al usuario tras login
        java.util.List<String> historial = MessageManager.getMessageHistory(username);
        StringBuilder sb = new StringBuilder();
        for (String msg : historial) {
            sb.append(msg).append("\n");
        }
        sendMessage("HISTORIAL:" + sb.toString());

        // Manejar mensajes
        String message;
        while (isRunning.get() && (message = in.readLine()) != null) {
            handleMessage(message);
        }
    }

    /**
     * Procesa los mensajes recibidos del cliente
     * @param message Mensaje recibido
     */
    private void handleMessage(String message) {
        try {
            if (message.startsWith("REGISTER:")) {
                handleRegister(message.substring(9));
            } else if (message.startsWith("MESSAGE:")) {
                handleGeneralMessage(message.substring(8));
            } else if (message.startsWith("PRIVATE:")) {
                handlePrivateMessage(message.substring(8));
            } else if (message.equals("GET_USERS")) {
                handleGetUsers();
            } else if (message.equals("LOGOUT")) {
                disconnect();
            } else if (message.startsWith("GET_PRIVATE_HISTORY:")) {
                String otherUser = message.substring("GET_PRIVATE_HISTORY:".length());
                java.util.List<String> historial = MessageManager.getPrivateHistory(username, otherUser);
                StringBuilder sb = new StringBuilder();
                for (String msg : historial) {
                    sb.append(msg).append("\n");
                }
                sendMessage("HISTORIAL_PRIVADO:" + sb.toString());
            } else if (message.equals("GET_GENERAL_HISTORY")) {
                java.util.List<String> historial = MessageManager.getGeneralHistory();
                StringBuilder sb = new StringBuilder();
                for (String msg : historial) {
                    sb.append(msg).append("\n");
                }
                sendMessage("HISTORIAL:" + sb.toString());
            } else if (message.startsWith("CLEAR_GENERAL")) {
                MessageManager.clearGeneralMessages();
                sendMessage("OK: Chat general limpiado");
            } else if (message.startsWith("CLEAR_PRIVATE:")) {
                String otherUser = message.substring("CLEAR_PRIVATE:".length());
                MessageManager.clearUserMessages(username);
                MessageManager.clearUserMessages(otherUser);
                sendMessage("OK: Chat privado limpiado");
            } else {
                sendMessage("ERROR: Comando no reconocido");
            }
        } catch (Exception e) {
            Logger.error("Error procesando mensaje", e);
            sendMessage("ERROR: Error procesando mensaje");
        }
    }

    /**
     * Maneja el registro de nuevos usuarios
     * @param data Datos de registro (usuario:contraseña)
     */
    private void handleRegister(String data) {
        String[] parts = data.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de registro inválido");
            return;
        }
        String newUser = parts[0];
        String newPass = parts[1];
        boolean ok = UserManager.registerUser(newUser, newPass);
        if (ok) {
            sendMessage("OK: Usuario registrado correctamente");
        } else {
            sendMessage("ERROR: Usuario ya existe o error en el registro");
        }
    }

    /**
     * Procesa y distribuye mensajes generales
     * @param message Contenido del mensaje
     */
    private void handleGeneralMessage(String message) {
        // Guardar el mensaje en la base de datos
        MessageManager.saveMessage(username, "", message);
        
        // Enviar el mensaje a todos los clientes
        server.broadcast(username + ": " + message, username);
    }

    /**
     * Procesa y envía mensajes privados
     * @param message Mensaje en formato destinatario:contenido
     */
    private void handlePrivateMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de mensaje privado inválido");
            return;
        }

        String recipient = parts[0];
        String content = parts[1];
        
        // Guardar el mensaje en la base de datos
        MessageManager.saveMessage(username, recipient, content);
        
        // Enviar el mensaje al destinatario
        if (content.startsWith("IMAGE:")) {
            server.sendPrivateMessage(username, recipient, "[Imagen]");
        } else {
            server.sendPrivateMessage(username, recipient, content);
        }
    }

    /**
     * Maneja la solicitud de lista de usuarios
     */
    private void handleGetUsers() {
        // Obtener todos los usuarios menos el actual y admin
        java.util.List<String> users = server.getAllRegisteredUsers();
        users.remove(username);
        users.remove("admin");
        sendMessage("USERLIST:" + String.join(",", users));
    }

    /**
     * Envía un mensaje al cliente
     * @param message Mensaje a enviar
     */
    public void sendMessage(String message) {
        out.println(message);
    }

    /**
     * Desconecta al cliente y limpia recursos
     */
    public void disconnect() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                if (username != null) {
                    server.removeClient(username);
                    Logger.log("Cliente desconectado: " + username);
                }
                socket.close();
            } catch (IOException e) {
                Logger.error("Error cerrando conexión", e);
            }
        }
    }

    /**
     * Obtiene el nombre de usuario del cliente
     * @return Nombre de usuario
     */
    public String getUsername() {
        return username;
    }

    /**
     * Obtiene la dirección del cliente
     * @return Dirección IP y estado SSL
     */
    public String getClientAddress() {
        return socket.getInetAddress().getHostAddress() + (isSecure ? " (SSL)" : "");
    }
}

