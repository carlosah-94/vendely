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
        /** Costo por unidad (V21): el menor de sus variantes, o null. Solo el panel del dueño lo muestra. */
        BigDecimal getCosto();
        String getSku();
        Long getStock();
        /** Suma del stock minimo de sus variantes: con el se sabe si al producto le queda poco. */
        Long getStockMinimo();
        String getImagenUrl();
        /** Todas las fotos en orden, separadas por espacios (los enlaces no llevan espacios). */
        String getImagenes();
        /** Ids de sus categorías separados por espacios ("3 7"). */
        String getCategorias();
        /** Pregunta al cliente (V19), o null. */
        String getPreguntaCliente();
    }

    /** El FROM del producto con su precio vigente y su stock. Se reutiliza en el conteo del catálogo. */
    String DESDE_PRODUCTOS = """
              FROM productos p
              LEFT JOIN variantes v   ON v.id_producto = p.id_producto
              LEFT JOIN precios pr    ON pr.id_variante = v.id_variante
                                     AND pr.fecha_inicio <= now() AND (pr.fecha_fin IS NULL OR pr.fecha_fin > now())
              LEFT JOIN inventario i  ON i.id_variante = v.id_variante
            """;

    // Los alias van entre comillas para que PostgreSQL respete las mayúsculas (si no, la proyección da null).
    String SELECT_FILA = """
            SELECT p.id_producto AS "idProducto", p.nombre AS "nombre", p.slug AS "slug",
                   p.descripcion AS "descripcion", p.marca AS "marca", p.estado AS "estado",
                   p.pregunta_cliente AS "preguntaCliente",
                   min(v.id_variante) AS "idVariante", count(v.id_variante) AS "variantes",
                   min(pr.monto) AS "precio", min(pr.monto_comparacion) AS "precioComparacion",
                   min(pr.costo) AS "costo",
                   min(v.sku) AS "sku", coalesce(sum(i.cantidad_disponible), 0) AS "stock",
                   coalesce(sum(i.stock_minimo), 0) AS "stockMinimo",
                   (SELECT im.url FROM imagenes_producto im
                     WHERE im.id_producto = p.id_producto ORDER BY im.posicion LIMIT 1) AS "imagenUrl",
                   (SELECT string_agg(im.url, ' ' ORDER BY im.posicion) FROM imagenes_producto im
                     WHERE im.id_producto = p.id_producto) AS "imagenes",
                   (SELECT string_agg(CAST(pc.id_categoria AS text), ' ' ORDER BY pc.id_categoria)
                      FROM producto_categorias pc WHERE pc.id_producto = p.id_producto) AS "categorias"
            """ + DESDE_PRODUCTOS;

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

    /**
     * Los filtros del catálogo (issue #4), compartidos por la búsqueda y por el conteo.
     *
     * El precio y el stock se filtran en HAVING y no en WHERE: son agregados de las variantes
     * (el precio es el menor, el stock la suma) y WHERE se evalúa antes de agrupar.
     *
     * Cada parámetro opcional va con CAST(... AS tipo): en una consulta nativa, PostgreSQL no
     * puede deducir el tipo de un parámetro que llega nulo y la consulta fallaría.
     */
    String FILTROS_PUBLICOS = """
             WHERE p.id_tienda = :idTienda AND p.estado = 1
               AND (CAST(:texto AS text) IS NULL
                    OR p.nombre ILIKE '%' || CAST(:texto AS text) || '%'
                    OR p.marca ILIKE '%' || CAST(:texto AS text) || '%'
                    OR p.descripcion ILIKE '%' || CAST(:texto AS text) || '%')
               AND (CAST(:idCategoria AS bigint) IS NULL
                    OR EXISTS (SELECT 1 FROM producto_categorias pc
                                WHERE pc.id_producto = p.id_producto
                                  AND pc.id_categoria = CAST(:idCategoria AS bigint)))
             GROUP BY p.id_producto
            HAVING (CAST(:precioMinimo AS numeric) IS NULL OR min(pr.monto) >= CAST(:precioMinimo AS numeric))
               AND (CAST(:precioMaximo AS numeric) IS NULL OR min(pr.monto) <= CAST(:precioMaximo AS numeric))
               AND (CAST(:soloConStock AS boolean) = false OR coalesce(sum(i.cantidad_disponible), 0) > 0)
            """;

    /**
     * Una página del catálogo, ya filtrada y ordenada.
     *
     * El orden se elige con un CASE por parámetro y no pegando texto a la consulta: así el valor
     * viaja como parámetro (nada que llegue del navegador se concatena al SQL) y la consulta
     * sigue siendo una sola. NULLS LAST deja al final los productos sin precio vigente.
     */
    @Query(value = SELECT_FILA + FILTROS_PUBLICOS + """
             ORDER BY
               CASE WHEN CAST(:orden AS text) = 'precio_asc'  THEN min(pr.monto) END ASC  NULLS LAST,
               CASE WHEN CAST(:orden AS text) = 'precio_desc' THEN min(pr.monto) END DESC NULLS LAST,
               CASE WHEN CAST(:orden AS text) = 'novedades'   THEN p.fecha_creacion END DESC NULLS LAST,
               p.nombre
             LIMIT :tamano OFFSET :desplazamiento
            """, nativeQuery = true)
    List<Fila> buscarPublicos(@Param("idTienda") Long idTienda,
                              @Param("texto") String texto,
                              @Param("idCategoria") Long idCategoria,
                              @Param("precioMinimo") BigDecimal precioMinimo,
                              @Param("precioMaximo") BigDecimal precioMaximo,
                              @Param("soloConStock") boolean soloConStock,
                              @Param("orden") String orden,
                              @Param("tamano") int tamano,
                              @Param("desplazamiento") int desplazamiento);

    /**
     * Cuántos productos pasan los filtros, para el paginador. Va envuelto en una subconsulta
     * porque con GROUP BY + HAVING cada fila es un producto: hay que contar las filas del
     * resultado, no las de la tabla.
     */
    @Query(value = "SELECT count(*) FROM (SELECT p.id_producto " + DESDE_PRODUCTOS + FILTROS_PUBLICOS
            + ") AS filtrados", nativeQuery = true)
    long contarPublicos(@Param("idTienda") Long idTienda,
                        @Param("texto") String texto,
                        @Param("idCategoria") Long idCategoria,
                        @Param("precioMinimo") BigDecimal precioMinimo,
                        @Param("precioMaximo") BigDecimal precioMaximo,
                        @Param("soloConStock") boolean soloConStock);

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

    /** Cuantos productos tiene la tienda: para el limite del plan. */
    long countByIdTienda(Long idTienda);

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
