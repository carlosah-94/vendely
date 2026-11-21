import { api } from './api';

/** Plan de la tienda: ver, pagar con PayPal, confirmar al volver e historial de pagos. */
const base = (idTienda) => `/panel/tiendas/${idTienda}/plan`;

export async function verMiPlan(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** Devuelve { idSuscripcion, urlPago }: hay que enviar al dueño a urlPago (PayPal). */
export async function pagarPlan(idTienda, idPlan) {
  return (await api.post(`${base(idTienda)}/pagar`, { idPlan })).data;
}

export async function confirmarPlan(idTienda, idSuscripcion) {
  return (await api.post(`${base(idTienda)}/confirmar`, { idSuscripcion })).data;
}

export async function cancelarPagoPlan(idTienda, idSuscripcion) {
  return (await api.post(`${base(idTienda)}/cancelar`, { idSuscripcion })).data;
}

export async function historialPlan(idTienda) {
  return (await api.get(`${base(idTienda)}/suscripciones`)).data;
}
