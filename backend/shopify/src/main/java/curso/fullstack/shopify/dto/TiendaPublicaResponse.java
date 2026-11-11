package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.DireccionRepository;

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
        Boolean permiteInvitados
) {
    public static TiendaPublicaResponse desde(Tienda t, DireccionRepository.PaisInfo p) {
        return new TiendaPublicaResponse(t.getIdTienda(), t.getIdPais(), p.getNombre(), p.getIdMoneda(), p.getDecimales(),
                p.getImpuesto(), p.getNivelFinal(), t.getNombre(), t.getSubdominio(), t.getDescripcion(),
                t.getCorreoContacto(), t.getLogoUrl(), t.getColorPrincipal(), t.getPermiteInvitados());
    }
}
