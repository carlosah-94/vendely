import { api } from './api';

/** Códigos de descuento de la tienda (panel → Descuentos → Códigos). Requiere el permiso de productos. */
const base = (idTienda) => `/panel/tiendas/${idTienda}/cupones`;

export async function listarCupones(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/**
 * datos = { codigo, tipoDescuento, valor, compraMinima, usosMaximos, unaVezPorCliente,
 *           fechaInicio, fechaFin, activo }
 * tipoDescuento: PORCENTAJE · MONTO_FIJO. compraMinima y usosMaximos null = sin límite.
 */
export async function crearCupon(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function actualizarCupon(idTienda, idCupon, datos) {
  return (await api.put(`${base(idTienda)}/${idCupon}`, datos)).data;
}

/** activo = false pausa el código; true lo reactiva. */
export async function cambiarEstadoCupon(idTienda, idCupon, activo) {
  return (await api.patch(`${base(idTienda)}/${idCupon}/estado`, { activa: activo })).data;
}

/** Solo se puede borrar un código que ningún pedido usó. */
export async function eliminarCupon(idTienda, idCupon) {
  await api.delete(`${base(idTienda)}/${idCupon}`);
}
