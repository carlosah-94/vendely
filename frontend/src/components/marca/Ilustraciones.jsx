/**
 * Dibujos planos de productos para las tiendas de ejemplo de la portada de Vendely.
 * Son SVG propios (no fotos de internet): pesan poco y se ven nítidos en cualquier pantalla.
 * color = color principal del objeto; el resto sale de él.
 */
const DIBUJOS = {
  taza: (c) => (
    <>
      <path d="M26 36h40v26a16 16 0 0 1-16 16h-8a16 16 0 0 1-16-16z" fill={c} />
      <path d="M66 44h6a9 9 0 0 1 0 18h-6" fill="none" stroke={c} strokeWidth="6" />
      <path d="M38 22c-3 4 3 6 0 10M48 20c-3 4 3 6 0 10" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity=".8" />
    </>
  ),
  zapatilla: (c) => (
    <>
      <path d="M14 66c0-8 4-16 12-18l14-4 10 8c6 4 14 6 24 7 8 1 12 5 12 9v4H14z" fill={c} />
      <path d="M14 70h72v6H14z" fill="#fff" />
      <path d="M40 50l4 6M46 48l4 6M52 52l4 5" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" />
    </>
  ),
  bolso: (c) => (
    <>
      <path d="M36 38v-6a14 14 0 0 1 28 0v6" fill="none" stroke={c} strokeWidth="5" />
      <rect x="20" y="38" width="60" height="44" rx="8" fill={c} />
      <rect x="44" y="50" width="12" height="8" rx="2" fill="#fff" opacity=".85" />
    </>
  ),
  planta: (c) => (
    <>
      <path d="M50 58C44 40 30 36 24 38c2 12 12 20 26 20zM50 58c6-18 20-24 28-22-2 12-14 22-28 22zM50 58c-2-16 0-28 0-34 6 8 6 22 0 34z" fill={c} />
      <path d="M32 58h36l-5 24H37z" fill="#fff" opacity=".9" />
    </>
  ),
  frasco: (c) => (
    <>
      <rect x="40" y="16" width="20" height="12" rx="3" fill="#fff" opacity=".9" />
      <rect x="30" y="28" width="40" height="56" rx="12" fill={c} />
      <rect x="38" y="46" width="24" height="18" rx="3" fill="#fff" opacity=".8" />
    </>
  ),
  audifonos: (c) => (
    <>
      <path d="M24 60V50a26 26 0 0 1 52 0v10" fill="none" stroke={c} strokeWidth="6" />
      <rect x="18" y="56" width="16" height="24" rx="6" fill={c} />
      <rect x="66" y="56" width="16" height="24" rx="6" fill={c} />
    </>
  ),
  polo: (c) => (
    <>
      <path d="M36 20l-20 10 8 16 8-4v42h36V42l8 4 8-16-20-10c-2 6-8 9-14 9s-12-3-14-9z" fill={c} />
      <path d="M44 22c2 4 4 5 6 5s4-1 6-5" fill="none" stroke="#fff" strokeWidth="2.5" opacity=".8" />
    </>
  ),
  reloj: (c) => (
    <>
      <rect x="40" y="12" width="20" height="76" rx="6" fill={c} opacity=".55" />
      <circle cx="50" cy="50" r="22" fill={c} />
      <circle cx="50" cy="50" r="16" fill="#fff" />
      <path d="M50 40v10l7 5" stroke={c} strokeWidth="3" strokeLinecap="round" fill="none" />
    </>
  ),
  vasija: (c) => (
    <>
      <path d="M38 18h24v8c10 6 16 16 16 28 0 16-12 28-28 28S22 70 22 54c0-12 6-22 16-28z" fill={c} />
      <path d="M28 52h44" stroke="#fff" strokeWidth="3" opacity=".7" />
      <path d="M30 60h40" stroke="#fff" strokeWidth="2" opacity=".5" />
    </>
  ),
};

export default function Ilustracion({ dibujo, color = '#15123B', className = '' }) {
  const pintar = DIBUJOS[dibujo] ?? DIBUJOS.bolso;
  return (
    <svg viewBox="0 0 100 100" className={className} aria-hidden="true">
      {pintar(color)}
    </svg>
  );
}
