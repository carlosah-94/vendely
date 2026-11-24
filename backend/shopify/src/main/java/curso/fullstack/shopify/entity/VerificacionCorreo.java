package curso.fullstack.shopify.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "verificaciones_correo": el último código de 6 dígitos que se envió a un correo antes
 * de crear la cuenta del dueño. Solo se guarda el hash del código, nunca el código.
 */
@Entity
@Table(name = "verificaciones_correo")
@Getter
@Setter
public class VerificacionCorreo {

    @Id
    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "enviado_en", nullable = false)
    private OffsetDateTime enviadoEn;

    @Column(name = "intentos", nullable = false)
    private Short intentos = 0;
}
