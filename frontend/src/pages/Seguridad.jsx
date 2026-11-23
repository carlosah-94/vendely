import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import Mensaje from '../components/Mensaje';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { mensajeDeError } from '../services/api';
import {
  cambiarContrasena,
  confirmarDobleFactor,
  desactivarDobleFactor,
  iniciarDobleFactor,
} from '../services/seguridad';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';

/**
 * inputPanel() trae w-full; para los campos cortos (el código, la contraseña junto a él) se
 * quita, porque si quedan las dos clases de ancho gana la que Tailwind escribió después, no la
 * que se puso al final, y el campo terminaba ocupando toda la tarjeta.
 */
const inputCorto = (ancho) => `${inputPanel().replace('w-full', '')} ${ancho}`;

/**
 * "Mi cuenta → Seguridad" (issue #4): doble autenticación y cambio de contraseña.
 * La usan los dueños de tienda y el superadmin.
 */
export default function Seguridad() {
  return (
    <section className="max-w-2xl space-y-4">
      <h1 className="text-xl font-bold text-[#101010]">Seguridad</h1>
      <DobleFactor />
      <CambiarContrasena />
    </section>
  );
}

/**
 * La doble autenticación en tres estados: apagada → mostrando el QR → activa.
 * El paso del medio existe para no dejar a nadie fuera de su cuenta: solo queda activa después
 * de escribir un código correcto, que prueba que la app quedó bien configurada.
 */
