package server;

import java.io.*;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import server.UserManager;
import server.MessageManager;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private String username;
    private final AtomicBoolean isRunning;
    private final ChatServer server;
    private final boolean isSecure;

    public ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.isSecure = socket instanceof SSLSocket;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.isRunning = new AtomicBoolean(true);
    }

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

    private void handleClient() throws IOException {
        // Esperar login
        String loginMessage = in.readLine();
        if (loginMessage == null || !loginMessage.startsWith("LOGIN:")) {
            sendMessage("ERROR: Formato de login inválido");
            return;
        }

        username = loginMessage.substring(6);
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

    private void handleMessage(String message) {
        try {
            if (message.startsWith("REGISTER:")) {
                handleRegister(message.substring(9));
            } else if (message.startsWith("MESSAGE:")) {
                handleGeneralMessage(message.substring(8));
            } else if (message.startsWith("PRIVATE:")) {
                handlePrivateMessage(message.substring(8));
            } else if (message.startsWith("FILE:")) {
                handleFileMessage(message.substring(5));
            } else if (message.startsWith("PRIVATE_FILE:")) {
                handlePrivateFileMessage(message.substring(13));
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
            } else {
                sendMessage("ERROR: Comando no reconocido");
            }
        } catch (Exception e) {
            Logger.error("Error procesando mensaje", e);
            sendMessage("ERROR: Error procesando mensaje");
        }
    }

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

    private void handleGeneralMessage(String message) {
        // Guardar el mensaje original (sin cifrar) en la base de datos
        MessageManager.saveMessage(username, null, message);
        
        // Enviar el mensaje a todos los clientes
        server.broadcast(username + ": " + message, username);
    }

    private void handlePrivateMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de mensaje privado inválido");
            return;
        }

        String recipient = parts[0];
        String content = parts[1];
        
        // Guardar el mensaje sin cifrar en la base de datos
        MessageManager.saveMessage(username, recipient, content);
        
        // Enviar el mensaje al destinatario
        server.sendPrivateMessage(username, recipient, content);
    }

    private void handleFileMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) {
            sendMessage("ERROR: Formato de archivo inválido");
            return;
        }

        String fileName = parts[0];
        byte[] fileData = Base64.getDecoder().decode(parts[1]);
        server.broadcastFile(username, null, fileName, fileData);
    }

    private void handlePrivateFileMessage(String message) {
        String[] parts = message.split(":", 3);
        if (parts.length != 3) {
            sendMessage("ERROR: Formato de archivo privado inválido");
            return;
        }

        String recipient = parts[0];
        String fileName = parts[1];
        byte[] fileData = Base64.getDecoder().decode(parts[2]);
        server.sendFile(username, recipient, fileName, fileData);
    }

    private void handleGetUsers() {
        // Obtener todos los usuarios menos el actual y admin
        java.util.List<String> users = server.getAllRegisteredUsers();
        users.remove(username);
        users.remove("admin");
        sendMessage("USERLIST:" + String.join(",", users));
    }

    public void sendMessage(String message) {
        out.println(message);
    }

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

    public String getUsername() {
        return username;
    }

    public String getClientAddress() {
        return socket.getInetAddress().getHostAddress() + (isSecure ? " (SSL)" : "");
    }
}

