package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final String LOG_FILE = "server.log";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Niveles de log
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
    
    // Nivel mínimo que se mostrará en consola (se puede cambiar según necesidades)
    private static Level consoleLevel = Level.ERROR;
    
    // Nivel mínimo que se guardará en archivo (se guardarán todos los logs)
    private static Level fileLevel = Level.DEBUG;
    
    /**
     * Configura el nivel mínimo de log para la consola
     */
    public static void setConsoleLevel(Level level) {
        consoleLevel = level;
    }
    
    /**
     * Log genérico con nivel especificado
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
     * Log de nivel INFO (compatibilidad con código existente)
     */
    public static void log(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Log para información importante (registro, conexiones nuevas)
     */
    public static void important(String message) {
        log(Level.IMPORTANT, message);
    }
    
    /**
     * Log para depuración
     */
    public static void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    /**
     * Log para errores
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