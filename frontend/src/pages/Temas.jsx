import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { IconoEnlaceExterno } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import TiendaEnVivo from '../components/TiendaEnVivo';
import { DISPOSITIVOS, SelectorDispositivo } from '../components/VistaPreviaTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, TARJETA } from '../utils/panel';
import { TEMAS, TEMAS_RUBRO, borradorTema, cuerpoAplicarTema, esTemaRubro, planPermiteTema, temaActual } from '../utils/temas';
import { nombreRubro } from '../utils/rubros';

/**
 * "Tienda online → Temas", como la de Shopify: arriba el tema que la tienda tiene publicado (la
 * tienda real, en vivo) y debajo "Descubre temas", con una miniatura en vivo de cada tema hecha con
 * los productos de la tienda. Cada tema se puede ver en grande (sin guardar nada) y aplicar.
 *
 * Aplicar = PUT /api/panel/tiendas/{id} con la plantilla y el estilo del tema (utils/temas.js).
 * El backend valida el plan; si el tema no entra en el plan, el botón lleva a mejorarlo.
 */
export default function Temas() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando, error } = useTiendaActual();

  if (error) return <Mensaje tipo="error" texto={error} />;
  if (cargando) return <p className="text-[#616161]">Cargando...</p>;
  if (!tienda) return <Mensaje tipo="info" texto="Primero crea una tienda." />;

  return <Galeria key={idTienda} tienda={tienda} tiendas={tiendas} idTienda={idTienda} onCambiarTienda={cambiarTienda} />;
}

