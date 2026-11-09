package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.DetallePedido;
import curso.fullstack.shopify.entity.DetallePedidoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, DetallePedidoId> {
}