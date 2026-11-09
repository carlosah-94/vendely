package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;
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
}