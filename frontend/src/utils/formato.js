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
