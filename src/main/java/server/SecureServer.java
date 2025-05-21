package server;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor seguro con soporte SSL/TLS.
 * Proporciona una capa de seguridad adicional para las comunicaciones del chat.
 */
public class SecureServer {
    // Configuración del servidor
    private static final int PORT = 12345;                // Puerto para conexiones seguras
    
    // Componentes del servidor
    private final ChatServer chatServer;                  // Instancia del servidor de chat
    private final ExecutorService pool;                   // Pool de hilos para manejar clientes
    private SSLServerSocket serverSocket;                 // Socket del servidor SSL
    private volatile boolean isRunning;                   // Estado del servidor

    /**
     * Constructor del servidor seguro
     * Inicializa los componentes necesarios
     */
    public SecureServer() {
        this.chatServer = new ChatServer();
        this.pool = Executors.newCachedThreadPool();
        this.isRunning = true;
    }

    /**
     * Inicia el servidor seguro y comienza a aceptar conexiones SSL
     */
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

    /**
     * Obtiene el contexto SSL configurado
     * @return Contexto SSL
     * @throws Exception Si hay error en la configuración SSL
     */
    private SSLContext getSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext;
    }

    /**
     * Apaga el servidor de forma segura
     * Cierra todas las conexiones y libera recursos
     */
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

    /**
     * Punto de entrada principal del servidor seguro
     */
    public static void main(String[] args) {
        new SecureServer().start();
    }
}
