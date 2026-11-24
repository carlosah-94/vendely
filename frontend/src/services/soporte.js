import { api } from './api';

/** Acceso de soporte de Vendely a la tienda (solo el dueño): /api/panel/tiendas/{idTienda}/soporte */
const base = (idTienda) => `/panel/tiendas/${idTienda}/soporte`;

/** { activoHasta (null = soporte no puede entrar), registro: [{ accion, metodo, ruta, fecha }] } */
export async function obtenerSoporte(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** Deja entrar a soporte por "dias" días (1 a 7) desde ahora. */
export async function permitirSoporte(idTienda, dias) {
  return (await api.post(base(idTienda), { dias })).data;
}

/** Quita el acceso al instante. */
export async function quitarSoporte(idTienda) {
  return (await api.delete(base(idTienda))).data;
}
