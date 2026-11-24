package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Plan;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.service.MetodoPago;
import java.util.List;

/** Lo que cualquier visitante puede ver de una tienda (la vitrina). */
public record TiendaPublicaResponse(
        Long idTienda,          // lo usa el frontend para comprobar que el cliente es de ESTA tienda
        String idPais,          // país de la tienda: de él salen las divisiones del buscador
        String pais,            // "Chile"
        String moneda,          // los precios se muestran en ella: "PEN", "CLP"
        Short decimalesMoneda,  // PEN 2, CLP 0
        String impuesto,        // "IGV", "IVA"
        String nivelFinal,      // "Distrito", "Comuna": etiqueta del buscador de la dirección
        String nombre,
        String subdominio,
        String descripcion,
        String correoContacto,
        String logoUrl,
        String colorPrincipal,
        Boolean permiteInvitados,
        String plantilla,       // clasica, moderna o boutique (la que su plan permite)
        Boolean selloVendely,   // true = muestra "Hecho con Vendely" en el pie (plan Gratis)
        // Con qué puede pagar el cliente: ["PAYPAL", "MERCADO_PAGO"]. El primero es el de por defecto.
        // Vacío = la tienda no tiene pago en línea (ninguna pasarela configurada en el servidor).
        List<MetodoPago> metodosPago
) {
    public static TiendaPublicaResponse desde(Tienda t, DireccionRepository.PaisInfo p) {
        return desde(t, p, null, List.of());
    }

    /**
     * Con el plan: lo que el plan no incluye no se muestra, aunque este guardado.
     * Gratis: colores de Vendely, sin logo, plantilla clasica y sello en el pie.
     * Al mejorar el plan vuelve a verse lo que ya habia configurado (no se borro nada).
     * Los métodos de pago llegan ya filtrados por MetodoPagoService (servidor + plan).
     */
    public static TiendaPublicaResponse desde(Tienda t, DireccionRepository.PaisInfo p, Plan plan,
                                              List<MetodoPago> metodosPago) {
        boolean personaliza = plan == null || Boolean.TRUE.equals(plan.getPermitePersonalizar());
        boolean plantillas = plan == null || Boolean.TRUE.equals(plan.getPermitePlantillas());
        return new TiendaPublicaResponse(t.getIdTienda(), t.getIdPais(), p.getNombre(), p.getIdMoneda(), p.getDecimales(),
                p.getImpuesto(), p.getNivelFinal(), t.getNombre(), t.getSubdominio(), t.getDescripcion(),
                t.getCorreoContacto(), personaliza ? t.getLogoUrl() : null, personaliza ? t.getColorPrincipal() : null,
                t.getPermiteInvitados(), plantillas ? t.getPlantilla() : "clasica", !personaliza, metodosPago);
    }
}
