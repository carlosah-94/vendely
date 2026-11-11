import { useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import { IconoCarrito, IconoEnlaceExterno } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { colorTextoSobre } from '../utils/formato';
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
    if (v.logoUrl.trim() && !/^https?:\/\/.+/.test(v.logoUrl.trim())) er.logoUrl = 'Pega un enlace que empiece con http:// o https://';
    if (!/^#[0-9A-Fa-f]{6}$/.test(v.colorPrincipal)) er.colorPrincipal = 'Formato #RRGGBB, por ejemplo #1D4ED8';
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      const { data } = await api.put(`/panel/tiendas/${idTienda}`, {
        nombre: v.nombre.trim(),
        descripcion: v.descripcion.trim() || null,
        correoContacto: v.correoContacto.trim(),
        logoUrl: v.logoUrl.trim() || null,
        colorPrincipal: v.colorPrincipal.toUpperCase(),
        permiteInvitados: v.permiteInvitados,
      });
      setV(desdeTienda(data));
      avisar('Tienda actualizada');
    } catch (ex) {
      if (ex.response?.data?.campos) setErrores(ex.response.data.campos);
      else setError(mensajeDeError(ex));
    } finally {
      setGuardando(false);
    }
  }

  const color = /^#[0-9A-Fa-f]{6}$/.test(v.colorPrincipal) ? v.colorPrincipal : '#303030';
  const texto = colorTextoSobre(color);

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
              <span className="flex items-center gap-2 font-semibold">
                {v.logoUrl.trim() && !logoRoto && (
                  <img src={v.logoUrl.trim()} alt="" onError={() => setLogoRoto(true)} className="h-7 w-7 rounded object-contain" />
                )}
                {v.nombre || 'Tu tienda'}
              </span>
              <IconoCarrito tamano={18} />
            </div>
            <div className="space-y-3 px-6 py-12 text-center" style={{ background: color, color: texto }}>
              <p className="text-2xl font-semibold">{v.nombre || 'Tu tienda'}</p>
              {v.descripcion && <p className="text-sm opacity-85">{v.descripcion}</p>}
              <span className="inline-block rounded-md px-4 py-2 text-sm font-medium" style={{ background: texto, color }}>
                Ver productos
              </span>
            </div>
            <div className="grid grid-cols-3 gap-3 p-4">
              {[1, 2, 3].map((n) => (
                <div key={n} className="space-y-1.5">
                  <div className="aspect-square rounded-md bg-[#f1f1f1]" />
                  <div className="h-2.5 w-3/4 rounded bg-[#e3e3e3]" />
                  <div className="h-2.5 w-1/3 rounded bg-[#e3e3e3]" />
                </div>
              ))}
            </div>
            <div className="px-4 pb-4">
              <span className="block rounded-md py-2 text-center text-sm font-medium" style={{ background: color, color: texto }}>
                Agregar al carrito
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
