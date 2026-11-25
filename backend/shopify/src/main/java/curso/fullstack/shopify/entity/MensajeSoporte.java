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

    public static MensajeSoporte de(Long idTienda, Long idLogin, boolean deSoporte, String texto) {
        MensajeSoporte m = new MensajeSoporte();
        m.setIdTienda(idTienda);
        m.setIdLogin(idLogin);
        m.setDeSoporte(deSoporte);
        m.setTexto(texto);
        return m;
    }
}
