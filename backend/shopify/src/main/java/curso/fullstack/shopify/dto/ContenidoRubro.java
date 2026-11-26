package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * Contenido de la plantilla por rubro (V23, columna JSON tiendas.contenido_rubro). Son los textos y
 * las secciones que el dueño adapta a su negocio, en CUALQUIER plan (como la descripción): los
 * colores y la letra siguen siendo de Básico en adelante.
 *
 * Cada campo vale para uno o varios rubros; si queda vacío (null), la vitrina usa el texto por
 * defecto de su plantilla (utils/temas.js y components/tienda/rubros). Ejemplos:
 *   - comida:     anticipacion "24 horas", sello "Hecho hoy", pasos de "¿Cómo hago mi pedido?"
 *   - tecnología: garantia "12 meses", mostrarMarcas
 *   - deporte:    lema "Entrena, Supérate, No pares"
 *   - moda:       tallas ropa | calzado | ninos | ninguna
 *   - belleza:    pasos de la rutina
 *   - mascotas:   globos "¡Guau!" y "¡Miau!"
 *   - todos:      etiqueta y botón de la portada, preguntas frecuentes propias
 *
 * Seguridad: solo se guardan estos campos (cualquier otro que llegue se descarta), con largo
 * máximo, a lo más 4 pasos y 6 preguntas, y sin los signos < y >. La vitrina además lo muestra
 * siempre como texto, nunca como código.
 */
public record ContenidoRubro(
        @Size(max = 60, message = "La frase de la portada puede tener hasta 60 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String etiqueta,
        @Size(max = 30, message = "El texto del botón puede tener hasta 30 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String boton,
        @Size(max = 30, message = "El texto del sello puede tener hasta 30 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String sello,
        @Size(max = 40, message = "La anticipación puede tener hasta 40 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String anticipacion,
        @Size(max = 40, message = "La garantía puede tener hasta 40 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String garantia,
        @Size(max = 120, message = "Las palabras de la cinta pueden tener hasta 120 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String lema,
        @Size(max = 16, message = "Cada globo puede tener hasta 16 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String globo1,
        @Size(max = 16, message = "Cada globo puede tener hasta 16 caracteres")
        @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String globo2,
        @Pattern(regexp = "^(ropa|calzado|ninos|ninguna)$", message = "La guía de tallas debe ser ropa, calzado, ninos o ninguna")
        String tallas,
        Boolean mostrarFechaEspecial, // comida: tarjeta "¿Lo necesitas para una fecha especial?"
        Boolean mostrarMarcas,        // tecnología: fila con las marcas de sus productos
        Boolean mostrarPasos,         // comida y belleza: los pasos
        @Valid @Size(max = 4, message = "Puedes poner hasta 4 pasos") List<Paso> pasos,
        @Valid @Size(max = 6, message = "Puedes poner hasta 6 preguntas frecuentes") List<Pregunta> preguntas
) {
    static final String TEXTO = "^[^<>]*$";
    static final String SIN_SIGNOS = "No se permiten los signos < ni >";

    public record Paso(
            @NotBlank(message = "Cada paso necesita un título")
            @Size(max = 30, message = "El título del paso puede tener hasta 30 caracteres")
            @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String titulo,
            @Size(max = 140, message = "El texto del paso puede tener hasta 140 caracteres")
            @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String texto
    ) {}

    public record Pregunta(
            @NotBlank(message = "Escribe la pregunta")
            @Size(max = 120, message = "La pregunta puede tener hasta 120 caracteres")
            @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String pregunta,
            @NotBlank(message = "Escribe la respuesta")
            @Size(max = 500, message = "La respuesta puede tener hasta 500 caracteres")
            @Pattern(regexp = TEXTO, message = SIN_SIGNOS) String respuesta
    ) {}

    /** Para guardar: textos sin espacios de más (vacío = null) y pasos o preguntas vacíos fuera. */
    public ContenidoRubro limpio() {
        return new ContenidoRubro(texto(etiqueta), texto(boton), texto(sello), texto(anticipacion), texto(garantia),
                texto(lema), texto(globo1), texto(globo2), tallas, mostrarFechaEspecial, mostrarMarcas, mostrarPasos,
                pasos == null ? null : pasos.stream().filter(Objects::nonNull)
                        .map(p -> new Paso(texto(p.titulo()), texto(p.texto()))).filter(p -> p.titulo() != null).toList(),
                preguntas == null ? null : preguntas.stream().filter(Objects::nonNull)
                        .map(q -> new Pregunta(texto(q.pregunta()), texto(q.respuesta())))
                        .filter(q -> q.pregunta() != null && q.respuesta() != null).toList());
    }

    /** ¿Tiene algo? Si todo está vacío, se guarda null (la plantilla usa sus textos por defecto). */
    public boolean usaAlgo() {
        ContenidoRubro c = limpio();
        return c.etiqueta != null || c.boton != null || c.sello != null || c.anticipacion != null || c.garantia != null
                || c.lema != null || c.globo1 != null || c.globo2 != null || c.tallas != null
                || c.mostrarFechaEspecial != null || c.mostrarMarcas != null || c.mostrarPasos != null
                || (c.pasos != null && !c.pasos.isEmpty()) || (c.preguntas != null && !c.preguntas.isEmpty());
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
