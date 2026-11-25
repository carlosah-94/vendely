import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import Campo from '../components/Campo';
import LayoutAcceso from '../components/LayoutAcceso';
import { useAuth } from '../context/AuthContext';
import { NOMBRE_MONEDA, usePaisesHabilitados } from '../hooks/usePaisesHabilitados';
import { api, mensajeDeError } from '../services/api';
import { estiloInput } from '../utils/estilos';
import SelectorRubro from '../components/SelectorRubro';

/**
 * Países donde un dueño puede registrarse: los que tienen tipos de documento en la tabla
 * tipos_documento (db/init/02_datos_iniciales.sql), con el mismo formato que exige la base.
 * Las "empresas" piden razón social. El prefijo es el de la tabla paises (prefijo_telefono).
 *
 * El país se elige primero (como en Shopify) y de él salen los tipos de documento que se
 * muestran, el prefijo del teléfono y el país que se propone para la tienda.
 */
const TIPOS_DOCUMENTO = [
  { pais: 'Perú', idPais: 'PE', prefijo: '+51', tipos: [
    { id: 1, codigo: 'DNI', patron: /^[0-9]{8}$/, ejemplo: '12345678' },
    { id: 2, codigo: 'RUC', patron: /^(10|15|17|20)[0-9]{9}$/, ejemplo: '20601234567', empresa: true },
    { id: 3, codigo: 'Carné de extranjería', patron: /^[0-9]{9}$/, ejemplo: '123456789' },
  ] },
  { pais: 'Chile', idPais: 'CL', prefijo: '+56', tipos: [
    { id: 4, codigo: 'RUT', patron: /^[0-9]{7,8}-[0-9K]$/, ejemplo: '12345678-K', empresa: true },
  ] },
  { pais: 'México', idPais: 'MX', prefijo: '+52', tipos: [
    { id: 5, codigo: 'RFC', patron: /^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$/, ejemplo: 'ABC010203XYZ', empresa: true },
    { id: 6, codigo: 'CURP', patron: /^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$/, ejemplo: 'ABCD010203HDFRRN09' },
  ] },
  { pais: 'Colombia', idPais: 'CO', prefijo: '+57', tipos: [
    { id: 7, codigo: 'Cédula de ciudadanía', patron: /^[0-9]{6,10}$/, ejemplo: '1234567890' },
    { id: 8, codigo: 'NIT', patron: /^[0-9]{9}-[0-9]$/, ejemplo: '900123456-7', empresa: true },
  ] },
  { pais: 'Argentina', idPais: 'AR', prefijo: '+54', tipos: [
    { id: 9, codigo: 'DNI', patron: /^[0-9]{7,8}$/, ejemplo: '12345678' },
    { id: 10, codigo: 'CUIT', patron: /^(20|23|24|27|30|33|34)[0-9]{9}$/, ejemplo: '20123456789', empresa: true },
  ] },
  { pais: 'Ecuador', idPais: 'EC', prefijo: '+593', tipos: [
    { id: 11, codigo: 'Cédula de identidad', patron: /^[0-9]{10}$/, ejemplo: '1712345678' },
    { id: 12, codigo: 'RUC', patron: /^[0-9]{10}001$/, ejemplo: '1712345678001', empresa: true },
  ] },
];

/** El país de la lista (si no está, Perú). */
function grupoDe(idPais) {
  return TIPOS_DOCUMENTO.find((g) => g.idPais === idPais) ?? TIPOS_DOCUMENTO[0];
}

/**
 * País con el que empieza el formulario: el del idioma del navegador si está en la lista
 * ("es-CL" → Chile, "es-MX" → México); si no, Perú. Es solo una propuesta: se cambia arriba.
 */
function paisDelNavegador() {
  const idiomas = typeof navigator === 'undefined' ? [] : (navigator.languages ?? [navigator.language]);
  for (const idioma of idiomas) {
    const region = idioma?.split('-')[1]?.toUpperCase();
    if (TIPOS_DOCUMENTO.some((g) => g.idPais === region)) return region;
  }
  return 'PE';
}

/** true si el teléfono solo tiene el prefijo (o nada): se toma como "sin teléfono". */
function soloPrefijo(telefono) {
  return /^\+?\d{0,4}$/.test(telefono.trim());
}

