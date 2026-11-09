package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;

/** Tabla "precios": historial de precios de cada variante (incluyen el impuesto). */
@Entity
@Table(name = "precios")
@Getter
public class Precio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio")
    private Long idPrecio;

    @Column(name = "id_variante", nullable = false)
    private Long idVariante;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    /** null = vigente */
    @Column(name = "fecha_fin")
    private OffsetDateTime fechaFin;
}
