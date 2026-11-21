import { useState } from 'react';
import { Link } from 'react-router-dom';
import CampoPanel from '../components/CampoPanel';
import { IconoCarrito, IconoEnlaceExterno } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { colorTextoSobre } from '../utils/formato';
import { PLANTILLAS } from '../utils/plantillas';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';

const COLORES = ['#303030', '#1D4ED8', '#0F766E', '#B91C1C', '#7C3AED', '#C2410C', '#DB2777', '#F59E0B'];
const MAX_DESCRIPCION = 2000;

function desdeTienda(t) {
  return {
    nombre: t.nombre ?? '',
    descripcion: t.descripcion ?? '',
    correoContacto: t.correoContacto ?? '',
    logoUrl: t.logoUrl ?? '',
    colorPrincipal: t.colorPrincipal ?? '#303030',
    permiteInvitados: t.permiteInvitados ?? true,
    plantilla: t.plantilla ?? 'clasica',
  };
}

/**
 * "Tienda online → Personalizar": nombre, descripción, logo y color de la tienda pública,
 * con una vista previa que cambia mientras escribes (como el editor de temas de Shopify).
 * Se guarda en la tabla tiendas (logo_url, color_principal, descripcion...) con PUT /api/panel/tiendas/{id}.
 */
export default function PersonalizarTienda() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando, error: errorTiendas } = useTiendaActual();

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;
  if (cargando) return <p className="text-[#616161]">Cargando...</p>;
  if (!tienda) return <Mensaje tipo="info" texto="Primero crea una tienda." />;

  // key: al cambiar de tienda, el formulario empieza de nuevo con los datos de la otra.
  return (
    <Editor key={idTienda} tienda={tienda} tiendas={tiendas} idTienda={idTienda} onCambiarTienda={cambiarTienda} />
  );
}

