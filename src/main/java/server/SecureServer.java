package server;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecureServer {
    private static final int PORT = 12345;
    private final ChatServer chatServer;
    private final ExecutorService pool;
    private SSLServerSocket serverSocket;
    private volatile boolean isRunning;

    public SecureServer() {
        this.chatServer = new ChatServer();
        this.pool = Executors.newCachedThreadPool();
        this.isRunning = true;
    }

    public void start() {
        try {
            SSLServerSocketFactory factory = getSSLContext().getServerSocketFactory();
            serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            
            // Configurar los protocolos SSL/TLS permitidos
            serverSocket.setEnabledProtocols(new String[] {"TLSv1.2", "TLSv1.3"});
            
            // Configurar los cifrados permitidos
            serverSocket.setEnabledCipherSuites(serverSocket.getSupportedCipherSuites());
            
            Logger.log("🔐 Servidor seguro iniciado en el puerto " + PORT);
            
            while (isRunning) {
                try {
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    Logger.log("✅ Nueva conexión segura desde: " + clientSocket.getInetAddress().getHostAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, chatServer);
                    pool.execute(clientHandler);
                    
                } catch (Exception e) {
                    if (isRunning) {
                        Logger.error("Error aceptando conexión", e);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Error iniciando el servidor seguro", e);
        } finally {
            shutdown();
        }
    }

    private SSLContext getSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext;
    }

    public void shutdown() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Logger.error("Error cerrando el servidor", e);
        }
        
        pool.shutdown();
        Logger.log("Servidor seguro detenido");
    }

    public static void main(String[] args) {
        new SecureServer().start();
    }
}
