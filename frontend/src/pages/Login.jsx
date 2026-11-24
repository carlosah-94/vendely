import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
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
  const location = useLocation();

  // Al volver de "¿Olvidaste tu contraseña?" llega el correo y un aviso de que ya cambió.
  const [correo, setCorreo] = useState(location.state?.correo ?? '');
  const aviso = location.state?.aviso;
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
      <div className="mb-8">
        <h1 className="letra-titular text-4xl text-[var(--v-tinta)]">Hola de nuevo</h1>
        <p className="mt-2 text-[15px] text-slate-500">Inicia sesión para entrar al panel de tu tienda.</p>
      </div>

      <form onSubmit={enviar} className="space-y-4">
        {aviso && !error && (
          <p className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800">{aviso}</p>
        )}
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
            <div className="-mt-2 text-right">
              <Link to="/recuperar" state={{ correo }} className="text-sm font-medium text-violet-700 hover:underline">
                ¿Olvidaste tu contraseña?
              </Link>
            </div>
          </>
        )}

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-full bg-[var(--v-tinta)] py-3 font-semibold text-white transition hover:bg-[var(--v-tinta-2)] disabled:opacity-60"
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
