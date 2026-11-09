import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Portero de las rutas privadas.
 * - Sin sesión: manda al login.
 * - Con sesión pero sin el rol permitido: manda a "Acceso denegado".
 * - Si todo está bien: muestra la página (Outlet).
 */
export default function RutaProtegida({ roles }) {
  const { usuario, cargando } = useAuth();

  if (cargando) {
    return <p className="p-8 text-slate-500">Cargando...</p>;
  }
  if (!usuario) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(usuario.rol)) {
    return <Navigate to="/acceso-denegado" replace />;
  }
  return <Outlet />;
}