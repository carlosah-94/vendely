package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.DireccionRepository;

/** Un país para los selectores (dirección del cliente, tarifas internacionales y crear tienda). */
public record PaisResponse(
        String idPais,              // ISO 3166-1: "ES"
        String nombre,              // "España"
        String prefijoTelefono,     // "+34"
        boolean requiereCodigoPostal,
        String moneda,
        boolean habilitado,         // tiene lista oficial de ubicaciones: se elige la división y se pueden crear tiendas
        String nivelFinal           // nombre de esa división: "Distrito" (Perú), "Comuna" (Chile)
) {
    public static PaisResponse desde(DireccionRepository.PaisInfo p) {
        return new PaisResponse(p.getIdPais(), p.getNombre(), p.getPrefijoTelefono(),
                Boolean.TRUE.equals(p.getRequiereCodigoPostal()), p.getIdMoneda(),
                p.getEstado() != null && p.getEstado() == 1, p.getNivelFinal());
    }
}
