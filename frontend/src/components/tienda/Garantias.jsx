import { textoEnvioCorto } from '../../utils/catalogo';
import { metodosDeTienda, textoMetodos } from '../../utils/metodosPago';

const TRAZOS = {
  envio: 'M3 7h11v9H3zM14 10h4l3 3v3h-7M7 19a1.6 1.6 0 1 0 0-.01M17 19a1.6 1.6 0 1 0 0-.01',
  pago: 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6zM9 12l2 2 4-4',
  cuenta: 'M4 6h16v12H4zM4 9h16M8 14h4',
};

/** Lo que la tienda ofrece de verdad: a dónde envía, cómo se paga y cómo se compra. */
// eslint-disable-next-line react-refresh/only-export-components
export function garantiasDe(tienda, paises) {
  const metodos = metodosDeTienda(tienda);
  return [
    { icono: 'envio', titulo: textoEnvioCorto(paises), texto: 'Ves el costo del envío antes de pagar.' },
    metodos.length > 0 && {
      icono: 'pago', titulo: `Paga con ${textoMetodos(metodos)}`, texto: 'Con tarjeta, en una página segura.',
    },
    tienda.permiteInvitados
      ? { icono: 'cuenta', titulo: 'Compra sin crear cuenta', texto: 'Solo tu correo y tu dirección.' }
      : { icono: 'cuenta', titulo: 'Sigue tus pedidos', texto: 'Con tu cuenta ves el estado de cada compra.' },
  ].filter(Boolean);
}

export function IconoGarantia({ icono, tamano = 22 }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={TRAZOS[icono]} />
    </svg>
  );
}

/**
 * Franja de garantías debajo de la portada. estilo: clasica, moderna o boutique.
 */
export default function Garantias({ tienda, paises, estilo = 'clasica' }) {
  const lista = garantiasDe(tienda, paises);
  if (estilo === 'boutique') {
    return (
      <section className="border-b border-[#ebe9e6]">
        <div className="mx-auto grid max-w-6xl gap-6 px-6 py-8 text-center text-sm text-[#6b6b6b] sm:grid-cols-3">
          {lista.map((g) => (
            <p key={g.icono}>
              <span className="letra-serifa block text-xl text-[#222]">{g.titulo}</span>
              {g.texto}
            </p>
          ))}
        </div>
      </section>
    );
  }
  const moderna = estilo === 'moderna';
  return (
    <section className={moderna ? 'bg-[#141414] text-white' : 'border-b border-[#ececec] bg-white'}>
      <div className="mx-auto grid max-w-6xl gap-5 px-5 py-6 sm:grid-cols-3">
        {lista.map((g) => (
          <div key={g.icono} className="flex items-center gap-3.5">
            <span
              className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${
                moderna ? 'bg-[var(--tienda)] text-[var(--tienda-texto)]' : 'bg-[var(--tienda)]/10 text-[var(--tienda)]'
              }`}
            >
              <IconoGarantia icono={g.icono} />
            </span>
            <p className="text-sm leading-snug">
              <b className={moderna ? 'text-white' : 'text-[#1a1a1a]'}>{g.titulo}</b>
              <br />
              <span className={moderna ? 'text-white/65' : 'text-[#666]'}>{g.texto}</span>
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
