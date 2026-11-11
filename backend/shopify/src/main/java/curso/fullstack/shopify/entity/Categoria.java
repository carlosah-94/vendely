package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "categorias": grupos de productos de una tienda ("Polos", "Gorras"), como las colecciones
 * de Shopify. Un producto puede estar en varias categorías (tabla intermedia producto_categorias,
 * con llave compuesta id_producto + id_categoria).
 */
@Entity
@Table(name = "categorias")
@Getter
public class Categoria {

    public static final short ACTIVA = 1;
    public static final short OCULTA = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Long idCategoria;

    /** No cambia después de crearla (trigger tg_categorias_dueno). */
    @Setter
    @Column(name = "id_tienda", nullable = false, updatable = false)
    private Long idTienda;

    @Setter
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Texto para la URL ("polos-de-algodon"). Único en la tienda. */
    @Setter
    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Setter
    @Column(name = "descripcion")
    private String descripcion;

    /** 1 activa (se ve en la tienda) · 0 oculta */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = ACTIVA;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;
}
