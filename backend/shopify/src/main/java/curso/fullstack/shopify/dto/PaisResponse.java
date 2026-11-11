package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.DireccionRepository;

/** Un país para los selectores (dirección del cliente y tarifas internacionales). */
public record PaisResponse(
        String idPais,              // ISO 3166-1: "ES"
        String nombre,              // "España"
        String prefijoTelefono,     // "+34"
        boolean requiereCodigoPostal,
        String moneda
) {
    public static PaisResponse desde(DireccionRepository.PaisInfo p) {
        return new PaisResponse(p.getIdPais(), p.getNombre(), p.getPrefijoTelefono(),
                Boolean.TRUE.equals(p.getRequiereCodigoPostal()), p.getIdMoneda());
    }
}
