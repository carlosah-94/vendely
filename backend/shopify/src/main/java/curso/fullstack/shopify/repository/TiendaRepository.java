package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Tienda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    Optional<Tienda> findBySubdominio(String subdominio);

    boolean existsBySubdominio(String subdominio);

    // Las tiendas de un dueño, de la más antigua a la más nueva
    List<Tienda> findByIdPropietarioOrderByIdTiendaAsc(Long idPropietario);

    /** Tarifa de envío por defecto a cada región del país de la tienda. */
    @Modifying
    @Query(value = """
            INSERT INTO tarifas_envio (id_tienda, id_division, costo, envio_gratis_desde, dias_entrega_min, dias_entrega_max)
            SELECT t.id_tienda, d.id_division, 10.00, NULL, 1, 5
              FROM tiendas t JOIN divisiones d ON d.id_pais = t.id_pais AND d.nivel = 1
             WHERE t.id_tienda = :idTienda
            """, nativeQuery = true)
    int crearTarifasPorDefecto(@Param("idTienda") Long idTienda);
}
