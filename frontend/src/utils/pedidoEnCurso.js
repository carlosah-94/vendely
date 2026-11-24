/**
 * Pedido que el checkout ya creó y mandó a pagar, guardado en sessionStorage (solo esta pestaña)
 * con la clave vendely_pedido_<subdominio>: { idPedido, huella }.
 *
 * Sirve para cuando el cliente vuelve al checkout con "Atrás" desde la pasarela o desde el
 * resultado del pago: así se sabe qué pedido revisar (si ya se pagó no se cobra dos veces; si no,
 * se reintenta el pago del mismo pedido en vez de crear otro).
 * La huella son las líneas del carrito: si el cliente cambia el carrito, el pedido guardado ya no
 * corresponde y se crea uno nuevo.
 */

function clave(subdominio) {
  return `vendely_pedido_${subdominio}`;
}

export function huellaCarrito(items) {
  return items
    .map((i) => `${i.idVariante}x${i.cantidad}`)
    .sort()
    .join(',');
}

export function leerPedidoEnCurso(subdominio) {
  try {
    const datos = JSON.parse(sessionStorage.getItem(clave(subdominio)) ?? 'null');
    return datos?.idPedido ? datos : null;
  } catch {
    return null;
  }
}

export function guardarPedidoEnCurso(subdominio, idPedido, items) {
  try {
    sessionStorage.setItem(clave(subdominio), JSON.stringify({ idPedido, huella: huellaCarrito(items) }));
  } catch {
    // Sin almacenamiento solo se pierde la ayuda al volver atrás; el backend igual evita el doble cobro.
  }
}

export function borrarPedidoEnCurso(subdominio) {
  try {
    sessionStorage.removeItem(clave(subdominio));
  } catch {
    // nada que borrar
  }
}