function Galeria({ tienda: inicial, tiendas, idTienda, onCambiarTienda }) {
  const { avisar } = useToast();
  // La tienda se actualiza aquí al aplicar un tema (useTiendaActual no se vuelve a pedir).
  const [tienda, setTienda] = useState(inicial);
  const [aplicando, setAplicando] = useState('');
  const [error, setError] = useState('');
  const [enGrande, setEnGrande] = useState(null);
  // Cambia al aplicar un tema: la tienda en vivo de arriba se vuelve a cargar con lo publicado.
  const [version, setVersion] = useState(0);

  const { tema: actual, personalizado } = temaActual(tienda);
  const url = `${window.location.origin}/tienda/${tienda.subdominio}`;
  // Plantillas por rubro (V22): primero la de su rubro (incluida en su plan), luego las demás.
  const suRubro = TEMAS_RUBRO.find((t) => t.rubro === tienda.rubro);
  const otrosRubros = TEMAS_RUBRO.filter((t) => t !== suRubro);

  async function aplicar(tema) {
    setAplicando(tema.valor);
    setError('');
    try {
      const { data } = await api.put(`/panel/tiendas/${idTienda}`, cuerpoAplicarTema(tienda, tema));
      setTienda(data);
      setVersion((v) => v + 1);
      setEnGrande(null);
      avisar(`Tema ${tema.nombre} aplicado`);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (ex) {
      setError(mensajeDeError(ex));
    } finally {
      setAplicando('');
    }
  }

  return (
    <section className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Temas</h1>
          <p className="text-[13px] text-[#616161]">Elige cómo se ve tu tienda. Después ajusta los detalles en Personalizar.</p>
        </div>
        <div className="flex items-center gap-3">
          <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={onCambiarTienda} />
          <a href={url} target="_blank" rel="noreferrer" className={`${BOTON_SECUNDARIO} inline-flex items-center gap-1 text-[13px]`}>
            Ver tienda <IconoEnlaceExterno tamano={14} />
          </a>
        </div>
      </div>

      {error && <Mensaje tipo="error" texto={error} />}

      {/* ---------------------------------------------------------------- Tema actual */}
      <div className={`${TARJETA} overflow-hidden`}>
        <div className="relative">
          <TiendaEnVivo key={version} subdominio={tienda.subdominio} interactiva={false} titulo="Tu tienda publicada"
            className="aspect-[16/8] w-full" />
          {/* Franja inferior con los datos del tema, sobre la tienda, como en Shopify */}
          <div className="absolute inset-x-0 bottom-0 flex flex-wrap items-end justify-between gap-3 bg-gradient-to-t from-black/75 via-black/45 to-transparent px-5 pb-4 pt-16 text-white">
            <div>
              <p className="text-[12px] font-medium uppercase tracking-wide text-white/70">Tema actual</p>
              <p className="text-xl font-semibold">
                {actual.nombre}{personalizado && <span className="font-normal text-white/80"> (personalizado)</span>}
              </p>
              <p className="text-[12px] text-white/75">
                {esTemaRubro(actual.valor) ? 'Plantilla por rubro'
                  : `Plantilla ${tienda.plantilla === 'moderna' ? 'Moderna' : tienda.plantilla === 'boutique' ? 'Boutique' : 'Clásica'}`}
                {tienda.temaAplicadoEl && <> · Aplicado el {formatoFecha(tienda.temaAplicadoEl)}</>}
                {' · '}Plan {tienda.plan}
              </p>
            </div>
            <div className="flex gap-2">
              <a href={url} target="_blank" rel="noreferrer" className="rounded-lg bg-white/15 px-3 py-1.5 text-[13px] font-semibold backdrop-blur hover:bg-white/25">
                Ver tienda
              </a>
              <Link to="/panel/personalizar" className="rounded-lg bg-white px-3 py-1.5 text-[13px] font-semibold text-[#101010] hover:bg-[#f1f1f1]">
                Personalizar
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* ---------------------------------------------------------------- Plantillas por rubro */}
      <div>
        <div className="mb-3">
          <h2 className="text-[15px] font-semibold text-[#101010]">Plantillas para tu rubro</h2>
          <p className="text-[12px] text-[#616161]">
            Más que colores: cada una cambia la portada, los beneficios y trae una sección pensada para ese negocio.
            {suRubro && <> La de <b>{nombreRubro(tienda.rubro)}</b> está incluida en todos los planes.</>}
          </p>
        </div>
        {suRubro && (
          <TarjetaRubroDestacada
            tema={suRubro}
            tienda={tienda}
            esActual={suRubro.valor === actual.valor && !personalizado}
            aplicando={aplicando === suRubro.valor}
            onVer={() => setEnGrande(suRubro)}
            onAplicar={() => aplicar(suRubro)}
          />
        )}
        <div className={`grid gap-x-4 gap-y-6 sm:grid-cols-2 lg:grid-cols-3 ${suRubro ? 'mt-6' : ''}`}>
          {otrosRubros.map((tema) => (
            <TarjetaTema
              key={tema.valor}
              tema={tema}
              tienda={tienda}
              esActual={tema.valor === actual.valor && !personalizado}
              aplicando={aplicando === tema.valor}
              onVer={() => setEnGrande(tema)}
              onAplicar={() => aplicar(tema)}
            />
          ))}
        </div>
      </div>

      {/* ---------------------------------------------------------------- Descubre temas */}
      <div>
        <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 className="text-[15px] font-semibold text-[#101010]">Descubre temas</h2>
            <p className="text-[12px] text-[#616161]">Se muestran con tus productos. Tu logo, tu portada, el anuncio y las secciones se mantienen.</p>
          </div>
          <Link to="/panel/plan" className="text-[13px] font-medium text-[#1650d0] hover:underline">Ver planes</Link>
        </div>
        <div className="grid gap-x-4 gap-y-6 sm:grid-cols-2 lg:grid-cols-3">
          {TEMAS.map((tema) => (
            <TarjetaTema
              key={tema.valor}
              tema={tema}
              tienda={tienda}
              esActual={tema.valor === actual.valor && !personalizado}
              aplicando={aplicando === tema.valor}
              onVer={() => setEnGrande(tema)}
              onAplicar={() => aplicar(tema)}
            />
          ))}
        </div>
      </div>

      {enGrande && (
        <VistaGrande
          tema={enGrande}
          tienda={tienda}
          aplicando={aplicando === enGrande.valor}
          onAplicar={() => aplicar(enGrande)}
          onCerrar={() => setEnGrande(null)}
        />
      )}
    </section>
  );
}