/** Formulario vacío para un país: su primer tipo de documento y el teléfono con su prefijo. */
function cuentaInicial(idPais) {
  const grupo = grupoDe(idPais);
  return {
    idPais: grupo.idPais,
    nombres: '', primerApellido: '', segundoApellido: '', correo: '', usuario: '',
    contrasena: '', telefono: grupo.prefijo, tipoDocumento: String(grupo.tipos[0].id), numeroDocumento: '', razonSocial: '',
  };
}

/** Convierte un nombre en un subdominio válido: "Moda Lima" → "moda-lima". */
function aSubdominio(texto) {
  return texto
    .normalize('NFD').replace(/[̀-ͯ]/g, '') // quita tildes
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 63);
}

// ---------------------------------------------------------------- validaciones
// Son las mismas reglas que valida el backend (RegistroDuenoRequest y TiendaCrearRequest),
// para avisar antes de enviar. El backend vuelve a validar igual: es la regla real.

function validarCuenta(d) {
  const e = {};
  if (!d.nombres.trim()) e.nombres = 'Escribe tus nombres';
  if (!d.primerApellido.trim()) e.primerApellido = 'Escribe tu primer apellido';
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(d.correo.trim())) e.correo = 'El correo no es válido';
  if (!/^[a-zA-Z0-9._-]{3,50}$/.test(d.usuario.trim())) {
    e.usuario = 'Entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo';
  }
  if (d.contrasena.length < 8 || d.contrasena.length > 72) {
    e.contrasena = 'Debe tener entre 8 y 72 caracteres';
  } else if (!/[A-Za-z]/.test(d.contrasena) || !/\d/.test(d.contrasena)) {
    e.contrasena = 'Debe tener al menos una letra y un número';
  }
  if (!soloPrefijo(d.telefono) && !/^\+[1-9][0-9]{6,14}$/.test(d.telefono.trim())) {
    e.telefono = `Con código de país, por ejemplo ${grupoDe(d.idPais).prefijo}987654321`;
  }
  // Solo vale un tipo de documento del país elegido.
  const tipo = grupoDe(d.idPais).tipos.find((t) => t.id === Number(d.tipoDocumento));
  if (!tipo) {
    e.tipoDocumento = 'Elige el tipo de documento';
  } else if (!tipo.patron.test(d.numeroDocumento.trim().toUpperCase())) {
    e.numeroDocumento = `Formato no válido. Ejemplo: ${tipo.ejemplo}`;
  }
  return e;
}

function validarTienda(d) {
  const e = {};
  if (!d.nombre.trim()) e.nombre = 'Escribe el nombre de tu tienda';
  if (!/^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$/.test(d.subdominio)) {
    e.subdominio = 'Entre 3 y 63 caracteres: letras, números y guiones (sin guion al inicio ni al final)';
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(d.correoContacto.trim())) e.correoContacto = 'El correo no es válido';
  return e;
}

/** Errores del backend: 400 trae "campos" (van junto a cada campo); otros (409…) van arriba. */
function erroresDelBackend(error) {
  const campos = error.response?.data?.campos;
  return campos ? { campos, general: '' } : { campos: {}, general: mensajeDeError(error) };
}

/**
 * Crear cuenta de dueño de tienda, en dos pasos, como en Shopify:
 *   1. La cuenta: primero el país y luego datos personales, acceso y documento. Antes de
 *      crearla se verifica el correo con un código de 6 dígitos (VerificacionCorreoService).
 *   2. La primera tienda: nombre y subdominio (su dirección web).
 * Al terminar, el dueño entra al panel para registrar sus productos.
 */
