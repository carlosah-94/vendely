import axios from "axios";

// Dirección del backend. En Docker llega por VITE_API_BASE_URL;
// en desarrollo local usa el backend de NetBeans.
export const URL_API =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

const CLAVE_TOKEN = "vendely_token";

/** Guarda, lee y borra el token JWT del navegador. */
export const tokenStorage = {
  get: () => localStorage.getItem(CLAVE_TOKEN),
  set: (token) => localStorage.setItem(CLAVE_TOKEN, token),
  clear: () => localStorage.removeItem(CLAVE_TOKEN),
};

/** Cliente HTTP de toda la app. Todas las pantallas llaman al backend con "api". */
export const api = axios.create({ baseURL: URL_API });

// Antes de cada petición: si hay token, lo agrega en la cabecera Authorization.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Nombre del aviso que se lanza cuando el backend rechaza el token. */
export const EVENTO_SESION_EXPIRADA = "vendely:sesion-expirada";

/** Nombre del aviso que se lanza cuando el backend niega el permiso. */
export const EVENTO_ACCESO_DENEGADO = "vendely:acceso-denegado";

// Después de cada respuesta:
// - 401 (token vencido o inválido): se borra el token y se avisa a la app
//   para que vuelva al login.
// - 403 (hay sesión, pero sin permiso para esa acción): se avisa a la app
//   para que muestre la página "Acceso denegado".
// Las rutas /auth no cuentan: ahí un 401 o 403 se muestra como mensaje en el
// mismo formulario (por ejemplo, contraseña incorrecta).
// Un 403 por el PLAN ("Mejora tu plan para usar esto") tampoco: la cuenta sí tiene
// permiso, solo que su plan no incluye esa función. Se muestra como mensaje en la pantalla.
// Lo mismo con lo que un EMPLEADO no puede hacer en una tienda donde sí trabaja (por ejemplo,
// "Solo el dueño de la tienda puede hacer esto"): se avisa ahí mismo.
api.interceptors.response.use(
  (respuesta) => respuesta,
  (error) => {
    const esAuth = error.config?.url?.startsWith("/auth/");
    const estado = error.response?.status;
    if (estado === 401 && !esAuth) {
      tokenStorage.clear();
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA));
    }
    const esLimiteDePlan = esErrorDePlan(error);
    if (estado === 403 && !esAuth && !esLimiteDePlan && !esErrorDeEmpleado(error)) {
      window.dispatchEvent(new Event(EVENTO_ACCESO_DENEGADO));
    }
    return Promise.reject(error);
  },
);

/** true si es un empleado que intentó algo que su dueño no le permitió (o que solo hace el dueño). */
export function esErrorDeEmpleado(error) {
  const mensaje = error.response?.data?.mensaje ?? "";
  return (
    error.response?.status === 403 &&
    (mensaje.includes("Tu cuenta de empleado") || mensaje.includes("Solo el dueño") ||
      mensaje.includes("suspendió tu acceso") || mensaje.includes("ya no incluye empleados"))
  );
}

/** true si el backend rechazó la acción porque el plan de la tienda no la incluye. */
export function esErrorDePlan(error) {
  return (
    error.response?.status === 403 &&
    (error.response?.data?.mensaje ?? "").includes("Mejora tu plan")
  );
}

/**
 * Descarga un PDF que devuelve el backend y lo guarda en la computadora.
 *
 * responseType blob: axios no intenta leerlo como JSON. El nombre del archivo lo manda el
 * backend en Content-Disposition; si no llega (por ejemplo, si un proxy lo quita), se usa
 * el que pase la pantalla.
 */
export async function descargarPdf(url, nombrePorDefecto, cabeceras = undefined) {
  const respuesta = await api.get(url, { responseType: "blob", headers: cabeceras });
  const enlace = document.createElement("a");
  const direccion = URL.createObjectURL(respuesta.data);
  enlace.href = direccion;
  enlace.download = nombreDelArchivo(respuesta.headers, nombrePorDefecto);
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(direccion);
}

function nombreDelArchivo(cabeceras, porDefecto) {
  const disposicion = cabeceras?.["content-disposition"] ?? "";
  const encontrado = /filename\*?="?([^";]+)"?/.exec(disposicion);
  return encontrado ? encontrado[1] : porDefecto;
}

/** Texto para mostrar al usuario cuando una petición falla. */
export function mensajeDeError(error) {
  return error.response?.data?.mensaje ?? "No se pudo conectar con el servidor";
}
