package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Tabla "tiendas". Cada tienda pertenece a un propietario y se identifica
 * en internet por su subdominio: zapatillas-carlos → zapatillas-carlos.vendely.pe
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

    /** País donde vende (ISO 3166-1: PE, CL…). Define su moneda e impuesto. No cambia después de crear la tienda (T8). */
    @Column(name = "id_pais", nullable = false, length = 2, columnDefinition = "bpchar(2)", updatable = false)
    private String idPais = "PE";

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Solo minúsculas, números y guiones (CHECK ck_tiendas_subdominio). No se cambia después de crear la tienda. */
    @Column(name = "subdominio", nullable = false, unique = true, length = 63, updatable = false)
    private String subdominio;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Color de la marca en formato #RRGGBB */
    @Column(name = "color_principal", length = 7)
    private String colorPrincipal;

    /** true = se puede comprar sin crear cuenta */
    @Column(name = "permite_invitados", nullable = false)
    private Boolean permiteInvitados = true;

    /** Lo maneja el trigger de pedidos (#1001, #1002…). Java solo lo lee. */
    @Column(name = "ultimo_numero_pedido", insertable = false, updatable = false)
    private Integer ultimoNumeroPedido;

    /** 1 activa · 0 suspendida */
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;

    public boolean estaActiva() {
        return estado != null && estado == 1;
    }

    public Long getIdTienda() { return idTienda; }
    public Long getIdPropietario() { return idPropietario; }
    public void setIdPropietario(Long idPropietario) { this.idPropietario = idPropietario; }
    public String getIdPais() { return idPais; }
    public void setIdPais(String idPais) { this.idPais = idPais; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSubdominio() { return subdominio; }
    public void setSubdominio(String subdominio) { this.subdominio = subdominio; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCorreoContacto() { return correoContacto; }
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getColorPrincipal() { return colorPrincipal; }
    public void setColorPrincipal(String colorPrincipal) { this.colorPrincipal = colorPrincipal; }
    public Boolean getPermiteInvitados() { return permiteInvitados; }
    public void setPermiteInvitados(Boolean permiteInvitados) { this.permiteInvitados = permiteInvitados; }
    public Integer getUltimoNumeroPedido() { return ultimoNumeroPedido; }
    public Short getEstado() { return estado; }
    public void setEstado(Short estado) { this.estado = estado; }
    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public OffsetDateTime getFechaModificacion() { return fechaModificacion; }
}
