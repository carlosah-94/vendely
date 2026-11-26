import { api } from './api';

/** Acceso de soporte de Vendely a la tienda (solo el dueño): /api/panel/tiendas/{idTienda}/soporte */
const base = (idTienda) => `/panel/tiendas/${idTienda}/soporte`;

/** { activoHasta (null = soporte no puede entrar), registro: [{ accion, metodo, ruta, fecha }] } */
export async function obtenerSoporte(idTienda) {
  return (await api.get(base(idTienda))).data;
}

/** Deja entrar a soporte por "dias" días (1 a 7) desde ahora. "mensaje" (opcional): qué necesita el dueño. */
export async function permitirSoporte(idTienda, dias, mensaje) {
  return (await api.post(base(idTienda), { dias, mensaje: mensaje || null })).data;
}

/** La conversación con soporte: [{ idMensaje, deSoporte, texto, fecha }], de la más antigua a la más nueva. */
export async function obtenerMensajes(idTienda) {
  return (await api.get(`${base(idTienda)}/mensajes`)).data;
}

/** Escribe en la conversación (el dueño o soporte, con el acceso activo). Devuelve la conversación completa. */
export async function enviarMensaje(idTienda, mensaje) {
  return (await api.post(`${base(idTienda)}/mensajes`, { mensaje })).data;
}

/**
 * Bandeja del equipo de Vendely (solo superadmin): por tienda con acceso,
 * [{ idTienda, sinResponder, ultimoTexto, ultimaFecha, ultimoDeSoporte }].
 */
export async function obtenerBandeja() {
  return (await api.get('/admin/soporte/bandeja')).data;
}

/** Lo que se puede adjuntar (lo mismo que revisa ArchivoSoporte en el backend). */
export const EXTENSIONES_ADJUNTO = ['xlsx', 'xls', 'csv', 'pdf', 'docx', 'doc', 'txt', 'jpg', 'jpeg', 'png', 'webp', 'zip'];
export const MAXIMO_ADJUNTO = 10 * 1024 * 1024;   // 10 MB

/** Mensaje para el usuario si el archivo no se puede adjuntar; null si está bien. */
export function revisarAdjunto(archivo) {
  const extension = archivo.name.includes('.') ? archivo.name.split('.').pop().toLowerCase() : '';
  if (!EXTENSIONES_ADJUNTO.includes(extension)) {
    return 'Puedes adjuntar Excel, CSV, PDF, Word, TXT, fotos (JPG, PNG, WEBP) o ZIP';
  }
  if (archivo.size > MAXIMO_ADJUNTO) {
    return 'El archivo no puede pesar más de 10 MB';
  }
  return null;
}

/**
 * Escribe con un archivo adjunto (el texto es opcional). alProgresar(0..100) mientras sube.
 * Devuelve la conversación completa.
 */
export async function enviarAdjunto(idTienda, archivo, mensaje, alProgresar) {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  if (mensaje) cuerpo.append('mensaje', mensaje);
  // Sin Content-Type a mano: el navegador lo pone con el "boundary" que necesita el multipart.
  return (await api.post(`${base(idTienda)}/mensajes/adjunto`, cuerpo, {
    onUploadProgress: (e) => e.total && alProgresar?.(Math.round((e.loaded / e.total) * 100)),
  })).data;
}

/** Quita el acceso al instante. */
export async function quitarSoporte(idTienda) {
  return (await api.delete(base(idTienda))).data;
}
