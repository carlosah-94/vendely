package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.DireccionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Datos de la tienda para el panel (su dueño o un empleado). */
public record TiendaResponse(
        Long idTienda,
        String nombre,
        String subdominio,
        String idPais,              // ISO 3166-1: "PE", "CL"…
        String pais,                // "Perú", "Chile"
        String moneda,              // la del país: "PEN", "CLP". Precios, envíos y ventas van en ella
        Short decimalesMoneda,      // PEN 2, CLP 0 (la base rechaza montos con más decimales, T16)
        String impuesto,            // "IGV", "IVA": va incluido en los precios
        BigDecimal tasaImpuesto,    // 18.00, 19.00
        String nivelFinal,          // última división de la dirección: "Distrito", "Comuna"
        String descripcion,
        String correoContacto,
        String logoUrl,
        String colorPrincipal,
        Boolean permiteInvitados,
        String estado,              // "ACTIVA" o "SUSPENDIDA"
        OffsetDateTime fechaCreacion,
        String plantilla,           // clasica, moderna o boutique
        String plan,                // "Gratis", "Basico", "Pro"
        Boolean permitePersonalizar,// su plan deja poner logo, colores, portada, letra y botones
        Boolean permitePlantillas,  // su plan deja elegir plantilla y color de fondo
        Boolean esDueno,            // true: puede todo. false: es empleado y solo puede lo de "permisos"
        List<String> permisos,      // PEDIDOS, PRODUCTOS, INVENTARIO, CLIENTES (el dueño las tiene todas)
        DisenoTienda diseno,        // letra, botones, acento, fondo, portada, anuncio y secciones (lo guardado)
        String tema,                // tema aplicado (Tienda online → Temas) o null
        OffsetDateTime temaAplicadoEl,
        Integer maxProductos,       // límite de productos del plan (null = sin límite)
        Boolean soporte,            // true: quien la mira es soporte de Vendely, con permiso del dueño
        OffsetDateTime soporteHasta, // hasta cuándo soporte de Vendely puede entrar (null = sin permiso)
        String whatsapp,            // +51987654321 o null (V17)
        String rubro                // moda, tecnologia, comida, belleza, hogar, deporte, mascotas, otro o null
) {
    private static final List<String> TODOS = Arrays.stream(PermisoTienda.values()).map(Enum::name).toList();

    public static TiendaResponse desde(Tienda t, DireccionRepository.PaisInfo p) {
        return desde(t, p, null);
    }

    /** Para el dueño: puede todo. */
    public static TiendaResponse desde(Tienda t, DireccionRepository.PaisInfo p, Plan plan) {
        return desde(t, p, plan, true, false, null);
    }

    /** Con lo que puede hacer quien la mira (el dueño, o un empleado con sus permisos). */
    public static TiendaResponse desde(Tienda t, DireccionRepository.PaisInfo p, Plan plan, boolean esDueno,
            boolean soporte, Set<PermisoTienda> permisos) {
        List<String> lista = esDueno || soporte || permisos == null ? TODOS : permisos.stream().map(Enum::name).toList();
        return new TiendaResponse(t.getIdTienda(), t.getNombre(), t.getSubdominio(), t.getIdPais(),
                p.getNombre(), p.getIdMoneda(), p.getDecimales(), p.getImpuesto(), p.getTasaImpuesto(), p.getNivelFinal(),
                t.getDescripcion(), t.getCorreoContacto(), t.getLogoUrl(), t.getColorPrincipal(), t.getPermiteInvitados(),
                t.estaActiva() ? "ACTIVA" : "SUSPENDIDA", t.getFechaCreacion(), t.getPlantilla(),
                plan == null ? null : plan.getNombre(),
                plan == null || Boolean.TRUE.equals(plan.getPermitePersonalizar()),
                plan == null || Boolean.TRUE.equals(plan.getPermitePlantillas()),
                esDueno, lista, DisenoTienda.de(t),
                t.getTema(), t.getTemaAplicadoEl(),
                plan == null ? null : plan.getMaxProductos() == null ? null : plan.getMaxProductos().intValue(),
                soporte, t.soporteActivo() ? t.getSoporteHasta() : null, t.getWhatsapp(), t.getRubro());
    }
}
