package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Suscripcion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    /** Historial de pagos de plan de una tienda, el mas reciente primero. */
    List<Suscripcion> findByIdTiendaOrderByFechaCreacionDesc(Long idTienda);

    /** Una suscripcion de ESTA tienda (si es de otra, vacio: 404). */
    Optional<Suscripcion> findByIdSuscripcionAndIdTienda(Long idSuscripcion, Long idTienda);

    Optional<Suscripcion> findByReferenciaPago(String referenciaPago);
}
