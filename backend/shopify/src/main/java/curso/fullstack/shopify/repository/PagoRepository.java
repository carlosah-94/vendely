package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Pago;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByIdPedidoAndEstado(Long idPedido, Short estado);

    Optional<Pago> findByReferenciaTransaccion(String referenciaTransaccion);

    /** El último intento con ese estado (por ejemplo, el pendiente) de un pedido. */
    Optional<Pago> findFirstByIdPedidoAndEstadoOrderByIdPagoDesc(Long idPedido, Short estado);

    /** Estado de pago de un pedido para mostrarlo en las listas. */
    interface EstadoPedido {
        Long getIdPedido();
        Short getEstado();
    }

    /**
     * Por cada pedido, el estado que cuenta: el aprobado si existe; si no, el último intento.
     * Una sola consulta para toda la lista (evita una por pedido).
     */
    @Query(value = """
            SELECT DISTINCT ON (id_pedido) id_pedido AS "idPedido", estado AS "estado"
              FROM pagos
             WHERE id_pedido IN (:ids)
             ORDER BY id_pedido, (estado = 1) DESC, id_pago DESC
            """, nativeQuery = true)
    List<EstadoPedido> estadosDe(@Param("ids") Collection<Long> ids);
}
