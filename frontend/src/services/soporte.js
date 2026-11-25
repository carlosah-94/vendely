import { api } from './api';

/** Acceso de soporte de Vendely a la tienda (solo el dueño): /api/panel/tiendas/{idTienda}/soporte */
const base = (idTienda) => `/panel/tiendas/${idTienda}/soporte`;

/** { activoHasta (null = soporte no puede entrar), registro: [{ accion, metodo, ruta, fecha }] } */
export async function obtenerSoporte(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** Deja entrar a soporte por "dias" días (1 a 7) desde ahora. "mensaje" (opcional): qué necesita el dueño. */
export async function permitirSoporte(idTienda, dias, mensaje) {
  return (await api.post(base(idTienda), { dias, mensaje: mensaje || null })).data;
}

/** La conversación con soporte: [{ idMensaje, deSoporte, texto, fecha }], de la más antigua a la más nueva. */
export async function obtenerMensajes(idTienda) {
  return (await api.get(`${base(idTienda)}/mensajes`)).data;
}

/** Escribe en la conversación (el dueño o soporte, con el acceso activo). Devuelve la conversación completa. */
export async function enviarMensaje(idTienda, mensaje) {
  return (await api.post(`${base(idTienda)}/mensajes`, { mensaje })).data;
}

/** Quita el acceso al instante. */
export async function quitarSoporte(idTienda) {
  return (await api.delete(base(idTienda))).data;
}