function DobleFactor() {
  const { usuario, actualizarUsuario } = useAuth();
  const { avisar } = useToast();
  const activa = usuario?.dobleFactor;

  const [alta, setAlta] = useState(null);         // { secreto, enlace } mientras se configura
  const [qr, setQr] = useState('');               // la imagen del QR, generada en el navegador
  const [codigo, setCodigo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [quitando, setQuitando] = useState(false);
  const [error, setError] = useState('');
  const [trabajando, setTrabajando] = useState(false);

  // El QR se dibuja aquí, en el navegador: el secreto no se manda a ningún servicio externo
  // para convertirlo en imagen (eso sería entregarle la llave de la cuenta a un tercero).
  useEffect(() => {
    if (!alta) return undefined;
    let vigente = true;
    QRCode.toDataURL(alta.enlace, { width: 200, margin: 1 })
      .then((imagen) => vigente && setQr(imagen))
      .catch(() => vigente && setQr(''));
    return () => {
      vigente = false;
    };
  }, [alta]);

  async function empezar() {
    setError('');
    setTrabajando(true);
    try {
      setAlta(await iniciarDobleFactor());
      setCodigo('');
    } catch (e) {
      setError(mensajeDeError(e));
    } finally {
      setTrabajando(false);
    }
  }

  async function confirmar(e) {
    e.preventDefault();
    setError('');
    setTrabajando(true);
    try {
      actualizarUsuario(await confirmarDobleFactor(codigo));
      setAlta(null);
      setQr('');
      setCodigo('');
      avisar('Doble autenticación activada');
    } catch (err) {
      setError(mensajeDeError(err));
      setCodigo('');
    } finally {
      setTrabajando(false);
    }
  }

  async function quitar(e) {
    e.preventDefault();
    setError('');
    setTrabajando(true);
    try {
      actualizarUsuario(await desactivarDobleFactor(contrasena, codigo));
      setQuitando(false);
      setContrasena('');
      setCodigo('');
      avisar('Doble autenticación desactivada');
    } catch (err) {
      setError(mensajeDeError(err));
      setCodigo('');
    } finally {
      setTrabajando(false);
    }
  }

  const campoCodigo = (
    <input
      inputMode="numeric"
      autoComplete="one-time-code"
      required
      maxLength={6}
      pattern="\d{6}"
      placeholder="123456"
      value={codigo}
      onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))}
      aria-label="Código de 6 dígitos"
      className={`${inputCorto('w-36')} text-center tracking-[0.3em]`}
    />
  );

  return (
    <div className={`${TARJETA} space-y-3 p-5 text-sm`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-[#303030]">Doble autenticación</h2>
          <p className="text-[#616161]">
            Además de la contraseña, al iniciar sesión se pide un código de 6 dígitos de una app en tu
            teléfono. Si alguien roba tu contraseña, no puede entrar sin él.
          </p>
        </div>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${
            activa ? 'bg-[#e8fcd9] text-[#0c5132]' : 'bg-[#e3e3e3] text-[#4a4a4a]'
          }`}
        >
          {activa ? 'Activa' : 'Desactivada'}
        </span>
      </div>

      {error && <Mensaje tipo="error" texto={error} onCerrar={() => setError('')} />}

      {/* Apagada y sin empezar */}
      {!activa && !alta && (
        <button type="button" onClick={empezar} disabled={trabajando} className={BOTON}>
          Activar
        </button>
      )}

      {/* Configurando: QR + primer código */}
      {!activa && alta && (
        <form onSubmit={confirmar} className="space-y-3">
          <ol className="list-decimal space-y-1 pl-5 text-[#303030]">
            <li>Instala Google Authenticator, Microsoft Authenticator o Authy en tu teléfono.</li>
            <li>En la app, agrega una cuenta y escanea este código.</li>
            <li>Escribe el código de 6 dígitos que te muestra.</li>
          </ol>
          <div className="flex flex-wrap items-center gap-4">
            {qr ? (
              <img src={qr} alt="Código QR para la app de autenticación" className="h-[200px] w-[200px] rounded border border-[#eee]" />
            ) : (
              <div className="flex h-[200px] w-[200px] items-center justify-center rounded border border-[#eee] text-[#8a8a8a]">
                Generando…
              </div>
            )}
            <div className="space-y-1 text-[12px] text-[#616161]">
              <p>¿No puedes escanearlo? Escribe esta clave en la app:</p>
              <code className="block break-all rounded bg-[#f4f4f4] px-2 py-1 font-mono text-[13px] text-[#303030]">
                {alta.secreto.match(/.{1,4}/g).join(' ')}
              </code>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {campoCodigo}
            <button type="submit" disabled={trabajando || codigo.length !== 6} className={BOTON}>
              Confirmar y activar
            </button>
            <button
              type="button"
              onClick={() => {
                setAlta(null);
                setQr('');
                setError('');
              }}
              className="px-2 text-[#616161] hover:underline"
            >
              Cancelar
            </button>
          </div>
        </form>
      )}

      {/* Activa */}
      {activa && !quitando && (
        <button
          type="button"
          onClick={() => {
            setQuitando(true);
            setError('');
          }}
          className="rounded-lg border border-[#f2c6c0] px-3 py-1.5 font-medium text-[#c70a24] hover:bg-[#ffe7e6]"
        >
          Desactivar
        </button>
      )}

      {activa && quitando && (
        <form onSubmit={quitar} className="space-y-3">
          <p className="text-[#303030]">Para quitarla, confirma con tu contraseña y un código de la app.</p>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="password"
              required
              autoComplete="current-password"
              placeholder="Contraseña"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              className={inputCorto('w-48')}
            />
            {campoCodigo}
            <button
              type="submit"
              disabled={trabajando || codigo.length !== 6}
              className="rounded-lg bg-[#c70a24] px-3 py-1.5 font-medium text-white hover:bg-[#a30a1e] disabled:opacity-50"
            >
              Desactivar
            </button>
            <button type="button" onClick={() => setQuitando(false)} className="px-2 text-[#616161] hover:underline">
              Cancelar
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

/**
 * Cambiar la contraseña. El backend devuelve un token nuevo y los anteriores dejan de servir:
 * si la cuenta estaba abierta en otra computadora, esa sesión se cierra en su siguiente acción.
 */
function CambiarContrasena() {
  const { guardarSesion } = useAuth();
  const { avisar } = useToast();
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    setError('');
    // Las mismas reglas que el backend (CambioContrasenaRequest), para avisar al instante.
    if (nueva.length < 8 || !/[A-Za-z]/.test(nueva) || !/\d/.test(nueva)) {
      setError('La contraseña nueva debe tener al menos 8 caracteres, con letras y números');
      return;
    }
    if (nueva !== repetida) {
      setError('Las dos contraseñas nuevas no coinciden');
      return;
    }
    setEnviando(true);
    try {
      guardarSesion(await cambiarContrasena(actual, nueva));
      setActual('');
      setNueva('');
      setRepetida('');
      avisar('Contraseña cambiada. Las demás sesiones abiertas se cerraron');
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar} className={`${TARJETA} space-y-3 p-5 text-sm`}>
      <div>
        <h2 className="font-semibold text-[#303030]">Cambiar contraseña</h2>
        <p className="text-[#616161]">Al cambiarla, se cierran las sesiones abiertas en otros equipos.</p>
      </div>
      {error && <Mensaje tipo="error" texto={error} onCerrar={() => setError('')} />}
      <div className="grid gap-2 sm:grid-cols-3">
        <input
          type="password"
          required
          autoComplete="current-password"
          placeholder="Contraseña actual"
          value={actual}
          onChange={(e) => setActual(e.target.value)}
          className={inputPanel()}
        />
        <input
          type="password"
          required
          autoComplete="new-password"
          placeholder="Nueva"
          value={nueva}
          onChange={(e) => setNueva(e.target.value)}
          className={inputPanel()}
        />
        <input
          type="password"
          required
          autoComplete="new-password"
          placeholder="Repite la nueva"
          value={repetida}
          onChange={(e) => setRepetida(e.target.value)}
          className={inputPanel()}
        />
      </div>
      <button type="submit" disabled={enviando} className={BOTON}>
        {enviando ? 'Guardando…' : 'Cambiar contraseña'}
      </button>
    </form>
  );
}
