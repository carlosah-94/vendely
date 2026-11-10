import { api } from './api';

/**
 * Llamadas al backend para el CRUD de clientes del panel (tarea #17).
 * Cada función devuelve directamente los datos (response.data) para que las
 * pantallas no tengan que repetir ".data". Los errores se dejan pasar: la
 * pantalla decide cómo mostrarlos (junto al campo o como mensaje).
 */

const base = (idTienda) => `/panel/tiendas/${idTienda}/clientes`;

export async function listarClientes(idTienda) {
  return (await api.get(base(idTienda))).data;
}

export async function verCliente(idTienda, idCliente) {
  return (await api.get(`${base(idTienda)}/${idCliente}`)).data;
}

export async function crearCliente(idTienda, datos) {
  return (await api.post(base(idTienda), datos)).data;
}

export async function actualizarCliente(idTienda, idCliente, datos) {
  return (await api.put(`${base(idTienda)}/${idCliente}`, datos)).data;
}

export async function eliminarCliente(idTienda, idCliente) {
  await api.delete(`${base(idTienda)}/${idCliente}`);
}

/**
 * Buscador de distritos para el formulario (endpoint público, sin token).
 * Se busca en el país de la tienda porque la tienda vende dentro de su país.
 * Ejemplo: buscarDistritos('PE', 'mira') → [{ idDistrito, nombre: 'Miraflores', provincia: 'Lima' }, ...]
 */
export async function buscarDistritos(idPais, texto) {
  const { data } = await api.get(`/public/paises/${idPais}/distritos`, { params: { buscar: texto } });
  return data;
}
