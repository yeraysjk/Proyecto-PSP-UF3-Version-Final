package server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class UserManager {
    private static final SecureRandom random = new SecureRandom();
    private static final int SALT_LENGTH = 16;

    public static boolean registerUser(String username, String password) {
        if (userExists(username)) {
            Logger.log("Intento de registro fallido: usuario ya existe - " + username);
            return false;
        }

        // Guardar contraseña en texto plano para pruebas
        String sql = "INSERT INTO usuarios (username, password) VALUES (?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            Logger.log("Usuario registrado correctamente: " + username);
            return true;
        } catch (SQLException e) {
            Logger.error("Error registrando usuario", e);
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password_hash, salt FROM usuarios WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String salt = rs.getString("salt");
                String calculatedHash = hashPassword(password, salt);
                boolean authenticated = storedHash.equals(calculatedHash);
                
                if (authenticated) {
                    Logger.log("Usuario autenticado: " + username);
                } else {
                    Logger.log("Intento de autenticación fallido: " + username);
                }
                
                return authenticated;
            }
            return false;
        } catch (SQLException e) {
            Logger.error("Error autenticando usuario", e);
            return false;
        }
    }

    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            Logger.error("Error verificando existencia de usuario", e);
            return false;
        }
    }

    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            Logger.error("Error en el algoritmo de hash", e);
            throw new RuntimeException("Error en el algoritmo de hash", e);
        }
    }

    public static boolean isAdmin(String username) {
        String sql = "SELECT is_admin FROM usuarios WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBoolean("is_admin");
        } catch (SQLException e) {
            Logger.error("Error verificando privilegios de administrador", e);
            return false;
        }
    }
}
