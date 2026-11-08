package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Tabla "login": una sola tabla de cuentas para los tres roles.
 * - Cuentas del panel (SUPERADMIN, ADMIN_TIENDA): idTienda = null.
 * - Clientes: idTienda = la tienda donde se registraron.
 */
@Entity
@Table(name = "login")
public class Login {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_login")
    private Long idLogin;

    @Column(name = "id_rol", nullable = false)
    private Short idRol;

    @Column(name = "id_tienda")
    private Long idTienda;

    @Column(name = "usuario", nullable = false, length = 50)
    private String usuario;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    /** Solo el hash BCrypt. La contraseña real nunca se guarda. */
    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "primer_apellido", nullable = false, length = 80)
    private String primerApellido;

    @Column(name = "segundo_apellido", length = 80)
    private String segundoApellido;

    /** Formato internacional E.164: +51987654321 (CHECK ck_login_telefono). */
    @Column(name = "telefono", length = 16)
    private String telefono;

    @Column(name = "acepta_marketing", nullable = false)
    private Boolean aceptaMarketing = false;

    /** 1 activo · 0 inactivo */
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    /** Si cambia, los tokens emitidos antes dejan de servir. */
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

    public Long getIdLogin() { return idLogin; }
    public Short getIdRol() { return idRol; }
    public Long getIdTienda() { return idTienda; }
    public void setIdTienda(Long idTienda) { this.idTienda = idTienda; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }
    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String segundoApellido) { this.segundoApellido = segundoApellido; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Boolean getAceptaMarketing() { return aceptaMarketing; }
    public void setAceptaMarketing(Boolean aceptaMarketing) { this.aceptaMarketing = aceptaMarketing; }
    public Short getEstado() { return estado; }
    public void setEstado(Short estado) { this.estado = estado; }
    public OffsetDateTime getContrasenaCambiadaEn() { return contrasenaCambiadaEn; }
    public void setContrasenaCambiadaEn(OffsetDateTime contrasenaCambiadaEn) { this.contrasenaCambiadaEn = contrasenaCambiadaEn; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public OffsetDateTime getFechaModificacion() { return fechaModificacion; }
}
