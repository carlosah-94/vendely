package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Precio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Issue #10: cambiar el precio = CERRAR el vigente y crear otro (la base no deja editar el monto).
     * Se cierra con now() de la base, el mismo instante en que empieza el precio nuevo
     * (fecha_inicio DEFAULT now()), así los periodos quedan pegados sin cruzarse ni dejar huecos.
     * Si se usara la hora de Java, sería un poco posterior y los periodos se cruzarían (EXCLUDE).
     */
    @Modifying
    @Query(value = """
            UPDATE precios SET fecha_fin = now()
             WHERE id_variante = :idVariante AND fecha_fin IS NULL AND fecha_inicio < now()
            """, nativeQuery = true)
    int cerrarVigente(@Param("idVariante") Long idVariante);
}