import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Página de entrada: lleva a cada cuenta a su propio panel según su rol. */
export default function Inicio() {
  const { usuario, cargando } = useAuth();

  if (cargando) {
    return <p className="p-8 text-slate-500">Cargando...</p>;
  }
  if (!usuario) {
    return <Navigate to="/login" replace />;
  }
  if (usuario.rol === 'SUPERADMIN') {
    return <Navigate to="/admin/tiendas" replace />;
  }
  if (usuario.rol === 'ADMIN_TIENDA') {
    return <Navigate to="/panel/tiendas" replace />;
  }
  return <Navigate to="/acceso-denegado" replace />;
}