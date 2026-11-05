package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Tabla "tiendas" (versión mínima para la seguridad: el cliente se registra
 * e inicia sesión en una tienda, que se identifica por su subdominio).
 * El módulo de tiendas puede agregar los demás campos después.
 */
@Entity
@Table(name = "tiendas")
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tienda")
    private Long idTienda;

    @Column(name = "id_propietario", nullable = false)
    private Long idPropietario;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** zapatillas-carlos → zapatillas-carlos.vendely.pe */
    @Column(name = "subdominio", nullable = false, unique = true, length = 63)
    private String subdominio;

    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    /** 1 activa · 0 suspendida */
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public boolean estaActiva() {
        return estado != null && estado == 1;
    }

    public Long getIdTienda() { return idTienda; }
    public Long getIdPropietario() { return idPropietario; }
    public void setIdPropietario(Long idPropietario) { this.idPropietario = idPropietario; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSubdominio() { return subdominio; }
    public void setSubdominio(String subdominio) { this.subdominio = subdominio; }
    public String getCorreoContacto() { return correoContacto; }
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }
    public Short getEstado() { return estado; }
    public void setEstado(Short estado) { this.estado = estado; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
}
