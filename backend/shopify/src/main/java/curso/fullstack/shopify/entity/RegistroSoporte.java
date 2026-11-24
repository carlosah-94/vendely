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
 * Tabla "soporte_registro" (V16): lo que pasó con el acceso de soporte de una tienda.
 * PERMITIDO y QUITADO los hace el dueño; CAMBIO es cada petición que modificó algo
 * mientras soporte de Vendely trabajaba en la tienda.
 */
@Entity
@Table(name = "soporte_registro")
@Getter
@Setter
public class RegistroSoporte {

    public static final String PERMITIDO = "PERMITIDO";
    public static final String QUITADO = "QUITADO";
    public static final String CAMBIO = "CAMBIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long idRegistro;

    @Column(name = "id_tienda", nullable = false)
    private Long idTienda;

    @Column(name = "id_login")
    private Long idLogin;

    @Column(name = "accion", nullable = false, length = 20)
    private String accion;

    @Column(name = "metodo", length = 10)
    private String metodo;

    @Column(name = "ruta", length = 300)
    private String ruta;

    /** Se pone aquí (no solo en la base) para que la respuesta la muestre en la misma transacción. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    public static RegistroSoporte de(Long idTienda, Long idLogin, String accion) {
        RegistroSoporte r = new RegistroSoporte();
        r.setIdTienda(idTienda);
        r.setIdLogin(idLogin);
        r.setAccion(accion);
        return r;
    }
}