function Editor({ tienda, tiendas, idTienda, onCambiarTienda }) {
  const { avisar } = useToast();
  const [v, setV] = useState(() => desdeTienda(tienda));
  // Lo último guardado: si el plan no deja personalizar, el logo y el color se envían como estaban.
  const [guardada, setGuardada] = useState(tienda);
  const personaliza = tienda.permitePersonalizar !== false;
  const conPlantillas = tienda.permitePlantillas !== false;
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [logoRoto, setLogoRoto] = useState(false);

  function cambiar(campo, valor) {
    setV((actual) => ({ ...actual, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));
    if (campo === 'logoUrl') setLogoRoto(false);
  }

  async function guardar(e) {
    e.preventDefault();
    const er = {};
    if (!v.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    if (v.descripcion.length > MAX_DESCRIPCION) er.descripcion = `Hasta ${MAX_DESCRIPCION} caracteres`;
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.correoContacto.trim())) er.correoContacto = 'El correo no es válido';
    if (personaliza && v.logoUrl.trim() && !/^https?:\/\/.+/.test(v.logoUrl.trim())) er.logoUrl = 'Pega un enlace que empiece con http:// o https://';
    if (personaliza && !/^#[0-9A-Fa-f]{6}$/.test(v.colorPrincipal)) er.colorPrincipal = 'Formato #RRGGBB, por ejemplo #1D4ED8';
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      const { data } = await api.put(`/panel/tiendas/${idTienda}`, {
        nombre: v.nombre.trim(),
        descripcion: v.descripcion.trim() || null,
        correoContacto: v.correoContacto.trim(),
        logoUrl: personaliza ? v.logoUrl.trim() || null : guardada.logoUrl ?? null,
        colorPrincipal: personaliza ? v.colorPrincipal.toUpperCase() : guardada.colorPrincipal ?? null,
        permiteInvitados: v.permiteInvitados,
        plantilla: v.plantilla,
      });
      setGuardada(data);
      setV(desdeTienda(data));
      avisar('Tienda actualizada');
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  // Sin "personalizar" en el plan, la tienda se ve con el color de Vendely y sin logo.
  const color = personaliza && /^#[0-9A-Fa-f]{6}$/.test(v.colorPrincipal) ? v.colorPrincipal : '#303030';
  const texto = colorTextoSobre(color);
  const logo = personaliza ? v.logoUrl.trim() : '';

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Personalizar tienda</h1>
          <p className="text-[13px] text-[#616161]">Así se ve tu tienda online para tus clientes.</p>
        </div>
        <div className="flex items-center gap-3">
          <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={onCambiarTienda} />
          <a
            href={`${window.location.origin}/tienda/${tienda.subdominio}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 rounded-lg border border-[#ddd] bg-white px-3 py-1.5 text-[13px] font-medium hover:bg-[#f7f7f7]"
          >
            Ver tienda <IconoEnlaceExterno tamano={14} />
          </a>
        </div>
      </div>

      {error && <Mensaje tipo="error" texto={error} />}

      <div className="grid gap-4 lg:grid-cols-[380px_1fr]">
        <form onSubmit={guardar} noValidate className={`${TARJETA} space-y-4 p-4`}>
          <CampoPanel etiqueta="Nombre de la tienda" error={errores.nombre}>
            <input value={v.nombre} onChange={(e) => cambiar('nombre', e.target.value)} className={inputPanel(errores.nombre)} />
          </CampoPanel>
          <CampoPanel
            etiqueta="Descripción (se muestra en la portada)"
            error={errores.descripcion}
            ayuda={`${v.descripcion.length} / ${MAX_DESCRIPCION}`}
          >
            <textarea
              rows={3}
              value={v.descripcion}
              onChange={(e) => cambiar('descripcion', e.target.value)}
              className={inputPanel(errores.descripcion)}
            />
          </CampoPanel>
          {!personaliza && (
            <p className="rounded-lg border border-[#ffd79d] bg-[#fff8e6] px-3 py-2 text-[12px] text-[#4f4700]">
              Tu plan {tienda.plan} usa los colores de Vendely y no muestra logo.{' '}
              <Link to="/panel/plan" className="font-semibold underline">Mejora tu plan</Link> para usar tu marca.
            </p>
          )}
          <fieldset disabled={!personaliza} className={`space-y-4 ${personaliza ? '' : 'opacity-50'}`}>
          <CampoPanel etiqueta="Logo (enlace de la imagen)" error={errores.logoUrl} ayuda={logoRoto ? 'No se pudo cargar esa imagen: revisa el enlace' : 'Opcional. Una imagen cuadrada se ve mejor'}>
            <input
              value={v.logoUrl}
              onChange={(e) => cambiar('logoUrl', e.target.value)}
              placeholder="https://..."
              className={inputPanel(errores.logoUrl)}
            />
          </CampoPanel>
          <div>
            <span className="text-[13px] text-[#303030]">Color principal</span>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              {COLORES.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => cambiar('colorPrincipal', c)}
                  aria-label={`Color ${c}`}
                  className={`h-7 w-7 rounded-full border-2 ${v.colorPrincipal.toUpperCase() === c ? 'border-[#1650d0] ring-2 ring-[#c9d7f8]' : 'border-white shadow'}`}
                  style={{ background: c }}
                />
              ))}
              <input
                type="color"
                value={color}
                onChange={(e) => cambiar('colorPrincipal', e.target.value.toUpperCase())}
                aria-label="Elegir otro color"
                className="h-7 w-9 cursor-pointer rounded border border-[#ddd] bg-white"
              />
              <input
                value={v.colorPrincipal}
                onChange={(e) => cambiar('colorPrincipal', e.target.value)}
                className={`${inputPanel(errores.colorPrincipal)} !w-24 font-mono`}
                aria-label="Código del color"
              />
            </div>
            {errores.colorPrincipal && <span className="mt-1 block text-[12px] text-[#8e1f0b]">{errores.colorPrincipal}</span>}
          </div>
          </fieldset>
          <div>
            <span className="text-[13px] text-[#303030]">Plantilla</span>
            <div className="mt-1 grid gap-2">
              {PLANTILLAS.map((p) => {
                const bloqueada = !conPlantillas && p.valor !== 'clasica';
                const elegida = v.plantilla === p.valor;
                return (
                  <button
                    key={p.valor}
                    type="button"
                    disabled={bloqueada}
                    onClick={() => cambiar('plantilla', p.valor)}
                    className={`rounded-lg border px-3 py-2 text-left ${
                      elegida ? 'border-[#1650d0] bg-[#eaf4ff]' : 'border-[#ddd] bg-white hover:bg-[#f7f7f7]'
                    } disabled:cursor-not-allowed disabled:opacity-60`}
                  >
                    <span className="flex items-center justify-between text-[13px] font-semibold text-[#101010]">
                      {p.nombre}
                      {bloqueada && <span className="text-[11px] font-medium text-[#8a8a8a]">Solo plan Pro</span>}
                    </span>
                    <span className="block text-[12px] text-[#616161]">{p.descripcion}</span>
                  </button>
                );
              })}
            </div>
            {!conPlantillas && (
              <p className="mt-1 text-[12px] text-[#616161]">
                <Link to="/panel/plan" className="font-semibold underline">Mejora a Pro</Link> para elegir Moderna o Boutique.
              </p>
            )}
          </div>
          <CampoPanel etiqueta="Correo de contacto" error={errores.correoContacto} ayuda="Se muestra en el pie de la tienda">
            <input value={v.correoContacto} onChange={(e) => cambiar('correoContacto', e.target.value)} className={inputPanel(errores.correoContacto)} />
          </CampoPanel>
          <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
            La dirección de la tienda (<b>{tienda.subdominio}</b>.vendely.pe) no se cambia: tus clientes ya la conocen.
          </p>
          <div className="flex justify-end">
            <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar'}</button>
          </div>
        </form>

        {/* Vista previa: una versión pequeña de la tienda pública con los datos del formulario */}
        <div className={`${TARJETA} overflow-hidden`}>
          <p className="border-b border-[#ebebeb] px-4 py-2.5 text-[12px] font-medium text-[#616161]">Vista previa</p>
          <div className="text-[#303030]">
            <div className="py-1.5 text-center text-[11px]" style={{ background: color, color: texto }}>Envíos a todo el país</div>
            <div className="flex items-center justify-between border-b border-[#e3e3e3] px-4 py-3">
              <span className="text-[12px]">Inicio · Catálogo</span>
              <span className={`flex items-center gap-2 font-semibold ${v.plantilla === 'boutique' ? 'letra-serifa text-lg font-medium' : v.plantilla === 'moderna' ? 'letra-ancha' : ''}`}>
                {logo && !logoRoto && (
                  <img src={logo} alt="" onError={() => setLogoRoto(true)} className="h-7 w-7 rounded object-contain" />
                )}
                {v.nombre || 'Tu tienda'}
              </span>
              <IconoCarrito tamano={18} />
            </div>
            <PortadaPrevia plantilla={v.plantilla} nombre={v.nombre || 'Tu tienda'} descripcion={v.descripcion} color={color} texto={texto} />
            <div className={`grid grid-cols-3 p-4 ${v.plantilla === 'boutique' ? 'gap-5' : 'gap-3'}`}>
              {[1, 2, 3].map((n) => (
                <div
                  key={n}
                  className={`space-y-1.5 ${v.plantilla === 'moderna' ? 'rounded-xl p-2 shadow-[0_4px_16px_rgba(0,0,0,0.08)]' : ''}`}
                >
                  <div className={`aspect-square bg-[#f1f1f1] ${v.plantilla === 'boutique' ? '' : v.plantilla === 'moderna' ? 'rounded-lg' : 'rounded-md'}`} />
                  <div className={`h-2.5 w-3/4 rounded bg-[#e3e3e3] ${v.plantilla === 'boutique' ? 'mx-auto' : ''}`} />
                  <div className={`h-2.5 w-1/3 rounded bg-[#e3e3e3] ${v.plantilla === 'boutique' ? 'mx-auto' : ''}`} />
                </div>
              ))}
            </div>
            <div className="px-4 pb-4">
              <span
                className={`block py-2 text-center text-sm font-medium ${v.plantilla === 'moderna' ? 'rounded-full' : v.plantilla === 'boutique' ? 'mx-auto w-fit border-b !py-0.5' : 'rounded-md'}`}
                style={v.plantilla === 'boutique' ? { borderColor: '#222', color: '#222' } : v.plantilla === 'moderna' ? { background: '#141414', color: '#fff' } : { background: color, color: texto }}
              >
                Agregar al carrito
              </span>
            </div>
            {!personaliza && <p className="pb-3 text-center text-[11px] text-[#8a8a8a]">© 2026 · Hecho con Vendely</p>}
          </div>
        </div>
      </div>
    </section>
  );
}

