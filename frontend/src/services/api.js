import axios from "axios";

// Dirección del backend. En Docker llega por VITE_API_BASE_URL;
// en desarrollo local usa el backend de NetBeans.
const URL_API =
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

// Después de cada respuesta: si el backend responde 401 (token vencido o
// inválido), se borra el token y se avisa a la app para que vuelva al login.
// El login no cuenta: ahí un 401 solo significa contraseña incorrecta.
api.interceptors.response.use(
  (respuesta) => respuesta,
  (error) => {
    const esLogin = error.config?.url === "/auth/login";
    if (error.response?.status === 401 && !esLogin) {
      tokenStorage.clear();
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA));
    }
    return Promise.reject(error);
  },
);

/** Texto para mostrar al usuario cuando una petición falla. */
export function mensajeDeError(error) {
  return error.response?.data?.mensaje ?? "No se pudo conectar con el servidor";
}
