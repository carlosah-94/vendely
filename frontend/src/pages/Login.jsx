import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import Campo from '../components/Campo';
import LayoutAcceso from '../components/LayoutAcceso';
import { useAuth } from '../context/AuthContext';
import { mensajeDeError } from '../services/api';
import { estiloInput } from '../utils/estilos';

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
  // Doble autenticación (issue #4): si la cuenta la tiene, después de la contraseña se pide
  // el código. Es el mismo formulario: el correo y la contraseña se reenvían con el código.
  const [pideCodigo, setPideCodigo] = useState(false);
  const [codigo, setCodigo] = useState('');

  // Si ya inició sesión, no tiene sentido mostrar el formulario.
  if (usuario) {
    return <Navigate to="/inicio" replace />;
  }

  async function enviar(evento) {
    evento.preventDefault();
    setError('');
    setEnviando(true);
    try {
      await iniciarSesion({ correo, contrasena, codigo: pideCodigo ? codigo : undefined });
      navigate('/inicio', { replace: true });
    } catch (e) {
      // El backend avisa en campos.codigo, no solo en el texto: así no hay que adivinar.
      const motivo = e.response?.data?.campos?.codigo;
      if (motivo === 'requerido') {
        setPideCodigo(true);
        setError('');
      } else {
        setError(mensajeDeError(e));
      }
      if (motivo === 'incorrecto') {
        setCodigo('');
      }
    } finally {
      setEnviando(false);
    }
  }

  function volverACredenciales() {
    setPideCodigo(false);
    setCodigo('');
    setError('');
  }

  return (
    <LayoutAcceso>
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">Iniciar sesión</h1>
        <p className="text-sm text-slate-500">Accede al panel de tu tienda</p>
      </div>

      <form onSubmit={enviar} className="space-y-4">
        {error && (
          <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>
        )}

        {pideCodigo ? (
          <>
            <p className="text-sm text-slate-600">
              Tu cuenta tiene doble autenticación. Abre tu app (Google Authenticator u otra) y escribe
              el código de 6 dígitos de <strong>Vendely</strong>.
            </p>
            <Campo etiqueta="Código">
              <input
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                required
                maxLength={6}
                pattern="\d{6}"
                value={codigo}
                onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))}
                className={`${estiloInput()} text-center text-lg tracking-[0.4em]`}
              />
            </Campo>
          </>
        ) : (
          <>
            <Campo etiqueta="Correo">
              <input
                type="email"
                required
                autoComplete="username"
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                className={estiloInput()}
              />
            </Campo>

            <Campo etiqueta="Contraseña">
              <input
                type="password"
                required
                autoComplete="current-password"
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
                className={estiloInput()}
              />
            </Campo>
          </>
        )}

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-lg bg-slate-900 py-2.5 font-medium text-white hover:bg-slate-800 disabled:opacity-60"
        >
          {enviando ? 'Ingresando...' : pideCodigo ? 'Verificar' : 'Iniciar sesión'}
        </button>

        {pideCodigo && (
          <button type="button" onClick={volverACredenciales} className="w-full text-sm text-slate-600 hover:underline">
            Volver
          </button>
        )}
      </form>

      <p className="mt-6 border-t border-slate-200 pt-5 text-center text-sm text-slate-600">
        ¿No tienes cuenta?{' '}
        <Link to="/registro" className="font-medium text-violet-700 hover:underline">
          Crear cuenta
        </Link>
      </p>
    </LayoutAcceso>
  );
}
