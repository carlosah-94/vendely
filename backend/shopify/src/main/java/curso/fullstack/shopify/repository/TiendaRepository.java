package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    Optional<Tienda> findBySubdominio(String subdominio);

    boolean existsBySubdominio(String subdominio);

    // Las tiendas de un dueño, de la más antigua a la más nueva
    List<Tienda> findByIdPropietarioOrderByIdTiendaAsc(Long idPropietario);
}
