import { createContext, useContext, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, EVENTO_ACCESO_DENEGADO, EVENTO_SESION_EXPIRADA, tokenStorage } from '../services/api';

const AuthContext = createContext(null);

/**
 * Guarda quién inició sesión y lo comparte con todas las pantallas.
 * Cualquier componente lo lee con useAuth().
 */
export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [usuario, setUsuario] = useState(null);
  // Solo hay algo que cargar si quedó un token guardado de una visita anterior.
  const [cargando, setCargando] = useState(() => tokenStorage.get() !== null);

  // Al abrir la app: si quedó un token guardado, se pregunta al backend de quién es.
  useEffect(() => {
    if (!tokenStorage.get()) {
      return;
    }
    api.get('/auth/yo')
      .then((respuesta) => setUsuario(respuesta.data))
      .catch(() => tokenStorage.clear())
      .finally(() => setCargando(false));
  }, []);

  // Si el backend rechaza el token en cualquier pantalla, se cierra la sesión.
  useEffect(() => {
    const alExpirar = () => setUsuario(null);
    window.addEventListener(EVENTO_SESION_EXPIRADA, alExpirar);
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, alExpirar);
  }, []);

  // Si el backend responde 403 (sin permiso), se muestra la página de acceso denegado.
  // La sesión sigue abierta: la cuenta es válida, solo que no puede hacer esa acción.
  useEffect(() => {
    const alDenegar = () => navigate('/acceso-denegado', { replace: true });
    window.addEventListener(EVENTO_ACCESO_DENEGADO, alDenegar);
    return () => window.removeEventListener(EVENTO_ACCESO_DENEGADO, alDenegar);
  }, [navigate]);

  /**
   * Sin subdominio inicia sesión en el panel; con subdominio, como cliente de esa tienda.
   * codigo: el de 6 dígitos, solo para las cuentas con doble autenticación. Si hace falta y no
   * viene, el backend responde 401 con campos.codigo = "requerido" y el formulario lo pide.
   */
  async function iniciarSesion({ correo, contrasena, subdominio, codigo }) {
    const cuerpo = { correo, contrasena };
    if (subdominio) {
      cuerpo.subdominio = subdominio;
    }
    if (codigo) {
      cuerpo.codigo = codigo;
    }
    const { data } = await api.post('/auth/login', cuerpo);
    tokenStorage.set(data.token);
    setUsuario(data.usuario);
    return data.usuario;
  }

  /** Crea la cuenta de un dueño de tienda y deja la sesión iniciada con el token que devuelve el backend. */
  async function registrarDueno(datos) {
    const { data } = await api.post('/auth/registro/dueno', datos);
    tokenStorage.set(data.token);
    setUsuario(data.usuario);
    return data.usuario;
  }

  /**
   * Después de cambiar la contraseña el backend entrega un token nuevo (el anterior ya no
   * sirve): se guarda para que esta sesión siga abierta.
   */
  function guardarSesion(respuesta) {
    tokenStorage.set(respuesta.token);
    setUsuario(respuesta.usuario);
  }

  /** Activar o quitar la doble autenticación cambia la cuenta, pero no el token. */
  function actualizarUsuario(datos) {
    setUsuario(datos);
  }

  function cerrarSesion() {
    tokenStorage.clear();
    setUsuario(null);
  }

  return (
    <AuthContext.Provider
      value={{ usuario, cargando, iniciarSesion, registrarDueno, cerrarSesion, guardarSesion, actualizarUsuario }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}