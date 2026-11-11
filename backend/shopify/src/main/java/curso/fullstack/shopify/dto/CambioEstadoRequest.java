package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotNull;

/** Activar (true) o desactivar (false) una tienda o una cuenta desde el panel del superadmin. */
public record CambioEstadoRequest(
        @NotNull(message = "Indica si queda activa (true) o no (false)")
        Boolean activa
) {}
