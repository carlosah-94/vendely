package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "cupones" (V14): códigos de descuento que el cliente escribe en el checkout ("VERANO10").
 * Rebajan el pedido completo (el subtotal, ya con las ofertas automáticas): un porcentaje o un
 * monto fijo. Un "uso" es un pedido PAGADO con el código; se cuenta, no se guarda.
 */
@Entity
@Table(name = "cupones")
@Getter
@Setter
public class Cupon {

    public static final short ACTIVO = 1;
    public static final short PAUSADO = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cupon")
    @Setter(lombok.AccessLevel.NONE)
    private Long idCupon;

    /** No cambia después de crearlo (trigger tg_cupones_dueno). */
    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    /** En mayúsculas, único en la tienda. */
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    /** 1 porcentaje · 2 monto fijo (los mismos valores que Oferta). */
    @Column(name = "tipo_descuento", nullable = false)
    private Short tipoDescuento;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    /** null = sin compra mínima. Se compara con el subtotal (productos, ya con ofertas). */
    @Column(name = "compra_minima", precision = 12, scale = 2)
    private BigDecimal compraMinima;

    /** null = sin límite de usos. */
    @Column(name = "usos_maximos")
    private Integer usosMaximos;

    @Column(name = "una_vez_por_cliente", nullable = false)
    private boolean unaVezPorCliente;

    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private OffsetDateTime fechaFin;

    @Column(name = "estado", nullable = false)
    private Short estado = ACTIVO;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    @Setter(lombok.AccessLevel.NONE)
    private OffsetDateTime fechaCreacion;
}
