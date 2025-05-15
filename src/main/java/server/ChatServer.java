package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Base64;

public class ChatServer {
    private static final int PORT = 5000;
    private final ExecutorService pool;
    private final Map<String, ClientHandler> clients;
    private final Set<String> connectedUsers;
    private final AtomicBoolean isRunning;
    private ServerSocket serverSocket;

    public ChatServer() {
        this.pool = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
        this.connectedUsers = ConcurrentHashMap.newKeySet();
        this.isRunning = new AtomicBoolean(true);
    }

    public void start() {
        Logger.log("Iniciando servidor de chat...");
        
        try {
            serverSocket = new ServerSocket(PORT);
            Logger.log("Servidor iniciado en el puerto " + PORT);
            Logger.log("Esperando conexiones...");
            
            startServerCommandThread();
            
            while (isRunning.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Logger.log("Nueva conexión desde: " + clientSocket.getInetAddress().getHostAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    pool.execute(clientHandler);
                    
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Logger.error("Error aceptando conexión", e);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error("Error iniciando el servidor", e);
        } finally {
            shutdown();
        }
    }

    private void startServerCommandThread() {
        new Thread(() -> {
            while (isRunning.get()) {
                try {
                    int command = System.in.read();
                    if (command == 'q' || command == 'Q') {
                        shutdown();
                        break;
                    } else if (command == 'l' || command == 'L') {
                        listConnectedClients();
                    }
                } catch (IOException e) {
                    Logger.error("Error leyendo comando", e);
                }
            }
        }).start();
    }

    private void listConnectedClients() {
        Logger.log("Clientes conectados:");
        clients.forEach((username, handler) -> 
            Logger.log("- " + username + " (" + handler.getClientAddress() + ")"));
    }

    public void shutdown() {
        isRunning.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.error("Error cerrando el servidor", e);
        }
        
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Logger.log("Servidor detenido");
    }

    public boolean addClient(String username, ClientHandler handler) {
        if (connectedUsers.contains(username)) {
            return false;
        }
        connectedUsers.add(username);
        clients.put(username, handler);
        broadcastUserList();
        return true;
    }

    public void removeClient(String username) {
        connectedUsers.remove(username);
        clients.remove(username);
        broadcastUserList();
    }

    public void broadcast(String message, String sender) {
        clients.forEach((username, handler) -> {
            if (!username.equals(sender)) {
                handler.sendMessage(message);
            }
        });
    }

    public void broadcastFile(String sender, String recipient, String fileName, byte[] fileData) {
        String message = "FILE:" + sender + ":" + fileName + ":" + Base64.getEncoder().encodeToString(fileData);
        if (recipient == null) {
            clients.forEach((username, handler) -> {
                if (!username.equals(sender)) {
                    handler.sendMessage(message);
                }
            });
        } else {
            ClientHandler handler = clients.get(recipient);
            if (handler != null) {
                handler.sendMessage(message);
            }
        }
    }

    public void sendPrivateMessage(String sender, String recipient, String message) {
        ClientHandler handler = clients.get(recipient);
        if (handler != null) {
            handler.sendMessage(sender + " (privado): " + message);
        }
    }

    public void sendFile(String sender, String recipient, String fileName, byte[] fileData) {
        String message = "FILE:" + sender + ":" + fileName + ":" + Base64.getEncoder().encodeToString(fileData);
        ClientHandler handler = clients.get(recipient);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    private void broadcastUserList() {
        String userList = "USERLIST:" + String.join(",", connectedUsers);
        clients.forEach((username, handler) -> handler.sendMessage(userList));
    }

    public java.util.List<String> getAllRegisteredUsers() {
        return server.DatabaseConfig.getAllUsers();
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
} 