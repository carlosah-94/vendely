package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.RegistroSoporte;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroSoporteRepository extends JpaRepository<RegistroSoporte, Long> {

    /** Lo último que pasó con el soporte de la tienda (lo más nuevo primero). */
    List<RegistroSoporte> findTop50ByIdTiendaOrderByFechaDescIdRegistroDesc(Long idTienda);
}
