package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    Optional<Tienda> findBySubdominio(String subdominio);
}
