package client;

import java.time.LocalDate;
import java.time.LocalTime;

public class MensajeChat {
    private String texto;
    private String emisor;
    private boolean enviado; // true si lo envía el usuario actual
    private LocalDate fecha;
    private LocalTime hora;
    private boolean imagen; // true si el mensaje contiene una imagen

    public MensajeChat(String texto, String emisor, boolean enviado, LocalDate fecha, LocalTime hora) {
        this.texto = texto;
        this.emisor = emisor;
        this.enviado = enviado;
        this.fecha = fecha;
        this.hora = hora;
        this.imagen = false;
    }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getEmisor() { return emisor; }
    public boolean isEnviado() { return enviado; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHora() { return hora; }
    public boolean isImagen() { return imagen; }
    public void setImagen(boolean imagen) { this.imagen = imagen; }
} 