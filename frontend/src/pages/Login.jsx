import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { mensajeDeError } from '../services/api';

/**
 * Inicio de sesión del panel de Vendely (dueños de tienda y superadmin).
 * No se elige rol: el backend responde con el rol de la cuenta.
 * Los clientes no entran aquí, sino desde la tienda en la que compran.
 */
export default function Login() {
  const { usuario, iniciarSesion } = useAuth();
  const navigate = useNavigate();

  const [correo, setCorreo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  // Si ya inició sesión, no tiene sentido mostrar el formulario.
  if (usuario) {
    return <Navigate to="/" replace />;
  }

  async function enviar(evento) {
    evento.preventDefault();
    setError('');
    setEnviando(true);
    try {
      await iniciarSesion({ correo, contrasena });
      navigate('/', { replace: true });
    } catch (e) {
      setError(mensajeDeError(e));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-100 px-4">
      <form onSubmit={enviar} className="w-full max-w-sm bg-white rounded-xl shadow p-8 space-y-4">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-slate-800">Vendely</h1>
          <p className="text-sm text-slate-500">Inicia sesión para administrar tu tienda</p>
        </div>

        {error && (
          <p className="rounded-md bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <label className="block">
          <span className="text-sm font-medium text-slate-700">Correo</span>
          <input
            type="email"
            required
            autoComplete="username"
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-violet-500"
          />
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">Contraseña</span>
          <input
            type="password"
            required
            autoComplete="current-password"
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-violet-500"
          />
        </label>

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-md bg-violet-600 py-2 font-semibold text-white hover:bg-violet-700 disabled:opacity-60"
        >
          {enviando ? 'Ingresando...' : 'Iniciar sesión'}
        </button>
      </form>
    </main>
  );
}