package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "soporte_mensajes" (V17): la conversación entre el dueño y soporte de Vendely sobre lo
 * que hay que armar en la tienda. deSoporte dice quién escribió: true soporte, false el dueño.
 *
 * Desde V26 un mensaje puede llevar un archivo (adjunto*): el Excel de productos, las fotos...
 * Con archivo, el texto puede ir vacío.
 */
@Entity
@Table(name = "soporte_mensajes")
@Getter
@Setter
public class MensajeSoporte {

    public static final int MAXIMO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensaje")
    private Long idMensaje;

    @Column(name = "id_tienda", nullable = false)
    private Long idTienda;

    @Column(name = "id_login")
    private Long idLogin;

    @Column(name = "de_soporte", nullable = false)
    private Boolean deSoporte;

    @Column(name = "texto", nullable = false, length = MAXIMO)
    private String texto;

    @Column(name = "fecha", nullable = false, updatable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Column(name = "adjunto_url", length = 500)
    private String adjuntoUrl;

    @Column(name = "adjunto_public_id", length = 300)
    private String adjuntoPublicId;

    @Column(name = "adjunto_nombre", length = 200)
    private String adjuntoNombre;

    @Column(name = "adjunto_tipo", length = 100)
    private String adjuntoTipo;

    @Column(name = "adjunto_bytes")
    private Long adjuntoBytes;

    public static MensajeSoporte de(Long idTienda, Long idLogin, boolean deSoporte, String texto) {
        MensajeSoporte m = new MensajeSoporte();
        m.setIdTienda(idTienda);
        m.setIdLogin(idLogin);
        m.setDeSoporte(deSoporte);
        m.setTexto(texto == null ? "" : texto);
        return m;
    }

    public boolean tieneAdjunto() {
        return adjuntoUrl != null;
    }

    /** Lo que se muestra como adelanto: el texto, o el nombre del archivo si no hay texto. */
    public String resumen() {
        if (texto != null && !texto.isBlank()) {
            return texto;
        }
        return tieneAdjunto() ? "📎 " + adjuntoNombre : "";
    }
}
