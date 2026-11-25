import { Link } from 'react-router-dom';
import { formatoMoneda } from '../../../utils/formato';
import { BotonWhatsappRubro, FotoO, Icono, fotosDe, textosDe } from './comun';

/**
 * Portada de cada plantilla por rubro. Cada una tiene su propia composición, no solo otro color:
 * se ve bien con fotos de productos y también en una tienda recién creada, sin productos
 * (entonces usa ilustraciones propias). Los colores de marca salen de --tienda y --acento, así un
 * dueño con plan Básico o Pro puede cambiarlos en Personalizar.
 */

function Logo({ tienda, className = '' }) {
  if (!tienda.logoUrl) return null;
  return <img src={tienda.logoUrl} alt="" className={`h-14 w-14 rounded-2xl bg-white object-contain p-1.5 shadow ${className}`} />;
}

// ------------------------------------------------------------------- Horno (comida y postres)

function IlustracionTorta() {
  return (
    <svg viewBox="0 0 200 200" className="h-full w-full" aria-hidden="true">
      <ellipse cx="100" cy="160" rx="78" ry="14" fill="#000" opacity="0.08" />
      <rect x="38" y="104" width="124" height="52" rx="12" fill="#FDE7C8" />
      <rect x="38" y="104" width="124" height="18" rx="9" fill="var(--tienda)" />
      <path d="M38 122c10 10 20 10 31 0s21-10 31 0 21 10 31 0 21-10 31 0" stroke="var(--tienda)" strokeWidth="10" fill="none" strokeLinecap="round" />
      <rect x="54" y="62" width="92" height="44" rx="10" fill="#FFF7EC" />
      <path d="M54 78c8 8 15 8 23 0s15-8 23 0 15 8 23 0 15-8 23 0" stroke="var(--acento)" strokeWidth="8" fill="none" strokeLinecap="round" />
      <rect x="96" y="34" width="8" height="26" rx="3" fill="#fff" stroke="var(--tienda)" strokeWidth="2" />
      <path d="M100 18c6 7 6 12 0 15-6-3-6-8 0-15z" fill="var(--acento)" />
      <circle cx="70" cy="96" r="5" fill="#D9363E" /><circle cx="130" cy="96" r="5" fill="#D9363E" /><circle cx="100" cy="98" r="5" fill="#D9363E" />
    </svg>
  );
}

function SelloCircular({ texto, className = '' }) {
  return (
    <div className={`giro-lento flex items-center justify-center rounded-full bg-[var(--acento)] text-[var(--acento-texto)] shadow-xl ${className}`}>
      <svg viewBox="0 0 120 120" className="h-full w-full" aria-hidden="true">
        <defs><path id="circulo-sello" d="M60 60m-44 0a44 44 0 1 1 88 0a44 44 0 1 1-88 0" /></defs>
        <text fontSize="12" fontWeight="700" letterSpacing="2.6" fill="currentColor">
          {/* textLength: el texto ocupa justo la vuelta, sea corto o largo. */}
          <textPath href="#circulo-sello" textLength="268" lengthAdjust="spacingAndGlyphs">{texto}</textPath>
        </text>
        <path d="M60 46l4 9 10 1-7.5 6.5 2.3 9.8L60 67l-8.8 5.3 2.3-9.8L46 56l10-1z" fill="currentColor" />
      </svg>
    </div>
  );
}

