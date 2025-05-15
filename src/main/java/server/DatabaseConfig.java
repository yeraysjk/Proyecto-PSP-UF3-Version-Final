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
            
            // Crear tabla de usuarios1
            String createUsersTable = 
                "CREATE TABLE IF NOT EXISTS usuarios1 (" +
                "    id SERIAL PRIMARY KEY," +
                "    username VARCHAR(50) UNIQUE NOT NULL," +
                "    password VARCHAR(255) NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    is_admin BOOLEAN DEFAULT FALSE" +
                ")";
            
            // Crear tabla de mensajes1
            String createMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes1 (" +
                "    id SERIAL PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    recipient VARCHAR(50) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    is_read BOOLEAN DEFAULT FALSE," +
                "    FOREIGN KEY (sender) REFERENCES usuarios1(username)," +
                "    FOREIGN KEY (recipient) REFERENCES usuarios1(username)" +
                ")";
            
            // Crear tabla de mensajes_generales1
            String createGeneralMessagesTable = 
                "CREATE TABLE IF NOT EXISTS mensajes_generales1 (" +
                "    id SERIAL PRIMARY KEY," +
                "    sender VARCHAR(50) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (sender) REFERENCES usuarios1(username)" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createUsersTable);
                stmt.execute(createMessagesTable);
                stmt.execute(createGeneralMessagesTable);
                Logger.log("Tablas nuevas creadas correctamente");
            }
            // Crear usuarios de prueba si no existen
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios1 (username, password) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM usuarios1 WHERE username = ?)");) {
                ps.setString(1, "user1");
                ps.setString(2, "user1");
                ps.setString(3, "user1");
                ps.executeUpdate();
                ps.setString(1, "user2");
                ps.setString(2, "user2");
                ps.setString(3, "user2");
                ps.executeUpdate();
                ps.setString(1, "prueba1");
                ps.setString(2, "prueba1");
                ps.setString(3, "prueba1");
                ps.executeUpdate();
                ps.setString(1, "prueba2");
                ps.setString(2, "prueba2");
                ps.setString(3, "prueba2");
                ps.executeUpdate();
            }
        }
    }

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO usuarios1 (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("Usuario registrado correctamente: " + username);
            return true;
        } catch (SQLException e) {
            System.err.println("Error registrando usuario: " + e.getMessage());
            return false;
        }
    }

    public static boolean validateUser(String username, String password) {
        String sql = "SELECT password FROM usuarios1 WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    boolean isValid = storedPassword.equals(password);
                    System.out.println("Validación de usuario " + username + ": " + (isValid ? "correcta" : "incorrecta"));
                    return isValid;
                }
                System.out.println("Usuario no encontrado: " + username);
            }
        } catch (SQLException e) {
            System.err.println("Error validando usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM usuarios1 WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                boolean exists = rs.getInt(1) > 0;
                System.out.println("Verificación de existencia de usuario " + username + ": " + (exists ? "existe" : "no existe"));
                return exists;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando usuario: " + e.getMessage());
            return false;
        }
    }

    public static void saveMessage(String sender, String recipient, String message) {
        String sql = "INSERT INTO mensajes1 (sender, recipient, message, is_read) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, message);
            pstmt.setBoolean(4, false);
            pstmt.executeUpdate();
            System.out.println("Mensaje guardado en la base de datos: " + sender + " -> " + recipient + ": " + message);
        } catch (SQLException e) {
            System.err.println("Error guardando mensaje: " + e.getMessage());
        }
    }

    public static List<String> getUnreadMessages(String recipient) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, message, timestamp FROM mensajes1 WHERE recipient = ? AND is_read = 0 ORDER BY timestamp";
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
            String updateSql = "UPDATE mensajes1 SET is_read = 1 WHERE recipient = ? AND is_read = 0";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, recipient);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo mensajes: " + e.getMessage());
        }
        return messages;
    }

    public static List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM usuarios1 WHERE username != 'admin' ORDER BY username";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo usuarios: " + e.getMessage());
        }
        return users;
    }

    public static void saveGeneralMessage(String sender, String message) {
        String sql = "INSERT INTO mensajes_generales1 (sender, message) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
            System.out.println("Mensaje general guardado en la base de datos: " + sender + ": " + message);
        } catch (SQLException e) {
            System.err.println("Error guardando mensaje general: " + e.getMessage());
        }
    }

    public static List<String> getMessageHistory(String username) {
        List<String> messages = new ArrayList<>();
        // Obtener mensajes privados
        String privateSql = 
            "SELECT sender, recipient, message, timestamp " +
            "FROM mensajes1 " +
            "WHERE sender = ? OR recipient = ? " +
            "ORDER BY timestamp DESC " +
            "LIMIT 50";
        // Obtener mensajes generales
        String generalSql = 
            "SELECT sender, message, timestamp " +
            "FROM mensajes_generales1 " +
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
            System.out.println("Total de mensajes encontrados: " + messages.size());
        } catch (SQLException e) {
            System.err.println("Error obteniendo historial de mensajes: " + e.getMessage());
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
            String privateSql = "DELETE FROM mensajes1";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales
            String generalSql = "DELETE FROM mensajes_generales1";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            System.out.println("Historial de mensajes limpiado correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar el historial de mensajes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearUserMessages(String username) {
        try (Connection conn = getConnection()) {
            // Borrar mensajes privados del usuario
            String privateSql = "DELETE FROM mensajes1 WHERE sender = ? OR recipient = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(privateSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            }

            // Borrar mensajes generales del usuario
            String generalSql = "DELETE FROM mensajes_generales1 WHERE sender = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
            }

            System.out.println("Mensajes del usuario " + username + " limpiados correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar los mensajes del usuario: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearGeneralMessages() {
        try (Connection conn = getConnection()) {
            // Borrar solo mensajes generales
            String generalSql = "DELETE FROM mensajes_generales1";
            try (PreparedStatement pstmt = conn.prepareStatement(generalSql)) {
                pstmt.executeUpdate();
            }

            System.out.println("Mensajes generales limpiados correctamente");
        } catch (SQLException e) {
            System.err.println("Error al limpiar los mensajes generales: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 