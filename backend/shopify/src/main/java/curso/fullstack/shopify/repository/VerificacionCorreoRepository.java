package curso.fullstack.shopify.repository;

import curso.fullstack.shopify.entity.VerificacionCorreo;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VerificacionCorreoRepository extends JpaRepository<VerificacionCorreo, String> {

    /**
     * Con bloqueo de fila: dos intentos al mismo tiempo se comprueban uno detrás del otro, así
     * nadie puede mandar muchos códigos en paralelo para saltarse el límite de 5 intentos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificacionCorreo v where v.correo = :correo")
    Optional<VerificacionCorreo> bloquear(String correo);

    /** Limpieza: los códigos vencidos que nadie usó. */
    @Modifying
    @Query("delete from VerificacionCorreo v where v.expiraEn < :antes")
    int borrarVencidos(OffsetDateTime antes);
}
