package curso.fullstack.shopify.exception;

import java.time.OffsetDateTime;
import java.util.Map;

/** Formato único de error que recibe el frontend. */
public record ApiError(
        OffsetDateTime fecha,
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> campos   // errores de validación por campo (puede ser null)
) {}
