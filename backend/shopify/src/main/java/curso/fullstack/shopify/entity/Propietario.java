package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla "propietarios": los datos comerciales del dueño de tiendas.
 * id_rol siempre vale 2: junto con id_login forma la FK compuesta que
 * impide registrar a un cliente como dueño.
 */
@Entity
@Table(name = "propietarios")
@Getter
public class Propietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_propietario")
    private Long idPropietario;

    @Setter
    @Column(name = "id_login", nullable = false, unique = true)
    private Long idLogin;

    @Column(name = "id_rol", nullable = false)
    private Short idRol = RolUsuario.ADMIN_TIENDA.getId();

    /** id de tipos_documento: 1 DNI · 2 RUC · 3 CE (Perú) · 4 RUT (Chile)… El formato lo valida el trigger T13. */
    @Setter
    @Column(name = "tipo_documento", nullable = false)
    private Short tipoDocumento;

    @Setter
    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Setter
    @Column(name = "razon_social", length = 150)
    private String razonSocial;

    @Setter
    @Column(name = "telefono_comercial", length = 16)
    private String telefonoComercial;

    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;
}
