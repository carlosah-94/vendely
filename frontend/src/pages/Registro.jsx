import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import Campo from '../components/Campo';
import LayoutAcceso from '../components/LayoutAcceso';
import { useAuth } from '../context/AuthContext';
import { api, mensajeDeError } from '../services/api';
import { estiloInput } from '../utils/estilos';

/**
 * Tipos de documento de la tabla tipos_documento (db/init/02_datos_iniciales.sql),
 * con el mismo formato que exige la base. Las "empresas" piden razón social.
 */
const TIPOS_DOCUMENTO = [
  { pais: 'Perú', tipos: [
    { id: 1, codigo: 'DNI', patron: /^[0-9]{8}$/, ejemplo: '12345678' },
    { id: 2, codigo: 'RUC', patron: /^(10|15|17|20)[0-9]{9}$/, ejemplo: '20601234567', empresa: true },
    { id: 3, codigo: 'Carné de extranjería', patron: /^[0-9]{9}$/, ejemplo: '123456789' },
  ] },
  { pais: 'Chile', tipos: [{ id: 4, codigo: 'RUT', patron: /^[0-9]{7,8}-[0-9K]$/, ejemplo: '12345678-K', empresa: true }] },
  { pais: 'México', tipos: [
    { id: 5, codigo: 'RFC', patron: /^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$/, ejemplo: 'ABC010203XYZ', empresa: true },
    { id: 6, codigo: 'CURP', patron: /^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$/, ejemplo: 'ABCD010203HDFRRN09' },
  ] },
  { pais: 'Colombia', tipos: [
    { id: 7, codigo: 'Cédula de ciudadanía', patron: /^[0-9]{6,10}$/, ejemplo: '1234567890' },
    { id: 8, codigo: 'NIT', patron: /^[0-9]{9}-[0-9]$/, ejemplo: '900123456-7', empresa: true },
  ] },
  { pais: 'Argentina', tipos: [
    { id: 9, codigo: 'DNI', patron: /^[0-9]{7,8}$/, ejemplo: '12345678' },
    { id: 10, codigo: 'CUIT', patron: /^(20|23|24|27|30|33|34)[0-9]{9}$/, ejemplo: '20123456789', empresa: true },
  ] },
  { pais: 'Ecuador', tipos: [
    { id: 11, codigo: 'Cédula de identidad', patron: /^[0-9]{10}$/, ejemplo: '1712345678' },
    { id: 12, codigo: 'RUC', patron: /^[0-9]{10}001$/, ejemplo: '1712345678001', empresa: true },
  ] },
];
const TODOS_LOS_TIPOS = TIPOS_DOCUMENTO.flatMap((g) => g.tipos);

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
  if (d.telefono.trim() && !/^\+[1-9][0-9]{6,14}$/.test(d.telefono.trim())) {
    e.telefono = 'Con código de país, por ejemplo +51987654321';
  }
  const tipo = TODOS_LOS_TIPOS.find((t) => t.id === Number(d.tipoDocumento));
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
 *   1. La cuenta: datos personales, acceso y documento.
 *   2. La primera tienda: nombre y subdominio (su dirección web).
 * Al terminar, el dueño entra al panel para registrar sus productos.
 */
export default function Registro() {
  const { usuario, registrarDueno } = useAuth();
  const navigate = useNavigate();

  // 'cuenta' → 'registrando' (esperando al backend) → 'tienda'
  const [paso, setPaso] = useState('cuenta');
  const [cuenta, setCuenta] = useState({
    nombres: '', primerApellido: '', segundoApellido: '', correo: '', usuario: '',
    contrasena: '', telefono: '', tipoDocumento: '1', numeroDocumento: '', razonSocial: '',
  });
  const [tienda, setTienda] = useState({ nombre: '', subdominio: '', correoContacto: '' });
  // Mientras el dueño no escriba el subdominio a mano, se arma solo con el nombre de la tienda.
  const [subdominioEditado, setSubdominioEditado] = useState(false);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  // Una persona que ya inició sesión no necesita crear otra cuenta.
  if (usuario && paso === 'cuenta') {
    return <Navigate to="/" replace />;
  }

  const tipoElegido = TODOS_LOS_TIPOS.find((t) => t.id === Number(cuenta.tipoDocumento));

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

  async function crearCuenta(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const encontrados = validarCuenta(cuenta);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) return;

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
        telefono: cuenta.telefono.trim() || null,
        tipoDocumento: Number(cuenta.tipoDocumento),
        numeroDocumento: cuenta.numeroDocumento.trim().toUpperCase(),
        razonSocial: tipoElegido?.empresa ? cuenta.razonSocial.trim() || null : null,
      });
      setTienda((t) => ({ ...t, correoContacto: nuevo.correo }));
      setErrores({});
      setPaso('tienda');
    } catch (e) {
      const { campos, general } = erroresDelBackend(e);
      setErrores(campos);
      setErrorGeneral(general); // por ejemplo, 409 "El correo ya está registrado"
      setPaso('cuenta');
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

  return (
    <LayoutAcceso ancho="max-w-lg">
      <div className="mb-6">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Paso {enPasoTienda ? 2 : 1} de 2</p>
        <h1 className="text-xl font-semibold text-slate-900">
          {enPasoTienda ? 'Datos de tu tienda' : 'Crear cuenta'}
        </h1>
        <p className="text-sm text-slate-500">
          {enPasoTienda
            ? 'Podrás crear más tiendas después desde el panel.'
            : 'Tus datos como dueño de tienda.'}
        </p>
      </div>

      {errorGeneral && (
        <p className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{errorGeneral}</p>
      )}

      {!enPasoTienda ? (
        <form onSubmit={crearCuenta} noValidate className="space-y-4">
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
            <Campo etiqueta="Teléfono (opcional)" error={errores.telefono} ayuda="Con código de país: +51987654321">
              <input type="tel" value={cuenta.telefono} onChange={cambiarCuenta('telefono')} placeholder="+51987654321" className={estiloInput(errores.telefono)} />
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
              <select value={cuenta.tipoDocumento} onChange={cambiarCuenta('tipoDocumento')} className={estiloInput(errores.tipoDocumento)}>
                {TIPOS_DOCUMENTO.map((g) => (
                  <optgroup key={g.pais} label={g.pais}>
                    {g.tipos.map((t) => <option key={t.id} value={t.id}>{t.codigo}</option>)}
                  </optgroup>
                ))}
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
            className="w-full rounded-lg bg-slate-900 py-2.5 font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {enviando ? 'Creando cuenta...' : 'Continuar'}
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

          <Campo etiqueta="Correo de contacto" error={errores.correoContacto} >
            <input type="email" value={tienda.correoContacto} onChange={cambiarTienda('correoContacto')} className={estiloInput(errores.correoContacto)} />
          </Campo>

          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-lg bg-slate-900 py-2.5 font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {enviando ? 'Creando tienda...' : 'Crear tienda'}
          </button>

          <p className="text-center text-sm text-slate-500">
            <Link to="/" className="hover:underline">Hacerlo después</Link>
          </p>
        </form>
      )}
    </LayoutAcceso>
  );
}
