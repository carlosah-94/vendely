package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    /** Descuenta solo si alcanza el stock. Devuelve 1 si lo hizo, 0 si no había suficiente. */
    @Modifying
    @Query(value = """
            UPDATE inventario SET cantidad_disponible = cantidad_disponible - :cantidad
             WHERE id_variante = :idVariante AND cantidad_disponible >= :cantidad
            """, nativeQuery = true)
    int descontar(@Param("idVariante") Long idVariante, @Param("cantidad") int cantidad);

    /**
     * Al eliminar una venta (issue #8) el stock vuelve a su valor anterior: suma a cada variante
     * la cantidad de su línea en detalle_pedido. Es un solo UPDATE ... FROM (no un bucle en Java):
     * más rápido y atómico. Debe ejecutarse ANTES de borrar el pedido, porque al borrarlo sus
     * líneas desaparecen en cascada y ya no se sabría cuánto devolver.
     */
    @Modifying
    @Query(value = """
            UPDATE inventario i SET cantidad_disponible = i.cantidad_disponible + d.cantidad
              FROM detalle_pedido d
             WHERE d.id_pedido = :idPedido AND i.id_variante = d.id_variante
            """, nativeQuery = true)
    int devolverStock(@Param("idPedido") Long idPedido);

    // =====================================================================
    // Página de Inventario del panel
    // =====================================================================

    /** Una fila del inventario: una variante con su producto, su stock y su mínimo. */
    interface Fila {
        Long getIdVariante();
        Long getIdProducto();
        String getProducto();
        String getVariante();            // "M / Negro", o "Única" si el producto no tiene opciones
        String getSku();
        String getImagenUrl();
        Short getEstadoProducto();
        Integer getCantidad();
        Integer getStockMinimo();
        Long getUltimaReposicion();      // segundos desde 1970, o null si nunca se repuso
    }

    @Query(value = """
            SELECT v.id_variante AS "idVariante", p.id_producto AS "idProducto", p.nombre AS "producto",
                   COALESCE((SELECT string_agg(vv.valor, ' / ' ORDER BY vv.posicion)
                               FROM variante_valores vv WHERE vv.id_variante = v.id_variante), 'Única') AS "variante",
                   v.sku AS "sku",
                   (SELECT im.url FROM imagenes_producto im
                     WHERE im.id_producto = p.id_producto ORDER BY im.posicion LIMIT 1) AS "imagenUrl",
                   p.estado AS "estadoProducto",
                   COALESCE(i.cantidad_disponible, 0) AS "cantidad", COALESCE(i.stock_minimo, 0) AS "stockMinimo",
                   CAST(extract(epoch FROM i.fecha_ultima_reposicion) AS bigint) AS "ultimaReposicion"
              FROM variantes v
              JOIN productos p ON p.id_producto = v.id_producto
              LEFT JOIN inventario i ON i.id_variante = v.id_variante
             WHERE p.id_tienda = :idTienda
             ORDER BY p.nombre, v.posicion, v.id_variante
            """, nativeQuery = true)
    List<Fila> filasDeTienda(@Param("idTienda") Long idTienda);

    /**
     * Recibir mercadería: SUMA lo que llegó al stock actual y anota la fecha de reposición.
     * Es un UPDATE que suma en la base (no "leer, sumar en Java y guardar"): si en ese momento
     * entra una venta, ninguna de las dos operaciones pisa a la otra.
     */
    @Modifying
    @Query(value = """
            UPDATE inventario SET cantidad_disponible = cantidad_disponible + :cantidad,
                                  fecha_ultima_reposicion = now()
             WHERE id_variante = :idVariante
            """, nativeQuery = true)
    int recibir(@Param("idVariante") Long idVariante, @Param("cantidad") int cantidad);
}