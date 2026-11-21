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
 * Tabla "tiendas". Cada tienda pertenece a un propietario y se identifica
 * en internet por su subdominio: zapatillas-carlos → zapatillas-carlos.vendely.pe
 */
@Entity
@Table(name = "tiendas")
@Getter
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tienda")
    private Long idTienda;

    @Setter
    @Column(name = "id_propietario", nullable = false)
    private Long idPropietario;

    /** País donde vende (ISO 3166-1: PE, CL…). Define su moneda e impuesto. No cambia después de crear la tienda (T8). */
    @Setter
    @Column(name = "id_pais", nullable = false, length = 2, columnDefinition = "bpchar(2)", updatable = false)
    private String idPais = "PE";

    @Setter
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Solo minúsculas, números y guiones (CHECK ck_tiendas_subdominio). No se cambia después de crear la tienda. */
    @Setter
    @Column(name = "subdominio", nullable = false, unique = true, length = 63, updatable = false)
    private String subdominio;

    @Setter
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Setter
    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    @Setter
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Color de la marca en formato #RRGGBB */
    @Setter
    @Column(name = "color_principal", length = 7)
    private String colorPrincipal;

    /** true = se puede comprar sin crear cuenta */
    @Setter
    @Column(name = "permite_invitados", nullable = false)
    private Boolean permiteInvitados = true;

    /** Lo maneja el trigger de pedidos (#1001, #1002…). Java solo lo lee. */
    @Column(name = "ultimo_numero_pedido", insertable = false, updatable = false)
    private Integer ultimoNumeroPedido;

    /** Plan actual: 1 Gratis · 2 Basico · 3 Pro (tabla planes). Toda tienda nueva empieza en Gratis. */
    @Setter
    @Column(name = "id_plan", nullable = false)
    private Short idPlan = 1;

    /** Hasta cuando esta pagado el plan. null en Gratis. */
    @Setter
    @Column(name = "plan_vence_el")
    private OffsetDateTime planVenceEl;

    /** Diseno de la tienda: clasica, moderna o boutique (CHECK ck_tiendas_plantilla). */
    @Setter
    @Column(name = "plantilla", nullable = false, length = 20)
    private String plantilla = "clasica";

    /** 1 activa · 0 suspendida */
    @Setter
    @Column(name = "estado", nullable = false)
    private Short estado = 1;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;

    public boolean estaActiva() {
        return estado != null && estado == 1;
    }
}
