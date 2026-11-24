package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "ofertas": descuentos automáticos de una tienda (porcentaje o monto fijo) sobre algunos
 * productos, entre dos fechas. Mientras está vigente, la tienda muestra el precio rebajado y el
 * pedido guarda en cada línea la oferta y el descuento por unidad (detalle_pedido).
 *
 * La base protege las reglas (V1, triggers T7, T8, T11 y T16): la oferta no cambia de tienda; una
 * oferta con la que ya se vendió no cambia su tipo, su valor ni su inicio, ni pierde un producto
 * vendido; y el descuento de cada línea debe ser exactamente el que da la oferta.
 */
@Entity
@Table(name = "ofertas")
@Getter
public class Oferta {

    public static final short ACTIVA = 1;
    public static final short PAUSADA = 0;

    public static final short PORCENTAJE = 1;
    public static final short MONTO_FIJO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oferta")
    private Long idOferta;

    /** No cambia después de crearla (trigger tg_ofertas_dueno). */
    @Setter
    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Setter
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** 1 liquidación · 2 temporada · 3 lanzamiento · 4 otro */
    @Setter
    @Column(name = "motivo", nullable = false)
    private Short motivo;

    /** 1 porcentaje · 2 monto fijo */
    @Setter
    @Column(name = "tipo_descuento", nullable = false)
    private Short tipoDescuento;

    /** Porcentaje (hasta 100) o monto en la moneda de la tienda. */
    @Setter
    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Setter
    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    /** null = sin fecha de fin. */
    @Setter
    @Column(name = "fecha_fin")
    private OffsetDateTime fechaFin;

    /** 1 activa · 0 pausada */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = ACTIVA;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    /** Productos de la oferta (tabla puente oferta_productos). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oferta_productos", joinColumns = @JoinColumn(name = "id_oferta"))
    @Column(name = "id_producto")
    private Set<Long> productos = new LinkedHashSet<>();
}
