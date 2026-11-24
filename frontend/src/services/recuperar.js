import { api } from './api';

/**
 * "¿Olvidaste tu contraseña?". Sin subdominio, cuentas del panel; con subdominio, el cliente de
 * esa tienda. La respuesta es la misma exista o no la cuenta (no se revela qué correos tienen una).
 * Devuelve { requerido, correo, venceEnSegundos, reenviarEnSegundos }.
 */
export async function pedirCodigoRecuperacion(correo, subdominio) {
  return (await api.post('/auth/recuperar/codigo', { correo, subdominio: subdominio ?? null })).data;
}

/** Con el código del correo, guarda la contraseña nueva (y cierra las sesiones abiertas en otros equipos). */
export async function restablecerContrasena({ correo, subdominio, codigo, contrasena }) {
  await api.post('/auth/recuperar/restablecer', { correo, subdominio: subdominio ?? null, codigo, contrasena });
}
