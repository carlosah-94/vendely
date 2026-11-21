/**
 * Las 3 plantillas de la tienda pública. Cambian la portada, la grilla de productos, las
 * esquinas de los botones y la letra; todas usan el color y el logo de la tienda.
 * - clasica:  la de siempre (portada centrada con el color de la tienda).
 * - moderna:  portada grande a todo el ancho, texto a la izquierda, botones redondos (solo Pro).
 * - boutique: elegante y minimalista, letra con serifa, fondo blanco, esquinas rectas (solo Pro).
 * La clase "plantilla-..." va en el contenedor de la tienda y en index.css ajusta letra y bordes
 * de todas las páginas (portada, producto, carrito y checkout).
 */
export const PLANTILLAS = [
  { valor: 'clasica', nombre: 'Clásica', descripcion: 'Portada centrada con tu color. Simple y clara.' },
  { valor: 'moderna', nombre: 'Moderna', descripcion: 'Portada grande, letras fuertes y botones redondos.' },
  { valor: 'boutique', nombre: 'Boutique', descripcion: 'Elegante y minimalista, con letra de revista.' },
];

const ESTILOS = {
  clasica: {
    portada: 'bg-[var(--tienda)] px-4 py-16 text-center text-[var(--tienda-texto)] sm:py-24',
    contenidoPortada: 'mx-auto max-w-2xl space-y-4',
    titulo: 'text-4xl font-semibold tracking-tight sm:text-5xl',
    botonPortada: 'inline-block rounded-md bg-[var(--tienda-texto)] px-6 py-3 font-medium text-[var(--tienda)] hover:opacity-90',
    grilla: 'grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-4',
    tarjeta: 'group flex flex-col',
    imagen: 'relative overflow-hidden rounded-md',
  },
  moderna: {
    portada: 'bg-[linear-gradient(120deg,var(--tienda),color-mix(in_srgb,var(--tienda)_55%,black))] px-6 py-24 text-[var(--tienda-texto)] sm:py-32',
    contenidoPortada: 'mx-auto max-w-6xl space-y-6 text-left',
    titulo: 'max-w-3xl text-5xl font-extrabold leading-none tracking-tighter sm:text-7xl',
    botonPortada: 'inline-block rounded-full bg-[var(--tienda-texto)] px-8 py-3.5 font-semibold text-[var(--tienda)] hover:opacity-90',
    grilla: 'grid grid-cols-2 gap-6 lg:grid-cols-3',
    tarjeta: 'group flex flex-col rounded-2xl bg-white p-3 shadow-[0_8px_30px_rgba(0,0,0,0.08)]',
    imagen: 'relative overflow-hidden rounded-xl',
  },
  boutique: {
    portada: 'border-b border-[#e8e4dc] bg-[#faf8f4] px-4 py-20 text-center text-[#1a1a1a] sm:py-28',
    contenidoPortada: 'mx-auto max-w-2xl space-y-5',
    titulo: 'text-5xl font-normal tracking-wide sm:text-6xl',
    botonPortada: 'inline-block border border-[var(--tienda)] px-10 py-3 text-xs uppercase tracking-[0.25em] text-[#1a1a1a] hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)]',
    grilla: 'grid grid-cols-2 gap-x-8 gap-y-12 lg:grid-cols-3',
    tarjeta: 'group flex flex-col text-center',
    imagen: 'relative overflow-hidden',
  },
};

/** Clases de la plantilla (si llega una desconocida, la clásica). */
export function estiloPlantilla(plantilla) {
  return ESTILOS[plantilla] ?? ESTILOS.clasica;
}
