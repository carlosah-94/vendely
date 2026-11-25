/**
 * Formas de entrega de un pedido (V18): cómo se llaman y qué significa "enviado" en cada una.
 * En recojo no "sale" nada: el pedido queda listo en el local.
 */
const ENTREGA = {
  ENVIO: { nombre: 'Envío por courier', enviado: 'Enviado', accion: 'Marcar como enviado', fecha: 'Enviado el' },
  DELIVERY: { nombre: 'Delivery de la tienda', enviado: 'En camino', accion: 'Marcar en camino', fecha: 'Salió el' },
  RECOJO: { nombre: 'Recojo en tienda', enviado: 'Listo para recoger', accion: 'Marcar listo para recoger', fecha: 'Listo desde' },
};

export function entregaDe(tipo) {
  return ENTREGA[tipo] ?? ENTREGA.ENVIO;
}