function PortadaHorno({ tienda, todos, categorias, subdominio }) {
  const t = textosDe(tienda);
  const [principal, segunda] = fotosDe(todos, 2);
  return (
    <section className="relative isolate overflow-hidden bg-[#FFF4E6] text-[#2B1609]">
      <span className="absolute inset-0 -z-10 opacity-60 [background-image:radial-gradient(#F1D3B0_1.2px,transparent_1.2px)] [background-size:22px_22px]" />
      <span className="absolute -left-32 top-10 -z-10 h-96 w-96 rounded-full bg-[var(--acento)] opacity-20 blur-3xl" />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 pb-24 pt-14 md:grid-cols-[1.15fr_1fr] md:pb-32 md:pt-20">
        <div className="space-y-7">
          <Logo tienda={tienda} />
          <p className="inline-flex items-center gap-2 rounded-full border border-[#E9C9A5] bg-white/70 px-4 py-1.5 text-sm font-semibold text-[var(--tienda)]">
            <span className="relative flex h-2.5 w-2.5"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--acento)] opacity-75" /><span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-[var(--acento)]" /></span>
            {t.etiqueta}
          </p>
          <h1 className="relative break-words text-[clamp(3.2rem,8.5vw,6.8rem)] font-bold leading-[0.9] tracking-tight">
            {tienda.nombre}
            <svg viewBox="0 0 300 20" className="mt-2 block h-4 w-2/3 max-w-xs text-[var(--acento)]" aria-hidden="true">
              <path d="M2 14C40 2 70 2 100 10s60 8 98-2 66-6 100 4" stroke="currentColor" strokeWidth="6" fill="none" strokeLinecap="round" />
            </svg>
          </h1>
          {tienda.descripcion && <p className="max-w-lg text-lg leading-relaxed text-[#6B4A35]">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda bg-[var(--tienda)] px-8 py-3.5 font-semibold text-[var(--tienda-texto)] shadow-[0_14px_30px_-12px_var(--tienda)] transition hover:-translate-y-0.5">
              {t.boton}
            </a>
            <BotonWhatsappRubro tienda={tienda} />
          </div>
          <dl className="flex flex-wrap gap-x-8 gap-y-3 pt-2 text-sm">
            {categorias.slice(0, 3).map((c) => (
              <div key={c.slug} className="flex items-center gap-2"><span className="h-1.5 w-1.5 rounded-full bg-[var(--tienda)]" /><dt className="font-semibold">{c.nombre}</dt></div>
            ))}
          </dl>
        </div>

        <div className="relative mx-auto aspect-square w-full max-w-[520px]">
          <span className="absolute inset-[6%] rounded-full bg-[var(--tienda)]" />
          <span className="absolute inset-[3%] rounded-full border-2 border-dashed border-[var(--tienda)] opacity-40" />
          <div className="flotar absolute inset-[14%] overflow-hidden rounded-full border-[10px] border-white bg-[#FFF7EC] shadow-[0_40px_80px_-30px_rgba(80,30,0,0.6)]">
            <FotoO foto={principal}><div className="p-8"><IlustracionTorta /></div></FotoO>
          </div>
          <SelloCircular texto={t.sello.length <= 13 ? `${t.sello} • ${t.sello} • `.toUpperCase() : `${t.sello} • `.toUpperCase()} className="absolute -right-1 top-2 h-28 w-28 sm:h-32 sm:w-32" />
          {segunda ? (
            <Link to={`/tienda/${subdominio}/producto/${segunda.idProducto}`} className="absolute -left-2 bottom-6 flex items-center gap-3 rounded-2xl bg-white p-2.5 pr-5 shadow-xl transition hover:-translate-y-1 sm:left-0">
              <img src={segunda.imagenUrl} alt="" className="h-14 w-14 rounded-xl object-cover" />
              <span className="text-sm"><b className="block max-w-[10rem] truncate">{segunda.nombre}</b><span className="text-[var(--tienda)]">{formatoMoneda(segunda.precio)}</span></span>
            </Link>
          ) : (
            <span className="absolute -left-2 bottom-8 flex items-center gap-2 rounded-2xl bg-white px-4 py-3 text-sm font-semibold shadow-xl sm:left-0">
              <Icono nombre="reloj" tamano={20} className="text-[var(--tienda)]" /> Pide con {t.anticipacion} de anticipación
            </span>
          )}
        </div>
      </div>
      <svg viewBox="0 0 1440 80" preserveAspectRatio="none" className="absolute bottom-0 left-0 h-14 w-full text-[var(--fondo)]" aria-hidden="true">
        <path d="M0 40c120 30 240 30 360 0s240-30 360 0 240 30 360 0 240-30 360 0v40H0z" fill="currentColor" />
      </svg>
    </section>
  );
}

// ------------------------------------------------------------------- Circuito (tecnología)

