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

/**
 * Servidor principal del chat.
 * Maneja las conexiones de los clientes, la distribución de mensajes y la gestión de usuarios.
 */
public class ChatServer {
    // Configuración del servidor
    private static final int PORT = 5000;                    // Puerto del servidor
    
    // Componentes del servidor
    private final ExecutorService pool;                      // Pool de hilos para manejar clientes
    private final Map<String, ClientHandler> clients;        // Mapa de clientes conectados
    private final Set<String> connectedUsers;               // Conjunto de usuarios conectados
    private final AtomicBoolean isRunning;                  // Estado del servidor
    private ServerSocket serverSocket;                      // Socket del servidor

    /**
     * Constructor del servidor
     * Inicializa los componentes necesarios para el funcionamiento
     */
    public ChatServer() {
        this.pool = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
        this.connectedUsers = ConcurrentHashMap.newKeySet();
        this.isRunning = new AtomicBoolean(true);
    }

    /**
     * Inicia el servidor y comienza a aceptar conexiones
     */
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

    /**
     * Inicia el hilo que maneja los comandos del servidor
     * Permite apagar el servidor (q) o listar clientes (l)
     */
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

    /**
     * Lista los clientes conectados en el log
     */
    private void listConnectedClients() {
        Logger.log("Clientes conectados:");
        clients.forEach((username, handler) -> 
            Logger.log("- " + username + " (" + handler.getClientAddress() + ")"));
    }

    /**
     * Apaga el servidor de forma segura
     * Cierra todas las conexiones y libera recursos
     */
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

    /**
     * Añade un nuevo cliente al servidor
     * @param username Nombre del usuario
     * @param handler Manejador del cliente
     * @return true si se añadió correctamente
     */
    public boolean addClient(String username, ClientHandler handler) {
        if (connectedUsers.contains(username)) {
            return false;
        }
        connectedUsers.add(username);
        clients.put(username, handler);
        broadcastUserList();
        return true;
    }

    /**
     * Elimina un cliente del servidor
     * @param username Nombre del usuario a eliminar
     */
    public void removeClient(String username) {
        connectedUsers.remove(username);
        clients.remove(username);
        broadcastUserList();
    }

    /**
     * Envía un mensaje a todos los clientes excepto al remitente
     * @param message Mensaje a enviar
     * @param sender Remitente del mensaje
     */
    public void broadcast(String message, String sender) {
        clients.forEach((username, handler) -> {
            if (!username.equals(sender)) {
                handler.sendMessage(message);
            }
        });
    }

    /**
     * Envía un mensaje privado entre dos usuarios
     * @param sender Remitente del mensaje
     * @param recipient Destinatario del mensaje
     * @param message Contenido del mensaje
     */
    public void sendPrivateMessage(String sender, String recipient, String message) {
        ClientHandler handler = clients.get(recipient);
        if (handler != null) {
            handler.sendMessage(sender + " (privado): " + message);
        }
    }

    /**
     * Envía la lista actualizada de usuarios a todos los clientes
     */
    private void broadcastUserList() {
        String userList = "USERLIST:" + String.join(",", connectedUsers);
        clients.forEach((username, handler) -> handler.sendMessage(userList));
    }

    /**
     * Obtiene la lista de todos los usuarios registrados
     * @return Lista de usuarios
     */
    public java.util.List<String> getAllRegisteredUsers() {
        return server.DatabaseConfig.getAllUsers();
    }

    /**
     * Punto de entrada principal del servidor
     */
    public static void main(String[] args) {
        new ChatServer().start();
    }
} 