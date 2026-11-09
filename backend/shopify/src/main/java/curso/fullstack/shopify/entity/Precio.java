package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "precios": historial de precios de cada variante (incluyen el impuesto).
 *
 * Un precio NO se edita (la base lo impide): para cambiarlo se CIERRA el vigente (fecha_fin)
 * y se crea uno nuevo. Así cada venta conserva el precio con el que se hizo (id_precio).
 * Por eso solo hay setters para crear; el cierre se hace con una consulta en PrecioRepository.
 */
@Entity
@Table(name = "precios")
@Getter
public class Precio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio")
    private Long idPrecio;

    @Setter
    @Column(name = "id_variante", nullable = false)
    private Long idVariante;

    @Setter
    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /**
     * La pone la base (DEFAULT now() = hora de la transacción). No se envía desde Java para que
     * coincida exactamente con el cierre del precio anterior (fecha_fin = now()) y los periodos
     * no se crucen (EXCLUDE ex_precios_periodo).
     */
    @Column(name = "fecha_inicio", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime fechaInicio;

    /** null = vigente */
    @Column(name = "fecha_fin", insertable = false, updatable = false)
    private OffsetDateTime fechaFin;
}
