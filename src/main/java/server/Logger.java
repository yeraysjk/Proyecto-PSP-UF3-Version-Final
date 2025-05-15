package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private static final String LOG_FILE = "server.log";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public static void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] " + message;
        
        // Imprimir en consola
        System.out.println(logMessage);
        
        // Guardar en archivo
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(logMessage);
        } catch (IOException e) {
            System.err.println("Error escribiendo en el archivo de log: " + e.getMessage());
        }
    }
    
    public static void error(String message, Exception e) {
        log("ERROR: " + message);
        if (e != null) {
            log("Exception: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                log("  at " + element.toString());
            }
        }
    }
} 