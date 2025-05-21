package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Sistema de registro (logging) del servidor.
 * Maneja el registro de eventos, errores y depuración tanto en consola como en archivo.
 */
public class Logger {
    // Configuración del logger
    private static final String LOG_FILE = "server.log";                    // Archivo de log
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  // Formato de fecha
    
    /**
     * Niveles de severidad para los mensajes de log
     */
    public enum Level {
        DEBUG(0),      // Información detallada, útil para depuración
        INFO(1),       // Información general sobre el funcionamiento normal
        IMPORTANT(2),  // Información importante (registro de usuarios, conexiones)
        WARNING(3),    // Advertencias
        ERROR(4);      // Errores
        
        private final int value;
        
        Level(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    // Configuración de niveles de log
    private static Level consoleLevel = Level.ERROR;    // Nivel mínimo para mostrar en consola
    private static Level fileLevel = Level.DEBUG;       // Nivel mínimo para guardar en archivo
    
    /**
     * Configura el nivel mínimo de log para la consola
     * @param level Nuevo nivel mínimo
     */
    public static void setConsoleLevel(Level level) {
        consoleLevel = level;
    }
    
    /**
     * Registra un mensaje con el nivel especificado
     * @param level Nivel de severidad del mensaje
     * @param message Mensaje a registrar
     */
    public static void log(Level level, String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] " + message;
        
        // Imprimir en consola solo si el nivel es suficiente
        if (level.getValue() >= consoleLevel.getValue()) {
            System.out.println(logMessage);
        }
        
        // Guardar en archivo solo si el nivel es suficiente
        if (level.getValue() >= fileLevel.getValue()) {
            try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                out.println(logMessage);
            } catch (IOException e) {
                if (level.getValue() >= Level.ERROR.getValue()) {
                    System.err.println("Error escribiendo en el archivo de log: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Registra un mensaje de nivel INFO
     * @param message Mensaje a registrar
     */
    public static void log(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Registra un mensaje importante
     * @param message Mensaje a registrar
     */
    public static void important(String message) {
        log(Level.IMPORTANT, message);
    }
    
    /**
     * Registra un mensaje de depuración
     * @param message Mensaje a registrar
     */
    public static void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    /**
     * Registra un error y su stack trace
     * @param message Mensaje de error
     * @param e Excepción que causó el error
     */
    public static void error(String message, Exception e) {
        log(Level.ERROR, "ERROR: " + message);
        if (e != null) {
            log(Level.ERROR, "Exception: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                log(Level.ERROR, "  at " + element.toString());
            }
        }
    }
} 