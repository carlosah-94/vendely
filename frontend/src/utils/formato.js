/*
 * Cada tienda cobra en la moneda de su país: Perú en soles (PEN, con céntimos) y Chile en pesos
 * (CLP, sin decimales). La tienda que se está viendo avisa su moneda con usarMoneda() (lo hacen
 * useTiendaActual en el panel y LayoutTienda en la tienda pública), y desde ahí los precios,
 * los inputs y las validaciones usan esa moneda sin pasarla a cada rato.
 */
let monedaActual = 'PEN';

// Cómo se escribe el dinero en cada país: "S/ 49.90" en Perú, "$9.990" en Chile.
const IDIOMA_MONEDA = { PEN: 'es-PE', CLP: 'es-CL', USD: 'en-US', MXN: 'es-MX', ARS: 'es-AR', COP: 'es-CO', EUR: 'es-ES' };

/** La tienda que se está viendo dice en qué moneda trabaja ("PEN", "CLP"). */
export function usarMoneda(moneda) {
  if (moneda) monedaActual = moneda.trim();
}

function formateador(moneda) {
  return new Intl.NumberFormat(IDIOMA_MONEDA[moneda] ?? 'es-PE', { style: 'currency', currency: moneda });
}

/** Muestra un monto como dinero: 110 en PEN se ve "S/ 110.00"; 9990 en CLP, "$9.990". */
export function formatoMoneda(monto, moneda = monedaActual) {
  if (monto === null || monto === undefined) {
    return '-';
  }
  return formateador(moneda).format(Number(monto));
}

/** Símbolo para poner delante de un input de monto: "S/" o "$". */
export function simboloMoneda(moneda = monedaActual) {
  return formateador(moneda).formatToParts(0).find((p) => p.type === 'currency')?.value ?? moneda;
}

/** Decimales de la moneda: PEN 2, CLP 0. La base rechaza montos con más (trigger T16). */
export function decimalesMoneda(moneda = monedaActual) {
  return formateador(moneda).resolvedOptions().maximumFractionDigits;
}

/** ¿El texto es un monto válido en la moneda? "49.90" sí en PEN; en CLP solo enteros como "9990". */
export function esMontoValido(texto, moneda = monedaActual) {
  const d = decimalesMoneda(moneda);
  const patron = d === 0 ? /^\d+$/ : new RegExp(`^\\d+(\\.\\d{1,${d}})?$`);
  return patron.test(String(texto).trim().replace(',', '.'));
}

/** Ayuda para el mensaje de error y el placeholder: "con hasta 2 decimales (ej. 49.90)" o "sin decimales (ej. 9990)". */
export function ejemploMonto(moneda = monedaActual) {
  return decimalesMoneda(moneda) === 0 ? 'sin decimales ni puntos (ej. 9990)' : 'con hasta 2 decimales (ej. 49.90)';
}

/** Placeholder de un input de monto: "0.00" o "0". */
export function montoVacio(moneda = monedaActual) {
  const d = decimalesMoneda(moneda);
  return d === 0 ? '0' : `0.${'0'.repeat(d)}`;
}

/** Muestra una fecha del backend en formato peruano: "10 nov 2026, 10:30". */
export function formatoFecha(fecha) {
  if (!fecha) {
    return '-';
  }
  return new Date(fecha).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

/** true si el producto tiene un precio "antes" mayor que el actual (está en oferta). */
export function enOferta(producto) {
  return producto.precioComparacion != null && Number(producto.precioComparacion) > Number(producto.precio);
}

/**
 * Color del texto que se lee bien sobre el color de la tienda: blanco sobre colores oscuros
 * y casi negro sobre colores claros (fórmula de luminancia, como hacen los temas de Shopify).
 */
export function colorTextoSobre(hex) {
  const m = /^#([0-9a-f]{6})$/i.exec(hex ?? '');
  if (!m) return '#ffffff';
  const n = parseInt(m[1], 16);
  const [r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  return (0.299 * r + 0.587 * g + 0.114 * b) > 160 ? '#1a1a1a' : '#ffffff';
}
