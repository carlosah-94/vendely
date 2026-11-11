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
    <main className="flex min-h-screen items-center justify-center bg-[#0a1314] px-4">
      <div className="w-full max-w-md space-y-3 rounded-2xl bg-white p-8 text-center">
        <p className="text-sm font-semibold text-[#8e1f0b]">Error 403</p>
        <h1 className="text-2xl font-semibold text-[#101010]">Acceso denegado</h1>
        <p className="text-[#4a4a4a]">Tu cuenta no tiene permiso para ver esta página.</p>
        <div className="flex justify-center gap-3 pt-2">
          <Link to="/inicio" className="rounded-lg bg-[#303030] px-4 py-2 font-medium text-white hover:bg-[#1a1a1a]">
            Ir a mi inicio
          </Link>
          {usuario && (
            <button onClick={salir} className="rounded-lg border border-[#ddd] px-4 py-2 font-medium text-[#303030] hover:bg-[#f7f7f7]">
              Cerrar sesión
            </button>
          )}
        </div>
      </div>
    </main>
  );
}
