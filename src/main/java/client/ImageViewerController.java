package client;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class ImageViewerController {
    @FXML
    private ImageView imageView;
    @FXML
    private Label imageInfoLabel;
    @FXML
    private Button closeButton;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setImage(Image image, String sender, String fileName) {
        imageView.setImage(image);
        imageInfoLabel.setText("Imagen enviada por: " + sender + " - " + fileName);
        
        // Ajustar el tamaño de la ventana según la imagen
        if (stage != null) {
            stage.setWidth(Math.min(800, image.getWidth() + 40));
            stage.setHeight(Math.min(600, image.getHeight() + 100));
        }
    }

    @FXML
    private void handleClose() {
        if (stage != null) {
            stage.close();
        }
    }
} 