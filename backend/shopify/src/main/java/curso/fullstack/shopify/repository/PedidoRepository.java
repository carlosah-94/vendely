package curso.fullstack.shopify.repository;
import curso.fullstack.shopify.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long>  {
    
}
