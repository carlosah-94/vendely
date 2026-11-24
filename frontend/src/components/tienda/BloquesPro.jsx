import { Link } from 'react-router-dom';
import { colorTextoSobre } from '../../utils/formato';
import { enlaceSeguro } from '../../utils/diseno';

/**
 * Bloques del diseño avanzado (plan Pro) que comparten las tres plantillas: la portada a medida
 * y la franja de promoción. Los textos, colores y enlaces los elige el dueño en Personalizar.
 */

/** Un botón con el enlace del dueño: ruta de la tienda (sin recargar), ancla o web externa. */
export function BotonEnlace({ texto, enlace, className }) {
  if (!texto) return null;
  const destino = enlaceSeguro(enlace) ? enlace.trim() : '#catalogo';
  if (destino.startsWith('/')) {
    return <Link to={destino} className={className}>{texto}</Link>;
  }
  const externo = destino.startsWith('http');
  return (
    <a href={destino} className={className} {...(externo && { target: '_blank', rel: 'noreferrer' })}>
      {texto}
    </a>
  );
}

const ALTOS = {
  medio: 'min-h-[360px] sm:min-h-[420px]',
  alto: 'min-h-[460px] sm:min-h-[560px]',
  pantalla: 'min-h-[calc(100svh-7rem)]',
};

/**
 * Portada a medida, a todo lo ancho (como las grandes tiendas): la imagen de portada de fondo
 * con un velo oscuro para que el texto se lea, el título, un texto y hasta dos botones.
 * Sin imagen, el fondo es el color de la tienda.
 */
export function PortadaPro({ tienda, diseno }) {
  const p = diseno.pro.portada;
  const centro = p.alineacion === 'centro';
  const imagen = diseno.imagenPortadaUrl;
  const titulo = p.titulo || tienda.nombre;
  const subtitulo = p.subtitulo ?? tienda.descripcion;
  const conBotones = p.boton1Texto || p.boton2Texto;
  const textoClaro = Boolean(imagen) || colorTextoSobre(tienda.colorPrincipal || '#303030').toLowerCase() === '#ffffff';

  return (
    <section
      className={`relative isolate flex overflow-hidden bg-[var(--tienda)] ${ALTOS[p.alto] ?? ALTOS.alto} ${
        textoClaro ? 'text-white' : 'text-[var(--tienda-texto)]'
      }`}
    >
      {imagen && (
        <>
          <img src={imagen} alt="" className="absolute inset-0 -z-20 h-full w-full object-cover" />
          <span className="absolute inset-0 -z-10 bg-black" style={{ opacity: (p.oscurecer ?? 35) / 100 }} />
        </>
      )}
      <div
        className={`mx-auto flex w-full max-w-6xl flex-col justify-center gap-5 px-6 py-16 sm:px-10 ${
          centro ? 'items-center text-center' : 'items-start text-left'
        }`}
      >
        <h1 className={`max-w-3xl break-words text-[clamp(2.4rem,6vw,4.75rem)] font-semibold leading-[1.03] tracking-tight ${
          imagen ? 'drop-shadow-[0_2px_12px_rgba(0,0,0,0.35)]' : ''
        }`}>
          {titulo}
        </h1>
        {subtitulo && <p className="max-w-xl text-lg leading-relaxed opacity-90 sm:text-xl">{subtitulo}</p>}
        {conBotones && (
          <div className={`flex flex-wrap gap-3 pt-2 ${centro ? 'justify-center' : ''}`}>
            <BotonEnlace
              texto={p.boton1Texto}
              enlace={p.boton1Enlace}
              className="boton-tienda bg-white px-8 py-3.5 font-semibold text-[#141414] transition hover:bg-white/90"
            />
            <BotonEnlace
              texto={p.boton2Texto}
              enlace={p.boton2Enlace}
              className="boton-tienda bg-[var(--acento)] px-8 py-3.5 font-semibold text-[var(--acento-texto)] transition hover:brightness-95"
            />
          </div>
        )}
      </div>
    </section>
  );
}

/** Franja de promoción debajo de la portada: título, texto y un botón, sobre el color elegido. */
export function BandaPromo({ diseno }) {
  const pr = diseno.pro.promo;
  if (!pr.mostrar || !(pr.titulo || pr.texto)) return null;
  const fondo = pr.color || 'var(--acento)';
  const texto = pr.color ? colorTextoSobre(pr.color) : 'var(--acento-texto)';
  return (
    <section style={{ background: fondo, color: texto }}>
      <div className="mx-auto grid max-w-6xl items-center gap-4 px-6 py-8 sm:px-10 md:grid-cols-[1.2fr_1fr_auto] md:gap-10">
        {pr.titulo && <h2 className="text-2xl font-semibold leading-tight sm:text-3xl">{pr.titulo}</h2>}
        {pr.texto && <p className="text-base leading-relaxed opacity-90">{pr.texto}</p>}
        <BotonEnlace
          texto={pr.botonTexto}
          enlace={pr.botonEnlace}
          className="boton-tienda justify-self-start border-2 border-current px-7 py-3 text-center font-semibold transition hover:bg-white/10 md:justify-self-end"
        />
      </div>
    </section>
  );
}
