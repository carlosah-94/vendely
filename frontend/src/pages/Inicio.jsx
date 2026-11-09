import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Pantalla temporal: muestra quién inició sesión. Se reemplaza por el menú en el siguiente paso. */
export default function Inicio() {
  const { usuario, cargando, cerrarSesion } = useAuth();

  if (cargando) {
    return <p className="p-8 text-slate-500">Cargando...</p>;
  }
  if (!usuario) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main className="p-8 space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">Hola, {usuario.nombres}</h1>
      <p className="text-slate-600">
        Rol: <strong>{usuario.rol}</strong>
        {usuario.idTienda && <> · Tienda: {usuario.idTienda}</>}
      </p>
      <button
        onClick={cerrarSesion}
        className="rounded-md bg-slate-800 px-4 py-2 text-white hover:bg-slate-700"
      >
        Cerrar sesión
      </button>
    </main>
  );
}