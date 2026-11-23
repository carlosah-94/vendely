import { api } from './api';

/**
 * Seguridad de la propia cuenta (issue #4): doble autenticación y cambio de contraseña.
 * Devuelven los datos (response.data) y dejan pasar los errores para que la pantalla los muestre.
 */

/** Paso 1: el backend genera el secreto. Devuelve { secreto, enlace } (enlace = lo que va en el QR). */
export async function iniciarDobleFactor() {
  return (await api.post('/auth/doble-factor/iniciar')).data;
}

/** Paso 2: con el primer código de la app queda activa. Devuelve la cuenta actualizada. */
export async function confirmarDobleFactor(codigo) {
  return (await api.post('/auth/doble-factor/confirmar', { codigo })).data;
}

/** Quitarla pide la contraseña y un código. Devuelve la cuenta actualizada. */
export async function desactivarDobleFactor(contrasena, codigo) {
  return (await api.post('/auth/doble-factor/desactivar', { contrasena, codigo })).data;
}

/**
 * Cambia la contraseña. El backend responde con un token nuevo: los anteriores dejan de
 * servir, así que cualquier otra sesión abierta con esta cuenta se cierra sola.
 */
export async function cambiarContrasena(contrasenaActual, contrasenaNueva) {
  return (await api.post('/auth/cambiar-contrasena', { contrasenaActual, contrasenaNueva })).data;
}
