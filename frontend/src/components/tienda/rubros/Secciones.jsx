import { useState } from 'react';
import { BotonWhatsappRubro, Icono, datosReales } from './comun';

/**
 * Franja de beneficios y sección propia de cada plantilla por rubro. Lo que se promete (envío,
 * pago, recojo) sale de los datos reales de la tienda; si algo no está configurado, no se muestra.
 */

// ------------------------------------------------------------------- beneficios

function beneficiosDe(tema, tienda, paises) {
  const d = datosReales(tienda, paises);
  const pago = d.pago && { icono: 'tarjeta', titulo: d.pago, texto: 'En una página segura.' };
  const envio = { icono: 'camion', titulo: d.envio, texto: 'Ves el costo antes de pagar.' };
  const recojo = d.recojo && { icono: 'tienda', titulo: 'Recojo gratis', texto: d.recojo.horario || d.recojo.direccion };
  const porTema = {
    horno: [
      { icono: 'reloj', titulo: 'Pide con anticipación', texto: 'Elige el día que lo necesitas.' },
      recojo ?? { icono: 'moto', titulo: 'Delivery', texto: 'Te lo llevamos con cuidado.' },
      { icono: 'corazon', titulo: 'Hecho en casa', texto: 'Con insumos frescos.' },
      pago,
    ],
    circuito: [
      { icono: 'escudo', titulo: 'Garantía', texto: 'Equipos originales con boleta.' },
      envio, pago,
      { icono: 'chat', titulo: 'Soporte', texto: 'Te ayudamos antes y después.' },
    ],
    impulso: [
      { icono: 'rayo', titulo: 'Envío rápido', texto: d.envio },
      { icono: 'chat', titulo: 'Asesoría', texto: 'Te ayudamos a elegir.' },
      pago, recojo,
    ],
    pasarela: [envio, { icono: 'regla', titulo: 'Guía de tallas', texto: 'Encuentra tu talla ideal.' }, pago],
    glow: [
      { icono: 'chispa', titulo: 'Originales', texto: 'Productos con registro sanitario.' },
      envio, pago,
    ],
    nido: [envio, { icono: 'caja', titulo: 'Bien embalado', texto: 'Llega como lo elegiste.' }, pago, recojo],
    huellitas: [
      { icono: 'corazon', titulo: 'Con amor', texto: 'Elegido para tu engreído.' },
      recojo ?? envio, pago,
    ],
  };
  return (porTema[tema] ?? []).filter(Boolean).slice(0, 4);
}

const ESTILO_BENEFICIOS = {
  horno: { seccion: 'bg-[var(--fondo)]', tarjeta: 'rounded-3xl bg-[#FFF4E6] p-5', icono: 'bg-[var(--tienda)] text-[var(--tienda-texto)] rounded-2xl', titulo: 'text-[#2B1609]' },
  circuito: { seccion: 'bg-[#0B0F19] text-white', tarjeta: 'rounded-xl border border-white/10 bg-white/[0.03] p-5', icono: 'bg-[var(--acento)]/15 text-[var(--acento)] rounded-lg', titulo: 'text-white', texto: 'text-white/55' },
  impulso: { seccion: 'bg-[var(--acento)] text-[var(--acento-texto)]', tarjeta: 'p-4', icono: 'bg-[#0A0A0A] text-[var(--acento)]', titulo: 'uppercase italic font-extrabold tracking-wide', texto: 'opacity-75' },
  pasarela: { seccion: 'border-y border-[#1C1917]/10 bg-[#FAF8F4]', tarjeta: 'p-4 text-center', icono: 'mx-auto text-[var(--tienda)]', titulo: 'letra-serifa text-xl', centro: true },
  glow: { seccion: 'bg-[var(--fondo)]', tarjeta: 'rounded-full bg-[#FFF1F5] px-6 py-4', icono: 'bg-white text-[var(--tienda)] rounded-full shadow-sm', titulo: 'text-[#3B0A22]' },
  nido: { seccion: 'bg-[var(--fondo)]', tarjeta: 'rounded-3xl border border-[#DCE5D5] p-5', icono: 'bg-[#EEF2EA] text-[var(--tienda)] rounded-full', titulo: 'text-[#1F2A1F]' },
  huellitas: { seccion: 'bg-[var(--fondo)]', tarjeta: 'rounded-3xl border-2 border-[#2A1B4A] bg-white p-5 shadow-[5px_5px_0_0_#2A1B4A]', icono: 'bg-[var(--acento)] text-[var(--acento-texto)] rounded-2xl', titulo: 'font-extrabold text-[#2A1B4A]' },
};

