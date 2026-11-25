import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import CampoPanel from '../components/CampoPanel';
import ContenidoPlantilla, { contenidoParaEnviar, formularioContenido, validarContenido } from '../components/ContenidoPlantilla';
import { IconoEnlaceExterno } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import VistaPreviaTienda from '../components/VistaPreviaTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { revisarArchivo, subirImagen } from '../services/imagenes';
import { BOTONES, DISENO_INICIAL, FONDOS, FUENTES, disenoDe, enlaceSeguro } from '../utils/diseno';
import { listarCategorias } from '../services/categorias';
import { esTemaRubro, rubroIntacto, temaActual } from '../utils/temas';
import { BOTON, TARJETA, inputPanel } from '../utils/panel';
import { PATRON_WHATSAPP, normalizarWhatsapp } from '../utils/whatsapp';

const COLORES = ['#303030', '#1D4ED8', '#0F766E', '#B91C1C', '#7C3AED', '#C2410C', '#DB2777', '#F59E0B'];
const MAX_DESCRIPCION = 2000;
const MAX_ANUNCIO = 120;
const HEX = /^#[0-9A-Fa-f]{6}$/;
const ES_URL = /^https?:\/\/.+/;
const ENLACE = /^(\/|#|https?:\/\/)\S{0,300}$/;

/** El diseño Pro guardado, como campos del formulario ('' en vez de null; siempre 3 mensajes). */
function formularioPro(pro) {
  const t = (x) => x ?? '';
  const mensajes = [...(pro.anuncio.mensajes ?? []), '', '', ''].slice(0, 3);
  return {
    portada: {
      activa: pro.portada.activa, titulo: t(pro.portada.titulo), subtitulo: t(pro.portada.subtitulo),
      boton1Texto: t(pro.portada.boton1Texto), boton1Enlace: t(pro.portada.boton1Enlace),
      boton2Texto: t(pro.portada.boton2Texto), boton2Enlace: t(pro.portada.boton2Enlace),
      alineacion: pro.portada.alineacion, oscurecer: pro.portada.oscurecer, alto: pro.portada.alto,
    },
    anuncio: { color: t(pro.anuncio.color), mensajes },
    promo: {
      mostrar: pro.promo.mostrar, titulo: t(pro.promo.titulo), texto: t(pro.promo.texto),
      botonTexto: t(pro.promo.botonTexto), botonEnlace: t(pro.promo.botonEnlace), color: t(pro.promo.color),
    },
    categoriasEnMenu: pro.categoriasEnMenu,
    mostrarDescuento: pro.mostrarDescuento,
  };
}

/**
 * Del formulario a lo que se guarda (o se ve en la vista previa): textos sin espacios de más,
 * vacíos como null. Con soloValido, un color o un enlace a medio escribir no se manda.
 */
function proParaEnviar(f, soloValido = false) {
  const t = (x) => (x.trim() ? x.trim() : null);
  const color = (x) => (!x.trim() ? null : soloValido && !HEX.test(x.trim()) ? null : x.trim().toUpperCase());
  const enlace = (x) => (!x.trim() ? null : soloValido && !enlaceSeguro(x) ? null : x.trim());
  return {
    portada: {
      activa: f.portada.activa, titulo: t(f.portada.titulo), subtitulo: t(f.portada.subtitulo),
      boton1Texto: t(f.portada.boton1Texto), boton1Enlace: enlace(f.portada.boton1Enlace),
      boton2Texto: t(f.portada.boton2Texto), boton2Enlace: enlace(f.portada.boton2Enlace),
      alineacion: f.portada.alineacion, oscurecer: Number(f.portada.oscurecer), alto: f.portada.alto,
    },
    anuncio: { color: color(f.anuncio.color), mensajes: f.anuncio.mensajes.map((m) => m.trim()).filter(Boolean) },
    promo: {
      mostrar: f.promo.mostrar, titulo: t(f.promo.titulo), texto: t(f.promo.texto),
      botonTexto: t(f.promo.botonTexto), botonEnlace: enlace(f.promo.botonEnlace), color: color(f.promo.color),
    },
    categoriasEnMenu: f.categoriasEnMenu,
    mostrarDescuento: f.mostrarDescuento,
  };
}

/** Datos del formulario a partir de la tienda guardada (los textos vacíos como '' para los inputs). */
function desdeTienda(t) {
  const d = disenoDe(t);
  return {
    nombre: t.nombre ?? '',
    descripcion: t.descripcion ?? '',
    correoContacto: t.correoContacto ?? '',
    whatsapp: t.whatsapp ?? '',
    logoUrl: t.logoUrl ?? '',
    colorPrincipal: t.colorPrincipal ?? '#303030',
    permiteInvitados: t.permiteInvitados ?? true,
    plantilla: t.plantilla ?? 'clasica',
    colorAcento: d.colorAcento ?? '',
    textoAnuncio: d.textoAnuncio ?? '',
    mostrarAnuncio: d.mostrarAnuncio,
    imagenPortadaUrl: d.imagenPortadaUrl ?? '',
    fuente: d.fuente,
    estiloBotones: d.estiloBotones,
    fondo: d.fondo,
    mostrarGarantias: d.mostrarGarantias,
    mostrarDestacado: d.mostrarDestacado,
    mostrarCategorias: d.mostrarCategorias,
    mostrarSeccionRubro: d.mostrarSeccionRubro !== false,
    contenido: formularioContenido(d.contenidoRubro, t.tema),
    pro: formularioPro(d.pro),
  };
}

/**
 * "Tienda online → Personalizar": datos, marca, portada, estilo y secciones del tema que la
 * tienda tiene publicado, con una vista previa de la tienda real que cambia mientras editas (como el
 * editor de temas de Shopify). Se guarda con PUT /api/panel/tiendas/{id}.
 *
 * La plantilla (Clásica, Moderna o Boutique) NO se elige aquí: viene del tema, y el tema se cambia
 * en "Temas". Como en Shopify: en la biblioteca eliges el tema y aquí lo personalizas.
 *
 * Qué entra en cada plan (el backend lo vuelve a validar y responde "Mejora tu plan"):
 *   Gratis: datos y secciones · Básico: + marca, portada, anuncio, letra y botones ·
 *   Pro: + color de fondo (y, en Temas, los temas con plantilla Moderna o Boutique). Ver utils/diseno.js.
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
  const personaliza = tienda.permitePersonalizar !== false;
  const conPlantillas = tienda.permitePlantillas !== false;
  // Plan Gratis con la plantilla de su rubro intacta: la vista previa la muestra con sus colores.
  const rubroFijo = !personaliza && rubroIntacto(tienda);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [subiendo, setSubiendo] = useState('');

  function cambiar(campo, valor) {
    setV((actual) => ({ ...actual, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));
  }

  /** Cambia una opción del diseño Pro: cambiarPro('portada', 'titulo', 'Black Friday'). campo null = opción suelta. */
  function cambiarPro(bloque, campo, valor) {
    setV((actual) => ({
      ...actual,
      pro: campo === null
        ? { ...actual.pro, [bloque]: valor }
        : { ...actual.pro, [bloque]: { ...actual.pro[bloque], [campo]: valor } },
    }));
    setErrores((e) => ({ ...e, [`pro.${bloque}.${campo}`]: undefined }));
  }

  /**
   * Sube el logo o la portada y lo deja en el campo. No se guarda la tienda todavía: el dueño lo
   * ve en la vista previa y decide. Si nunca guarda, la foto queda en Cloudinary sin usarse; es
   * el mismo caso que una foto de producto que no se guarda, y pesa poco.
   */
  async function subir(campo, archivo) {
    const problema = revisarArchivo(archivo);
    if (problema) {
      setErrores((er) => ({ ...er, [campo]: problema }));
      return;
    }
    setSubiendo(campo);
    setErrores((er) => ({ ...er, [campo]: undefined }));
    try {
      const { url } = await subirImagen(idTienda, archivo);
      cambiar(campo, url);
    } catch (err) {
      setErrores((er) => ({ ...er, [campo]: mensajeDeError(err) }));
    } finally {
      setSubiendo('');
    }
  }

  async function guardar(e) {
    e.preventDefault();
    const er = {};
    if (!v.nombre.trim()) er.nombre = 'El nombre es obligatorio';
    if (v.descripcion.length > MAX_DESCRIPCION) er.descripcion = `Hasta ${MAX_DESCRIPCION} caracteres`;
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.correoContacto.trim())) er.correoContacto = 'El correo no es válido';
    const whatsapp = normalizarWhatsapp(v.whatsapp, tienda.idPais);
    if (whatsapp && !PATRON_WHATSAPP.test(whatsapp)) er.whatsapp = 'Escribe tu número de celular, por ejemplo 987654321';
    if (personaliza) {
      if (v.logoUrl.trim() && !ES_URL.test(v.logoUrl.trim())) er.logoUrl = 'Pega un enlace que empiece con http:// o https://';
      if (v.imagenPortadaUrl.trim() && !ES_URL.test(v.imagenPortadaUrl.trim())) er.imagenPortadaUrl = 'Pega un enlace que empiece con http:// o https://';
      if (!HEX.test(v.colorPrincipal)) er.colorPrincipal = 'Formato #RRGGBB, por ejemplo #1D4ED8';
      if (v.colorAcento && !HEX.test(v.colorAcento)) er.colorAcento = 'Formato #RRGGBB, por ejemplo #DB2777';
      if (v.textoAnuncio.length > MAX_ANUNCIO) er.textoAnuncio = `Hasta ${MAX_ANUNCIO} caracteres`;
    }
    if (conPlantillas) {
      const p = v.pro;
      for (const [bloque, campo] of [['portada', 'boton1Enlace'], ['portada', 'boton2Enlace'], ['promo', 'botonEnlace']]) {
        const x = p[bloque][campo].trim();
        if (x && !ENLACE.test(x)) er[`pro.${bloque}.${campo}`] = 'Empieza con # (ej. #catalogo), con / o con https://';
      }
      if (p.anuncio.color && !HEX.test(p.anuncio.color)) er['pro.anuncio.color'] = 'Formato #RRGGBB';
      if (p.promo.color && !HEX.test(p.promo.color)) er['pro.promo.color'] = 'Formato #RRGGBB';
      if (p.promo.mostrar && !p.promo.titulo.trim() && !p.promo.texto.trim()) {
        er['pro.promo.titulo'] = 'Escribe un título o un texto para la promoción';
      }
    }
    // Contenido de la plantilla por rubro (V23): cualquier plan.
    const conRubro = esTemaRubro(tienda.tema);
    if (conRubro) Object.assign(er, validarContenido(v.contenido));
    setErrores(er);
    if (Object.keys(er).length) return;

    setGuardando(true);
    setError('');
    try {
      // Lo que el plan no incluye no se envía (null = no cambia): así no se pierde lo que la
      // tienda ya tenía guardado de un plan anterior y el backend no responde "Mejora tu plan".
      const { data } = await api.put(`/panel/tiendas/${idTienda}`, {
        nombre: v.nombre.trim(),
        descripcion: v.descripcion.trim() || null,
        correoContacto: v.correoContacto.trim(),
        whatsapp,
        logoUrl: personaliza ? v.logoUrl.trim() || null : tienda.logoUrl ?? null,
        colorPrincipal: personaliza ? v.colorPrincipal.toUpperCase() : tienda.colorPrincipal ?? null,
        permiteInvitados: v.permiteInvitados,
        plantilla: v.plantilla,
        ...(personaliza && {
          colorAcento: v.colorAcento.toUpperCase(),
          textoAnuncio: v.textoAnuncio.trim(),
          mostrarAnuncio: v.mostrarAnuncio,
          imagenPortadaUrl: v.imagenPortadaUrl.trim(),
          fuente: v.fuente,
          estiloBotones: v.estiloBotones,
        }),
        ...(conPlantillas && { fondo: v.fondo, disenoPro: proParaEnviar(v.pro) }),
        mostrarGarantias: v.mostrarGarantias,
        mostrarDestacado: v.mostrarDestacado,
        mostrarCategorias: v.mostrarCategorias,
        mostrarSeccionRubro: v.mostrarSeccionRubro,
        ...(conRubro && { contenidoRubro: contenidoParaEnviar(v.contenido) }),
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

  // Lo que se ve en la vista previa: el borrador con las mismas reglas de plan que la tienda real
  // (TiendaPublicaResponse). Un color a medio escribir no se manda hasta que sea válido.
  const borrador = useMemo(() => ({
    nombre: v.nombre || 'Tu tienda',
    descripcion: v.descripcion,
    logoUrl: personaliza && ES_URL.test(v.logoUrl.trim()) ? v.logoUrl.trim() : null,
    // La plantilla de su rubro, sin cambios, se ve con sus colores en cualquier plan (como la vitrina).
    colorPrincipal: personaliza && HEX.test(v.colorPrincipal) ? v.colorPrincipal : rubroFijo ? tienda.colorPrincipal : null,
    plantilla: conPlantillas ? v.plantilla : 'clasica',
    permiteInvitados: v.permiteInvitados,
    selloVendely: !personaliza,
    diseno: {
      ...DISENO_INICIAL,
      ...(rubroFijo && {
        colorAcento: disenoDe(tienda).colorAcento, fuente: disenoDe(tienda).fuente, estiloBotones: disenoDe(tienda).estiloBotones,
      }),
      ...(personaliza && {
        colorAcento: HEX.test(v.colorAcento) ? v.colorAcento : null,
        textoAnuncio: v.textoAnuncio.trim() || null,
        mostrarAnuncio: v.mostrarAnuncio,
        imagenPortadaUrl: ES_URL.test(v.imagenPortadaUrl.trim()) ? v.imagenPortadaUrl.trim() : null,
        fuente: v.fuente,
        estiloBotones: v.estiloBotones,
      }),
      fondo: conPlantillas ? v.fondo : 'blanco',
      mostrarGarantias: v.mostrarGarantias,
      mostrarDestacado: v.mostrarDestacado,
      mostrarCategorias: v.mostrarCategorias,
      mostrarSeccionRubro: v.mostrarSeccionRubro,
      contenidoRubro: contenidoParaEnviar(v.contenido),
      ...(conPlantillas && { pro: proParaEnviar(v.pro, true) }),
    },
  }), [v, personaliza, conPlantillas, rubroFijo, tienda]);

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Personalizar tienda</h1>
          <p className="text-[13px] text-[#616161]">Así se ve tu tienda online para tus clientes. Plan actual: <b>{tienda.plan}</b>.</p>
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

      <div className="grid items-start gap-4 lg:grid-cols-[400px_1fr]">
        <form onSubmit={guardar} noValidate className="space-y-4">
          {/* ---------------------------------------------------------------- Tema */}
          <TemaActual tienda={tienda} conPlantillas={conPlantillas} />

          {/* ---------------------------------------------------------------- Datos */}
          <Seccion titulo="Datos de la tienda">
            <CampoPanel etiqueta="Nombre de la tienda" error={errores.nombre}>
              <input value={v.nombre} onChange={(e) => cambiar('nombre', e.target.value)} className={inputPanel(errores.nombre)} />
            </CampoPanel>
            <CampoPanel
              etiqueta="Descripción (se muestra en la portada)"
              error={errores.descripcion}
              ayuda={`${v.descripcion.length} / ${MAX_DESCRIPCION}`}
            >
              <textarea rows={3} value={v.descripcion} onChange={(e) => cambiar('descripcion', e.target.value)} className={inputPanel(errores.descripcion)} />
            </CampoPanel>
            <CampoPanel etiqueta="Correo de contacto" error={errores.correoContacto} ayuda="Se muestra en el pie de la tienda">
              <input value={v.correoContacto} onChange={(e) => cambiar('correoContacto', e.target.value)} className={inputPanel(errores.correoContacto)} />
            </CampoPanel>
            <CampoPanel
              etiqueta="WhatsApp de la tienda"
              error={errores.whatsapp}
              ayuda="Opcional. Tus clientes verán un botón para escribirte y preguntar por un producto. Si no pones código de país, usamos el de tu tienda."
            >
              <input
                value={v.whatsapp}
                onChange={(e) => cambiar('whatsapp', e.target.value)}
                inputMode="tel"
                placeholder={tienda.idPais === 'CL' ? '+56 9 1234 5678' : '+51 987 654 321'}
                className={inputPanel(errores.whatsapp)}
              />
            </CampoPanel>
            <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
              La dirección de la tienda (<b>{tienda.subdominio}</b>.vendely.pe) no se cambia: tus clientes ya la conocen.
            </p>
          </Seccion>

          {/* ---------------------------------------------------------------- Marca (Básico) */}
          <Seccion titulo="Marca" plan="Básico" bloqueada={!personaliza}
            aviso="Tu plan usa los colores de Vendely y no muestra logo.">
            <CampoImagen
              etiqueta="Logo"
              ayuda="Opcional. Una imagen cuadrada se ve mejor. JPG, PNG o WEBP, hasta 2 MB"
              valor={v.logoUrl}
              error={errores.logoUrl}
              subiendo={subiendo === 'logoUrl'}
              onCambiar={(x) => cambiar('logoUrl', x)}
              onSubir={(archivo) => subir('logoUrl', archivo)}
            />
            <SelectorColor etiqueta="Color principal" ayuda="Portada, barra de anuncio y botones"
              valor={v.colorPrincipal} error={errores.colorPrincipal} onCambiar={(c) => cambiar('colorPrincipal', c)} />
            <SelectorColor etiqueta="Color de acento" ayuda="Etiquetas de oferta y detalles. Vacío = el color principal"
              valor={v.colorAcento} error={errores.colorAcento} opcional onCambiar={(c) => cambiar('colorAcento', c)} />
          </Seccion>

          {/* ---------------------------------------------------------------- Portada y anuncio (Básico) */}
          <Seccion titulo="Portada y anuncio" plan="Básico" bloqueada={!personaliza}>
            <CampoImagen
              etiqueta="Imagen de portada"
              ayuda="Opcional. Una foto horizontal de tu marca o tus productos (al menos 1600 px de ancho)"
              valor={v.imagenPortadaUrl}
              error={errores.imagenPortadaUrl}
              subiendo={subiendo === 'imagenPortadaUrl'}
              ancha
              onCambiar={(x) => cambiar('imagenPortadaUrl', x)}
              onSubir={(archivo) => subir('imagenPortadaUrl', archivo)}
            />
            <Interruptor etiqueta="Mostrar la barra de anuncio" valor={v.mostrarAnuncio} onCambiar={(x) => cambiar('mostrarAnuncio', x)} />
            {v.mostrarAnuncio && (
              <CampoPanel
                etiqueta="Texto del anuncio"
                error={errores.textoAnuncio}
                ayuda={`Vacío = a dónde envías ("Envíos a todo Perú"). ${v.textoAnuncio.length} / ${MAX_ANUNCIO}`}
              >
                <input
                  value={v.textoAnuncio}
                  maxLength={MAX_ANUNCIO}
                  placeholder="Ej.: Envío gratis en compras desde S/ 150"
                  onChange={(e) => cambiar('textoAnuncio', e.target.value)}
                  className={inputPanel(errores.textoAnuncio)}
                />
              </CampoPanel>
            )}
          </Seccion>

          {/* ---------------------------------------------------------------- Estilo (Básico; fondo Pro) */}
          <Seccion titulo="Estilo" plan="Básico" bloqueada={!personaliza}>
            <div>
              <span className="text-[13px] text-[#303030]">Tipografía</span>
              <div className="mt-1 grid grid-cols-2 gap-2">
                {FUENTES.map((f) => (
                  <Opcion key={f.valor} elegida={v.fuente === f.valor} onClick={() => cambiar('fuente', f.valor)}>
                    <span className="block text-[17px] leading-tight text-[#101010]" style={{ fontFamily: f.muestra, fontWeight: f.valor === 'elegante' ? 500 : 700 }}>
                      {f.valor === 'auto' ? 'Aa' : f.nombre}
                    </span>
                    <span className="block text-[11px] text-[#616161]">{f.descripcion ?? f.nombre}</span>
                  </Opcion>
                ))}
              </div>
            </div>
            <div>
              <span className="text-[13px] text-[#303030]">Botones y bordes</span>
              <div className="mt-1 grid grid-cols-4 gap-2">
                {BOTONES.map((b) => (
                  <Opcion key={b.valor} elegida={v.estiloBotones === b.valor} onClick={() => cambiar('estiloBotones', b.valor)} centrada>
                    <span
                      className="mx-auto mb-1 block h-5 w-12 bg-[#303030]"
                      style={{ borderRadius: b.radio ?? '9999px', opacity: b.radio ? 1 : 0.35 }}
                    />
                    <span className="text-[11px] text-[#303030]">{b.nombre}</span>
                  </Opcion>
                ))}
              </div>
            </div>
            <div>
              <span className="flex items-center justify-between text-[13px] text-[#303030]">
                Color de fondo
                {!conPlantillas && <EtiquetaPlan plan="Pro" />}
              </span>
              <div className="mt-1 grid grid-cols-4 gap-2">
                {FONDOS.map((f) => (
                  <Opcion
                    key={f.valor}
                    elegida={(conPlantillas ? v.fondo : 'blanco') === f.valor}
                    disabled={!conPlantillas && f.valor !== 'blanco'}
                    onClick={() => cambiar('fondo', f.valor)}
                    centrada
                  >
                    <span className="mx-auto mb-1 block h-6 w-6 rounded-full border border-[#d4d4d4]" style={{ background: f.color }} />
                    <span className="text-[11px] text-[#303030]">{f.nombre}</span>
                  </Opcion>
                ))}
              </div>
            </div>
          </Seccion>

          {/* ---------------------------------------------------------------- Diseño Pro */}
          <DisenoPro
            pro={v.pro}
            errores={errores}
            bloqueada={!conPlantillas}
            idTienda={idTienda}
            subdominio={tienda.subdominio}
            hayPortada={Boolean(v.imagenPortadaUrl.trim())}
            onCambiar={cambiarPro}
          />

          {/* ---------------------------------------------------------------- Contenido de la plantilla (todos) */}
          {esTemaRubro(tienda.tema) && (
            <Seccion titulo="Contenido de tu plantilla">
              <ContenidoPlantilla tema={tienda.tema} valor={v.contenido} errores={errores}
                onCambiar={(x) => { setV((a) => ({ ...a, contenido: x })); setErrores((e) => Object.fromEntries(Object.entries(e).filter(([k]) => !k.startsWith('contenidoRubro')))); }} />
            </Seccion>
          )}

          {/* ---------------------------------------------------------------- Secciones (todos) */}
          <Seccion titulo="Secciones de la portada">
            <Interruptor etiqueta="Beneficios" ayuda="Envíos, formas de pago y compra sin cuenta" valor={v.mostrarGarantias}
              onCambiar={(x) => cambiar('mostrarGarantias', x)} />
            <Interruptor etiqueta="Producto destacado" ayuda="Con 4 o más productos, uno en grande (primero los que están en oferta)" valor={v.mostrarDestacado}
              onCambiar={(x) => cambiar('mostrarDestacado', x)} />
            <Interruptor etiqueta="Categorías" ayuda="Accesos a cada categoría en la portada" valor={v.mostrarCategorias}
              onCambiar={(x) => cambiar('mostrarCategorias', x)} />
            {esTemaRubro(tienda.tema) && (
              <Interruptor etiqueta="Sección de tu rubro" valor={v.mostrarSeccionRubro}
                ayuda="La sección pensada para tu negocio: cómo pedir y recojo (comida), marcas y garantía (tecnología), guía de tallas (moda), rutina y preguntas (belleza)..."
                onCambiar={(x) => cambiar('mostrarSeccionRubro', x)} />
            )}
          </Seccion>

          <div className="sticky bottom-0 -mx-1 flex items-center justify-end gap-3 bg-[#f1f1f1]/90 px-1 py-3 backdrop-blur">
            <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : 'Guardar'}</button>
          </div>
        </form>

        <div className={`${TARJETA} overflow-hidden lg:sticky lg:top-[4.5rem]`}>
          <VistaPreviaTienda subdominio={tienda.subdominio} borrador={borrador} />
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------ diseño Pro

/**
 * "Diseño Pro": portada a medida, barra de anuncio con varios mensajes y color, franja de
 * promoción, categorías en el menú y etiqueta "-25 %". Los enlaces de los botones se eligen de
 * una lista (el catálogo, cada categoría) o se escriben.
 */
function DisenoPro({ pro, errores, bloqueada, idTienda, subdominio, hayPortada, onCambiar }) {
  const [categorias, setCategorias] = useState([]);
  useEffect(() => {
    let vigente = true;
    listarCategorias(idTienda)
      .then((lista) => vigente && setCategorias(lista.filter((c) => c.estado === 'ACTIVA')))
      .catch(() => vigente && setCategorias([]));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  const destinos = [
    { valor: '#catalogo', texto: 'Catálogo' },
    ...categorias.map((c) => ({ valor: `/tienda/${subdominio}?categoria=${c.slug}#catalogo`, texto: `Categoría ${c.nombre}` })),
  ];
  const p = pro.portada;
  const error = (bloque, campo) => errores[`pro.${bloque}.${campo}`];

  return (
    <Seccion titulo="Diseño Pro" plan="Pro" bloqueada={bloqueada}
      aviso="Portada a medida, anuncios que rotan, franja de promoción y más.">
      <datalist id="destinos-tienda">
        {destinos.map((d) => <option key={d.valor} value={d.valor}>{d.texto}</option>)}
      </datalist>

      {/* Portada a medida */}
      <div className="space-y-3 rounded-lg border border-[#ebebeb] p-3">
        <Interruptor etiqueta="Portada a medida" ayuda="Un banner a todo lo ancho con tu título y tus botones"
          valor={p.activa} onCambiar={(x) => onCambiar('portada', 'activa', x)} />
        {p.activa && (
          <>
            {!hayPortada && (
              <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
                Sube una <b>imagen de portada</b> (arriba, en &quot;Portada y anuncio&quot;) para que se vea de fondo.
              </p>
            )}
            <CampoPanel etiqueta="Título" ayuda="Vacío = el nombre de la tienda">
              <input value={p.titulo} maxLength={90} placeholder="Ej.: Black Friday: hasta 40 % menos"
                onChange={(e) => onCambiar('portada', 'titulo', e.target.value)} className={inputPanel()} />
            </CampoPanel>
            <CampoPanel etiqueta="Texto" ayuda="Vacío = la descripción de la tienda">
              <textarea rows={2} value={p.subtitulo} maxLength={200} placeholder="Ej.: Solo este fin de semana, en toda la tienda"
                onChange={(e) => onCambiar('portada', 'subtitulo', e.target.value)} className={inputPanel()} />
            </CampoPanel>
            {[1, 2].map((n) => (
              <div key={n} className="grid grid-cols-[1fr_1.3fr] gap-2">
                <CampoPanel etiqueta={`Botón ${n}`}>
                  <input value={p[`boton${n}Texto`]} maxLength={30} placeholder={n === 1 ? 'Comprar ahora' : 'Opcional'}
                    onChange={(e) => onCambiar('portada', `boton${n}Texto`, e.target.value)} className={inputPanel()} />
                </CampoPanel>
                <CampoPanel etiqueta="Lleva a" error={error('portada', `boton${n}Enlace`)}>
                  <input list="destinos-tienda" value={p[`boton${n}Enlace`]} placeholder="#catalogo"
                    onChange={(e) => onCambiar('portada', `boton${n}Enlace`, e.target.value)}
                    className={inputPanel(error('portada', `boton${n}Enlace`))} />
                </CampoPanel>
              </div>
            ))}
            <div>
              <span className="text-[13px] text-[#303030]">Alineación</span>
              <div className="mt-1 grid grid-cols-2 gap-2">
                {[['izquierda', 'Izquierda'], ['centro', 'Centro']].map(([valor, texto]) => (
                  <Opcion key={valor} elegida={p.alineacion === valor} onClick={() => onCambiar('portada', 'alineacion', valor)} centrada>
                    <span className="text-[12px]">{texto}</span>
                  </Opcion>
                ))}
              </div>
            </div>
            <div>
              <span className="text-[13px] text-[#303030]">Alto</span>
              <div className="mt-1 grid grid-cols-3 gap-2">
                {[['medio', 'Medio'], ['alto', 'Alto'], ['pantalla', 'Pantalla completa']].map(([valor, texto]) => (
                  <Opcion key={valor} elegida={p.alto === valor} onClick={() => onCambiar('portada', 'alto', valor)} centrada>
                    <span className="text-[12px]">{texto}</span>
                  </Opcion>
                ))}
              </div>
            </div>
            {hayPortada && (
              <label className="block">
                <span className="flex justify-between text-[13px] text-[#303030]">
                  Oscurecer la imagen <span className="text-[#616161]">{p.oscurecer} %</span>
                </span>
                <input type="range" min={0} max={80} step={5} value={p.oscurecer}
                  onChange={(e) => onCambiar('portada', 'oscurecer', Number(e.target.value))} className="mt-1 w-full accent-[#303030]" />
                <span className="block text-[12px] text-[#616161]">Más oscuro = el texto blanco se lee mejor</span>
              </label>
            )}
          </>
        )}
      </div>

      {/* Barra de anuncio */}
      <div className="space-y-3 rounded-lg border border-[#ebebeb] p-3">
        <p className="text-[13px] font-medium text-[#303030]">Barra de anuncio</p>
        <SelectorColor etiqueta="Color de la barra" ayuda="Vacío = el color principal" opcional
          valor={pro.anuncio.color} error={error('anuncio', 'color')} onCambiar={(c) => onCambiar('anuncio', 'color', c)} />
        <div className="space-y-2">
          <span className="text-[13px] text-[#303030]">Mensajes que van rotando (hasta 3)</span>
          {pro.anuncio.mensajes.map((m, i) => (
            <input key={i} value={m} maxLength={120}
              placeholder={['Ej.: Envío gratis desde S/ 150', 'Ej.: Cambios gratis en 30 días', 'Opcional'][i]}
              onChange={(e) => onCambiar('anuncio', 'mensajes', pro.anuncio.mensajes.map((x, j) => (j === i ? e.target.value : x)))}
              className={inputPanel()} aria-label={`Mensaje ${i + 1}`} />
          ))}
          <span className="block text-[12px] text-[#616161]">Si escribes mensajes aquí, reemplazan el texto del anuncio de arriba.</span>
        </div>
      </div>

      {/* Franja de promoción */}
      <div className="space-y-3 rounded-lg border border-[#ebebeb] p-3">
        <Interruptor etiqueta="Franja de promoción" ayuda="Debajo de la portada, a todo lo ancho"
          valor={pro.promo.mostrar} onCambiar={(x) => onCambiar('promo', 'mostrar', x)} />
        {pro.promo.mostrar && (
          <>
            <CampoPanel etiqueta="Título" error={error('promo', 'titulo')}>
              <input value={pro.promo.titulo} maxLength={80} placeholder="Ej.: 50 % en packs exclusivos"
                onChange={(e) => onCambiar('promo', 'titulo', e.target.value)} className={inputPanel(error('promo', 'titulo'))} />
            </CampoPanel>
            <CampoPanel etiqueta="Texto (opcional)">
              <input value={pro.promo.texto} maxLength={200} placeholder="Ej.: Y hasta 40 % en el resto por tiempo limitado"
                onChange={(e) => onCambiar('promo', 'texto', e.target.value)} className={inputPanel()} />
            </CampoPanel>
            <div className="grid grid-cols-[1fr_1.3fr] gap-2">
              <CampoPanel etiqueta="Botón (opcional)">
                <input value={pro.promo.botonTexto} maxLength={30} placeholder="Ver packs"
                  onChange={(e) => onCambiar('promo', 'botonTexto', e.target.value)} className={inputPanel()} />
              </CampoPanel>
              <CampoPanel etiqueta="Lleva a" error={error('promo', 'botonEnlace')}>
                <input list="destinos-tienda" value={pro.promo.botonEnlace} placeholder="#catalogo"
                  onChange={(e) => onCambiar('promo', 'botonEnlace', e.target.value)} className={inputPanel(error('promo', 'botonEnlace'))} />
              </CampoPanel>
            </div>
            <SelectorColor etiqueta="Color de la franja" ayuda="Vacío = el color de acento" opcional
              valor={pro.promo.color} error={error('promo', 'color')} onCambiar={(c) => onCambiar('promo', 'color', c)} />
          </>
        )}
      </div>

      <Interruptor etiqueta="Categorías en el menú" ayuda="Hasta 5 categorías como enlaces en la cabecera (en pantallas grandes)"
        valor={pro.categoriasEnMenu} onCambiar={(x) => onCambiar('categoriasEnMenu', null, x)} />
      <Interruptor etiqueta="Mostrar el % de descuento" ayuda="En los productos rebajados: «-25 %» en lugar de «Oferta»"
        valor={pro.mostrarDescuento} onCambiar={(x) => onCambiar('mostrarDescuento', null, x)} />
    </Seccion>
  );
}

// ------------------------------------------------------------------ piezas del formulario

function EtiquetaPlan({ plan }) {
  return (
    <Link to="/panel/plan" className="rounded-full bg-[#fff1d6] px-2 py-0.5 text-[11px] font-semibold text-[#7a4b00] hover:underline">
      Plan {plan}
    </Link>
  );
}

/** Tarjeta del formulario. bloqueada: el plan no la incluye (se ve, pero no se puede editar). */
function Seccion({ titulo, plan, bloqueada, etiquetaPlan = bloqueada, aviso, children }) {
  return (
    <fieldset className={`${TARJETA} space-y-4 p-4`}>
      <legend className="sr-only">{titulo}</legend>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-[14px] font-semibold text-[#101010]">{titulo}</h2>
        {plan && etiquetaPlan && <EtiquetaPlan plan={plan} />}
      </div>
      {bloqueada && (
        <p className="rounded-lg border border-[#ffd79d] bg-[#fff8e6] px-3 py-2 text-[12px] text-[#4f4700]">
          {aviso ?? `Disponible desde el plan ${plan}.`}{' '}
          <Link to="/panel/plan" className="font-semibold underline">Mejora tu plan</Link> para usarlo.
        </p>
      )}
      <fieldset disabled={bloqueada} className={`space-y-4 ${bloqueada ? 'opacity-50' : ''}`}>
        {children}
      </fieldset>
    </fieldset>
  );
}

function Opcion({ elegida, disabled, onClick, centrada, children }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      aria-pressed={elegida}
      className={`rounded-lg border px-3 py-2 ${centrada ? 'text-center' : 'text-left'} ${
        elegida ? 'border-[#1650d0] bg-[#eaf4ff] ring-1 ring-[#1650d0]' : 'border-[#ddd] bg-white hover:bg-[#f7f7f7]'
      } disabled:cursor-not-allowed disabled:opacity-50`}
    >
      {children}
    </button>
  );
}

function Interruptor({ etiqueta, ayuda, valor, onCambiar }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-3">
      <span>
        <span className="block text-[13px] text-[#303030]">{etiqueta}</span>
        {ayuda && <span className="block text-[12px] text-[#616161]">{ayuda}</span>}
      </span>
      <input type="checkbox" checked={valor} onChange={(e) => onCambiar(e.target.checked)} className="peer sr-only" />
      <span className="relative h-5 w-9 shrink-0 rounded-full bg-[#cfcfcf] transition peer-checked:bg-[#303030] peer-focus-visible:ring-2 peer-focus-visible:ring-[#1650d0] after:absolute after:left-0.5 after:top-0.5 after:h-4 after:w-4 after:rounded-full after:bg-white after:transition peer-checked:after:translate-x-4" />
    </label>
  );
}

function SelectorColor({ etiqueta, ayuda, valor, error, opcional, onCambiar }) {
  const valido = HEX.test(valor);
  return (
    <div>
      <span className="text-[13px] text-[#303030]">{etiqueta}</span>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        {COLORES.map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => onCambiar(c)}
            aria-label={`${etiqueta} ${c}`}
            className={`h-7 w-7 rounded-full border-2 ${valor.toUpperCase() === c ? 'border-[#1650d0] ring-2 ring-[#c9d7f8]' : 'border-white shadow'}`}
            style={{ background: c }}
          />
        ))}
        <input
          type="color"
          value={valido ? valor : '#303030'}
          onChange={(e) => onCambiar(e.target.value.toUpperCase())}
          aria-label={`${etiqueta}: elegir otro color`}
          className="h-7 w-9 cursor-pointer rounded border border-[#ddd] bg-white"
        />
        <input
          value={valor}
          onChange={(e) => onCambiar(e.target.value)}
          placeholder={opcional ? 'Ninguno' : '#303030'}
          className={`${inputPanel(error)} !w-24 font-mono`}
          aria-label={`${etiqueta}: código`}
        />
        {opcional && valor && (
          <button type="button" onClick={() => onCambiar('')} className="text-[12px] font-medium text-[#616161] underline">Quitar</button>
        )}
      </div>
      {error ? (
        <span className="mt-1 block text-[12px] text-[#8e1f0b]">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-[12px] text-[#616161]">{ayuda}</span>
      )}
    </div>
  );
}

function CampoImagen({ etiqueta, ayuda, valor, error, subiendo, ancha, onCambiar, onSubir }) {
  const campo = useRef(null);
  const [rota, setRota] = useState('');
  const url = valor.trim();
  return (
    <CampoPanel etiqueta={etiqueta} error={error} ayuda={rota === url && url ? 'No se pudo cargar esa imagen: revisa el enlace' : ayuda}>
      {url && ES_URL.test(url) && rota !== url && (
        <div className="relative mb-2">
          <img
            src={url}
            alt=""
            onError={() => setRota(url)}
            className={`${ancha ? 'aspect-[16/7] w-full object-cover' : 'h-14 w-14 object-contain'} rounded-lg border border-[#e3e3e3] bg-[#f7f7f7]`}
          />
          <button
            type="button"
            onClick={() => onCambiar('')}
            className="absolute right-1.5 top-1.5 rounded-md bg-white/90 px-2 py-0.5 text-[11px] font-medium shadow-sm hover:bg-white"
            style={ancha ? undefined : { left: '4rem', right: 'auto' }}
          >
            Quitar
          </button>
        </div>
      )}
      <div className="flex gap-2">
        <input value={valor} onChange={(e) => onCambiar(e.target.value)} placeholder="https://... o sube una imagen" className={inputPanel(error)} />
        <button
          type="button"
          onClick={() => campo.current?.click()}
          disabled={subiendo}
          className="shrink-0 rounded-lg border border-[#ddd] bg-white px-3 text-[13px] font-medium hover:bg-[#f7f7f7] disabled:opacity-50"
        >
          {subiendo ? 'Subiendo...' : 'Subir'}
        </button>
        <input
          ref={campo}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            if (e.target.files?.[0]) onSubir(e.target.files[0]);
            e.target.value = '';
          }}
        />
      </div>
    </CampoPanel>
  );
}

