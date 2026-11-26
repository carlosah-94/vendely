package curso.fullstack.shopify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import curso.fullstack.shopify.dto.ContenidoRubro;
import curso.fullstack.shopify.dto.DisenoPro;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    // ---- Personalización de la vitrina (V12). Qué permite cada plan: TiendaService.actualizar.

    /** Segundo color (#RRGGBB): etiquetas de oferta, detalles y cinta. null = el principal. Básico y Pro. */
    @Setter
    @Column(name = "color_acento", length = 7)
    private String colorAcento;

    /** Texto de la barra de arriba. null = "Envíos a todo ..." según las tarifas. Básico y Pro. */
    @Setter
    @Column(name = "texto_anuncio", length = 120)
    private String textoAnuncio;

    /** false = sin barra de anuncio. Básico y Pro. */
    @Setter
    @Column(name = "mostrar_anuncio", nullable = false)
    private Boolean mostrarAnuncio = true;

    /** Banner de la portada. Básico y Pro. */
    @Setter
    @Column(name = "imagen_portada_url", length = 500)
    private String imagenPortadaUrl;

    /** auto (la de la plantilla), moderna, expresiva, elegante o impacto (CHECK ck_tiendas_fuente). Básico y Pro. */
    @Setter
    @Column(name = "fuente", nullable = false, length = 20)
    private String fuente = "auto";

    /** auto (el de la plantilla), redondo, suave o recto (CHECK ck_tiendas_estilo_botones). Básico y Pro. */
    @Setter
    @Column(name = "estilo_botones", nullable = false, length = 20)
    private String estiloBotones = "auto";

    /** blanco, hueso, perla o arena (CHECK ck_tiendas_fondo). Distinto de blanco solo con Pro. */
    @Setter
    @Column(name = "fondo", nullable = false, length = 20)
    private String fondo = "blanco";

    /** Secciones de la portada: las puede ocultar cualquier plan. */
    @Setter
    @Column(name = "mostrar_garantias", nullable = false)
    private Boolean mostrarGarantias = true;

    @Setter
    @Column(name = "mostrar_destacado", nullable = false)
    private Boolean mostrarDestacado = true;

    @Setter
    @Column(name = "mostrar_categorias", nullable = false)
    private Boolean mostrarCategorias = true;

    /** Sección propia del rubro (horario y entrega, marcas, guía de tallas...), V22. */
    @Setter
    @Column(name = "mostrar_seccion_rubro", nullable = false)
    private Boolean mostrarSeccionRubro = true;

    /** Tema aplicado en Tienda online → Temas (V13). null = nunca aplicó uno. Catálogo: utils/temas.js. */
    @Setter
    @Column(name = "tema", length = 20)
    private String tema;

    @Setter
    @Column(name = "tema_aplicado_el")
    private OffsetDateTime temaAplicadoEl;

    /** Diseño avanzado del plan Pro (V15): portada a medida, anuncio, promoción... null = nunca se configuró. */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diseno_pro", columnDefinition = "jsonb")
    private DisenoPro disenoPro;

    /** Textos y secciones de la plantilla por rubro que el dueño adapta (V23). null = los de la plantilla. */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contenido_rubro", columnDefinition = "jsonb")
    private ContenidoRubro contenidoRubro;

    /** ¿Ya puede vender? En Perú, solo si su dueño registró su RUC (V24, fn_tienda_puede_vender). */
    @org.hibernate.annotations.Formula("fn_tienda_puede_vender(id_tienda)")
    private Boolean puedeVender;

    /** WhatsApp de la tienda con código de país (+51987654321), V17. null = sin botón de WhatsApp. Todos los planes. */
    @Setter
    @Column(name = "whatsapp", length = 16)
    private String whatsapp;

    /** Qué vende (V17): moda, tecnologia, comida, belleza, hogar, deporte, mascotas u otro. */
    @Setter
    @Column(name = "rubro", length = 20)
    private String rubro;

    /** true = el cliente puede recoger su pedido en el local, gratis (V18). Exige recojoDireccion. */
    @Setter
    @Column(name = "recojo_activo", nullable = false)
    private Boolean recojoActivo = false;

    @Setter
    @Column(name = "recojo_direccion", length = 200)
    private String recojoDireccion;

    /** Texto libre: "Lunes a sábado de 10 a. m. a 7 p. m." */
    @Setter
    @Column(name = "recojo_horario", length = 120)
    private String recojoHorario;

    /** Hasta cuándo el equipo de Vendely puede entrar a armar la tienda (V16). null = sin permiso. */
    @Setter
    @Column(name = "soporte_hasta")
    private OffsetDateTime soporteHasta;

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

    /** true si el dueño dio permiso a soporte de Vendely y todavía no vence. */
    public boolean soporteActivo() {
        return soporteHasta != null && soporteHasta.isAfter(OffsetDateTime.now());
    }
}