/** El plan que pide un tema. Una plantilla por rubro: gratis la de su rubro; la de otro rubro, Pro. */
function planDe(tema, tienda) {
  if (tema.plan !== 'Rubro') return tema.plan;
  return tema.rubro === tienda?.rubro ? 'Gratis' : 'Pro';
}

function EtiquetaPlan({ tema, tienda }) {
  const plan = planDe(tema, tienda);
  const tono = plan === 'Pro' ? 'bg-[#efe9ff] text-[#5b21b6]' : plan === 'Básico' ? 'bg-[#e6f0ff] text-[#1e40af]' : 'bg-[#eaf7ee] text-[#166534]';
  const texto = plan !== 'Gratis' ? `Plan ${plan}` : tema.plan === 'Rubro' ? 'Incluida en tu plan' : 'Todos los planes';
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${tono}`}>{texto}</span>;
}

/** El botón principal de un tema: Aplicar, "Tema actual" o "Mejora a ..." si el plan no lo incluye. */
function BotonTema({ tema, tienda, esActual, aplicando, onAplicar, grande }) {
  const clase = grande ? 'px-4 py-2 text-[13px]' : 'px-3 py-1.5 text-[13px]';
  if (esActual) {
    return <span className={`rounded-lg bg-[#f1f1f1] font-semibold text-[#616161] ${clase}`}>Tema actual</span>;
  }
  if (!planPermiteTema(tema, tienda)) {
    return (
      <Link to="/panel/plan" className={`rounded-lg border border-[#ddd] bg-white font-semibold text-[#303030] hover:bg-[#f7f7f7] ${clase}`}>
        Mejora a {planDe(tema, tienda)}
      </Link>
    );
  }
  return (
    <button type="button" onClick={onAplicar} disabled={aplicando} className={`${BOTON} ${clase}`}>
      {aplicando ? 'Aplicando...' : 'Aplicar'}
    </button>
  );
}

function TarjetaTema({ tema, tienda, esActual, aplicando, onVer, onAplicar }) {
  const borrador = useMemo(() => borradorTema(tienda, tema), [tienda, tema]);
  return (
    <article>
      <button
        type="button"
        onClick={onVer}
        aria-label={`Ver el tema ${tema.nombre} en grande`}
        className={`group relative block w-full overflow-hidden rounded-xl text-left shadow-[0_0_0_1px_rgba(0,0,0,0.08)] transition hover:shadow-[0_0_0_1px_rgba(0,0,0,0.12),0_16px_32px_-16px_rgba(0,0,0,0.35)] ${
          esActual ? 'ring-2 ring-[#1650d0]' : ''
        }`}
      >
        <TiendaEnVivo subdominio={tienda.subdominio} borrador={borrador} interactiva={false}
          titulo={`Tema ${tema.nombre}`} className="aspect-[4/3.4] w-full" />
        <span className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition group-hover:bg-black/25 group-hover:opacity-100">
          <span className="rounded-lg bg-white px-3 py-1.5 text-[13px] font-semibold text-[#101010] shadow">Vista previa</span>
        </span>
      </button>
      <div className="mt-2.5 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2 text-[14px] font-semibold text-[#101010]">
            {tema.nombre} <EtiquetaPlan tema={tema} tienda={tienda} />
          </p>
          <p className="text-[12px] text-[#616161]">por Vendely</p>
        </div>
        <BotonTema tema={tema} tienda={tienda} esActual={esActual} aplicando={aplicando} onAplicar={onAplicar} />
      </div>
      <p className="mt-1 text-[12px] leading-snug text-[#616161]">{tema.descripcion}</p>
    </article>
  );
}

