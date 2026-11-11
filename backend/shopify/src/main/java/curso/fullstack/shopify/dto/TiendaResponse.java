package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.DireccionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Datos de la tienda para su dueño (panel). */
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
        OffsetDateTime fechaCreacion
) {
    public static TiendaResponse desde(Tienda t, DireccionRepository.PaisInfo p) {
        return new TiendaResponse(t.getIdTienda(), t.getNombre(), t.getSubdominio(), t.getIdPais(),
                p.getNombre(), p.getIdMoneda(), p.getDecimales(), p.getImpuesto(), p.getTasaImpuesto(), p.getNivelFinal(),
                t.getDescripcion(), t.getCorreoContacto(), t.getLogoUrl(), t.getColorPrincipal(), t.getPermiteInvitados(),
                t.estaActiva() ? "ACTIVA" : "SUSPENDIDA", t.getFechaCreacion());
    }
}