export default function Registro() {
  const { usuario, registrarDueno } = useAuth();
  const navigate = useNavigate();

  // 'cuenta' → 'codigo' (verificar el correo) → 'registrando' (esperando al backend) → 'tienda'
  const [paso, setPaso] = useState('cuenta');
  // Código de verificación: a qué correo se envió, cuándo se puede pedir otro y lo que escribió.
  const [verificacion, setVerificacion] = useState({ enviadoA: '', reenviarDesde: 0 });
  const [codigo, setCodigo] = useState('');
  const [ahora, setAhora] = useState(() => Date.now());
  const [cuenta, setCuenta] = useState(() => cuentaInicial(paisDelNavegador()));
  const [tienda, setTienda] = useState({ nombre: '', subdominio: '', correoContacto: '', idPais: 'PE', rubro: '' });
  const paises = usePaisesHabilitados();
  // Mientras el dueño no escriba el subdominio a mano, se arma solo con el nombre de la tienda.
  const [subdominioEditado, setSubdominioEditado] = useState(false);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  // El error general va arriba del formulario: si el dueño está abajo (en "Continuar"), se
  // lleva la vista hasta el mensaje para que no parezca que el botón no hizo nada.
  const avisoRef = useRef(null);
  useEffect(() => {
    if (errorGeneral) avisoRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [errorGeneral]);

  // Mientras se muestra el paso del código, un reloj para la cuenta regresiva de "Reenviar".
  useEffect(() => {
    if (paso !== 'codigo') return undefined;
    const reloj = setInterval(() => setAhora(Date.now()), 1000);
    return () => clearInterval(reloj);
  }, [paso]);

  // Una persona que ya inició sesión no necesita crear otra cuenta.
  if (usuario && paso === 'cuenta') {
    return <Navigate to="/inicio" replace />;
  }

  const grupo = grupoDe(cuenta.idPais);
  const tipoElegido = grupo.tipos.find((t) => t.id === Number(cuenta.tipoDocumento));
  // Las tiendas solo pueden estar en los países habilitados (hoy Perú y Chile).
  const paisSinTiendas = !paises.some((p) => p.idPais === cuenta.idPais);

  /**
   * Al cambiar de país: el documento pasa al primer tipo de ese país y, si el teléfono
   * todavía solo tenía el prefijo anterior, se cambia por el nuevo (como en el registro de
   * clientes de la tienda). Si ya escribió un número, no se toca.
   */
  function cambiarPais(e) {
    const nuevo = grupoDe(e.target.value);
    setCuenta((c) => ({
      ...c,
      idPais: nuevo.idPais,
      tipoDocumento: String(nuevo.tipos[0].id),
      telefono: soloPrefijo(c.telefono) ? nuevo.prefijo : c.telefono,
    }));
    setErrores((er) => ({ ...er, telefono: undefined, tipoDocumento: undefined, numeroDocumento: undefined }));
  }

  function cambiarCuenta(campo) {
    return (e) => {
      const valor = e.target.value;
      setCuenta((c) => ({ ...c, [campo]: valor }));
      setErrores((er) => ({ ...er, [campo]: undefined }));
    };
  }

  function cambiarTienda(campo) {
    return (e) => {
      const valor = e.target.value;
      setTienda((t) => {
        const nueva = { ...t, [campo]: valor };
        if (campo === 'nombre' && !subdominioEditado) nueva.subdominio = aSubdominio(valor);
        if (campo === 'subdominio') nueva.subdominio = valor.toLowerCase();
        return nueva;
      });
      if (campo === 'subdominio') setSubdominioEditado(true);
      setErrores((er) => ({ ...er, [campo]: undefined, ...(campo === 'nombre' ? { subdominio: undefined } : {}) }));
    };
  }

  /**
   * Pide al backend un código para el correo (POST /auth/registro/codigo).
   * Devuelve false si el backend tiene la verificación apagada: entonces no hay código.
   */
  async function pedirCodigo(correo) {
    const { data } = await api.post('/auth/registro/codigo', { correo });
    if (!data.requerido) return false;
    setVerificacion({ enviadoA: correo, reenviarDesde: Date.now() + data.reenviarEnSegundos * 1000 });
    setAhora(Date.now());
    return true;
  }

  /**
   * "Continuar" del formulario: valida y envía el código al correo (como Shopify, la cuenta
   * no se crea hasta escribir el código). Si ya se envió uno a este mismo correo, por ejemplo
   * porque volvió a corregir su usuario, no se pide otro: se usa el mismo.
   */
  async function continuar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const encontrados = validarCuenta(cuenta);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) return;

    const correo = cuenta.correo.trim().toLowerCase();
    if (verificacion.enviadoA === correo) {
      setPaso('codigo');
      return;
    }
    setEnviando(true);
    try {
      const requerido = await pedirCodigo(correo);
      setCodigo('');
      if (requerido) {
        setPaso('codigo');
      } else {
        await crearCuenta(null);
      }
    } catch (e) {
      const { campos, general } = erroresDelBackend(e);
      setErrores(campos);             // 409: campos.correo = "El correo ya está registrado"
      setErrorGeneral(general);       // 429 o 502: el mensaje va arriba
    } finally {
      setEnviando(false);
    }
  }

  async function reenviarCodigo() {
    setErrorGeneral('');
    setErrores((er) => ({ ...er, codigoVerificacion: undefined }));
    setEnviando(true);
    try {
      await pedirCodigo(verificacion.enviadoA);
      setCodigo('');
    } catch (e) {
      setErrorGeneral(erroresDelBackend(e).general || 'No se pudo reenviar el código');
    } finally {
      setEnviando(false);
    }
  }

  function enviarConCodigo(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    if (!/^[0-9]{6}$/.test(codigo)) {
      setErrores((er) => ({ ...er, codigoVerificacion: 'Escribe los 6 dígitos del código' }));
      return;
    }
    crearCuenta(codigo);
  }

  /** Crea la cuenta con el código de verificación (null si el backend no lo pide). */
  async function crearCuenta(codigoVerificacion) {
    setEnviando(true);
    setPaso('registrando');
    try {
      const nuevo = await registrarDueno({
        nombres: cuenta.nombres.trim(),
        primerApellido: cuenta.primerApellido.trim(),
        segundoApellido: cuenta.segundoApellido.trim() || null,
        correo: cuenta.correo.trim().toLowerCase(),
        usuario: cuenta.usuario.trim(),
        contrasena: cuenta.contrasena,
        telefono: soloPrefijo(cuenta.telefono) ? null : cuenta.telefono.trim(),
        tipoDocumento: Number(cuenta.tipoDocumento),
        numeroDocumento: cuenta.numeroDocumento.trim().toUpperCase(),
        razonSocial: tipoElegido?.empresa ? cuenta.razonSocial.trim() || null : null,
        codigoVerificacion,
      });
      // La tienda se propone en el país elegido arriba, si ahí se pueden crear tiendas.
      setTienda((t) => ({
        ...t,
        correoContacto: nuevo.correo,
        idPais: paisSinTiendas ? t.idPais : cuenta.idPais,
      }));
      setErrores({});
      setPaso('tienda');
    } catch (e) {
      const { campos, general } = erroresDelBackend(e);
      setErrores(campos);
      setErrorGeneral(general); // por ejemplo, 409 "El nombre de usuario ya está en uso"
      // Un código malo o vencido se corrige en el mismo paso; lo demás, en el formulario.
      setPaso(campos.codigoVerificacion && codigoVerificacion ? 'codigo' : 'cuenta');
    } finally {
      setEnviando(false);
    }
  }

  async function crearTienda(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const encontrados = validarTienda(tienda);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) return;

    setEnviando(true);
    try {
      await api.post('/panel/tiendas', {
        nombre: tienda.nombre.trim(),
        subdominio: tienda.subdominio,
        correoContacto: tienda.correoContacto.trim(),
        permiteInvitados: true,
        idPais: tienda.idPais,
        rubro: tienda.rubro || null,
      });
      // Lo primero que hace un dueño con su tienda nueva es subir productos.
      navigate('/panel/productos', { replace: true });
    } catch (e) {
      const { campos, general } = erroresDelBackend(e);
      setErrores(campos);
      setErrorGeneral(general); // por ejemplo, 409 "Ese subdominio ya está en uso"
      setEnviando(false);
    }
  }

  const enPasoTienda = paso === 'tienda';
  // Mientras se crea la cuenta desde el paso del código, se sigue viendo ese paso.
  const enPasoCodigo = paso === 'codigo' || (paso === 'registrando' && Boolean(verificacion.enviadoA));
  const segundosParaReenviar = Math.max(0, Math.ceil((verificacion.reenviarDesde - ahora) / 1000));

  return (
    <LayoutAcceso ancho="max-w-lg">
      <div className="mb-6">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Paso {enPasoTienda ? 2 : 1} de 2</p>
        <h1 className="letra-titular mt-1 text-4xl text-[var(--v-tinta)]">
          {enPasoTienda ? 'Datos de tu tienda' : enPasoCodigo ? 'Verifica tu correo' : 'Crear cuenta'}
        </h1>
        <p className="text-sm text-slate-500">
          {enPasoTienda
            ? 'Podrás crear más tiendas después desde el panel.'
            : enPasoCodigo
              ? <>Te enviamos un código de 6 dígitos a <b className="text-slate-700">{verificacion.enviadoA}</b>.</>
              : 'Tus datos como dueño de tienda.'}
        </p>
      </div>

      {errorGeneral && (
        <p ref={avisoRef} role="alert" className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{errorGeneral}</p>
      )}

      {enPasoCodigo ? (
        <form onSubmit={enviarConCodigo} noValidate className="space-y-4">
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
            className="w-full rounded-full bg-[var(--v-tinta)] py-3 font-semibold text-white transition hover:bg-[var(--v-tinta-2)] disabled:opacity-60"
          >
            {enviando ? 'Creando cuenta...' : 'Crear cuenta'}
          </button>

          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              onClick={() => { setErrorGeneral(''); setPaso('cuenta'); }}
              className="text-slate-600 hover:underline"
            >
              Cambiar mis datos
            </button>
            <button
              type="button"
              onClick={reenviarCodigo}
              disabled={enviando || segundosParaReenviar > 0}
              className="font-medium text-violet-700 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline"
            >
              {segundosParaReenviar > 0 ? `Reenviar código en ${segundosParaReenviar} s` : 'Reenviar código'}
            </button>
          </div>
        </form>
      ) : !enPasoTienda ? (
        <form onSubmit={continuar} noValidate className="space-y-4">
          {/* El país va primero: de él salen los documentos y el prefijo del teléfono. */}
          <Campo etiqueta="País" ayuda="Según tu país te pedimos el documento y el código de teléfono.">
            <select value={cuenta.idPais} onChange={cambiarPais} autoComplete="country" className={estiloInput()}>
              {TIPOS_DOCUMENTO.map((g) => <option key={g.idPais} value={g.idPais}>{g.pais}</option>)}
            </select>
          </Campo>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo etiqueta="Nombres" error={errores.nombres}>
              <input value={cuenta.nombres} onChange={cambiarCuenta('nombres')} autoComplete="given-name" className={estiloInput(errores.nombres)} />
            </Campo>
            <Campo etiqueta="Primer apellido" error={errores.primerApellido}>
              <input value={cuenta.primerApellido} onChange={cambiarCuenta('primerApellido')} autoComplete="family-name" className={estiloInput(errores.primerApellido)} />
            </Campo>
            <Campo etiqueta="Segundo apellido (opcional)" error={errores.segundoApellido}>
              <input value={cuenta.segundoApellido} onChange={cambiarCuenta('segundoApellido')} className={estiloInput(errores.segundoApellido)} />
            </Campo>
            <Campo etiqueta="Teléfono (opcional)" error={errores.telefono} ayuda={`Ya incluye el código de ${grupo.pais} (${grupo.prefijo})`}>
              <input type="tel" value={cuenta.telefono} onChange={cambiarCuenta('telefono')} autoComplete="tel" className={estiloInput(errores.telefono)} />
            </Campo>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo etiqueta="Correo" error={errores.correo}>
              <input type="email" value={cuenta.correo} onChange={cambiarCuenta('correo')} autoComplete="email" className={estiloInput(errores.correo)} />
            </Campo>
            <Campo etiqueta="Nombre de usuario" error={errores.usuario}>
              <input value={cuenta.usuario} onChange={cambiarCuenta('usuario')} autoComplete="username" className={estiloInput(errores.usuario)} />
            </Campo>
          </div>

          <Campo etiqueta="Contraseña" error={errores.contrasena} ayuda="Mínimo 8 caracteres, con al menos una letra y un número">
            <input type="password" value={cuenta.contrasena} onChange={cambiarCuenta('contrasena')} autoComplete="new-password" className={estiloInput(errores.contrasena)} />
          </Campo>

          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)]">
            <Campo etiqueta="Tipo de documento" error={errores.tipoDocumento}>
              {/* Solo los documentos del país elegido (antes salían los 12 de los 6 países). */}
              <select value={cuenta.tipoDocumento} onChange={cambiarCuenta('tipoDocumento')} className={estiloInput(errores.tipoDocumento)}>
                {grupo.tipos.map((t) => <option key={t.id} value={t.id}>{t.codigo}</option>)}
              </select>
            </Campo>
            <Campo etiqueta="Número de documento" error={errores.numeroDocumento}>
              <input value={cuenta.numeroDocumento} onChange={cambiarCuenta('numeroDocumento')} placeholder={tipoElegido?.ejemplo} className={estiloInput(errores.numeroDocumento)} />
            </Campo>
          </div>

          {tipoElegido?.empresa && (
            <Campo etiqueta="Razón social (opcional)" error={errores.razonSocial}>
              <input value={cuenta.razonSocial} onChange={cambiarCuenta('razonSocial')} className={estiloInput(errores.razonSocial)} />
            </Campo>
          )}

          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-full bg-[var(--v-tinta)] py-3 font-semibold text-white transition hover:bg-[var(--v-tinta-2)] disabled:opacity-60"
          >
            {enviando ? 'Enviando código...' : 'Continuar'}
          </button>

          <p className="text-center text-sm text-slate-600">
            ¿Ya tienes una cuenta?{' '}
            <Link to="/login" className="font-medium text-violet-700 hover:underline">Inicia sesión</Link>
          </p>
        </form>
      ) : (
        <form onSubmit={crearTienda} noValidate className="space-y-4">
          <Campo etiqueta="Nombre de la tienda" error={errores.nombre}>
            <input value={tienda.nombre} onChange={cambiarTienda('nombre')} className={estiloInput(errores.nombre)} />
          </Campo>

          <SelectorRubro valor={tienda.rubro} onCambiar={(rubro) => setTienda((t) => ({ ...t, rubro }))} />

          <Campo
            etiqueta="Dirección web"
            error={errores.subdominio}
            ayuda="No se puede cambiar después."
          >
            <div className={`flex overflow-hidden rounded-lg border bg-white focus-within:ring-2 ${
              errores.subdominio ? 'border-red-400 focus-within:ring-red-200' : 'border-slate-300 focus-within:border-violet-500 focus-within:ring-violet-200'
            }`}>
              <input
                value={tienda.subdominio}
                onChange={cambiarTienda('subdominio')}
                placeholder="moda-lima"
                className="min-w-0 flex-1 px-3 py-2.5 text-slate-900 placeholder:text-slate-400 focus:outline-none"
              />
              <span className="flex items-center border-l border-slate-200 bg-slate-50 px-3 text-sm text-slate-500">.vendely.pe</span>
            </div>
          </Campo>

          <Campo
            etiqueta="País de la tienda"
            error={errores.idPais}
            ayuda={(() => {
              const p = paises.find((x) => x.idPais === tienda.idPais);
              const moneda = p ? `Cobrarás en ${NOMBRE_MONEDA[p.moneda] ?? p.moneda} (${p.moneda}). No se puede cambiar después.` : '';
              // Si el dueño es de un país donde aún no se crean tiendas, se le explica por qué no aparece.
              const aviso = paisSinTiendas
                ? `Por ahora las tiendas solo pueden estar en ${paises.map((x) => x.nombre).join(' o ')}. `
                : '';
              return aviso + moneda || undefined;
            })()}
          >
            <select value={tienda.idPais} onChange={cambiarTienda('idPais')} className={estiloInput(errores.idPais)}>
              {paises.map((p) => <option key={p.idPais} value={p.idPais}>{p.nombre}</option>)}
            </select>
          </Campo>

          <Campo etiqueta="Correo de contacto" error={errores.correoContacto} >
            <input type="email" value={tienda.correoContacto} onChange={cambiarTienda('correoContacto')} className={estiloInput(errores.correoContacto)} />
          </Campo>

          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-full bg-[var(--v-tinta)] py-3 font-semibold text-white transition hover:bg-[var(--v-tinta-2)] disabled:opacity-60"
          >
            {enviando ? 'Creando tienda...' : 'Crear tienda'}
          </button>

          <p className="text-center text-sm text-slate-500">
            <Link to="/inicio" className="hover:underline">Hacerlo después</Link>
          </p>
        </form>
      )}
    </LayoutAcceso>
  );
}
