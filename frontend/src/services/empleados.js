import { api } from './api';

/** Empleados de una tienda (solo el dueño): /api/panel/tiendas/{idTienda}/empleados */
const base = (idTienda) => `/panel/tiendas/${idTienda}/empleados`;

export async function listarEmpleados(idTienda) {
  return (await api.get(base(idTienda))).data;
}

export async function crearEmpleado(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function editarEmpleado(idTienda, idLogin, datos) {
  return (await api.put(`${base(idTienda)}/${idLogin}`, datos)).data;
}

export async function quitarEmpleado(idTienda, idLogin) {
  await api.delete(`${base(idTienda)}/${idLogin}`);
}
