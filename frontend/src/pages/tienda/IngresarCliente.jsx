import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import Campo from '../../components/Campo';
import { useAuth } from '../../context/AuthContext';
import { mensajeDeError } from '../../services/api';
import { listarPaises } from '../../services/envios';
import { pedirCodigoCliente, registrarCliente } from '../../services/tienda';
import { estiloInput } from '../../utils/estilos';

const VACIO = {
  nombres: '', primerApellido: '', segundoApellido: '', usuario: '', correo: '',
  telefono: '+51', contrasena: '',
};

/**
 * Iniciar sesión o crear una cuenta de cliente, siempre dentro de ESTA tienda.
 *
 * Crear cuenta tiene dos pasos (como el registro del dueño): primero los datos, y al continuar
 * se envía un código de 6 dígitos al correo; la cuenta se crea al escribir ese código. Así cada
 * cliente queda con un correo que existe y que es suyo (ahí le llegan sus pedidos y comprobantes).
 * Si el backend tiene la verificación apagada, la cuenta se crea en el primer paso.
 */
export default function IngresarCliente() {
  const { tienda, subdominio } = useOutletContext();
  const { usuario, iniciarSesion } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const destino = location.state?.volver ?? `/tienda/${subdominio}`;

  const [modo, setModo] = useState('ingresar'); // 'ingresar' | 'registro'
  // El país empieza en el de la tienda y el teléfono con su prefijo (+51 Perú, +56 Chile).
  // Si el cliente elige otro país, el prefijo cambia solo.
  const [paises, setPaises] = useState([]);
  const [idPais, setIdPais] = useState(tienda.idPais);
  const [datos, setDatos] = useState({ ...VACIO, telefono: tienda.idPais === 'CL' ? '+56' : '+51' });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  // Paso del código: a qué correo se envió y desde cuándo se puede pedir otro.
  const [paso, setPaso] = useState('datos'); // 'datos' | 'codigo'
  const [codigo, setCodigo] = useState('');
  const [verificacion, setVerificacion] = useState({ enviadoA: '', reenviarDesde: 0 });
  const [ahora, setAhora] = useState(() => Date.now());

  // Reloj para el "Reenviar código en N s": solo corre mientras se espera.
  useEffect(() => {
    if (paso !== 'codigo' || verificacion.reenviarDesde <= ahora) return undefined;
    const id = setTimeout(() => setAhora(Date.now()), 1000);
    return () => clearTimeout(id);
  }, [paso, ahora, verificacion.reenviarDesde]);
  const segundosParaReenviar = Math.max(0, Math.ceil((verificacion.reenviarDesde - ahora) / 1000));

  useEffect(() => {
    listarPaises()
      .then((lista) => {
        // El país de la tienda va primero; los demás, en orden alfabético.
        const propio = lista.filter((p) => p.idPais === tienda.idPais);
        const otros = lista
          .filter((p) => p.idPais !== tienda.idPais && p.prefijoTelefono)
          .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
        setPaises([...propio, ...otros]);
      })
      .catch(() => setPaises([]));
  }, [tienda.idPais]);

  if (usuario?.rol === 'CLIENTE' && usuario.idTienda === tienda.idTienda) {
    return <Navigate to={destino} replace />;
  }

  const cambiar = (campo) => (e) => {
    setDatos((d) => ({ ...d, [campo]: e.target.value }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
  };

  function cambiarPais(e) {
    const nuevo = e.target.value;
    const prefijo = paises.find((p) => p.idPais === nuevo)?.prefijoTelefono ?? '+';
    setIdPais(nuevo);
    // Si el teléfono todavía solo tenía el prefijo del país anterior, se cambia por el nuevo.
    setDatos((d) => ({ ...d, telefono: /^\+\d{0,4}$/.test(d.telefono.trim()) ? prefijo : d.telefono }));
    setErrores((er) => ({ ...er, telefono: undefined }));
  }

  const prefijoActual = paises.find((p) => p.idPais === idPais)?.prefijoTelefono ?? '+51';

  /** Muestra los errores del backend: los de un campo, junto a él; los demás, arriba. */
  function mostrarError(e) {
    if (e.response?.data?.campos) {
      setErrores(e.response.data.campos);
      // El error del código se ve en su paso; los de los datos, en el formulario de datos.
      if (!e.response.data.campos.codigoVerificacion) setPaso('datos');
    } else {
      setError(mensajeDeError(e));
    }
  }

  /** Pide el código para el correo. Devuelve false si el backend no lo pide (verificación apagada). */
  async function pedirCodigo(correo) {
    const r = await pedirCodigoCliente(correo, subdominio);
    if (!r.requerido) return false;
    const momento = Date.now();
    setVerificacion({ enviadoA: correo, reenviarDesde: momento + r.reenviarEnSegundos * 1000 });
    setAhora(momento);
    return true;
  }

  /** Crea la cuenta (con el código, o null si no hace falta) e inicia sesión. */
  async function crearCuenta(codigoVerificacion) {
    await registrarCliente({
      subdominio,
      usuario: datos.usuario.trim(),
      correo: datos.correo.trim(),
      contrasena: datos.contrasena,
      nombres: datos.nombres.trim(),
      primerApellido: datos.primerApellido.trim(),
      segundoApellido: datos.segundoApellido.trim() || null,
      telefono: datos.telefono.trim() || null,
      aceptaMarketing: false,
      codigoVerificacion,
    });
    // Registro y login usan el mismo correo y contraseña: tras crear la cuenta se inicia sesión.
    await iniciarSesion({ correo: datos.correo.trim(), contrasena: datos.contrasena, subdominio });
    navigate(destino, { replace: true });
  }

  async function enviar(evento) {
    evento.preventDefault();
    setError('');
    setErrores({});
    setEnviando(true);
    try {
      if (modo === 'registro') {
        const correo = datos.correo.trim().toLowerCase();
        // Si ya se envió un código a este mismo correo (volvió a corregir un dato), se reutiliza.
        if (verificacion.enviadoA === correo) {
          setPaso('codigo');
        } else if (await pedirCodigo(correo)) {
          setCodigo('');
          setPaso('codigo');
        } else {
          await crearCuenta(null);
        }
        return;
      }
      await iniciarSesion({ correo: datos.correo.trim(), contrasena: datos.contrasena, subdominio });
      navigate(destino, { replace: true });
    } catch (e) {
      mostrarError(e);
    } finally {
      setEnviando(false);
    }
  }

  async function enviarConCodigo(evento) {
    evento.preventDefault();
    setError('');
    if (!/^[0-9]{6}$/.test(codigo)) {
      setErrores((er) => ({ ...er, codigoVerificacion: 'Escribe los 6 dígitos del código' }));
      return;
    }
    setEnviando(true);
    try {
      await crearCuenta(codigo);
    } catch (e) {
      mostrarError(e);
    } finally {
      setEnviando(false);
    }
  }

  async function reenviarCodigo() {
    setError('');
    setErrores((er) => ({ ...er, codigoVerificacion: undefined }));
    setEnviando(true);
    try {
      await pedirCodigo(verificacion.enviadoA);
      setCodigo('');
    } catch (e) {
      setError(mensajeDeError(e));
    } finally {
      setEnviando(false);
    }
  }

  const registro = modo === 'registro';

  if (registro && paso === 'codigo') {
    return (
      <div className="mx-auto max-w-md px-4 pt-12">
        <h1 className="text-3xl font-semibold tracking-tight">Verifica tu correo</h1>
        <p className="mt-2 text-[15px] text-[#616161]">
          Te enviamos un código de 6 dígitos a <b className="text-[#303030]">{verificacion.enviadoA}</b>.
          Escríbelo para terminar de crear tu cuenta en {tienda.nombre}.
        </p>

        <form onSubmit={enviarConCodigo} noValidate className="mt-6 space-y-4">
          {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <Campo etiqueta="Código de verificación" error={errores.codigoVerificacion} ayuda="Revisa también la carpeta de spam. Vence en 10 minutos.">
            <input
              value={codigo}
              onChange={(e) => {
                setCodigo(e.target.value.replace(/\D/g, '').slice(0, 6));
                setErrores((er) => ({ ...er, codigoVerificacion: undefined }));
              }}
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              placeholder="123456"
              className={`${estiloInput(errores.codigoVerificacion)} text-center text-2xl tracking-[0.5em]`}
            />
          </Campo>
          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-full bg-[var(--tienda)] py-3.5 font-semibold text-[var(--tienda-texto)] transition hover:brightness-95 disabled:opacity-60"
          >
            {enviando ? 'Creando tu cuenta...' : 'Crear cuenta'}
          </button>
          <div className="flex items-center justify-between text-sm">
            <button type="button" onClick={() => { setError(''); setPaso('datos'); }} className="text-[#616161] hover:underline">
              Cambiar mis datos
            </button>
            <button
              type="button"
              onClick={reenviarCodigo}
              disabled={enviando || segundosParaReenviar > 0}
              className="font-medium underline-offset-4 hover:underline disabled:cursor-not-allowed disabled:text-[#9a9a9a] disabled:no-underline"
            >
              {segundosParaReenviar > 0 ? `Reenviar código en ${segundosParaReenviar} s` : 'Reenviar código'}
            </button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md px-4 pt-12">
      <h1 className="text-3xl font-semibold tracking-tight">{registro ? 'Crear cuenta' : 'Iniciar sesión'}</h1>
      <p className="mt-1 text-sm text-[#616161]">en {tienda.nombre}</p>

      <form onSubmit={enviar} className="mt-6 space-y-4" noValidate={false}>
        {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        {registro && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Campo etiqueta="Nombres" error={errores.nombres}>
                <input required value={datos.nombres} onChange={cambiar('nombres')} className={estiloInput(errores.nombres)} />
              </Campo>
              <Campo etiqueta="Primer apellido" error={errores.primerApellido}>
                <input required value={datos.primerApellido} onChange={cambiar('primerApellido')} className={estiloInput(errores.primerApellido)} />
              </Campo>
            </div>
            <Campo etiqueta="Segundo apellido (opcional)" error={errores.segundoApellido}>
              <input value={datos.segundoApellido} onChange={cambiar('segundoApellido')} className={estiloInput(errores.segundoApellido)} />
            </Campo>
            <Campo etiqueta="Usuario" error={errores.usuario} ayuda="3 a 50 caracteres: letras, números, punto o guion">
              <input required minLength={3} value={datos.usuario} onChange={cambiar('usuario')} autoComplete="username" className={estiloInput(errores.usuario)} />
            </Campo>
            <Campo etiqueta="País">
              <select value={idPais} onChange={cambiarPais} className={estiloInput()}>
                {paises.length === 0 && <option value={tienda.idPais}>{tienda.pais ?? tienda.idPais}</option>}
                {paises.map((p) => (
                  <option key={p.idPais} value={p.idPais}>{p.nombre} ({p.prefijoTelefono})</option>
                ))}
              </select>
            </Campo>
            <Campo etiqueta="Teléfono" error={errores.telefono} ayuda={`Con código de país: ${prefijoActual}...`}>
              <input required type="tel" value={datos.telefono} onChange={cambiar('telefono')} className={estiloInput(errores.telefono)} />
            </Campo>
          </>
        )}

        <Campo etiqueta="Correo" error={errores.correo}>
          <input required type="email" value={datos.correo} onChange={cambiar('correo')} autoComplete="email" className={estiloInput(errores.correo)} />
        </Campo>
        <Campo
          etiqueta="Contraseña"
          error={errores.contrasena}
          ayuda={registro ? 'Mínimo 8 caracteres, con al menos una letra y un número' : undefined}
        >
          <input
            required
            type="password"
            value={datos.contrasena}
            onChange={cambiar('contrasena')}
            autoComplete={registro ? 'new-password' : 'current-password'}
            className={estiloInput(errores.contrasena)}
          />
        </Campo>

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-full bg-[var(--tienda)] py-3.5 font-semibold text-[var(--tienda-texto)] transition hover:brightness-95 disabled:opacity-60"
        >
          {enviando ? 'Un momento...' : registro ? 'Continuar' : 'Iniciar sesión'}
        </button>
      </form>

      <p className="mt-6 border-t border-[#e3e3e3] pt-5 text-center text-sm text-[#616161]">
        {registro ? '¿Ya tienes cuenta?' : '¿Eres nuevo?'}{' '}
        <button
          type="button"
          onClick={() => {
            setModo(registro ? 'ingresar' : 'registro');
            setPaso('datos');
            setError('');
            setErrores({});
          }}
          className="font-semibold text-[#303030] underline-offset-4 hover:underline"
        >
          {registro ? 'Iniciar sesión' : 'Crear cuenta'}
        </button>
      </p>
    </div>
  );
}
