package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Lista de ventas del panel (RF3.2): solo las de UNA tienda, las más nuevas primero.
    List<Pedido> findByIdTiendaOrderByFechaCreacionDesc(Long idTienda);

    // "Mis pedidos" del cliente: filtrar por tienda Y por cliente evita que vea pedidos ajenos.
    List<Pedido> findByIdTiendaAndIdLoginOrderByFechaCreacionDesc(Long idTienda, Long idLogin);

    /**
     * El pedido solo si es de esa tienda. Buscar por id + tienda (y no solo por id) es lo que
     * impide ver o borrar una venta de otra tienda: si es ajena, vuelve vacío → 404.
     */
    Optional<Pedido> findByIdPedidoAndIdTienda(Long idPedido, Long idTienda);

    /**
     * Regla del issue #8: una venta con pago aprobado (estado 1) no se elimina, porque el cobro
     * ya se hizo. Se consulta con SQL directo para no crear todavía una entidad Pago.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM pagos WHERE id_pedido = :idPedido AND estado = 1)",
           nativeQuery = true)
    boolean tienePagoAprobado(@Param("idPedido") Long idPedido);

    /**
     * La FK pagos → pedidos NO tiene ON DELETE CASCADE: si quedara cualquier pago (aunque sea
     * pendiente o rechazado), la base impediría borrar el pedido. Por eso se borran antes
     * los pagos no aprobados. Los aprobados nunca llegan aquí (los frena tienePagoAprobado).
     */
    @Modifying
    @Query(value = "DELETE FROM pagos WHERE id_pedido = :idPedido AND estado <> 1", nativeQuery = true)
    int borrarPagosNoAprobados(@Param("idPedido") Long idPedido);
}
