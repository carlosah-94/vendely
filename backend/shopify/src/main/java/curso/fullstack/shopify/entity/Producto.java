package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "productos" (issue #10).
 * En Vendely un producto SIMPLE = producto + una variante + su precio + su stock.
 * El precio y el stock no están aquí: viven en "precios" e "inventario", ligados a la variante,
 * porque un producto con tallas o colores tiene un precio y un stock por cada variante.
 */
@Entity
@Table(name = "productos")
@Getter
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;

    /** Un producto no cambia de tienda (trigger T8): por eso updatable = false. */
    @Setter
    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Setter
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    /** Texto para la URL (zapatilla-runner). Único por tienda (UNIQUE id_tienda + slug). */
    @Setter
    @Column(name = "slug", nullable = false, length = 220)
    private String slug;

    @Setter
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Setter
    @Column(name = "marca", length = 100)
    private String marca;

    /** 0 borrador · 1 activo · 2 archivado. Solo los activos se ven en el catálogo y se venden. */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;
}
