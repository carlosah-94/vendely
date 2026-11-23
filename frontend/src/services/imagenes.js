import { api } from './api';

/**
 * Fotos de la tienda (issue #4): se suben a Cloudinary apenas se eligen, antes de guardar el
 * producto, y lo que se guarda después en el producto es el enlace que devuelve esta llamada.
 *
 * Sin CLOUDINARY_URL en el backend, subir responde 503 y la pantalla lo dice: pegar un enlace
 * sigue funcionando.
 */

const TIPOS = ['image/jpeg', 'image/png', 'image/webp'];
const MAXIMO_BYTES = 2 * 1024 * 1024;

/** Revisa el archivo antes de mandarlo. Devuelve el error, o null si está bien. */
export function revisarArchivo(archivo) {
  if (!archivo) {
    return 'No se eligió ninguna foto';
  }
  if (!TIPOS.includes(archivo.type)) {
    return 'La foto tiene que ser JPG, PNG o WEBP';
  }
  if (archivo.size > MAXIMO_BYTES) {
    return 'La foto no puede pesar más de 2 MB';
  }
  return null;
}

/** Sube una foto y devuelve { url, publicId }. */
export async function subirImagen(idTienda, archivo) {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  // Sin Content-Type a mano: el navegador lo pone con el "boundary" que necesita el multipart.
  return (await api.post(`/panel/tiendas/${idTienda}/imagenes`, cuerpo)).data;
}

/** Borra una foto que se subió pero no se llegó a guardar en ningún producto. */
export async function borrarImagen(idTienda, publicId) {
  await api.delete(`/panel/tiendas/${idTienda}/imagenes`, { params: { publicId } });
}
