package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Suscripcion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    /** Historial de pagos de plan de una tienda, el mas reciente primero. */
    List<Suscripcion> findByIdTiendaOrderByFechaCreacionDesc(Long idTienda);

    /** Una suscripcion de ESTA tienda (si es de otra, vacio: 404). */
    Optional<Suscripcion> findByIdSuscripcionAndIdTienda(Long idSuscripcion, Long idTienda);

    Optional<Suscripcion> findByReferenciaPago(String referenciaPago);

    /** Pagos de plan que todavia no terminan (el que corre hoy y los programados), en orden. */
    List<Suscripcion> findByIdTiendaAndEstadoAndFechaFinAfterOrderByFechaInicioAsc(Long idTienda, Short estado,
                                                                                   OffsetDateTime ahora);

    /**
     * La misma suscripcion, pero "con candado" hasta que termine la transaccion: si llegan dos
     * confirmaciones a la vez (doble clic, recarga), la segunda espera y ve que ya esta pagada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Suscripcion s WHERE s.idSuscripcion = :idSuscripcion AND s.idTienda = :idTienda")
    Optional<Suscripcion> bloquear(@Param("idSuscripcion") Long idSuscripcion, @Param("idTienda") Long idTienda);

    /**
     * Al cambiar a OTRO plan, la suscripcion pagada que esta corriendo termina hoy
     * (si no, chocaria con la nueva: ex_suscripciones_periodo).
     */
    @Modifying
    @Query(value = """
            UPDATE suscripciones SET fecha_fin = :ahora
             WHERE id_tienda = :idTienda AND estado = 1 AND fecha_inicio < :ahora AND fecha_fin > :ahora
            """, nativeQuery = true)
    int terminarVigente(@Param("idTienda") Long idTienda, @Param("ahora") OffsetDateTime ahora);

    /** Las pagadas que todavia no empezaban (renovaciones adelantadas) quedan canceladas. */
    @Modifying
    @Query(value = """
            UPDATE suscripciones SET estado = 2
             WHERE id_tienda = :idTienda AND estado = 1 AND fecha_inicio >= :ahora
            """, nativeQuery = true)
    int cancelarFuturas(@Param("idTienda") Long idTienda, @Param("ahora") OffsetDateTime ahora);
}
