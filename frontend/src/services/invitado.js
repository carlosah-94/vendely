import { api, descargarPdf } from './api';
import { clavePedido, claveDireccion, guardarClaveDireccion, guardarClavePedido } from '../utils/compraInvitado';

/**
 * Compra como invitado (sin cuenta). Rutas públicas de la tienda; cada dirección y pedido viaja
 * con su clave secreta en la cabecera X-Clave-Invitado (el backend responde 404 sin ella).
 */
const CABECERA = 'X-Clave-Invitado';

function base(subdominio) {
  return `/public/tiendas/${subdominio}/invitado`;
}

function conClave(clave) {
  return { headers: { [CABECERA]: clave ?? '' } };
}

/** Crea la dirección del invitado: { destinatario, telefono, direccion: {...} }. Guarda su clave. */
export async function crearDireccionInvitado(subdominio, datos) {
  const { datos: direccion, clave } = (await api.post(`${base(subdominio)}/direcciones`, datos)).data;
  guardarClaveDireccion(subdominio, direccion.idDireccion, clave);
  return direccion;
}

export async function cotizarEnvioInvitado(subdominio, idDireccion, subtotal) {
  return (await api.get(`${base(subdominio)}/direcciones/${idDireccion}/envio`, {
    params: { subtotal }, ...conClave(claveDireccion(subdominio, idDireccion)),
  })).data;
}

export async function validarCuponInvitado(subdominio, codigo, subtotal) {
  return (await api.post(`${base(subdominio)}/cupones/validar`, { codigo, subtotal })).data;
}

/** Crea el pedido del invitado (correo + datos del pedido). Guarda su clave y devuelve el pedido. */
export async function crearPedidoInvitado(subdominio, datos) {
  const cuerpo = { ...datos, claveDireccion: claveDireccion(subdominio, datos.idDireccionEnvio) };
  const { datos: pedido, clave } = (await api.post(`${base(subdominio)}/pedidos`, cuerpo)).data;
  guardarClavePedido(subdominio, pedido.idPedido, clave);
  return pedido;
}

/** clave: la del enlace del correo; si no viene, la guardada en este navegador. */
export async function verPedidoInvitado(subdominio, idPedido, clave = null) {
  return (await api.get(`${base(subdominio)}/pedidos/${idPedido}`,
    conClave(clave ?? clavePedido(subdominio, idPedido)))).data;
}

export async function iniciarPagoInvitado(subdominio, idPedido, metodo) {
  return (await api.post(`${base(subdominio)}/pedidos/${idPedido}/pago`, metodo ? { metodo } : {},
    conClave(clavePedido(subdominio, idPedido)))).data;
}

export async function confirmarPagoInvitado(subdominio, idPedido, paymentId) {
  return (await api.post(`${base(subdominio)}/pedidos/${idPedido}/pago/confirmar`, { paymentId },
    conClave(clavePedido(subdominio, idPedido)))).data;
}

export async function descargarComprobanteInvitado(subdominio, idPedido, clave, numeroPedido, numeroComprobante) {
  return descargarPdf(`${base(subdominio)}/pedidos/${idPedido}/comprobante`,
    `${numeroComprobante ?? `comprobante-${numeroPedido ?? idPedido}`}.pdf`,
    { [CABECERA]: clave ?? clavePedido(subdominio, idPedido) ?? '' });
}
