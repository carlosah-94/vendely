import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Se muestra cuando la cuenta no tiene permiso: porque la página pide otro rol
 * o porque el backend respondió 403.
 */
export default function AccesoDenegado() {
  const { usuario, cerrarSesion } = useAuth();
  const navigate = useNavigate();

  // Permite entrar con otra cuenta que sí tenga permiso.
  function salir() {
    cerrarSesion();
    navigate('/login', { replace: true });
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-100 px-4">
      <div className="max-w-md text-center space-y-3">
        <p className="text-5xl font-bold text-violet-600">403</p>
        <h1 className="text-2xl font-bold text-slate-800">Acceso denegado</h1>
        <p className="text-slate-600">Tu cuenta no tiene permiso para ver esta página.</p>
        <div className="flex justify-center gap-3">
          <Link to="/" className="inline-block rounded-md bg-slate-800 px-4 py-2 text-white hover:bg-slate-700">
            Volver al inicio
          </Link>
          {usuario && (
            <button
              onClick={salir}
              className="rounded-md border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-200"
            >
              Cerrar sesión
            </button>
          )}
        </div>
      </div>
    </main>
  );
}
