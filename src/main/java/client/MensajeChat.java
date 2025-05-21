package client;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Clase que representa un mensaje en el chat.
 * Almacena toda la información relacionada con un mensaje individual.
 */
public class MensajeChat {
    private String texto;        // Contenido del mensaje
    private String emisor;       // Usuario que envía el mensaje
    private boolean enviado;     // Indica si el mensaje fue enviado por el usuario actual
    private LocalDate fecha;     // Fecha del mensaje
    private LocalTime hora;      // Hora del mensaje
    private boolean imagen;      // Indica si el mensaje contiene una imagen

    /**
     * Constructor para crear un nuevo mensaje de chat
     * @param texto Contenido del mensaje
     * @param emisor Usuario que envía el mensaje
     * @param enviado Indica si el mensaje fue enviado por el usuario actual
     * @param fecha Fecha del mensaje
     * @param hora Hora del mensaje
     */
    public MensajeChat(String texto, String emisor, boolean enviado, LocalDate fecha, LocalTime hora) {
        this.texto = texto;
        this.emisor = emisor;
        this.enviado = enviado;
        this.fecha = fecha;
        this.hora = hora;
        this.imagen = false;
    }

    // Getters y setters para acceder y modificar los atributos del mensaje
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getEmisor() { return emisor; }
    public boolean isEnviado() { return enviado; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHora() { return hora; }
    public boolean isImagen() { return imagen; }
    public void setImagen(boolean imagen) { this.imagen = imagen; }
} 