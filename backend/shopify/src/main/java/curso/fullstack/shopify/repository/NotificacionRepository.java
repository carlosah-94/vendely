package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Notificacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /** Los correos de un pedido, del más reciente al más antiguo (detalle de la venta en el panel). */
    List<Notificacion> findByIdPedidoOrderByFechaCreacionDesc(Long idPedido);

    /**
     * Si ya se registró un correo de ese tipo para ese pedido. Evita mandarle al cliente dos
     * veces la misma confirmación cuando el aviso de la pasarela y el regreso del navegador
     * coinciden, o cuando la pasarela reintenta su aviso.
     */
    boolean existsByIdPedidoAndTipoAndEstadoNot(Long idPedido, Short tipo, Short estado);
}
