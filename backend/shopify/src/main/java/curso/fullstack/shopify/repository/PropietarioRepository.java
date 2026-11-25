package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.Propietario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropietarioRepository extends JpaRepository<Propietario, Long> {

    Optional<Propietario> findByIdLogin(Long idLogin);

    boolean existsByTipoDocumentoAndNumeroDocumento(Short tipoDocumento, String numeroDocumento);

    /** ¿Otro dueño ya registró este RUC? (V24, uq_propietarios_ruc) */
    boolean existsByRucAndIdPropietarioNot(String ruc, Long idPropietario);
}
