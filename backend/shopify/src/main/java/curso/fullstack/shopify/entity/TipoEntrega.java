package curso.fullstack.shopify.entity;

/**
 * Cómo recibe el cliente su pedido (pedidos.tipo_entrega, V18), como en Shopify:
 * "Envío", "Entrega local" y "Retiro en tienda".
 */
public enum TipoEntrega {
    ENVIO((short) 0, "Envío por courier"),
    DELIVERY((short) 1, "Delivery de la tienda"),
    RECOJO((short) 2, "Recojo en tienda");

    private final short codigo;
    private final String nombre;

    TipoEntrega(short codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public short codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public static TipoEntrega de(Short codigo) {
        if (codigo == null) return ENVIO;
        for (TipoEntrega t : values()) {
            if (t.codigo == codigo) return t;
        }
        return ENVIO;
    }

    /** "DELIVERY" → DELIVERY; null o vacío → ENVIO. */
    public static TipoEntrega de(String nombre) {
        return nombre == null || nombre.isBlank() ? ENVIO : valueOf(nombre);
    }
}
