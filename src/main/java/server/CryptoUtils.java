package server;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String CONFIG_FILE = "crypto.properties";
    private static SecretKey key;

    static {
        try {
            loadOrGenerateKey();
        } catch (Exception e) {
            Logger.error("Error inicializando CryptoUtils", e);
            throw new RuntimeException("Error inicializando sistema de encriptación", e);
        }
    }

    private static void loadOrGenerateKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            String salt = props.getProperty("salt");
            String keyStr = props.getProperty("key");
            
            if (salt != null && keyStr != null) {
                key = generateKey(keyStr, salt);
            } else {
                generateNewKey(props);
            }
        } catch (IOException e) {
            generateNewKey(props);
        }
    }

    private static void generateNewKey(Properties props) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        String salt = generateRandomString(16);
        String keyStr = generateRandomString(32);
        
        props.setProperty("salt", salt);
        props.setProperty("key", keyStr);
        
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Crypto configuration");
        }
        
        key = generateKey(keyStr, salt);
    }

    private static SecretKey generateKey(String password, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    public static String encrypt(String message) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            Logger.error("Error encriptando mensaje", e);
            throw new RuntimeException("Error en encriptación", e);
        }
    }

    public static String decrypt(String encryptedMessage) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedMessage));
            return new String(decryptedBytes);
        } catch (Exception e) {
            Logger.error("Error desencriptando mensaje", e);
            throw new RuntimeException("Error en desencriptación", e);
        }
    }
}
