package server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class FileManager {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String UPLOAD_DIR = "uploads";
    private final Connection conn;

    public FileManager(Connection conn) {
        this.conn = conn;
        createUploadDirectory();
    }

    private void createUploadDirectory() {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            if (uploadDir.mkdir()) {
                Logger.log("Directorio de uploads creado correctamente");
            } else {
                Logger.log("Error al crear el directorio de uploads");
            }
        }
    }

    public String saveFile(String filename, String sender, String recipient, byte[] fileData) throws IOException, SQLException {
        // Validar tamaño del archivo
        if (fileData.length > MAX_FILE_SIZE) {
            throw new IOException("El archivo excede el tamaño máximo permitido (10MB)");
        }

        // Generar ID único
        String fileId = UUID.randomUUID().toString();
        String filePath = Paths.get(UPLOAD_DIR, fileId).toString();

        // Guardar archivo en disco
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(fileData);
        }

        // Registrar en base de datos
        String sql = "INSERT INTO archivos (id, filename, sender, recipient, file_path, file_size) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            pstmt.setString(2, filename);
            pstmt.setString(3, sender);
            pstmt.setString(4, recipient);
            pstmt.setString(5, filePath);
            pstmt.setLong(6, fileData.length);
            pstmt.executeUpdate();
        }

        Logger.log("Archivo guardado correctamente: " + filename);
        return fileId;
    }

    public byte[] getFile(String fileId) throws IOException, SQLException {
        String sql = "SELECT file_path FROM archivos WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String filePath = rs.getString("file_path");
                return Files.readAllBytes(Paths.get(filePath));
            }
        }
        throw new IOException("Archivo no encontrado");
    }

    public void deleteFile(String fileId) throws IOException, SQLException {
        String sql = "SELECT file_path FROM archivos WHERE id = ?";
        String filePath = null;

        // Obtener ruta del archivo
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                filePath = rs.getString("file_path");
            }
        }

        if (filePath != null) {
            // Eliminar archivo del disco
            Files.deleteIfExists(Paths.get(filePath));

            // Eliminar registro de la base de datos
            sql = "DELETE FROM archivos WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, fileId);
                pstmt.executeUpdate();
            }

            Logger.log("Archivo eliminado correctamente: " + fileId);
        } else {
            throw new IOException("Archivo no encontrado");
        }
    }

    public FileInfo getFileInfo(String fileId) throws SQLException {
        String sql = "SELECT id, filename, sender, recipient, file_size, upload_date FROM archivos WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new FileInfo(
                    rs.getString("id"),
                    rs.getString("filename"),
                    rs.getString("sender"),
                    rs.getString("recipient"),
                    rs.getLong("file_size"),
                    rs.getTimestamp("upload_date")
                );
            }
        }
        return null;
    }

    public static class FileInfo {
        private final String id;
        private final String filename;
        private final String sender;
        private final String recipient;
        private final long fileSize;
        private final java.sql.Timestamp uploadDate;

        public FileInfo(String id, String filename, String sender, String recipient, long fileSize, java.sql.Timestamp uploadDate) {
            this.id = id;
            this.filename = filename;
            this.sender = sender;
            this.recipient = recipient;
            this.fileSize = fileSize;
            this.uploadDate = uploadDate;
        }

        public String getId() { return id; }
        public String getFilename() { return filename; }
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public long getFileSize() { return fileSize; }
        public java.sql.Timestamp getUploadDate() { return uploadDate; }
    }
} 