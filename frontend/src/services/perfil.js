import { api } from './api';

/** Mi perfil: los datos de la cuenta con sesión (y de su negocio si es dueño). */
export async function obtenerPerfil() {
  return (await api.get('/cuenta/perfil')).data;
}

/**
 * datos = { nombres, primerApellido, segundoApellido, telefono, razonSocial, telefonoComercial }.
 * Devuelve { perfil, usuario }: usuario sirve para actualizar el nombre del panel.
 */
export async function guardarPerfil(datos) {
  return (await api.put('/cuenta/perfil', datos)).data;
}
