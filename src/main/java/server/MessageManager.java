package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestor de mensajes del chat.
 * Maneja el almacenamiento, recuperación y gestión de mensajes en la base de datos.
 */
public class MessageManager {
    /**
     * Guarda un mensaje en la base de datos
     * @param sender Remitente del mensaje
     * @param recipient Destinatario (vacío para mensaje general)
     * @param message Contenido del mensaje
     */
    public static void saveMessage(String sender, String recipient, String message) {
        if (recipient == null || recipient.isEmpty()) {
            DatabaseConfig.saveGeneralMessage(sender, message);
        } else {
            DatabaseConfig.saveMessage(sender, recipient, message);
        }
    }

    /**
     * Obtiene el historial completo de mensajes de un usuario
     * Incluye mensajes generales y privados
     * @param username Nombre del usuario
     * @return Lista de mensajes formateados
     */
    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        
        // Obtener mensajes generales
        String generalSql = "SELECT sender, message, timestamp FROM mensajes_generales ORDER BY timestamp ASC";
        
        // Obtener mensajes privados donde el usuario es remitente o destinatario
        String privateSql = "SELECT sender, recipient, message, timestamp FROM mensajes " +
                           "WHERE sender = ? OR recipient = ? ORDER BY timestamp ASC";
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Obtener mensajes generales
            try (PreparedStatement generalStmt = conn.prepareStatement(generalSql);
                 ResultSet generalRs = generalStmt.executeQuery()) {
                
                while (generalRs.next()) {
                    String sender = generalRs.getString("sender");
                    String message = generalRs.getString("message");
                    String timestamp = generalRs.getString("timestamp");
                    messages.add("[" + timestamp + "] " + sender + ": " + message);
                }
            }
            
            // Obtener mensajes privados
            try (PreparedStatement privateStmt = conn.prepareStatement(privateSql)) {
                privateStmt.setString(1, username);
                privateStmt.setString(2, username);
                
                try (ResultSet privateRs = privateStmt.executeQuery()) {
                    while (privateRs.next()) {
                        String sender = privateRs.getString("sender");
                        String recipient = privateRs.getString("recipient");
                        String message = privateRs.getString("message");
                        String timestamp = privateRs.getString("timestamp");
                        
                        // Formatear de manera diferente según sea mensaje enviado o recibido
                        if (sender.equals(username)) {
                            messages.add("[" + timestamp + "] Tú -> " + recipient + ": " + message);
                        } else {
                            messages.add("[" + timestamp + "] " + sender + " -> Tú: " + message);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo historial de mensajes", e);
        }

        // Ordenar los mensajes por fecha (los más antiguos primero)
        messages.sort((m1, m2) -> {
            try {
                String timestamp1 = m1.substring(m1.indexOf("[") + 1, m1.indexOf("]"));
                String timestamp2 = m2.substring(m2.indexOf("[") + 1, m2.indexOf("]"));
                return timestamp1.compareTo(timestamp2);
            } catch (Exception e) {
                return 0;
            }
        });

        return messages;
    }

    /**
     * Obtiene los mensajes no leídos de un usuario
     * @param username Nombre del usuario
     * @return Lista de mensajes no leídos
     */
    public static List<String> getUnreadMessages(String username) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes " +
                    "WHERE recipient = ? AND is_read = 0 ORDER BY timestamp";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                messages.add("[" + timestamp + "] " + sender + " (privado): " + message);
            }

            // Marcar mensajes como leídos
            if (!messages.isEmpty()) {
                markMessagesAsRead(username);
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo mensajes no leídos", e);
        }

        return messages;
    }

    /**
     * Marca los mensajes de un usuario como leídos
     * @param username Nombre del usuario
     */
    private static void markMessagesAsRead(String username) {
        String sql = "UPDATE mensajes SET is_read = 1 WHERE recipient = ? AND is_read = 0";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.error("Error marcando mensajes como leídos", e);
        }
    }

    /**
     * Limpia todo el historial de mensajes
     * Borra mensajes generales y privados
     */
    public static void clearMessageHistory() {
        String[] tables = {"mensajes", "mensajes_generales"};
        for (String table : tables) {
            String sql = "DELETE FROM " + table;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                Logger.log("Historial de mensajes borrado: " + table);
            } catch (SQLException e) {
                Logger.error("Error borrando historial de mensajes", e);
            }
        }
    }

    /**
     * Obtiene el historial de mensajes privados entre dos usuarios
     * @param user1 Primer usuario
     * @param user2 Segundo usuario
     * @return Lista de mensajes privados
     */
    public static List<String> getPrivateHistory(String user1, String user2) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, recipient, message, timestamp FROM mensajes " +
                     "WHERE (sender = ? AND recipient = ?) OR (sender = ? AND recipient = ?) " +
                     "ORDER BY timestamp ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String recipient = rs.getString("recipient");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                
                // Formatear de manera que indique claramente si es mensaje enviado o recibido
                if (sender.equals(user1)) {
                    messages.add("[" + timestamp + "] Tú -> " + recipient + ": " + message);
                } else {
                    messages.add("[" + timestamp + "] " + sender + " -> Tú: " + message);
                }
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo historial privado", e);
        }
        return messages;
    }

    /**
     * Obtiene el historial de mensajes generales
     * @return Lista de mensajes generales
     */
    public static List<String> getGeneralHistory() {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes_generales ORDER BY timestamp ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                messages.add("[" + timestamp + "] " + sender + ": " + message);
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo historial general", e);
        }
        return messages;
    }

    /**
     * Limpia todos los mensajes generales
     */
    public static void clearGeneralMessages() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = "DELETE FROM mensajes_generales";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            Logger.error("Error limpiando mensajes generales", e);
        }
    }

    /**
     * Limpia todos los mensajes de un usuario específico
     * Incluye mensajes generales y privados
     * @param username Nombre del usuario
     */
    public static void clearUserMessages(String username) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Limpiar mensajes privados donde el usuario es remitente o destinatario
            String privateSql = "DELETE FROM mensajes WHERE sender = ? OR recipient = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            }

            // Limpiar mensajes generales del usuario
            String generalSql = "DELETE FROM mensajes_generales WHERE sender = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            Logger.error("Error limpiando mensajes del usuario", e);
        }
    }
}