/**
 * Qué tema se está personalizando y el camino para cambiarlo. Reemplaza al antiguo selector de
 * plantilla: tenerlo aquí Y en Temas confundía (elegías Pop en Temas y aquí aparecía "Moderna").
 */
function TemaActual({ tienda, conPlantillas }) {
  const { tema, personalizado } = temaActual(tienda);
  const plantilla = tienda.plantilla === 'moderna' ? 'Moderna' : tienda.plantilla === 'boutique' ? 'Boutique' : 'Clásica';
  const oculta = !conPlantillas && tienda.plantilla !== 'clasica';
  return (
    <div className={`${TARJETA} space-y-2 p-4`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[12px] text-[#616161]">Estás personalizando</p>
          <p className="text-[15px] font-semibold text-[#101010]">
            Tema {tema.nombre}
            {personalizado && <span className="font-normal text-[#616161]"> (con tus cambios)</span>}
          </p>
          <p className="text-[12px] text-[#616161]">Plantilla {plantilla}</p>
        </div>
        <Link to="/panel/temas" className="shrink-0 rounded-lg border border-[#ddd] bg-white px-3 py-1.5 text-[13px] font-semibold text-[#303030] hover:bg-[#f7f7f7]">
          Cambiar tema
        </Link>
      </div>
      {oculta && (
        <p className="rounded-lg bg-[#fff4e5] px-3 py-2 text-[12px] text-[#7a4b00]">
          Tu plan ya no incluye la plantilla {plantilla}: tus clientes ven la Clásica. Lo que elegiste queda
          guardado y vuelve si <Link to="/panel/plan" className="font-semibold underline">mejoras a Pro</Link>.
        </p>
      )}
    </div>
  );
}
