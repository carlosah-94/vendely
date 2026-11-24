/**
 * Métodos de pago de la tienda pública.
 *
 * La lista que ofrece cada tienda la decide el backend (tienda.metodosPago, de
 * GET /api/public/tiendas/{subdominio}): solo vienen las pasarelas configuradas en el servidor
 * que el plan de la tienda incluye. Aquí solo están los textos para mostrarlas.
 * Las claves son los nombres del enum MetodoPago del backend: no se cambian por separado.
 */
export const METODOS_PAGO = {
  PAYPAL: {
    nombre: 'PayPal',
    detalle: () => 'Tarjeta o saldo PayPal. En este modo de prueba el cobro se procesa en dólares (USD).',
  },
  MERCADO_PAGO: {
    nombre: 'Mercado Pago',
    detalle: (moneda) => `Tarjeta u otros medios de Mercado Pago. El cobro se procesa en ${moneda ?? 'la moneda de la tienda'}.`,
  },
};

/** Los métodos de la tienda que este frontend sabe mostrar, en el orden del backend. */
export function metodosDeTienda(tienda) {
  return (tienda?.metodosPago ?? []).filter((m) => METODOS_PAGO[m]);
}

/** "PayPal", "PayPal o Mercado Pago": para textos como "Paga con ...". */
export function textoMetodos(metodos) {
  return metodos.map((m) => METODOS_PAGO[m].nombre).join(' o ');
}

/**
 * De qué pasarela volvió el cliente, según lo que agrega cada una a la URL de regreso:
 * PayPal agrega token (id de la orden); Mercado Pago, payment_id o collection_id.
 * Sirve para que "Reintentar pago" vuelva a la misma pasarela.
 */
export function metodoDelRegreso(params) {
  if (params.get('token')) return 'PAYPAL';
  if (params.get('payment_id') || params.get('collection_id')) return 'MERCADO_PAGO';
  return undefined;
}
