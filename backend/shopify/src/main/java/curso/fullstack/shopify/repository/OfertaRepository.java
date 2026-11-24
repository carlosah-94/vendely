package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Oferta;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByIdTiendaOrderByFechaCreacionDesc(Long idTienda);

    Optional<Oferta> findByIdOfertaAndIdTienda(Long idOferta, Long idTienda);

    /** Una oferta vigente que incluye un producto. */
    interface Vigente {
        Long getIdProducto();
        Long getIdOferta();
        String getNombre();
        Short getTipoDescuento();
        BigDecimal getValor();
    }

    /**
     * Ofertas activas y vigentes AHORA de estos productos. now() es el inicio de la transacción:
     * al crear un pedido es el mismo instante que su fecha_creacion, la que revisa el trigger T7.
     */
    @Query(value = """
            SELECT op.id_producto AS "idProducto", o.id_oferta AS "idOferta", o.nombre AS "nombre",
                   o.tipo_descuento AS "tipoDescuento", o.valor AS "valor"
              FROM ofertas o
              JOIN oferta_productos op ON op.id_oferta = o.id_oferta
             WHERE o.id_tienda = :idTienda AND o.estado = 1
               AND o.fecha_inicio <= now() AND (o.fecha_fin IS NULL OR o.fecha_fin > now())
               AND op.id_producto IN (:ids)
            """, nativeQuery = true)
    List<Vigente> vigentesDeProductos(@Param("idTienda") Long idTienda, @Param("ids") Collection<Long> ids);

    /** Decimales de la moneda de la tienda (2 en soles, 0 en pesos chilenos), la misma función que usa T7. */
    @Query(value = "SELECT fn_decimales_tienda(:idTienda)", nativeQuery = true)
    Integer decimalesDeTienda(@Param("idTienda") Long idTienda);

    @Query(value = "SELECT count(*) FROM productos WHERE id_tienda = :idTienda AND id_producto IN (:ids)",
           nativeQuery = true)
    long contarProductosDeTienda(@Param("idTienda") Long idTienda, @Param("ids") Collection<Long> ids);

    /** Cuántas unidades se vendieron con cada oferta (las que no se usaron no aparecen). */
    interface Uso {
        Long getIdOferta();
        Long getUnidades();
    }

    @Query(value = """
            SELECT d.id_oferta AS "idOferta", sum(d.cantidad) AS "unidades"
              FROM detalle_pedido d
             WHERE d.id_oferta IN (:ids)
             GROUP BY d.id_oferta
            """, nativeQuery = true)
    List<Uso> usos(@Param("ids") Collection<Long> ids);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM detalle_pedido WHERE id_oferta = :idOferta)", nativeQuery = true)
    boolean usada(@Param("idOferta") Long idOferta);

    /** Productos que ya se vendieron con la oferta: no se pueden quitar de ella (trigger T11). */
    @Query(value = """
            SELECT DISTINCT v.id_producto
              FROM detalle_pedido d JOIN variantes v ON v.id_variante = d.id_variante
             WHERE d.id_oferta = :idOferta
            """, nativeQuery = true)
    List<Long> productosVendidos(@Param("idOferta") Long idOferta);

    /** ¿Hay pedidos con la oferta desde esa fecha? Entonces su fin no puede quedar ahí (trigger T11). */
    @Query(value = """
            SELECT EXISTS (SELECT 1
                             FROM detalle_pedido d JOIN pedidos p ON p.id_pedido = d.id_pedido
                            WHERE d.id_oferta = :idOferta AND p.fecha_creacion >= :fecha)
            """, nativeQuery = true)
    boolean vendidaDesde(@Param("idOferta") Long idOferta, @Param("fecha") OffsetDateTime fecha);
}