/** La plantilla del rubro de la tienda, en grande y primero: es la recomendada para su negocio. */
function TarjetaRubroDestacada({ tema, tienda, esActual, aplicando, onVer, onAplicar }) {
  const borrador = useMemo(() => borradorTema(tienda, tema), [tienda, tema]);
  return (
    <article className={`grid overflow-hidden rounded-xl bg-white shadow-[0_0_0_1px_rgba(0,0,0,0.08)] md:grid-cols-[1.6fr_1fr] ${esActual ? 'ring-2 ring-[#1650d0]' : ''}`}>
      <button type="button" onClick={onVer} aria-label={`Ver la plantilla ${tema.nombre} en grande`} className="group relative block">
        <TiendaEnVivo subdominio={tienda.subdominio} borrador={borrador} interactiva={false}
          titulo={`Plantilla ${tema.nombre}`} className="aspect-[16/10] w-full" />
        <span className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition group-hover:bg-black/25 group-hover:opacity-100">
          <span className="rounded-lg bg-white px-3 py-1.5 text-[13px] font-semibold text-[#101010] shadow">Vista previa</span>
        </span>
      </button>
      <div className="flex flex-col justify-center gap-3 p-6">
        <span className="w-fit rounded-full bg-[#fff4d6] px-2.5 py-0.5 text-[11px] font-semibold text-[#8a5a00]">Recomendada para {nombreRubro(tienda.rubro)}</span>
        <p className="flex flex-wrap items-center gap-2 text-xl font-bold text-[#101010]">{tema.nombre} <EtiquetaPlan tema={tema} tienda={tienda} /></p>
        <p className="text-[13px] leading-relaxed text-[#616161]">{tema.descripcion}</p>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <BotonTema tema={tema} tienda={tienda} esActual={esActual} aplicando={aplicando} onAplicar={onAplicar} grande />
          <button type="button" onClick={onVer} className={`${BOTON_SECUNDARIO} text-[13px]`}>Vista previa</button>
        </div>
      </div>
    </article>
  );
}

/** El tema en grande, a pantalla completa, para navegar la tienda con él antes de aplicarlo. */
function VistaGrande({ tema, tienda, aplicando, onAplicar, onCerrar }) {
  const [dispositivo, setDispositivo] = useState('escritorio');
  const d = DISPOSITIVOS[dispositivo];
  const borrador = useMemo(() => borradorTema(tienda, tema), [tienda, tema]);
  const { tema: actual, personalizado } = temaActual(tienda);

  useEffect(() => {
    function tecla(e) {
      if (e.key === 'Escape') onCerrar();
    }
    window.addEventListener('keydown', tecla);
    const antes = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', tecla);
      document.body.style.overflow = antes;
    };
  }, [onCerrar]);

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#1a1a1a]/80 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={`Vista previa del tema ${tema.nombre}`}>
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white px-4 py-2.5 shadow">
        <div className="flex items-center gap-3">
          <button type="button" onClick={onCerrar} className={`${BOTON_SECUNDARIO} text-[13px]`}>← Volver</button>
          <div>
            <p className="flex items-center gap-2 text-[14px] font-semibold text-[#101010]">{tema.nombre} <EtiquetaPlan tema={tema} tienda={tienda} /></p>
            <p className="text-[12px] text-[#616161]">Vista previa con tus productos · no se guarda nada</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <SelectorDispositivo valor={dispositivo} onCambiar={setDispositivo} />
          <BotonTema tema={tema} tienda={tienda} aplicando={aplicando} onAplicar={onAplicar} grande
            esActual={tema.valor === actual.valor && !personalizado} />
        </div>
      </div>
      <div className="flex min-h-0 flex-1 justify-center p-4">
        <TiendaEnVivo
          key={dispositivo}
          subdominio={tienda.subdominio}
          borrador={borrador}
          ancho={dispositivo === 'movil' ? d.ancho : 1440}
          titulo={`Tema ${tema.nombre} en grande`}
          className={`h-full rounded-lg shadow-2xl ${dispositivo === 'movil' ? 'w-[390px] max-w-full' : 'w-full max-w-[1440px]'}`}
        />
      </div>
    </div>
  );
}
