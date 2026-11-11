package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Tienda;

/** Lo que cualquier visitante puede ver de una tienda (la vitrina). */
public record TiendaPublicaResponse(
        Long idTienda,          // lo usa el frontend para comprobar que el cliente es de ESTA tienda
        String idPais,          // país de la tienda: de él salen los distritos del buscador
        String nombre,
        String subdominio,
        String descripcion,
        String correoContacto,
        String logoUrl,
        String colorPrincipal,
        Boolean permiteInvitados
) {
    public static TiendaPublicaResponse desde(Tienda t) {
        return new TiendaPublicaResponse(t.getIdTienda(), t.getIdPais(), t.getNombre(), t.getSubdominio(), t.getDescripcion(),
                t.getCorreoContacto(), t.getLogoUrl(), t.getColorPrincipal(), t.getPermiteInvitados());
    }
}
