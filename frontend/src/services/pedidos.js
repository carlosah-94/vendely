import { api, descargarPdf } from './api';

/**
 * Acciones del panel sobre un pedido (issue #3): seguimiento del envío, comprobante y correos.
 * Devuelven los datos (response.data) y dejan pasar los errores para que la pantalla los muestre.
 *
 * Las tres primeras son PATCH porque cambian una parte del pedido, no lo reemplazan, y el
 * backend responde con el pedido ya actualizado: la pantalla no tiene que recargarlo.
 */

/** Marca el pedido como enviado. Pide empresa y número de seguimiento. */
export async function marcarEnviado(idTienda, idPedido, datos) {
  return (await api.patch(`/panel/tiendas/${idTienda}/pedidos/${idPedido}/envio`, datos)).data;
}

/** Marca como entregado un pedido que ya se envió. */
export async function marcarEntregado(idTienda, idPedido) {
  return (await api.patch(`/panel/tiendas/${idTienda}/pedidos/${idPedido}/entrega`)).data;
}

/** Cancela un pedido que todavía no se envió: devuelve el stock y reembolsa el pago. */
export async function cancelarPedido(idTienda, idPedido) {
  return (await api.patch(`/panel/tiendas/${idTienda}/pedidos/${idPedido}/cancelacion`)).data;
}

/** Los correos que el sistema envió por este pedido, con su estado. */
export async function listarNotificaciones(idTienda, idPedido) {
  return (await api.get(`/panel/tiendas/${idTienda}/pedidos/${idPedido}/notificaciones`)).data;
}

/** Descarga el comprobante de la venta (PDF). */
export async function descargarComprobanteVenta(idTienda, idPedido, numeroPedido) {
  return descargarPdf(`/panel/tiendas/${idTienda}/pedidos/${idPedido}/comprobante`,
    `comprobante-${numeroPedido ?? idPedido}.pdf`);
}
