package curso.fullstack.shopify.dto;

import java.util.List;

/** Los empleados de una tienda y cuántos más permite su plan. */
public record EmpleadosResponse(
        String plan,                // "Gratis", "Básico", "Pro"
        int maxEmpleados,           // lo que permite el plan (0 = solo el dueño)
        long activos,               // los que cuentan para el límite
        List<EmpleadoResponse> empleados
) {}
