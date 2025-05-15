package client;

import java.time.LocalDate;
import java.time.LocalTime;

public class MensajeChat {
    private String texto;
    private String emisor;
    private boolean enviado; // true si lo envía el usuario actual
    private LocalDate fecha;
    private LocalTime hora;

    public MensajeChat(String texto, String emisor, boolean enviado, LocalDate fecha, LocalTime hora) {
        this.texto = texto;
        this.emisor = emisor;
        this.enviado = enviado;
        this.fecha = fecha;
        this.hora = hora;
    }

    public String getTexto() { return texto; }
    public String getEmisor() { return emisor; }
    public boolean isEnviado() { return enviado; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHora() { return hora; }
} 