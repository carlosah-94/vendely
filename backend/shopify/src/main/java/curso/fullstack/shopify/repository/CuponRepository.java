package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Cupon;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuponRepository extends JpaRepository<Cupon, Long> {

    List<Cupon> findByIdTiendaOrderByFechaCreacionDesc(Long idTienda);

    Optional<Cupon> findByIdCuponAndIdTienda(Long idCupon, Long idTienda);

    Optional<Cupon> findByIdTiendaAndCodigo(Long idTienda, String codigo);

    boolean existsByIdTiendaAndCodigo(Long idTienda, String codigo);

    boolean existsByIdTiendaAndCodigoAndIdCuponNot(Long idTienda, String codigo, Long idCupon);

    /** Usos de cada código: pedidos PAGADOS con él (los que no se usaron no aparecen). */
    interface Uso {
        Long getIdCupon();
        Long getUsos();
    }

    @Query(value = """
            SELECT p.id_cupon AS "idCupon", count(*) AS "usos"
              FROM pedidos p
             WHERE p.id_cupon IN (:ids)
               AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = p.id_pedido AND pg.estado = 1)
             GROUP BY p.id_cupon
            """, nativeQuery = true)
    List<Uso> usos(@Param("ids") Collection<Long> ids);

    @Query(value = """
            SELECT count(*) FROM pedidos p
             WHERE p.id_cupon = :idCupon
               AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = p.id_pedido AND pg.estado = 1)
            """, nativeQuery = true)
    long usosDe(@Param("idCupon") Long idCupon);

    /** ¿Este cliente ya pagó un pedido con el código? (para "una vez por cliente"). */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM pedidos p
                            WHERE p.id_cupon = :idCupon AND p.id_login = :idLogin
                              AND EXISTS (SELECT 1 FROM pagos pg WHERE pg.id_pedido = p.id_pedido AND pg.estado = 1))
            """, nativeQuery = true)
    boolean usadoPorCliente(@Param("idCupon") Long idCupon, @Param("idLogin") Long idLogin);

    /** ¿Algún pedido (pagado o no) lo menciona? Entonces no se borra: la FK lo impide. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM pedidos WHERE id_cupon = :idCupon)", nativeQuery = true)
    boolean enPedidos(@Param("idCupon") Long idCupon);
}