/** Portada de la vista previa: versión pequeña de las portadas de components/tienda/Vitrina*.jsx. */
function PortadaPrevia({ plantilla, nombre, descripcion, color, texto }) {
  if (plantilla === 'moderna') {
    return (
      <div className="grid grid-cols-[1.2fr_1fr] items-center gap-4 px-5 py-10" style={{ background: color, color: texto }}>
        <div className="space-y-3">
          <p className="letra-ancha text-3xl leading-none">{nombre}</p>
          {descripcion && <p className="text-xs opacity-85">{descripcion}</p>}
          <span className="inline-block rounded-full px-4 py-1.5 text-xs font-semibold" style={{ background: texto, color }}>
            Ver productos
          </span>
        </div>
        <div className="relative h-32">
          <span className="absolute left-0 top-0 h-24 w-20 -rotate-3 rounded-2xl bg-[#d9d4cc] ring-2 ring-white" />
          <span className="absolute bottom-0 right-2 h-20 w-16 rotate-3 rounded-2xl bg-[#3b4a63] ring-2 ring-white" />
          <span className="absolute right-4 top-1 h-10 w-10 rotate-6 rounded-xl bg-[#b9bab4] ring-2 ring-white" />
        </div>
      </div>
    );
  }
  if (plantilla === 'boutique') {
    return (
      <div className="flex h-56 items-end bg-[linear-gradient(to_top,#1c1a18,#6d635a)] px-6 pb-6 text-white">
        <div className="space-y-3">
          <p className="letra-serifa text-4xl leading-none">{nombre}</p>
          <span className="inline-block border border-white px-4 py-1.5 text-[11px] tracking-[0.12em]">Ver la colección</span>
        </div>
      </div>
    );
  }
  return (
    <div className="space-y-3 px-6 py-12 text-center" style={{ background: color, color: texto }}>
      <p className="text-2xl font-semibold">{nombre}</p>
      {descripcion && <p className="text-sm opacity-85">{descripcion}</p>}
      <span className="inline-block rounded-md px-4 py-2 text-sm font-medium" style={{ background: texto, color }}>
        Ver productos
      </span>
    </div>
  );
}
