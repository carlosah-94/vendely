package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.Tienda;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plantilla de cada rubro (V22): el diseño con que nace una tienda según lo que vende, como las
 * plantillas por industria de Shopify. Una pastelería y una tienda de tecnología no se ven igual:
 * la vitrina (frontend: components/tienda/VitrinaRubro.jsx) cambia la portada, la franja de
 * beneficios, la sección propia del rubro y las tarjetas de producto.
 *
 * Regla de planes:
 *   - La plantilla de SU rubro, tal cual, la usa cualquier plan (también Gratis): toda tienda
 *     nace bien diseñada para su negocio.
 *   - Cambiarle los colores, la letra o los botones: PERSONALIZAR (Básico y Pro), como siempre.
 *   - Usar la plantilla de OTRO rubro: PLANTILLAS (Pro).
 *
 * Los valores de aquí y los de utils/temas.js (TEMAS_RUBRO) deben ser los mismos.
 */
public record TemaRubro(String valor, String rubro, String colorPrincipal, String colorAcento,
                        String fuente, String estiloBotones) {

    /** Las plantillas por rubro usan la base "clasica" y fondo blanco: la vitrina pone el resto. */
    public static final String PLANTILLA = "clasica";
    public static final String FONDO = "blanco";

    private static final List<TemaRubro> TODOS = List.of(
            new TemaRubro("horno", "comida", "#C2410C", "#F59E0B", "expresiva", "redondo"),
            new TemaRubro("circuito", "tecnologia", "#4F46E5", "#22D3EE", "moderna", "suave"),
            new TemaRubro("impulso", "deporte", "#111111", "#C6F432", "impacto", "recto"),
            new TemaRubro("pasarela", "moda", "#1C1917", "#B45309", "elegante", "recto"),
            new TemaRubro("glow", "belleza", "#BE185D", "#F9A8D4", "elegante", "redondo"),
            new TemaRubro("nido", "hogar", "#4D6B4F", "#D97706", "expresiva", "suave"),
            new TemaRubro("huellitas", "mascotas", "#7C3AED", "#F97316", "expresiva", "redondo"));

    /** La plantilla del rubro (vacío para "otro" o sin rubro: esas tiendas usan Esencial). */
    public static Optional<TemaRubro> de(String rubro) {
        return TODOS.stream().filter(t -> t.rubro.equals(rubro)).findFirst();
    }

    public static Optional<TemaRubro> porValor(String valor) {
        return TODOS.stream().filter(t -> t.valor.equals(valor)).findFirst();
    }

    public static boolean esDeRubro(String valor) {
        return porValor(valor).isPresent();
    }

    /** Pone en la tienda los valores de esta plantilla (al crearla o al aplicarla). */
    public void aplicarA(Tienda t) {
        t.setTema(valor);
        t.setTemaAplicadoEl(java.time.OffsetDateTime.now());
        t.setPlantilla(PLANTILLA);
        t.setColorPrincipal(colorPrincipal);
        t.setColorAcento(colorAcento);
        t.setFuente(fuente);
        t.setEstiloBotones(estiloBotones);
        t.setFondo(FONDO);
    }

    /** ¿La tienda tiene la plantilla de SU rubro sin cambios? Entonces se ve igual en cualquier plan. */
    public static boolean intacta(Tienda t) {
        return de(t.getRubro())
                .filter(tema -> tema.valor.equals(t.getTema()))
                .filter(tema -> tema.mismosValores(t.getPlantilla(), t.getColorPrincipal(), t.getColorAcento(),
                        t.getFuente(), t.getEstiloBotones(), t.getFondo()))
                .isPresent();
    }

    /** ¿Estos valores son exactamente los de la plantilla? (colores sin importar mayúsculas). */
    public boolean mismosValores(String plantilla, String principal, String acento, String fuenteX,
                                 String botones, String fondo) {
        return PLANTILLA.equals(plantilla)
                && colorPrincipal.equalsIgnoreCase(Objects.toString(principal, ""))
                && colorAcento.equalsIgnoreCase(Objects.toString(acento, ""))
                && fuente.equals(fuenteX)
                && estiloBotones.equals(botones)
                && FONDO.equals(fondo);
    }
}
