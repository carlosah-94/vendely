package curso.fullstack.shopify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una tienda. El subdominio no se cambia: es la dirección
 * que ya conocen los clientes y con la que inician sesión.
 */
public record TiendaActualizarRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150)
        String nombre,

        @Size(max = 2000) String descripcion,

        @NotBlank(message = "El correo de contacto es obligatorio") @Email(message = "El correo no es válido") @Size(max = 150)
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no es válido")
        String correoContacto,

        @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "El logo debe ser una dirección http o https")
        String logoUrl,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener el formato #RRGGBB, por ejemplo #1D4ED8")
        String colorPrincipal,

        Boolean permiteInvitados,

        // Opcional: si no se envia, la plantilla no cambia. Moderna y Boutique solo con plan Pro.
        @Pattern(regexp = "^(clasica|moderna|boutique)$", message = "La plantilla debe ser clasica, moderna o boutique")
        String plantilla,

        // ---- Personalización (V12). Todos opcionales: null = no cambia; "" en los textos = quitarlo.
        // Qué permite cada plan lo valida TiendaService.actualizar.

        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "El color de acento debe tener el formato #RRGGBB")
        String colorAcento,

        @Size(max = 120, message = "El anuncio puede tener hasta 120 caracteres")
        String textoAnuncio,

        Boolean mostrarAnuncio,

        @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "La imagen de portada debe ser una dirección http o https")
        String imagenPortadaUrl,

        @Pattern(regexp = "^(auto|moderna|expresiva|elegante|impacto)$",
                message = "La letra debe ser auto, moderna, expresiva, elegante o impacto")
        String fuente,

        @Pattern(regexp = "^(auto|redondo|suave|recto)$", message = "Los botones deben ser auto, redondo, suave o recto")
        String estiloBotones,

        @Pattern(regexp = "^(blanco|hueso|perla|arena)$", message = "El fondo debe ser blanco, hueso, perla o arena")
        String fondo,

        Boolean mostrarGarantias,
        Boolean mostrarDestacado,
        Boolean mostrarCategorias,

        // Tema aplicado desde Tienda online → Temas (V13). Opcional: null = no cambia. Es solo el
        // nombre: los colores, la letra, la plantilla... del tema llegan en los campos de arriba,
        // y con ellos se valida el plan.
        @Pattern(regexp = "^(esencial|mercado|minimal|escaparate|pop|atelier|arena)$", message = "Ese tema no existe")
        String tema
) {}
