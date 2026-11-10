package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Direccion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DireccionRepository extends JpaRepository<Direccion, Long> {

    // Issue #7: la dirección de envío de una venta debe ser del cliente y estar
    // activa.
    boolean existsByIdDireccionAndIdLoginAndEstado(Long idDireccion, Long idLogin, Short estado);

    // Issue #9: la dirección que se muestra en la ficha del cliente (la
    // predeterminada, o la más nueva).
    Optional<Direccion> findFirstByIdLoginAndEstadoOrderByPredeterminadaDescIdDireccionDesc(Long idLogin, Short estado);

    /**
     * Issue #9: el distrito elegido existe, es del país de la tienda y es del
     * ÚLTIMO nivel
     * (en Perú: distrito, no provincia ni departamento). La base lo exige igual
     * (trigger T12),
     * pero validarlo antes permite responder 400 con un mensaje claro en vez de un
     * error genérico.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM divisiones d
                 WHERE d.id_division = :idDivision AND d.id_pais = :idPais
                   AND d.nivel = (SELECT max(nivel) FROM niveles_division WHERE id_pais = :idPais))
            """, nativeQuery = true)
    boolean esDistritoValido(@Param("idDivision") Long idDivision, @Param("idPais") String idPais);

    // Issue #9: nombre del distrito para mostrarlo en la respuesta ("Miraflores").
    @Query(value = "SELECT nombre FROM divisiones WHERE id_division = :idDivision", nativeQuery = true)
    String nombreDivision(@Param("idDivision") Long idDivision);

    /**
     * Issue #9: al eliminar un cliente SIN ventas, sus direcciones se borran antes
     * que la cuenta,
     * porque la FK direcciones → login no tiene ON DELETE CASCADE y la base
     * impediría el borrado.
     * (Si tuviera ventas no se llega aquí: el service responde 409 antes.)
     */
    @Modifying
    @Query(value = "DELETE FROM direcciones WHERE id_login = :idLogin", nativeQuery = true)
    int borrarDeCliente(@Param("idLogin") Long idLogin);

    /** Una fila del buscador de distritos. */
    interface DistritoFila {
        Long getIdDistrito();

        String getNombre();

        String getProvincia();
    }

    /** Distritos del país cuyo nombre contiene el texto (máximo 20). */
    @Query(value = """
            SELECT d.id_division AS "idDistrito", d.nombre AS "nombre", p.nombre AS "provincia"
              FROM divisiones d LEFT JOIN divisiones p ON p.id_division = d.id_padre
             WHERE d.id_pais = :idPais
               AND d.nivel = (SELECT max(nivel) FROM niveles_division WHERE id_pais = :idPais)
               AND d.nombre ILIKE '%' || :texto || '%'
             ORDER BY d.nombre LIMIT 20
            """, nativeQuery = true)
    List<DistritoFila> buscarDistritos(@Param("idPais") String idPais, @Param("texto") String texto);
}
