package server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Gestor de usuarios del sistema.
 * Maneja el registro, autenticación y gestión de usuarios con seguridad.
 */
public class UserManager {
    // Configuración de seguridad
    private static final SecureRandom random = new SecureRandom();  // Generador de números aleatorios seguros
    private static final int SALT_LENGTH = 16;                      // Longitud del salt en bytes

    /**
     * Registra un nuevo usuario en el sistema
     * @param username Nombre de usuario
     * @param password Contraseña en texto plano
     * @return true si el registro fue exitoso
     */
    public static boolean registerUser(String username, String password) {
        Logger.log("Intentando registrar usuario: " + username);
        if (userExists(username)) {
            Logger.log("Intento de registro fallido: usuario ya existe - " + username);
            return false;
        }

        // Generar salt y hash para la contraseña
        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);
        Logger.log("Hash y salt generados para el usuario: " + username);
        
        // Guardar usuario con contraseña cifrada
        String sql = "INSERT INTO usuarios (username, password_hash, salt) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, salt);
            stmt.executeUpdate();
            Logger.log("Usuario registrado correctamente: " + username);
            return true;
        } catch (SQLException e) {
            Logger.error("Error SQL registrando usuario: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Autentica a un usuario usando su contraseña
     * @param username Nombre de usuario
     * @param password Contraseña en texto plano
     * @return true si la autenticación fue exitosa
     */
    public static boolean authenticateUser(String username, String password) {
        Logger.log("Autenticando usuario: " + username + " (usando hash+salt)");
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
                    Logger.log("Usuario autenticado correctamente: " + username);
                } else {
                    Logger.log("Intento de autenticación fallido (hash no coincide): " + username);
                }
                
                return authenticated;
            }
            Logger.log("Intento de autenticación fallido (usuario no existe): " + username);
            return false;
        } catch (SQLException e) {
            Logger.error("Error SQL autenticando usuario: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifica si un usuario existe en el sistema
     * @param username Nombre de usuario a verificar
     * @return true si el usuario existe
     */
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

    /**
     * Genera un salt aleatorio para el hash de contraseñas
     * @return Salt codificado en Base64
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Genera el hash de una contraseña usando SHA-256
     * @param password Contraseña en texto plano
     * @param salt Salt para el hash
     * @return Hash de la contraseña codificado en Base64
     */
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

    /**
     * Verifica si un usuario tiene privilegios de administrador
     * @param username Nombre de usuario
     * @return true si el usuario es administrador
     */
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