export function BeneficiosRubro({ tema, tienda, paises }) {
  const lista = beneficiosDe(tema, tienda, paises);
  const e = ESTILO_BENEFICIOS[tema];
  if (!e || lista.length === 0) return null;
  const columnas = lista.length === 4 ? 'sm:grid-cols-2 lg:grid-cols-4' : 'sm:grid-cols-3';
  return (
    <section className={e.seccion}>
      <div className={`mx-auto grid max-w-6xl gap-4 px-5 py-8 ${columnas}`}>
        {lista.map((b) => (
          <div key={b.titulo} className={`${e.centro ? '' : 'flex items-center gap-4'} ${e.tarjeta}`}>
            <span className={`flex h-12 w-12 shrink-0 items-center justify-center ${e.icono}`}><Icono nombre={b.icono} /></span>
            <p className={`text-sm leading-snug ${e.centro ? 'mt-2' : ''}`}>
              <b className={`block text-[15px] ${e.titulo}`}>{b.titulo}</b>
              <span className={e.texto ?? 'text-[#6b6b6b]'}>{b.texto}</span>
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

// ------------------------------------------------------------------- preguntas frecuentes

function preguntasDe(tienda, paises, extra = []) {
  const d = datosReales(tienda, paises);
  return [
    ...extra,
    d.pago && { p: '¿Cómo puedo pagar?', r: `${d.pago}. El pago se hace en una página segura y te llega la confirmación por correo.` },
    { p: '¿A dónde envían?', r: `${d.envio}. El costo del envío lo ves en el checkout antes de pagar.` },
    d.recojo && { p: '¿Puedo recoger mi pedido?', r: `Sí, gratis en ${d.recojo.direccion}${d.recojo.horario ? ` (${d.recojo.horario})` : ''}.` },
    { p: '¿Cómo sigo mi pedido?', r: 'Con tu cuenta, en "Mis pedidos", ves el estado de cada compra. También te avisamos por correo.' },
  ].filter(Boolean);
}

function Preguntas({ lista, claseItem, clasePregunta }) {
  const [abierta, setAbierta] = useState(0);
  return (
    <div className="space-y-3">
      {lista.map((q, i) => (
        <div key={q.p} className={claseItem}>
          <button type="button" onClick={() => setAbierta(abierta === i ? -1 : i)} aria-expanded={abierta === i}
            className={`flex w-full items-center justify-between gap-4 text-left font-semibold ${clasePregunta}`}>
            {q.p}
            <span aria-hidden="true" className={`text-xl transition ${abierta === i ? 'rotate-45' : ''}`}>+</span>
          </button>
          {abierta === i && <p className="mt-3 text-[15px] leading-relaxed opacity-80">{q.r}</p>}
        </div>
      ))}
    </div>
  );
}

// ------------------------------------------------------------------- secciones por rubro

function SeccionHorno({ tienda, paises }) {
  const d = datosReales(tienda, paises);
  const pasos = [
    ['Elige', 'Mira la carta y elige lo que se te antoja.'],
    ['Personaliza', 'Escribe el mensaje o los detalles de tu pedido.'],
    [d.recojo ? 'Recibe o recoge' : 'Recibe', d.recojo ? 'Te lo llevamos o pasas por él, gratis.' : 'Te lo llevamos con todo el cuidado.'],
  ];
  return (
    <section className="mx-auto max-w-6xl px-5 py-16">
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="rounded-[2rem] bg-[#FFF4E6] p-8 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-widest text-[var(--tienda)]">Así de fácil</p>
          <h2 className="mt-2 text-4xl font-bold tracking-tight text-[#2B1609]">¿Cómo hago mi pedido?</h2>
          <ol className="mt-8 grid gap-5 sm:grid-cols-3">
            {pasos.map(([titulo, texto], i) => (
              <li key={titulo}>
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--tienda)] text-xl font-bold text-[var(--tienda-texto)]">{i + 1}</span>
                <b className="mt-3 block text-lg text-[#2B1609]">{titulo}</b>
                <span className="text-sm text-[#6B4A35]">{texto}</span>
              </li>
            ))}
          </ol>
        </div>
        <div className="flex flex-col gap-6">
          {d.recojo && (
            <div className="rounded-[2rem] border-2 border-dashed border-[var(--tienda)]/40 p-7">
              <Icono nombre="tienda" tamano={28} className="text-[var(--tienda)]" />
              <b className="mt-3 block text-lg text-[#2B1609]">Recoge en nuestro local</b>
              <p className="text-sm text-[#6B4A35]">{d.recojo.direccion}</p>
              {d.recojo.horario && <p className="mt-1 text-sm font-semibold text-[var(--tienda)]">{d.recojo.horario}</p>}
            </div>
          )}
          <div className="flex flex-1 flex-col justify-between gap-5 rounded-[2rem] bg-[var(--tienda)] p-7 text-[var(--tienda-texto)]">
            <div>
              <Icono nombre="reloj" tamano={28} />
              <b className="mt-3 block text-2xl">¿Lo necesitas para una fecha especial?</b>
              <p className="mt-1 text-sm opacity-85">Cuéntanos qué buscas y lo preparamos para ese día.</p>
            </div>
            <BotonWhatsappRubro tienda={tienda} texto="Escríbenos" className="self-start" mensaje={`Hola ${tienda.nombre}, quiero un pedido para una fecha especial.`} />
          </div>
        </div>
      </div>
    </section>
  );
}

function SeccionCircuito({ tienda, todos }) {
  const marcas = [...new Set((todos ?? []).map((p) => p.marca).filter(Boolean))].slice(0, 8);
  const sellos = [
    ['escudo', 'Garantía', 'Cada equipo con su garantía y boleta o factura.'],
    ['check', '100% originales', 'Productos sellados, de marcas oficiales.'],
    ['chat', 'Soporte real', 'Te ayudamos a configurar tu equipo.'],
    ['caja', 'Empaque seguro', 'Protegido para que llegue intacto.'],
  ];
  return (
    <section className="bg-[#070A13] text-white">
      <div className="mx-auto max-w-6xl px-5 py-16">
        {marcas.length > 0 && (
          <div className="mb-14">
            <p className="text-center font-mono text-xs uppercase tracking-[0.35em] text-white/45">Marcas que trabajamos</p>
            <div className="mt-6 flex flex-wrap items-center justify-center gap-x-10 gap-y-4">
              {marcas.map((m) => <span key={m} className="text-2xl font-bold tracking-tight text-white/70 transition hover:text-[var(--acento)]">{m}</span>)}
            </div>
          </div>
        )}
        <div className="grid items-center gap-10 lg:grid-cols-[1fr_1.4fr]">
          <div>
            <p className="font-mono text-xs uppercase tracking-[0.3em] text-[var(--acento)]">Por qué comprar aquí</p>
            <h2 className="mt-3 text-4xl font-bold tracking-tight">Tecnología con respaldo</h2>
            <p className="mt-3 text-white/60">Compra tranquilo: si tienes una duda antes o después de comprar, estamos para ayudarte.</p>
            <BotonWhatsappRubro tienda={tienda} texto="Hablar con un asesor" className="mt-6" mensaje={`Hola ${tienda.nombre}, necesito ayuda para elegir un equipo.`} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {sellos.map(([icono, titulo, texto]) => (
              <div key={titulo} className="group rounded-2xl border border-white/10 bg-gradient-to-br from-white/[0.06] to-transparent p-6 transition hover:border-[var(--acento)]/50">
                <Icono nombre={icono} tamano={26} className="text-[var(--acento)] transition group-hover:scale-110" />
                <b className="mt-4 block text-lg">{titulo}</b>
                <span className="text-sm text-white/55">{texto}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function SeccionImpulso({ categorias, elegirCategoria }) {
  if (categorias.length === 0) return null;
  const ir = (slug) => {
    elegirCategoria(slug);
    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
  };
  return (
    <section className="bg-[#0A0A0A] py-16 text-white">
      <div className="mx-auto max-w-6xl px-5">
        <h2 className="text-[clamp(2.5rem,6vw,4.5rem)] font-extrabold uppercase italic leading-none">Encuentra <span className="text-[var(--acento)]">lo tuyo</span></h2>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {categorias.slice(0, 8).map((c, i) => (
            <button key={c.slug} type="button" onClick={() => ir(c.slug)}
              className="group relative overflow-hidden border-2 border-white/10 bg-[#141414] p-6 text-left transition hover:-translate-y-1 hover:border-[var(--acento)]">
              <span className="text-6xl font-extrabold italic text-white/10 transition group-hover:text-[var(--acento)]">{String(i + 1).padStart(2, '0')}</span>
              <b className="mt-2 block text-xl font-extrabold uppercase italic">{c.nombre}</b>
              <span className="mt-3 inline-block text-sm font-bold uppercase tracking-widest text-[var(--acento)]">Ver todo →</span>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

function SeccionPasarela() {
  const tallas = [['XS', '80-84', '62-66', '86-90'], ['S', '85-89', '67-71', '91-95'], ['M', '90-95', '72-77', '96-101'],
    ['L', '96-101', '78-83', '102-107'], ['XL', '102-108', '84-90', '108-114']];
  return (
    <section className="bg-[#F5F1EA] py-16 text-[#1C1917]">
      <div className="mx-auto grid max-w-6xl items-center gap-10 px-5 lg:grid-cols-[1fr_1.5fr]">
        <div>
          <p className="text-[11px] uppercase tracking-[0.4em] text-[var(--acento)]">Encuentra tu talla</p>
          <h2 className="letra-serifa mt-3 text-5xl">Guía de tallas</h2>
          <p className="mt-4 text-[#57534E]">Mide tu contorno con una cinta métrica y compáralo con la tabla. Si estás entre dos tallas, elige la mayor.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead><tr className="border-b-2 border-[#1C1917] text-[11px] uppercase tracking-[0.25em]"><th className="py-3">Talla</th><th>Pecho (cm)</th><th>Cintura (cm)</th><th>Cadera (cm)</th></tr></thead>
            <tbody>
              {tallas.map(([t, ...medidas]) => (
                <tr key={t} className="border-b border-[#1C1917]/10">
                  <td className="letra-serifa py-3.5 text-xl">{t}</td>
                  {medidas.map((m, i) => <td key={i} className="text-[#57534E]">{m}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function SeccionGlow({ tienda, paises }) {
  const pasos = [['Limpia', 'Prepara tu piel quitando impurezas.'], ['Trata', 'Usa el producto según tu necesidad.'], ['Protege', 'Sella e hidrata para lucir radiante.']];
  return (
    <section className="bg-gradient-to-b from-[var(--fondo)] to-[#FFF1F5] py-16 text-[#3B0A22]">
      <div className="mx-auto max-w-6xl px-5">
        <div className="text-center">
          <p className="text-sm font-medium uppercase tracking-[0.3em] text-[var(--tienda)]">Tu ritual</p>
          <h2 className="mt-2 text-5xl font-medium italic">Tu rutina en 3 pasos</h2>
        </div>
        <div className="mt-10 grid gap-6 sm:grid-cols-3">
          {pasos.map(([t, x], i) => (
            <div key={t} className="rounded-t-full bg-white px-6 pb-8 pt-14 text-center shadow-[0_20px_50px_-30px_rgba(190,24,93,0.5)]">
              <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-[var(--acento)] to-[var(--tienda)] text-xl font-semibold text-white">{i + 1}</span>
              <b className="mt-4 block text-2xl italic">{t}</b>
              <span className="text-sm text-[#7A3B57]">{x}</span>
            </div>
          ))}
        </div>
        <div className="mx-auto mt-14 max-w-3xl">
          <h3 className="mb-5 text-center text-3xl italic">Preguntas frecuentes</h3>
          <Preguntas lista={preguntasDe(tienda, paises, [{ p: '¿Los productos son originales?', r: 'Sí. Vendemos productos originales; si tienes dudas sobre alguno, escríbenos antes de comprar.' }])}
            claseItem="rounded-3xl bg-white px-6 py-4 shadow-sm" clasePregunta="text-[#3B0A22]" />
        </div>
      </div>
    </section>
  );
}

function SeccionNido({ categorias, elegirCategoria }) {
  if (categorias.length === 0) return null;
  const tonos = ['bg-[#DCE5D5]', 'bg-[#F3E3CC]', 'bg-[#E4DDD3]', 'bg-[#D9E2E6]'];
  const ir = (slug) => {
    elegirCategoria(slug);
    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
  };
  return (
    <section className="mx-auto max-w-6xl px-5 py-16 text-[#1F2A1F]">
      <p className="text-sm font-semibold uppercase tracking-[0.25em] text-[var(--tienda)]">Inspírate</p>
      <h2 className="mt-2 text-4xl font-bold tracking-tight">Un rincón para cada ambiente</h2>
      <div className="mt-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {categorias.slice(0, 4).map((c, i) => (
          <button key={c.slug} type="button" onClick={() => ir(c.slug)}
            className={`group flex aspect-[3/4] flex-col justify-end rounded-t-[999px] rounded-b-3xl p-6 text-left transition hover:-translate-y-1 ${tonos[i % 4]}`}>
            <Icono nombre={['hoja', 'corazon', 'caja', 'chispa'][i % 4]} tamano={30} className="mb-auto mt-8 self-center text-[var(--tienda)] transition group-hover:scale-110" />
            <b className="text-xl">{c.nombre}</b>
            <span className="text-sm text-[var(--tienda)]">Ver productos →</span>
          </button>
        ))}
      </div>
    </section>
  );
}

function SeccionHuellitas({ tienda, paises, categorias, elegirCategoria }) {
  const colores = ['bg-[var(--tienda)] text-[var(--tienda-texto)]', 'bg-[var(--acento)] text-[var(--acento-texto)]', 'bg-[#22C55E] text-white', 'bg-[#0EA5E9] text-white'];
  const ir = (slug) => {
    elegirCategoria(slug);
    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
  };
  return (
    <section className="mx-auto max-w-6xl px-5 py-16 text-[#2A1B4A]">
      {categorias.length > 0 && (
        <>
          <h2 className="text-center text-4xl font-extrabold tracking-tight">¿Qué necesita tu engreído?</h2>
          <div className="mt-8 flex flex-wrap justify-center gap-4">
            {categorias.slice(0, 6).map((c, i) => (
              <button key={c.slug} type="button" onClick={() => ir(c.slug)}
                className={`rounded-full border-2 border-[#2A1B4A] px-7 py-3.5 text-lg font-extrabold shadow-[4px_4px_0_0_#2A1B4A] transition hover:-translate-y-1 ${colores[i % 4]}`}>
                {c.nombre}
              </button>
            ))}
          </div>
        </>
      )}
      <div className="mx-auto mt-14 max-w-3xl">
        <h3 className="mb-5 text-center text-3xl font-extrabold">Preguntas frecuentes</h3>
        <Preguntas lista={preguntasDe(tienda, paises)} claseItem="rounded-3xl border-2 border-[#2A1B4A] bg-white px-6 py-4" clasePregunta="text-[#2A1B4A]" />
      </div>
    </section>
  );
}

const SECCIONES = {
  horno: SeccionHorno,
  circuito: SeccionCircuito,
  impulso: SeccionImpulso,
  pasarela: SeccionPasarela,
  glow: SeccionGlow,
  nido: SeccionNido,
  huellitas: SeccionHuellitas,
};

export function SeccionRubro({ tema, ...vista }) {
  const Seccion = SECCIONES[tema];
  return Seccion ? <Seccion {...vista} /> : null;
}
