package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Producto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Fila de producto con su precio vigente y su stock. El precio y el stock están en otras
     * tablas (precios, inventario), así que se arman con una sola consulta en vez de una por producto.
     * Si el producto tuviera varias variantes: precio = el menor ("desde") y stock = la suma.
     */
    interface Fila {
        Long getIdProducto();
        String getNombre();
        String getSlug();
        String getDescripcion();
        String getMarca();
        Short getEstado();
        Long getIdVariante();
        Long getVariantes();
        BigDecimal getPrecio();
        BigDecimal getPrecioComparacion();
        String getSku();
        Long getStock();
        String getImagenUrl();
        /** Todas las fotos en orden, separadas por espacios (los enlaces no llevan espacios). */
        String getImagenes();
        /** Ids de sus categorías separados por espacios ("3 7"). */
        String getCategorias();
    }

    // Los alias van entre comillas para que PostgreSQL respete las mayúsculas (si no, la proyección da null).
    String SELECT_FILA = """
            SELECT p.id_producto AS "idProducto", p.nombre AS "nombre", p.slug AS "slug",
                   p.descripcion AS "descripcion", p.marca AS "marca", p.estado AS "estado",
                   min(v.id_variante) AS "idVariante", count(v.id_variante) AS "variantes",
                   min(pr.monto) AS "precio", min(pr.monto_comparacion) AS "precioComparacion",
                   min(v.sku) AS "sku", coalesce(sum(i.cantidad_disponible), 0) AS "stock",
                   (SELECT im.url FROM imagenes_producto im
                     WHERE im.id_producto = p.id_producto ORDER BY im.posicion LIMIT 1) AS "imagenUrl",
                   (SELECT string_agg(im.url, ' ' ORDER BY im.posicion) FROM imagenes_producto im
                     WHERE im.id_producto = p.id_producto) AS "imagenes",
                   (SELECT string_agg(CAST(pc.id_categoria AS text), ' ' ORDER BY pc.id_categoria)
                      FROM producto_categorias pc WHERE pc.id_producto = p.id_producto) AS "categorias"
              FROM productos p
              LEFT JOIN variantes v   ON v.id_producto = p.id_producto
              LEFT JOIN precios pr    ON pr.id_variante = v.id_variante
                                     AND pr.fecha_inicio <= now() AND (pr.fecha_fin IS NULL OR pr.fecha_fin > now())
              LEFT JOIN inventario i  ON i.id_variante = v.id_variante
            """;

    // Panel: todos los productos de la tienda (activos, borradores y archivados).
    @Query(value = SELECT_FILA + """
             WHERE p.id_tienda = :idTienda
             GROUP BY p.id_producto
             ORDER BY p.fecha_creacion DESC
            """, nativeQuery = true)
    List<Fila> filasDeTienda(@Param("idTienda") Long idTienda);

    // Panel: un producto, solo si es de esa tienda (si es de otra → vacío → 404).
    @Query(value = SELECT_FILA + """
             WHERE p.id_tienda = :idTienda AND p.id_producto = :idProducto
             GROUP BY p.id_producto
            """, nativeQuery = true)
    Optional<Fila> filaDeTienda(@Param("idTienda") Long idTienda, @Param("idProducto") Long idProducto);

    // Catálogo público: solo productos ACTIVOS (los borradores y archivados no se muestran).
    @Query(value = SELECT_FILA + """
             WHERE p.id_tienda = :idTienda AND p.estado = 1
             GROUP BY p.id_producto
             ORDER BY p.nombre
            """, nativeQuery = true)
    List<Fila> filasPublicas(@Param("idTienda") Long idTienda);

    // Detalle público: un producto ACTIVO de la tienda (borrador, archivado o de otra tienda → vacío → 404).
    @Query(value = SELECT_FILA + """
             WHERE p.id_tienda = :idTienda AND p.id_producto = :idProducto AND p.estado = 1
             GROUP BY p.id_producto
            """, nativeQuery = true)
    Optional<Fila> filaPublica(@Param("idTienda") Long idTienda, @Param("idProducto") Long idProducto);

    // El producto solo si es de esa tienda: candado multi-tienda para editar y eliminar.
    Optional<Producto> findByIdProductoAndIdTienda(Long idProducto, Long idTienda);

    // RF2.1.1: nombre único en la tienda (sin distinguir mayúsculas). Al editar se excluye el propio.
    boolean existsByIdTiendaAndNombreIgnoreCase(Long idTienda, String nombre);

    boolean existsByIdTiendaAndNombreIgnoreCaseAndIdProductoNot(Long idTienda, String nombre, Long idProducto);

    // El slug es UNIQUE por tienda en la base: se revisa antes para responder 409 claro.
    boolean existsByIdTiendaAndSlug(Long idTienda, String slug);

    boolean existsByIdTiendaAndSlugAndIdProductoNot(Long idTienda, String slug, Long idProducto);

    /**
     * Issue #10: un producto que ya se vendió no se elimina. Sus variantes están en detalle_pedido
     * (FK sin cascade), así que la base lo impediría con un error genérico: se revisa antes → 409.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM detalle_pedido d
                             JOIN variantes v ON v.id_variante = d.id_variante
                            WHERE v.id_producto = :idProducto)
            """, nativeQuery = true)
    boolean tieneVentas(@Param("idProducto") Long idProducto);
}
