import { api } from './api';

/** Envíos: tarifas del panel, cotización del cliente y países. */

// ---- Panel del dueño
const base = (idTienda) => `/panel/tiendas/${idTienda}/envios`;

export async function obtenerEnvios(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** tarifa = { costo, envioGratisDesde, diasEntregaMin, diasEntregaMax, activa } */
export async function guardarTarifaNacional(idTienda, idDivision, tarifa) {
  return (await api.put(`${base(idTienda)}/nacional/${idDivision}`, tarifa)).data;
}

export async function guardarTarifaInternacional(idTienda, idPais, tarifa) {
  return (await api.put(`${base(idTienda)}/internacional/${idPais}`, tarifa)).data;
}

export async function quitarTarifaInternacional(idTienda, idPais) {
  return (await api.delete(`${base(idTienda)}/internacional/${idPais}`)).data;
}

// ---- Público
export async function listarPaises() {
  return (await api.get('/public/paises')).data;
}

export async function paisesDeEnvio(subdominio) {
  return (await api.get(`/public/tiendas/${subdominio}/envios`)).data;
}

// ---- Cliente con sesión
export async function cotizarEnvio(idDireccion, subtotal) {
  return (await api.get(`/cliente/direcciones/${idDireccion}/envio`, { params: { subtotal } })).data;
}

// ---- Delivery de la tienda y recojo en el local (V18)
/** zona = { costo, envioGratisDesde, activa } */
export async function guardarZonaDelivery(idTienda, idDivision, zona) {
  return (await api.put(`${base(idTienda)}/delivery/${idDivision}`, zona)).data;
}

export async function quitarZonaDelivery(idTienda, idDivision) {
  return (await api.delete(`${base(idTienda)}/delivery/${idDivision}`)).data;
}

/** recojo = { activo, direccion, horario } */
export async function guardarRecojo(idTienda, recojo) {
  return (await api.put(`${base(idTienda)}/recojo`, recojo)).data;
}
