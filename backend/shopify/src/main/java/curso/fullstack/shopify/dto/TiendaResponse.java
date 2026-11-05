package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Tienda;
import java.time.OffsetDateTime;

/** Datos de la tienda para su dueño (panel). */
public record TiendaResponse(
        Long idTienda,
        String nombre,
        String subdominio,
        String descripcion,
        String correoContacto,
        String logoUrl,
        String colorPrincipal,
        Boolean permiteInvitados,
        String estado,              // "ACTIVA" o "SUSPENDIDA"
        OffsetDateTime fechaCreacion
) {
    public static TiendaResponse desde(Tienda t) {
        return new TiendaResponse(t.getIdTienda(), t.getNombre(), t.getSubdominio(), t.getDescripcion(),
                t.getCorreoContacto(), t.getLogoUrl(), t.getColorPrincipal(), t.getPermiteInvitados(),
                t.estaActiva() ? "ACTIVA" : "SUSPENDIDA", t.getFechaCreacion());
    }
}
