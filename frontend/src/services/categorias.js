import { api } from './api';

/** Categorías de productos: CRUD del panel y lista pública para los filtros de la tienda. */
const base = (idTienda) => `/panel/tiendas/${idTienda}/categorias`;

export async function listarCategorias(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** datos = { nombre, descripcion, activa } */
export async function crearCategoria(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function actualizarCategoria(idTienda, idCategoria, datos) {
  return (await api.put(`${base(idTienda)}/${idCategoria}`, datos)).data;
}

export async function eliminarCategoria(idTienda, idCategoria) {
  await api.delete(`${base(idTienda)}/${idCategoria}`);
}

export async function categoriasPublicas(subdominio) {
  return (await api.get(`/public/tiendas/${subdominio}/categorias`)).data;
}
