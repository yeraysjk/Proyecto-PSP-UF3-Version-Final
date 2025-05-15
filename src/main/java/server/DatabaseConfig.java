package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConfig {
    // Configuración para PostgreSQL remota
    private static final String DB_URL = "jdbc:postgresql://cicles.ies-eugeni.cat:5432/grupf_db";
    private static final String USER = "grupf";
    private static final String PASSWORD = "m13@24-25_grupf";

    static {
        try {
            // Cargar el driver de PostgreSQL
            Class.forName("org.postgresql.Driver");
            Logger.log("Driver PostgreSQL cargado correctamente");
            initDatabase();
        } catch (SQLException e) {
            Logger.error("Error inicializando la base de datos", e);
        } catch (ClassNotFoundException e) {
            Logger.error("Error: No se encontró el driver de PostgreSQL", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }

    private static void initDatabase() throws SQLException {
        try (Connection conn = getConnection()) {
            Logger.log("Conexión a la base de datos establecida correctamente");
            
            // Crear tabla de usuarios
            String createUsersTable = 
                "CREATE TABLE IF NOT EXISTS usuarios (" +
                "    id SERIAL PRIMARY KEY," +
                "    username VARCHAR(50) UNIQUE NOT NULL," +
                "    password_hash VARCHAR(255) NOT NULL," +
                "    salt VARCHAR(255) NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    is_admin BOOLEAN DEFAULT FALSE" +
                ")";
            
            // Crear tabla de mensajes
            String createMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes (" +
                "    id SERIAL PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    recipient VARCHAR(50) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    is_read BOOLEAN DEFAULT FALSE," +
                "    FOREIGN KEY (sender) REFERENCES usuarios(username)," +
                "    FOREIGN KEY (recipient) REFERENCES usuarios(username)" +
                ")";
            
            // Crear tabla de mensajes_generales
            String createGeneralMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes_generales (" +
                "    id SERIAL PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (sender) REFERENCES usuarios(username)" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createUsersTable);
                stmt.execute(createMessagesTable);
                stmt.execute(createGeneralMessagesTable);
                Logger.log("Tablas creadas correctamente");
            }
            // Crear usuario de prueba si no existe
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios (username, password_hash, salt) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE username = ?)");) {
                // Generar salt y hash para el usuario de prueba
                String salt1 = UserManager.generateSalt();
                String passwordHash1 = UserManager.hashPassword("user1", salt1);
                ps.setString(1, "user1");
                ps.setString(2, passwordHash1);
                ps.setString(3, salt1);
                ps.setString(4, "user1");
                ps.executeUpdate();
                Logger.debug("Usuario user1 creado o verificado en el sistema");
            }
        }
    }

    public static void saveMessage(String sender, String recipient, String message) {
        String sql = "INSERT INTO mensajes (sender, recipient, message, is_read) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, message);
            pstmt.setBoolean(4, false);
            pstmt.executeUpdate();
            Logger.debug("Mensaje guardado en la base de datos: " + sender + " -> " + recipient + ": " + message);
        } catch (SQLException e) {
            Logger.error("Error guardando mensaje: " + e.getMessage(), e);
        }
    }

    public static List<String> getUnreadMessages(String recipient) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes WHERE recipient = ? AND is_read = 0 ORDER BY timestamp";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipient);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String message = rs.getString("message");
                    String timestamp = rs.getString("timestamp");
                    messages.add("[" + timestamp + "] " + sender + " (privado): " + message);
                }
            }
            // Marcar mensajes como leídos
            String updateSql = "UPDATE mensajes SET is_read = 1 WHERE recipient = ? AND is_read = 0";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, recipient);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo mensajes: " + e.getMessage(), e);
        }
        return messages;
    }

    public static List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM usuarios WHERE username != 'admin' ORDER BY username";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            Logger.error("Error obteniendo usuarios: " + e.getMessage(), e);
        }
        return users;
    }

    public static void saveGeneralMessage(String sender, String message) {
        String sql = "INSERT INTO mensajes_generales (sender, message) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
            Logger.debug("Mensaje general guardado en la base de datos: " + sender + ": " + message);
        } catch (SQLException e) {
            Logger.error("Error guardando mensaje general: " + e.getMessage(), e);
        }
    }

    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        // Obtener mensajes privados
        String privateSql = 
            "SELECT sender, recipient, message, timestamp " +
            "FROM mensajes " +
            "WHERE sender = ? OR recipient = ? " +
            "ORDER BY timestamp DESC " +
            "LIMIT 50";
        // Obtener mensajes generales
        String generalSql = 
            "SELECT sender, message, timestamp " +
            "FROM mensajes_generales " +
            "ORDER BY timestamp DESC " +
            "LIMIT 50";
        try (Connection conn = getConnection()) {
            // Obtener mensajes privados
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String sender = rs.getString("sender");
                        String recipient = rs.getString("recipient");
                        String message = rs.getString("message");
                        String timestamp = rs.getString("timestamp");
                        String messageType;
                        if (sender.equals(username)) {
                            messageType = "Tú -> " + recipient;
                        } else if (recipient.equals(username)) {
                            messageType = sender + " -> Tú";
                        } else {
                            messageType = sender + " -> " + recipient;
                        }
                        String formattedMessage = "[" + timestamp + "] " + messageType + " (privado): " + message;
                        messages.add(formattedMessage);
                    }
                }
            }
            // Obtener mensajes generales
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(generalSql)) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String message = rs.getString("message");
                    String timestamp = rs.getString("timestamp");
                    String formattedMessage = "[" + timestamp + "] " + sender + " (general): " + message;
                    messages.add(formattedMessage);
                }
            }
            // Ordenar todos los mensajes por timestamp
            messages.sort((a, b) -> {
                String timestampA = a.substring(1, a.indexOf("]"));
                String timestampB = b.substring(1, b.indexOf("]"));
                return timestampB.compareTo(timestampA);
            });
            // Limitar a 50 mensajes
            if (messages.size() > 50) {
                messages = messages.subList(0, 50);
            }
            Logger.debug("Total de mensajes encontrados: " + messages.size());
        } catch (SQLException e) {
            Logger.error("Error obteniendo historial de mensajes: " + e.getMessage(), e);
        }
        return messages;
    }

    public static void saveMessage(String message) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO messages (message) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, message);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void clearMessageHistory() {
        try (Connection conn = getConnection()) {
            // Borrar mensajes privados
            String privateSql = "DELETE FROM mensajes";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales
            String generalSql = "DELETE FROM mensajes_generales";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            Logger.debug("Historial de mensajes limpiado correctamente");
        } catch (SQLException e) {
            Logger.error("Error al limpiar el historial de mensajes: " + e.getMessage(), e);
        }
    }

    public static void clearUserMessages(String username) {
        try (Connection conn = getConnection()) {
            // Borrar mensajes privados del usuario
            String privateSql = "DELETE FROM mensajes WHERE sender = ? OR recipient = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales del usuario
            String generalSql = "DELETE FROM mensajes_generales WHERE sender = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            }

            Logger.debug("Mensajes del usuario " + username + " limpiados correctamente");
        } catch (SQLException e) {
            Logger.error("Error al limpiar los mensajes del usuario: " + e.getMessage(), e);
        }
    }

    public static void clearGeneralMessages() {
        try (Connection conn = getConnection()) {
            // Borrar solo mensajes generales
            String generalSql = "DELETE FROM mensajes_generales";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            Logger.debug("Mensajes generales limpiados correctamente");
        } catch (SQLException e) {
            Logger.error("Error al limpiar los mensajes generales: " + e.getMessage(), e);
        }
    }
} 