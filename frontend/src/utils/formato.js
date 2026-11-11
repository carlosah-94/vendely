/** Muestra un monto como dinero: 110 y "PEN" se ven como "S/ 110.00". */
export function formatoMoneda(monto, moneda = 'PEN') {
  if (monto === null || monto === undefined) {
    return '-';
  }
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: moneda }).format(Number(monto));
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
