import { createContext, useContext, useEffect, useState } from 'react';
import { api, tokenStorage } from '../services/api';

const AuthContext = createContext(null);

/**
 * Guarda quién inició sesión y lo comparte con todas las pantallas.
 * Cualquier componente lo lee con useAuth().
 */
export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);

  // Al abrir la app: si quedó un token guardado, se pregunta al backend de quién es.
  useEffect(() => {
    if (!tokenStorage.get()) {
      setCargando(false);
      return;
    }
    api.get('/auth/yo')
      .then((respuesta) => setUsuario(respuesta.data))
      .catch(() => tokenStorage.clear())
      .finally(() => setCargando(false));
  }, []);

  /** Sin subdominio inicia sesión en el panel; con subdominio, como cliente de esa tienda. */
  async function iniciarSesion({ correo, contrasena, subdominio }) {
    const cuerpo = { correo, contrasena };
    if (subdominio) {
      cuerpo.subdominio = subdominio;
    }
    const { data } = await api.post('/auth/login', cuerpo);
    tokenStorage.set(data.token);
    setUsuario(data.usuario);
    return data.usuario;
  }

  function cerrarSesion() {
    tokenStorage.clear();
    setUsuario(null);
  }

  return (
    <AuthContext.Provider value={{ usuario, cargando, iniciarSesion, cerrarSesion }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}