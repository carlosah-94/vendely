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
 * Tabla "login": una sola tabla de cuentas para los tres roles.
 * - Cuentas del panel (SUPERADMIN, ADMIN_TIENDA): idTienda = null.
 * - Clientes: idTienda = la tienda donde se registraron.
 */
@Entity
@Table(name = "login")
@Getter
public class Login {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_login")
    private Long idLogin;

    @Column(name = "id_rol", nullable = false)
    private Short idRol;

    @Setter
    @Column(name = "id_tienda")
    private Long idTienda;

    @Setter
    @Column(name = "usuario", nullable = false, length = 50)
    private String usuario;

    @Setter
    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    /** Solo el hash BCrypt. La contraseña real nunca se guarda. */
    @Setter
    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Setter
    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Setter
    @Column(name = "primer_apellido", nullable = false, length = 80)
    private String primerApellido;

    @Setter
    @Column(name = "segundo_apellido", length = 80)
    private String segundoApellido;

    /** Formato internacional E.164: +51987654321 (CHECK ck_login_telefono). */
    @Setter
    @Column(name = "telefono", length = 16)
    private String telefono;

    @Setter
    @Column(name = "acepta_marketing", nullable = false)
    private Boolean aceptaMarketing = false;

    /** 1 activo · 0 inactivo */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    /** Si cambia, los tokens emitidos antes dejan de servir. */
    @Setter
    @Column(name = "contrasena_cambiada_en")
    private OffsetDateTime contrasenaCambiadaEn;

    // Las fechas las pone la base de datos (DEFAULT now() y el trigger T1).
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;

    public RolUsuario getRol() {
        return RolUsuario.desdeId(idRol);
    }

    public void setRol(RolUsuario rol) {
        this.idRol = rol.getId();
    }

    public boolean estaActivo() {
        return estado != null && estado == 1;
    }
}
