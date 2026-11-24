package curso.fullstack.shopify.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * Diseño avanzado de la vitrina, solo del plan Pro (V15, columna JSON tiendas.diseno_pro):
 *  - portada: banner a medida (título, texto, dos botones, alineación, velo y alto);
 *  - anuncio: color propio de la barra y hasta 3 mensajes que van rotando;
 *  - promo: una franja de promoción debajo de la portada ("50 % en toda la tienda");
 *  - categoriasEnMenu: las categorías como enlaces en la cabecera;
 *  - tarjetas: la etiqueta "-25 %" en los productos con precio rebajado.
 *
 * Los enlaces de los botones solo pueden ser rutas de la tienda ("/…"), anclas ("#catalogo") o
 * direcciones http(s): nunca "javascript:", que ejecutaría código en la tienda.
 */
public record DisenoPro(
        @Valid Portada portada,
        @Valid Anuncio anuncio,
        @Valid Promo promo,
        Boolean categoriasEnMenu,
        Boolean mostrarDescuento
) {
    static final String ENLACE = "^$|^(/|#|https?://)[^\\s<>\"']{0,300}$";
    static final String MENSAJE_ENLACE = "El enlace debe empezar con /, # o https://";
    static final String COLOR = "^$|^#[0-9A-Fa-f]{6}$";

    /**
     * activa: si es false (o null) la plantilla usa su portada de siempre.
     * alineacion: izquierda o centro. alto: medio, alto o pantalla. oscurecer: velo negro de 0 a 80 %.
     */
    public record Portada(
            Boolean activa,
            @Size(max = 90, message = "El título de la portada puede tener hasta 90 caracteres") String titulo,
            @Size(max = 200, message = "El texto de la portada puede tener hasta 200 caracteres") String subtitulo,
            @Size(max = 30, message = "El texto del botón puede tener hasta 30 caracteres") String boton1Texto,
            @Pattern(regexp = ENLACE, message = MENSAJE_ENLACE) String boton1Enlace,
            @Size(max = 30, message = "El texto del botón puede tener hasta 30 caracteres") String boton2Texto,
            @Pattern(regexp = ENLACE, message = MENSAJE_ENLACE) String boton2Enlace,
            @Pattern(regexp = "^(izquierda|centro)$", message = "La alineación debe ser izquierda o centro") String alineacion,
            @Min(0) @Max(80) Integer oscurecer,
            @Pattern(regexp = "^(medio|alto|pantalla)$", message = "El alto debe ser medio, alto o pantalla") String alto
    ) {}

    /** mensajes: de 1 a 3 (cada uno hasta 120 caracteres). color: el de la barra (#RRGGBB) o vacío. */
    public record Anuncio(
            @Pattern(regexp = COLOR, message = "El color de la barra debe tener el formato #RRGGBB") String color,
            @Size(max = 3, message = "La barra de anuncio puede tener hasta 3 mensajes")
            List<@Size(max = 120, message = "Cada mensaje puede tener hasta 120 caracteres") String> mensajes
    ) {}

    public record Promo(
            Boolean mostrar,
            @Size(max = 80, message = "El título de la promoción puede tener hasta 80 caracteres") String titulo,
            @Size(max = 200, message = "El texto de la promoción puede tener hasta 200 caracteres") String texto,
            @Size(max = 30, message = "El texto del botón puede tener hasta 30 caracteres") String botonTexto,
            @Pattern(regexp = ENLACE, message = MENSAJE_ENLACE) String botonEnlace,
            @Pattern(regexp = COLOR, message = "El color de la promoción debe tener el formato #RRGGBB") String color
    ) {}

    /** El diseño sin nada a medida: lo que tiene una tienda que nunca lo configuró. */
    public static final DisenoPro VACIO = new DisenoPro(null, null, null, null, null).limpio();

    /**
     * Normaliza para guardar: textos sin espacios de más (vacío = null), colores en mayúsculas,
     * mensajes vacíos fuera y valores por defecto en las opciones. Así dos diseños "iguales"
     * son equals() y el plan solo se exige cuando de verdad algo cambia.
     */
    public DisenoPro limpio() {
        Portada p = portada == null ? new Portada(null, null, null, null, null, null, null, null, null, null) : portada;
        Anuncio a = anuncio == null ? new Anuncio(null, null) : anuncio;
        Promo pr = promo == null ? new Promo(null, null, null, null, null, null) : promo;
        return new DisenoPro(
                new Portada(Boolean.TRUE.equals(p.activa()), texto(p.titulo()), texto(p.subtitulo()),
                        texto(p.boton1Texto()), texto(p.boton1Enlace()), texto(p.boton2Texto()), texto(p.boton2Enlace()),
                        p.alineacion() == null ? "izquierda" : p.alineacion(),
                        p.oscurecer() == null ? 35 : p.oscurecer(),
                        p.alto() == null ? "alto" : p.alto()),
                new Anuncio(color(a.color()),
                        a.mensajes() == null ? List.of()
                                : a.mensajes().stream().map(DisenoPro::texto).filter(Objects::nonNull).limit(3).toList()),
                new Promo(Boolean.TRUE.equals(pr.mostrar()), texto(pr.titulo()), texto(pr.texto()),
                        texto(pr.botonTexto()), texto(pr.botonEnlace()), color(pr.color())),
                Boolean.TRUE.equals(categoriasEnMenu),
                Boolean.TRUE.equals(mostrarDescuento));
    }

    /** ¿Usa algo del plan Pro? (si no, cualquier plan puede guardarlo: es volver a lo de siempre). */
    public boolean usaAlgo() {
        return !VACIO.equals(limpio());
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String color(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
