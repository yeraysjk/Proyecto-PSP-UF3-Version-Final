package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    public static void saveMessage(String sender, String recipient, String message) {
        if (recipient == null || recipient.isEmpty()) {
            DatabaseConfig.saveGeneralMessage(sender, message);
        } else {
            DatabaseConfig.saveMessage(sender, recipient, message);
        }
    }

    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        
        // Obtener mensajes generales
        String generalSql = "SELECT sender, message, timestamp FROM mensajes_generales ORDER BY timestamp DESC LIMIT 50";
        
        // Obtener mensajes privados donde el usuario es remitente o destinatario
        String privateSql = "SELECT sender, recipient, message, timestamp FROM mensajes " +
                           "WHERE sender = ? OR recipient = ? ORDER BY timestamp DESC LIMIT 50";
        
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

        // Ordenar los mensajes por fecha (los más recientes primero)
        messages.sort((m1, m2) -> {
            try {
                String timestamp1 = m1.substring(m1.indexOf("[") + 1, m1.indexOf("]"));
                String timestamp2 = m2.substring(m2.indexOf("[") + 1, m2.indexOf("]"));
                return timestamp2.compareTo(timestamp1);
            } catch (Exception e) {
                return 0;
            }
        });
        
        // Limitar a los 100 mensajes más recientes
        if (messages.size() > 100) {
            messages = messages.subList(0, 100);
        }

        return messages;
    }

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
}