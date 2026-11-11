package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Variante;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    interface Info {
        Long getIdTienda();
        String getNombreProducto();
        String getNombreVariante();
        Short getEstadoProducto();
    }

    /** Tienda, nombre del producto y nombre de la variante ("42 / Negro"; "Única" si no tiene opciones). */
    @Query(value = """
            SELECT p.id_tienda AS "idTienda", p.nombre AS "nombreProducto", p.estado AS "estadoProducto",
                   COALESCE(string_agg(vv.valor, ' / ' ORDER BY vv.posicion), 'Única') AS "nombreVariante"
              FROM variantes v
              JOIN productos p ON p.id_producto = v.id_producto
              LEFT JOIN variante_valores vv ON vv.id_variante = v.id_variante
             WHERE v.id_variante = :idVariante
             GROUP BY p.id_tienda, p.nombre, p.estado
            """, nativeQuery = true)
    Optional<Info> buscarInfo(@Param("idVariante") Long idVariante);

    // Variantes de un producto, en orden de creación (la primera es la de un producto simple).
    List<Variante> findByIdProductoOrderByIdVarianteAsc(Long idProducto);

    // =====================================================================
    // Opciones y variantes (talla, color...)
    // =====================================================================

    /** Una variante con sus valores, su precio vigente y su stock. */
    interface Fila {
        Long getIdProducto();
        Long getIdVariante();
        /** Valores en el orden de las opciones, separados por el carácter 31 (nunca se escribe en un texto). */
        String getValores();
        String getSku();
        BigDecimal getPrecio();
        BigDecimal getPrecioComparacion();
        Long getStock();
    }

    @Query(value = """
            SELECT v.id_producto AS "idProducto", v.id_variante AS "idVariante",
                   (SELECT string_agg(vv.valor, chr(31) ORDER BY vv.posicion)
                      FROM variante_valores vv WHERE vv.id_variante = v.id_variante) AS "valores",
                   v.sku AS "sku", pr.monto AS "precio", pr.monto_comparacion AS "precioComparacion",
                   CAST(COALESCE(i.cantidad_disponible, 0) AS bigint) AS "stock"
              FROM variantes v
              LEFT JOIN precios pr   ON pr.id_variante = v.id_variante
                                    AND pr.fecha_inicio <= now() AND (pr.fecha_fin IS NULL OR pr.fecha_fin > now())
              LEFT JOIN inventario i ON i.id_variante = v.id_variante
             WHERE v.id_producto IN (:ids) AND v.estado = 1
             ORDER BY v.id_producto, v.posicion, v.id_variante
            """, nativeQuery = true)
    List<Fila> filasDeProductos(@Param("ids") Collection<Long> idsProductos);

    /** Una opción de un producto (Talla, Color...). */
    interface FilaOpcion {
        Long getIdProducto();
        Short getPosicion();
        String getNombre();
    }

    @Query(value = """
            SELECT id_producto AS "idProducto", posicion AS "posicion", nombre AS "nombre"
              FROM producto_opciones WHERE id_producto IN (:ids)
             ORDER BY id_producto, posicion
            """, nativeQuery = true)
    List<FilaOpcion> opcionesDeProductos(@Param("ids") Collection<Long> idsProductos);

    /** Quita las opciones del producto; la base borra en cascada los valores de sus variantes. */
    @Modifying
    @Query(value = "DELETE FROM producto_opciones WHERE id_producto = :idProducto", nativeQuery = true)
    int borrarOpciones(@Param("idProducto") Long idProducto);

    @Modifying
    @Query(value = "INSERT INTO producto_opciones (id_producto, posicion, nombre) VALUES (:idProducto, :posicion, :nombre)",
           nativeQuery = true)
    int crearOpcion(@Param("idProducto") Long idProducto, @Param("posicion") short posicion, @Param("nombre") String nombre);

    @Modifying
    @Query(value = """
            INSERT INTO variante_valores (id_variante, id_producto, posicion, valor)
            VALUES (:idVariante, :idProducto, :posicion, :valor)
            """, nativeQuery = true)
    int crearValor(@Param("idVariante") Long idVariante, @Param("idProducto") Long idProducto,
                   @Param("posicion") short posicion, @Param("valor") String valor);

    /** ¿La variante ya se vendió? (detalle_pedido no tiene cascade: no se podría borrar). */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM detalle_pedido WHERE id_variante = :idVariante)", nativeQuery = true)
    boolean tieneVentas(@Param("idVariante") Long idVariante);
}