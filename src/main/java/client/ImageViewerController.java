package client;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

/**
 * Controlador para la ventana de visualización de imágenes.
 * Permite mostrar imágenes enviadas en el chat y ajustar el tamaño de la ventana.
 */
public class ImageViewerController {
    // Componentes de la interfaz gráfica
    @FXML private ImageView imageView;        // Visor de imagen
    @FXML private Label imageInfoLabel;       // Etiqueta con información de la imagen
    @FXML private Button closeButton;         // Botón para cerrar la ventana

    // Variables de control
    private Stage stage;                      // Ventana del visor

    /**
     * Establece la ventana del visor
     * @param stage Ventana del visor
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Configura la imagen a mostrar y su información
     * @param image Imagen a mostrar
     * @param sender Remitente de la imagen
     * @param fileName Nombre del archivo
     */
    public void setImage(Image image, String sender, String fileName) {
        imageView.setImage(image);
        imageInfoLabel.setText("Imagen enviada por: " + sender + " - " + fileName);
        
        // Ajustar el tamaño de la ventana según la imagen
        if (stage != null) {
            stage.setWidth(Math.min(800, image.getWidth() + 40));
            stage.setHeight(Math.min(600, image.getHeight() + 100));
        }
    }

    /**
     * Maneja el evento de cierre de la ventana
     */
    @FXML
    private void handleClose() {
        if (stage != null) {
            stage.close();
        }
    }
} 