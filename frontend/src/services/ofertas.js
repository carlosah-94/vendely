import { api } from './api';

/** Ofertas automáticas de la tienda (panel → Descuentos). Requiere el permiso de productos. */
const base = (idTienda) => `/panel/tiendas/${idTienda}/ofertas`;

export async function listarOfertas(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/**
 * datos = { nombre, motivo, tipoDescuento, valor, fechaInicio, fechaFin, productos, activa }
 * motivo: LIQUIDACION · TEMPORADA · LANZAMIENTO · OTRO; tipoDescuento: PORCENTAJE · MONTO_FIJO.
 * fechaInicio null = empieza ahora; fechaFin null = sin fin.
 */
export async function crearOferta(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function actualizarOferta(idTienda, idOferta, datos) {
  return (await api.put(`${base(idTienda)}/${idOferta}`, datos)).data;
}

/** activa = false pausa la oferta; true la reactiva. */
export async function cambiarEstadoOferta(idTienda, idOferta, activa) {
  return (await api.patch(`${base(idTienda)}/${idOferta}/estado`, { activa })).data;
}

/** Solo se puede borrar una oferta con la que nunca se vendió. */
export async function eliminarOferta(idTienda, idOferta) {
  await api.delete(`${base(idTienda)}/${idOferta}`);
}
