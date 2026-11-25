package curso.fullstack.shopify.service;

import java.util.List;
import java.util.Map;

/**
 * Kit de inicio según lo que vende la tienda (V17), como las plantillas por industria de Shopify:
 * no es solo un color distinto, es la tienda armada para ese negocio. Al crearla, Vendely le pone
 * categorías que ese rubro necesita, una descripción para la portada y un anuncio con la promesa
 * típica del rubro (una pastelería avisa la anticipación; una tienda de tecnología, la garantía).
 *
 * Todo se puede cambiar después. "otro" no trae nada: la tienda empieza en blanco.
 */
public record KitRubro(List<String> categorias, String descripcion, String anuncio) {

    private static final Map<String, KitRubro> KITS = Map.of(
            "moda", new KitRubro(List.of("Polos", "Pantalones", "Vestidos", "Accesorios"),
                    "Ropa y accesorios para todos los días. Elige tu talla y color, y te lo enviamos.",
                    "Cambios de talla dentro de los 7 días"),
            "tecnologia", new KitRubro(List.of("Celulares", "Accesorios", "Audio", "Cómputo"),
                    "Equipos y accesorios originales, con garantía y boleta.",
                    "Garantía de 12 meses en todos los equipos"),
            "comida", new KitRubro(List.of("Tortas", "Postres", "Bocaditos", "Pedidos especiales"),
                    "Hecho en casa, con insumos frescos. Haz tu pedido con anticipación.",
                    "Pedidos con 24 horas de anticipación"),
            "belleza", new KitRubro(List.of("Maquillaje", "Cuidado de la piel", "Cabello", "Fragancias"),
                    "Productos de belleza originales para tu rutina diaria.",
                    "Productos originales con registro sanitario"),
            "hogar", new KitRubro(List.of("Cocina", "Decoración", "Dormitorio", "Organización"),
                    "Todo para que tu casa se vea y se sienta mejor.",
                    "Envíos a todo el país"),
            "deporte", new KitRubro(List.of("Ropa deportiva", "Calzado", "Suplementos", "Accesorios"),
                    "Ropa, calzado y suplementos para entrenar mejor.",
                    "Asesoría gratis por WhatsApp"),
            "mascotas", new KitRubro(List.of("Alimento", "Juguetes", "Higiene", "Accesorios"),
                    "Alimento, juguetes y cuidado para tu mascota.",
                    "Delivery el mismo día en Lima"),
            "otro", new KitRubro(List.of(), null, null));

    /** El kit de ese rubro, o el de "otro" (vacío) si no viene. */
    public static KitRubro de(String rubro) {
        return rubro == null ? KITS.get("otro") : KITS.getOrDefault(rubro, KITS.get("otro"));
    }

    /** "Cuidado de la piel" → "cuidado-de-la-piel", "Cómputo" → "computo" (CHECK ck_categorias_slug). */
    public static String slug(String nombre) {
        String sinTildes = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
