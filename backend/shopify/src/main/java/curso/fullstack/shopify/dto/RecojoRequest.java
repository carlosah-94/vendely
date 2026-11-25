package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Recojo en tienda (V18). Para activarlo hace falta la dirección del local. */
public record RecojoRequest(
        @NotNull(message = "Indica si ofreces recojo en tienda") Boolean activo,
        @Size(max = 200, message = "La dirección puede tener hasta 200 caracteres") String direccion,
        @Size(max = 120, message = "El horario puede tener hasta 120 caracteres") String horario
) {}
