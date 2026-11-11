/**
 * Clases de Tailwind que se repiten en el panel (estilo del admin de Shopify), para no
 * copiarlas en cada pantalla.
 */
export const SOMBRA = 'shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]';
export const TARJETA = `rounded-xl bg-white ${SOMBRA}`;
export const BOTON = 'rounded-lg bg-[#303030] px-3 py-1.5 font-semibold text-white hover:bg-[#1a1a1a] disabled:opacity-60';
export const BOTON_SECUNDARIO = 'rounded-lg border border-[#ddd] bg-white px-3 py-1.5 font-medium text-[#303030] hover:bg-[#f7f7f7] disabled:opacity-60';
export const ENCABEZADO_TABLA = 'bg-[#f7f7f7] text-left text-[#616161]';

/** Input del panel: borde rojo si hay error, azul al enfocarlo. */
export function inputPanel(error) {
  return `w-full rounded-lg border bg-white px-3 py-1.5 text-[13px] focus:outline-none focus:ring-2 ${
    error ? 'border-[#e51c00] focus:ring-[#fecdca]' : 'border-[#8a8a8a]/50 focus:border-[#1650d0] focus:ring-[#c9d7f8]'
  }`;
}

/** Etiqueta redonda de estado. tono: 'exito' (verde), 'aviso' (amarillo), 'error' (rojo), 'info' (azul) o 'neutro'. */
export function etiqueta(tono) {
  const colores = {
    exito: 'bg-[#e8fcd9] text-[#0c5132]',
    aviso: 'bg-[#fff5c5] text-[#4f4700]',
    error: 'bg-[#ffe7e6] text-[#8e1f0b]',
    info: 'bg-[#eaf4ff] text-[#00527c]',
    neutro: 'bg-[#e3e3e3] text-[#4a4a4a]',
  };
  return `inline-block rounded-full px-2 py-0.5 text-xs font-semibold ${colores[tono] ?? colores.neutro}`;
}
