package curso.fullstack.shopify.service;

/**
 * Las pasarelas con las que un cliente puede pagar su pedido.
 *
 * El orden importa: el PRIMERO configurado en el servidor es el método base, el único que
 * incluye el plan Gratis (ver MetodoPagoService). PayPal va primero porque es el que la
 * tienda tenía antes de que existiera la elección.
 *
 * El nombre del enum es lo que viaja en el JSON ("PAYPAL", "MERCADO_PAGO"): no se renombra
 * sin cambiar también el frontend (frontend/src/utils/metodosPago.js).
 */
public enum MetodoPago {

    PAYPAL("PayPal"),
    MERCADO_PAGO("Mercado Pago");

    private final String nombre;

    MetodoPago(String nombre) {
        this.nombre = nombre;
    }

    /** El nombre para mostrar en los mensajes: "PayPal", "Mercado Pago". */
    public String nombre() {
        return nombre;
    }
}
