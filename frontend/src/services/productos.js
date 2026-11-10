import { api } from './api';

/**
 * Llamadas al backend para el CRUD de productos del panel (tarea #18).
 * Un producto simple = producto + una variante + su precio + su stock; el backend
 * guarda todo junto, así que aquí se envía y se recibe como un solo objeto:
 * { nombre, descripcion, marca, precio, stock, activo }.
 */

const base = (idTienda) => `/panel/tiendas/${idTienda}/productos`;

export async function listarProductos(idTienda) {
  return (await api.get(base(idTienda))).data;
}

export async function crearProducto(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function actualizarProducto(idTienda, idProducto, datos) {
  return (await api.put(`${base(idTienda)}/${idProducto}`, datos)).data;
}

export async function eliminarProducto(idTienda, idProducto) {
  await api.delete(`${base(idTienda)}/${idProducto}`);
}
