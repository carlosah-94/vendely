import { api } from './api';

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
