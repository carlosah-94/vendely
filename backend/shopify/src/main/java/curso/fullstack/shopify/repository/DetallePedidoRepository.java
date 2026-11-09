package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.DetallePedido;
import curso.fullstack.shopify.entity.DetallePedidoId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, DetallePedidoId> {

    // Líneas de un pedido, para mostrar el detalle de la venta (productos, cantidades y precios).
    List<DetallePedido> findByIdPedido(Long idPedido);
}