function PortadaCircuito({ tienda, todos, categorias, subdominio }) {
  const t = textosDe(tienda);
  const [principal] = fotosDe(todos, 1);
  const estrella = principal ?? todos[0];
  const cinta = categorias.length > 0 ? categorias.map((c) => c.nombre) : ['Originales', 'Garantía', 'Boleta o factura', 'Envío seguro'];
  return (
    <section className="relative isolate overflow-hidden bg-[#070A13] text-white">
      <span className="absolute inset-0 -z-10 opacity-[0.35] [background-image:linear-gradient(rgba(255,255,255,0.06)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.06)_1px,transparent_1px)] [background-size:48px_48px] [mask-image:radial-gradient(ellipse_at_center,black_30%,transparent_75%)]" />
      <span className="absolute -top-40 left-1/4 -z-10 h-[30rem] w-[30rem] rounded-full bg-[var(--tienda)] opacity-40 blur-[120px]" />
      <span className="absolute -bottom-40 right-0 -z-10 h-[26rem] w-[26rem] rounded-full bg-[var(--acento)] opacity-25 blur-[120px]" />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 py-20 md:grid-cols-[1.2fr_1fr] md:py-28">
        <div className="space-y-7">
          <Logo tienda={tienda} />
          <p className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-4 py-1.5 text-[13px] font-medium text-white/80 backdrop-blur">
            <span className="rounded-full bg-[var(--acento)] px-2 py-0.5 text-[11px] font-bold text-[var(--acento-texto)]">NUEVO</span>
            {t.etiqueta}
          </p>
          <h1 className="break-words bg-gradient-to-br from-white via-white to-[var(--acento)] bg-clip-text text-[clamp(3rem,7.5vw,6rem)] font-bold leading-[0.95] tracking-tight text-transparent">
            {tienda.nombre}
          </h1>
          {tienda.descripcion && <p className="max-w-lg text-lg leading-relaxed text-white/65">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda bg-[var(--acento)] px-7 py-3.5 font-semibold text-[var(--acento-texto)] shadow-[0_0_40px_-6px_var(--acento)] transition hover:-translate-y-0.5">
              {t.boton} →
            </a>
            <BotonWhatsappRubro tienda={tienda} texto="Consultar stock" mensaje={`Hola ${tienda.nombre}, quiero consultar el stock de un equipo.`} />
          </div>
          <div className="flex flex-wrap gap-2.5 pt-1">
            {[['escudo', `Garantía ${t.garantia}`], ['check', 'Originales'], ['camion', 'Envío seguro']].map(([icono, texto]) => (
              <span key={texto} className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2 font-mono text-[12px] uppercase tracking-wider text-white/75">
                <Icono nombre={icono} tamano={15} className="text-[var(--acento)]" /> {texto}
              </span>
            ))}
          </div>
        </div>

        <div className="relative mx-auto w-full max-w-[420px]">
          <span className="absolute inset-x-10 bottom-0 top-10 rounded-[3rem] bg-[var(--acento)] opacity-30 blur-3xl" />
          <div className="flotar relative mx-auto aspect-[9/17] w-[66%] rounded-[2.6rem] border border-white/15 bg-gradient-to-b from-[#1C2233] to-[#0B0F19] p-2.5 shadow-[0_50px_100px_-30px_rgba(0,0,0,0.9)]">
            <div className="relative h-full overflow-hidden rounded-[2.1rem] bg-[#0E1322]">
              <span className="absolute left-1/2 top-2.5 z-10 h-5 w-20 -translate-x-1/2 rounded-full bg-black" />
              <FotoO foto={principal}>
                <div className="flex h-full flex-col items-center justify-center gap-4 bg-[radial-gradient(circle_at_50%_30%,var(--tienda),transparent_70%)] p-6">
                  <svg viewBox="0 0 64 64" className="h-20 w-20 text-[var(--acento)]" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                    <rect x="16" y="16" width="32" height="32" rx="4" /><rect x="25" y="25" width="14" height="14" rx="2" fill="currentColor" opacity=".35" />
                    <path d="M24 16V8M32 16V8M40 16V8M24 56v-8M32 56v-8M40 56v-8M16 24H8M16 32H8M16 40H8M56 24h-8M56 32h-8M56 40h-8" />
                  </svg>
                  <span className="font-mono text-[11px] uppercase tracking-[0.3em] text-white/60">Tecnología</span>
                </div>
              </FotoO>
            </div>
          </div>
          {estrella && (
            <Link to={`/tienda/${subdominio}/producto/${estrella.idProducto}`} className="absolute -left-2 bottom-12 w-56 rounded-2xl border border-white/15 bg-white/10 p-4 backdrop-blur-xl transition hover:bg-white/15 sm:-left-8">
              <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-[var(--acento)]">Destacado</span>
              <b className="mt-1 block truncate">{estrella.nombre}</b>
              <span className="text-lg font-semibold">{formatoMoneda(estrella.precio)}</span>
            </Link>
          )}
          <span className="absolute -right-2 top-16 rounded-2xl border border-white/15 bg-white/10 px-4 py-3 text-center backdrop-blur-xl sm:-right-6">
            <Icono nombre="escudo" tamano={22} className="mx-auto text-[var(--acento)]" />
            <span className="mt-1 block font-mono text-[10px] uppercase tracking-widest text-white/70">Garantía</span>
          </span>
        </div>
      </div>
      <div className="border-y border-white/10 bg-white/[0.03] py-3.5">
        <div className="flex overflow-hidden">
          <div className="cinta flex shrink-0 gap-10 pr-10 font-mono text-[13px] uppercase tracking-[0.25em] text-white/55">
            {[...cinta, ...cinta, ...cinta, ...cinta].map((t, i) => (
              <span key={i} className="flex items-center gap-10 whitespace-nowrap">{t}<span className="h-1.5 w-1.5 rotate-45 bg-[var(--acento)]" /></span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------- Impulso (deporte)

function PortadaImpulso({ tienda, todos }) {
  const t = textosDe(tienda);
  const [principal, segunda] = fotosDe(todos, 2);
  const lema = [...t.palabras, tienda.nombre.toUpperCase()];
  return (
    <section className="relative isolate overflow-hidden bg-[#0A0A0A] text-white">
      <span className="absolute -right-20 top-0 -z-10 h-full w-2/3 -skew-x-12 bg-[#151515]" />
      <span className="absolute right-[18%] top-0 -z-10 h-full w-4 -skew-x-12 bg-[var(--acento)]" />
      <span className="absolute right-[22%] top-0 -z-10 h-full w-1.5 -skew-x-12 bg-[var(--acento)] opacity-60" />
      <div className="mx-auto grid max-w-7xl items-center gap-10 px-6 pb-28 pt-16 md:grid-cols-[1.25fr_1fr] md:pt-24">
        <div className="space-y-6">
          <Logo tienda={tienda} />
          <p className="inline-block -skew-x-12 bg-[var(--acento)] px-4 py-1.5 text-sm font-extrabold uppercase tracking-widest text-[var(--acento-texto)]">
            <span className="inline-block skew-x-12">{t.etiqueta}</span>
          </p>
          <h1 className="relative break-words text-[clamp(3.4rem,10vw,8.5rem)] font-extrabold uppercase italic leading-[0.85] tracking-tight">
            <span aria-hidden="true" className="absolute left-1.5 top-1.5 -z-10 text-transparent [-webkit-text-stroke:2px_var(--acento)]">{tienda.nombre}</span>
            {tienda.nombre}
          </h1>
          {tienda.descripcion && <p className="max-w-md text-lg text-white/70">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda group inline-flex items-center gap-3 bg-[var(--acento)] px-8 py-4 text-sm font-extrabold uppercase tracking-widest text-[var(--acento-texto)] transition hover:gap-5">
              {t.boton} <span aria-hidden="true">→</span>
            </a>
            <BotonWhatsappRubro tienda={tienda} texto="Asesoría gratis" mensaje={`Hola ${tienda.nombre}, quiero asesoría para elegir.`} />
          </div>
        </div>
        <div className="relative mx-auto h-[420px] w-full max-w-[460px]">
          <div className="absolute inset-y-0 right-0 w-[78%] overflow-hidden [clip-path:polygon(18%_0,100%_0,82%_100%,0_100%)]">
            <FotoO foto={principal}>
              <div className="flex h-full items-center justify-center bg-gradient-to-br from-[#1F1F1F] to-[#0A0A0A]">
                <Icono nombre="rayo" tamano={200} grosor={1.2} className="text-[var(--acento)] drop-shadow-[0_0_40px_var(--acento)]" />
              </div>
            </FotoO>
          </div>
          <div className="absolute bottom-6 left-0 h-40 w-36 overflow-hidden border-4 border-[var(--acento)] bg-[#141414] [clip-path:polygon(14%_0,100%_0,86%_100%,0_100%)]">
            <FotoO foto={segunda}>
              <div className="flex h-full flex-col items-center justify-center text-center">
                <span className="text-5xl font-extrabold italic text-[var(--acento)]">+</span>
                <span className="text-xs font-bold uppercase tracking-widest text-white/70">Rendimiento</span>
              </div>
            </FotoO>
          </div>
        </div>
      </div>
      <div className="absolute bottom-6 left-[-5%] w-[110%] -rotate-2 bg-[var(--acento)] py-3 text-[var(--acento-texto)] shadow-[0_10px_40px_-10px_var(--acento)]">
        <div className="flex overflow-hidden">
          <div className="cinta flex shrink-0 gap-8 pr-8 text-lg font-extrabold uppercase italic tracking-wider">
            {[...lema, ...lema, ...lema, ...lema].map((t, i) => (
              <span key={i} className="flex items-center gap-8 whitespace-nowrap">{t}<Icono nombre="rayo" tamano={18} grosor={2.4} /></span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------- Pasarela (moda)

function PortadaPasarela({ tienda, todos }) {
  const t = textosDe(tienda);
  const fotos = fotosDe(todos, 3);
  const anio = new Date().getFullYear();
  const bloque = (i, clase) => (
    <div className={`relative overflow-hidden bg-[#E7DFD3] ${clase}`}>
      <FotoO foto={fotos[i]} className="transition-transform duration-[1.5s] hover:scale-105">
        <div className={`flex h-full items-end p-5 ${['bg-[linear-gradient(160deg,#D8CBB8,#B9A68C)]', 'bg-[linear-gradient(160deg,#2A2522,#4A3F38)]', 'bg-[linear-gradient(160deg,#E9E1D6,#CDBFAE)]'][i]}`}>
          <span className={`letra-serifa text-3xl italic ${i === 1 ? 'text-white/80' : 'text-[#3B302A]/70'}`}>{['Esencial', 'Nueva', 'Atemporal'][i]}</span>
        </div>
      </FotoO>
    </div>
  );
  return (
    <section className="relative isolate overflow-hidden bg-[#F5F1EA] text-[#1C1917]">
      <div className="mx-auto max-w-7xl px-6 pb-16 pt-10">
        <div className="flex items-center justify-between border-b border-[#1C1917]/15 pb-4 text-[11px] uppercase tracking-[0.35em]">
          <span>{t.etiqueta}</span>
          <span className="hidden sm:inline">Temporada {anio}</span>
          <span>Vol. 01</span>
        </div>
        <h1 className="letra-serifa break-words py-8 text-center text-[clamp(3.6rem,13vw,11rem)] font-medium leading-[0.85] tracking-tight">
          {tienda.nombre}
        </h1>
        <div className="grid grid-cols-12 gap-3 sm:gap-5">
          {bloque(0, 'col-span-4 mt-12 aspect-[3/4]')}
          {bloque(1, 'col-span-4 aspect-[3/4.6]')}
          {bloque(2, 'col-span-4 mt-24 aspect-[3/4]')}
        </div>
        <div className="mt-10 flex flex-col items-center gap-5 text-center">
          {tienda.descripcion && <p className="max-w-xl text-lg italic text-[#57534E]">{tienda.descripcion}</p>}
          <a href="#catalogo" className="group inline-flex items-center gap-3 border-b-2 border-[var(--tienda)] pb-1 text-sm font-semibold uppercase tracking-[0.3em]">
            {t.boton}<span aria-hidden="true" className="transition group-hover:translate-x-1.5">→</span>
          </a>
        </div>
      </div>
      <span className="absolute left-3 top-1/2 hidden -translate-y-1/2 -rotate-90 text-[11px] uppercase tracking-[0.5em] text-[#1C1917]/50 lg:block">Colección {anio}</span>
      <span className="absolute right-3 top-1/2 hidden -translate-y-1/2 rotate-90 text-[11px] uppercase tracking-[0.5em] text-[#1C1917]/50 lg:block">{tienda.nombre}</span>
    </section>
  );
}

// ------------------------------------------------------------------- Glow (belleza)

function Destello({ className }) {
  return <Icono nombre="chispa" tamano={28} grosor={1.4} className={`absolute text-[var(--tienda)] ${className}`} />;
}

function PortadaGlow({ tienda, todos }) {
  const t = textosDe(tienda);
  const fotos = fotosDe(todos, 3);
  const arco = (i, clase, fondo) => (
    <div className={`overflow-hidden rounded-t-full border-[6px] border-white shadow-[0_30px_60px_-25px_rgba(190,24,93,0.45)] ${clase}`}>
      <FotoO foto={fotos[i]}>
        <div className={`flex h-full items-center justify-center ${fondo}`}>
          <svg viewBox="0 0 60 100" className="h-1/2 text-white" fill="currentColor" aria-hidden="true">
            <rect x="20" y="4" width="20" height="14" rx="3" opacity=".9" /><rect x="10" y="18" width="40" height="78" rx="14" opacity=".75" />
            <rect x="18" y="40" width="24" height="30" rx="6" fill="var(--tienda)" opacity=".35" />
          </svg>
        </div>
      </FotoO>
    </div>
  );
  return (
    <section className="relative isolate overflow-hidden bg-gradient-to-br from-[#FFF1F5] via-[#FDE8F0] to-[#F3E8FF] text-[#3B0A22]">
      <span className="absolute -left-20 top-0 -z-10 h-[28rem] w-[28rem] rounded-full bg-[var(--acento)] opacity-50 blur-3xl" />
      <span className="absolute bottom-0 right-0 -z-10 h-[24rem] w-[24rem] rounded-full bg-[#E9D5FF] opacity-70 blur-3xl" />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 py-20 md:grid-cols-2 md:py-28">
        <div className="relative space-y-7">
          <Logo tienda={tienda} />
          <p className="inline-flex items-center gap-2 rounded-full bg-white/70 px-4 py-1.5 text-sm font-medium text-[var(--tienda)] shadow-sm backdrop-blur">
            <Icono nombre="chispa" tamano={16} /> {t.etiqueta}
          </p>
          <h1 className="break-words text-[clamp(3.2rem,8vw,6.5rem)] font-medium italic leading-[0.95]">{tienda.nombre}</h1>
          {tienda.descripcion && <p className="max-w-md text-lg leading-relaxed text-[#7A3B57]">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda bg-[var(--tienda)] px-8 py-3.5 font-medium text-[var(--tienda-texto)] shadow-[0_16px_34px_-14px_var(--tienda)] transition hover:-translate-y-0.5">
              {t.boton}
            </a>
            <BotonWhatsappRubro tienda={tienda} texto="Te recomendamos" mensaje={`Hola ${tienda.nombre}, ¿qué me recomiendas para mi tipo de piel?`} />
          </div>
        </div>
        <div className="relative mx-auto flex h-[440px] w-full max-w-[480px] items-end justify-center gap-4">
          <Destello className="left-2 top-6" /><Destello className="right-6 top-0 scale-75" /><Destello className="bottom-24 right-0" />
          {arco(0, 'h-[62%] w-[30%]', 'bg-gradient-to-b from-[#F9A8D4] to-[#EC4899]')}
          {arco(1, 'flotar h-[88%] w-[36%]', 'bg-gradient-to-b from-[#FBCFE8] to-[var(--tienda)]')}
          {arco(2, 'h-[72%] w-[30%]', 'bg-gradient-to-b from-[#E9D5FF] to-[#C084FC]')}
          <span className="giro-lento absolute -left-2 bottom-10 flex h-24 w-24 items-center justify-center rounded-full bg-white text-center text-[11px] font-semibold uppercase leading-tight tracking-wider text-[var(--tienda)] shadow-xl">
            {t.sello}
          </span>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------- Nido (hogar y deco)

function IlustracionPlanta() {
  return (
    <svg viewBox="0 0 120 160" className="h-3/4" aria-hidden="true">
      <path d="M60 110C60 70 30 60 22 40c22 0 38 18 38 40 0-30 14-54 40-60-2 30-20 50-40 66" fill="var(--tienda)" opacity=".85" />
      <path d="M60 112c0-22 18-36 38-36-4 20-20 32-38 36z" fill="var(--tienda)" opacity=".6" />
      <path d="M38 112h44l-6 40H44z" fill="var(--acento)" />
      <rect x="34" y="106" width="52" height="10" rx="4" fill="#B45309" />
    </svg>
  );
}

function PortadaNido({ tienda, todos, categorias }) {
  const t = textosDe(tienda);
  const fotos = fotosDe(todos, 2);
  return (
    <section className="relative isolate overflow-hidden bg-[#EEF2EA] text-[#1F2A1F]">
      <Icono nombre="hoja" tamano={220} grosor={0.6} className="absolute -left-16 -top-10 -z-10 rotate-12 text-[var(--tienda)] opacity-15" />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 py-20 md:grid-cols-[1.1fr_1fr] md:py-24">
        <div className="space-y-7">
          <Logo tienda={tienda} />
          <p className="text-sm font-semibold uppercase tracking-[0.25em] text-[var(--tienda)]">{t.etiqueta}</p>
          <h1 className="break-words text-[clamp(3rem,8vw,6.2rem)] font-bold leading-[0.92] tracking-tight">{tienda.nombre}</h1>
          {tienda.descripcion && <p className="max-w-lg text-lg leading-relaxed text-[#4B5A4B]">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda bg-[var(--tienda)] px-8 py-3.5 font-semibold text-[var(--tienda-texto)] transition hover:-translate-y-0.5">{t.boton}</a>
            <BotonWhatsappRubro tienda={tienda} texto="Escríbenos" />
          </div>
          {categorias.length > 0 && (
            <div className="flex flex-wrap gap-2 pt-1">
              {categorias.slice(0, 4).map((c) => (
                <span key={c.slug} className="rounded-full border border-[var(--tienda)]/30 bg-white/60 px-4 py-1.5 text-sm">{c.nombre}</span>
              ))}
            </div>
          )}
        </div>
        <div className="relative mx-auto flex h-[440px] w-full max-w-[480px] items-end gap-5">
          <div className="h-full w-[58%] overflow-hidden rounded-t-[999px] bg-[#DCE5D5] shadow-[0_40px_70px_-35px_rgba(30,50,30,0.6)]">
            <FotoO foto={fotos[0]}><div className="flex h-full items-end justify-center bg-gradient-to-b from-[#E4ECDD] to-[#C9D7BF]"><IlustracionPlanta /></div></FotoO>
          </div>
          <div className="flex h-full w-[42%] flex-col justify-end gap-5">
            <div className="flotar aspect-square w-full overflow-hidden rounded-full border-8 border-white bg-[var(--acento)]/20 shadow-lg">
              <FotoO foto={fotos[1]}>
                <div className="flex h-full items-center justify-center">
                  <svg viewBox="0 0 80 80" className="h-1/2 text-[var(--acento)]" aria-hidden="true"><path d="M20 30h40l-8 22H28z" fill="currentColor" /><path d="M40 52v18M28 70h24" stroke="currentColor" strokeWidth="4" strokeLinecap="round" /></svg>
                </div>
              </FotoO>
            </div>
            <div className="rounded-t-[999px] rounded-b-3xl bg-[var(--tienda)] p-6 pt-10 text-center text-[var(--tienda-texto)]">
              <Icono nombre="corazon" tamano={26} className="mx-auto" />
              <p className="mt-2 text-sm font-semibold leading-snug">Hecho para tu hogar</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// ------------------------------------------------------------------- Huellitas (mascotas)

function Huella({ className }) {
  return (
    <svg viewBox="0 0 40 40" className={`absolute ${className}`} fill="currentColor" aria-hidden="true">
      <ellipse cx="20" cy="27" rx="9" ry="8" /><ellipse cx="9" cy="16" rx="4" ry="5" /><ellipse cx="17" cy="9" rx="4" ry="5" />
      <ellipse cx="25" cy="9" rx="4" ry="5" /><ellipse cx="32" cy="16" rx="4" ry="5" />
    </svg>
  );
}

function PortadaHuellitas({ tienda, todos }) {
  const t = textosDe(tienda);
  const [principal] = fotosDe(todos, 1);
  return (
    <section className="relative isolate overflow-hidden bg-[#FFF6D6] text-[#2A1B4A]">
      <Huella className="left-[6%] top-[12%] h-10 w-10 -rotate-12 text-[var(--tienda)] opacity-20" />
      <Huella className="left-[40%] top-[70%] h-8 w-8 rotate-12 text-[var(--acento)] opacity-30" />
      <Huella className="right-[8%] top-[8%] h-12 w-12 rotate-45 text-[var(--acento)] opacity-25" />
      <Huella className="bottom-[10%] left-[18%] h-9 w-9 -rotate-45 text-[var(--tienda)] opacity-20" />
      <div className="mx-auto grid max-w-7xl items-center gap-14 px-6 py-20 md:grid-cols-[1.1fr_1fr] md:py-24">
        <div className="space-y-7">
          <Logo tienda={tienda} />
          <p className="inline-flex -rotate-2 items-center gap-2 rounded-full bg-[var(--acento)] px-4 py-1.5 text-sm font-bold text-[var(--acento-texto)] shadow">
            <Icono nombre="corazon" tamano={16} /> {t.etiqueta}
          </p>
          <h1 className="break-words text-[clamp(3.2rem,8.5vw,6.6rem)] font-extrabold leading-[0.9] tracking-tight text-[var(--tienda)]">{tienda.nombre}</h1>
          {tienda.descripcion && <p className="max-w-lg text-lg leading-relaxed text-[#5B4B7A]">{tienda.descripcion}</p>}
          <div className="flex flex-wrap gap-3">
            <a href="#catalogo" className="boton-tienda bg-[var(--tienda)] px-8 py-3.5 font-bold text-[var(--tienda-texto)] shadow-[0_8px_0_0_#2A1B4A] transition hover:translate-y-0.5 hover:shadow-[0_4px_0_0_#2A1B4A]">
              {t.boton}
            </a>
            <BotonWhatsappRubro tienda={tienda} texto="Pedir por WhatsApp" />
          </div>
        </div>
        <div className="relative mx-auto aspect-square w-full max-w-[460px]">
          <span className="absolute inset-0 bg-[var(--tienda)] [border-radius:58%_42%_36%_64%/52%_38%_62%_48%]" />
          <span className="absolute inset-[8%] bg-[var(--acento)] opacity-90 [border-radius:42%_58%_64%_36%/48%_62%_38%_52%]" />
          <div className="flotar absolute inset-[14%] overflow-hidden border-8 border-white bg-white shadow-2xl [border-radius:50%_50%_46%_54%/56%_44%_56%_44%]">
            <FotoO foto={principal}>
              <div className="flex h-full items-center justify-center bg-[#FFF6D6]">
                <svg viewBox="0 0 120 120" className="h-2/3" aria-hidden="true">
                  <path d="M30 60c0-20 13-34 30-34s30 14 30 34-13 36-30 36-30-16-30-36z" fill="#F4C27A" />
                  <path d="M30 46c-8-12-6-26 2-30 8 4 10 16 6 28zM90 46c8-12 6-26-2-30-8 4-10 16-6 28z" fill="#C98A3A" />
                  <circle cx="48" cy="58" r="5" fill="#2A1B4A" /><circle cx="72" cy="58" r="5" fill="#2A1B4A" />
                  <ellipse cx="60" cy="72" rx="7" ry="5" fill="#2A1B4A" /><path d="M54 80c3 4 9 4 12 0" stroke="#2A1B4A" strokeWidth="3" fill="none" strokeLinecap="round" />
                  <path d="M58 84c0 8 4 10 4 10s4-2 4-10" fill="#F472B6" />
                </svg>
              </div>
            </FotoO>
          </div>
          <span className="absolute -right-2 top-6 rotate-6 rounded-2xl rounded-bl-none bg-white px-4 py-2.5 text-lg font-extrabold text-[var(--tienda)] shadow-xl">{t.globo1}</span>
          <span className="absolute bottom-6 left-0 -rotate-6 rounded-2xl rounded-tr-none bg-white px-4 py-2.5 text-lg font-extrabold text-[var(--acento)] shadow-xl">{t.globo2}</span>
        </div>
      </div>
      <svg viewBox="0 0 1440 60" preserveAspectRatio="none" className="absolute bottom-0 left-0 h-10 w-full text-[var(--fondo)]" aria-hidden="true">
        <path d="M0 30Q180 0 360 30T720 30T1080 30T1440 30V60H0z" fill="currentColor" />
      </svg>
    </section>
  );
}

export const PORTADAS = {
  horno: PortadaHorno,
  circuito: PortadaCircuito,
  impulso: PortadaImpulso,
  pasarela: PortadaPasarela,
  glow: PortadaGlow,
  nido: PortadaNido,
  huellitas: PortadaHuellitas,
};
