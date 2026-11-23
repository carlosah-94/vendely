import { api } from './api';

/** Quita la doble autenticación de una cuenta (cuando la persona perdió el teléfono). */
export async function quitarDobleFactor(idLogin) {
  await api.delete(`/admin/cuentas/${idLogin}/doble-factor`);
}

/** Llamadas al backend del panel del superadmin (/api/admin/**, solo rol SUPERADMIN). */

export async function obtenerResumen() {
  return (await api.get('/admin/resumen')).data;
}

export async function listarTiendasAdmin() {
  return (await api.get('/admin/tiendas')).data;
}

/** activa = false suspende la tienda; true la reactiva. */
export async function cambiarEstadoTienda(idTienda, activa) {
  await api.patch(`/admin/tiendas/${idTienda}/estado`, { activa });
}

export async function listarCuentasAdmin() {
  return (await api.get('/admin/cuentas')).data;
}

/** activa = false desactiva la cuenta (no puede iniciar sesión); true la vuelve a activar. */
export async function cambiarEstadoCuenta(idLogin, activa) {
  await api.patch(`/admin/cuentas/${idLogin}/estado`, { activa });
}

/** Planes de Vendely (Gratis, Básico, Pro) con su precio, límites y beneficios. */
export async function listarPlanesAdmin() {
  return (await api.get('/admin/planes')).data;
}

/** datos = { precioUsd, maxProductos (null = ilimitados), comision, permitePersonalizar, ... } */
export async function editarPlanAdmin(idPlan, datos) {
  return (await api.put(`/admin/planes/${idPlan}`, datos)).data;
}
