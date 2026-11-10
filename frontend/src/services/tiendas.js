import { api } from './api';

/** Tiendas del dueño que inició sesión (las usa el selector de tienda del panel). */
export async function listarMisTiendas() {
  return (await api.get('/panel/tiendas')).data;
}
