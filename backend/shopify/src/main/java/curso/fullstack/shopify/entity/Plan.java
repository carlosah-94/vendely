package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "planes": Gratis, Basico y Pro. Define cuanto cuesta al mes, cuantos productos
 * permite, que comision cobra Vendely por venta y que funciones habilita.
 */
@Entity
@Table(name = "planes")
@Getter
@Setter
public class Plan {

    public static final short GRATIS = 1;
    public static final short BASICO = 2;
    public static final short PRO = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Short idPlan;

    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private String nombre;

    /** Precio al mes, en dolares (se cobra con PayPal en USD). */
    @Column(name = "precio_usd", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioUsd;

    /** null = ilimitados */
    @Column(name = "max_productos")
    private Integer maxProductos;

    /** Porcentaje que cobra Vendely por cada venta (5.00 = 5 %). */
    @Column(name = "comision", nullable = false, precision = 5, scale = 2)
    private BigDecimal comision;

    @Column(name = "permite_personalizar", nullable = false)
    private Boolean permitePersonalizar = false;

    @Column(name = "permite_plantillas", nullable = false)
    private Boolean permitePlantillas = false;

    @Column(name = "permite_envio_internacional", nullable = false)
    private Boolean permiteEnvioInternacional = false;

    @Column(name = "permite_mas_metodos_pago", nullable = false)
    private Boolean permiteMasMetodosPago = false;

    /** 1 se ofrece · 0 ya no se vende */
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public boolean esGratis() {
        return precioUsd == null || precioUsd.signum() == 0;
    }
}
