import { api } from './api';

/** Inventario del panel: stock por variante, recibir mercadería y ajustar. */
const base = (idTienda) => `/panel/tiendas/${idTienda}/inventario`;

export async function listarInventario(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** cambio = { recibir } | { cantidad, stockMinimo } */
export async function actualizarInventario(idTienda, idVariante, cambio) {
  return (await api.patch(`${base(idTienda)}/${idVariante}`, cambio)).data;
}
