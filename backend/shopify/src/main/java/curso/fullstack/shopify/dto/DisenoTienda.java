package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.entity.Tienda;

/**
 * Opciones de diseño de la vitrina (V12), además del logo, el color principal y la plantilla.
 * Va dentro de TiendaResponse (panel: lo guardado) y de TiendaPublicaResponse (vitrina: solo
 * lo que el plan incluye; lo demás sale con el valor por defecto).
 */
public record DisenoTienda(
        String colorAcento,       // #RRGGBB o null (= el color principal)
        String textoAnuncio,      // null = "Envíos a todo ..." según las tarifas
        Boolean mostrarAnuncio,
        String imagenPortadaUrl,  // banner de la portada o null
        String fuente,            // auto, moderna, expresiva, elegante, impacto
        String estiloBotones,     // auto, redondo, suave, recto
        String fondo,             // blanco, hueso, perla, arena
        Boolean mostrarGarantias,
        Boolean mostrarDestacado,
        Boolean mostrarCategorias
) {
    /** Lo guardado, tal cual (para el panel). */
    public static DisenoTienda de(Tienda t) {
        return de(t, true, true);
    }

    /**
     * Lo que se ve en la vitrina según el plan:
     * sin PERSONALIZAR (Gratis) no hay acento, anuncio propio, portada, letra ni botones;
     * sin PLANTILLAS (Gratis y Básico) el fondo es blanco. Las secciones las elige cualquier plan.
     */
    public static DisenoTienda de(Tienda t, boolean personaliza, boolean plantillas) {
        return new DisenoTienda(
                personaliza ? t.getColorAcento() : null,
                personaliza ? t.getTextoAnuncio() : null,
                !personaliza || !Boolean.FALSE.equals(t.getMostrarAnuncio()),
                personaliza ? t.getImagenPortadaUrl() : null,
                personaliza ? t.getFuente() : "auto",
                personaliza ? t.getEstiloBotones() : "auto",
                plantillas ? t.getFondo() : "blanco",
                !Boolean.FALSE.equals(t.getMostrarGarantias()),
                !Boolean.FALSE.equals(t.getMostrarDestacado()),
                !Boolean.FALSE.equals(t.getMostrarCategorias()));
    }
}
