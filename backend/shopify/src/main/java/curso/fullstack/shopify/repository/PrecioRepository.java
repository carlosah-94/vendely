package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Precio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrecioRepository extends JpaRepository<Precio, Long> {

    /** Precio vigente AHORA (now() = inicio de la transacción = fecha del pedido). */
    @Query(value = """
            SELECT * FROM precios
             WHERE id_variante = :idVariante
               AND fecha_inicio <= now() AND (fecha_fin IS NULL OR fecha_fin > now())
            """, nativeQuery = true)
    Optional<Precio> vigente(@Param("idVariante") Long idVariante);
}