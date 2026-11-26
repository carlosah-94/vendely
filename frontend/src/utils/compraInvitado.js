/**
 * Claves de la compra como invitado (sin cuenta). El backend entrega una clave secreta por cada
 * dirección y cada pedido que crea un invitado; sin ella no deja ver, cotizar ni pagar.
 * Se guardan en este navegador (localStorage) con la clave vendely_invitado_<subdominio>:
 *   { d<idDireccion>: clave, p<idPedido>: clave }
 * Si se pierden (otro navegador), el enlace del correo de confirmación trae la clave del pedido.
 */

function nombre(subdominio) {
  return `vendely_invitado_${subdominio}`;
}

function leer(subdominio) {
  try {
    const datos = JSON.parse(localStorage.getItem(nombre(subdominio)) ?? '{}');
    return datos && typeof datos === 'object' ? datos : {};
  } catch {
    return {};
  }
}

function guardar(subdominio, llave, clave) {
  try {
    const datos = leer(subdominio);
    datos[llave] = clave;
    localStorage.setItem(nombre(subdominio), JSON.stringify(datos));
  } catch {
    // Sin almacenamiento: la clave sigue en memoria mientras dure la página y llega por correo.
  }
}

export function guardarClaveDireccion(subdominio, idDireccion, clave) {
  guardar(subdominio, `d${idDireccion}`, clave);
}

export function claveDireccion(subdominio, idDireccion) {
  return leer(subdominio)[`d${idDireccion}`] ?? null;
}

export function guardarClavePedido(subdominio, idPedido, clave) {
  guardar(subdominio, `p${idPedido}`, clave);
}

export function clavePedido(subdominio, idPedido) {
  return leer(subdominio)[`p${idPedido}`] ?? null;
}

/** Correo válido (el mismo criterio básico que el backend: algo@algo.algo). */
export function correoValido(correo) {
  return /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]{2,}$/.test((correo ?? '').trim()) && correo.trim().length <= 150;
}
