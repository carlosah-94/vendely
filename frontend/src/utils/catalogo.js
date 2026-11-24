/**
 * Ayudas para que la portada de la tienda se vea bien con cualquier cantidad de productos.
 * Con 1 producto no tiene sentido una grilla de 4 columnas con 3 huecos vacíos: ese producto
 * se muestra en grande. Con 2 o 3, las tarjetas crecen para llenar el ancho.
 */

/** true si el visitante puso algún filtro o búsqueda. */
export function hayFiltros(filtros) {
  return Boolean(filtros.categoria || filtros.q || filtros.min || filtros.max || filtros.stock);
}

/**
 * Clases de la grilla del catálogo según cuántos productos se muestran.
 * maximo = columnas en pantalla grande cuando hay muchos (4 en Clásica y Moderna, 3 en Boutique).
 */
export function claseGrilla(cantidad, maximo = 4) {
  if (cantidad === 1) return 'mx-auto max-w-md grid-cols-1';
  if (cantidad === 2) return 'mx-auto max-w-4xl grid-cols-2';
  if (cantidad === 3 || maximo === 3) return 'grid-cols-2 md:grid-cols-3';
  return 'grid-cols-2 lg:grid-cols-4';
}

/**
 * El producto que se luce en la portada: el primero en oferta con foto; si no, el primero
 * con foto; si no, el primero.
 */
export function productoEstrella(productos) {
  return productos.find((p) => p.imagenUrl && p.disponible && Number(p.precioComparacion) > Number(p.precio))
    ?? productos.find((p) => p.imagenUrl && p.disponible)
    ?? productos[0]
    ?? null;
}

/** "Envíos a todo Perú, Chile y Francia" con los países de la tienda. */
export function textoEnvioCorto(paises) {
  if (!paises?.length) return 'Envíos a todo el país';
  const nombres = paises.map((p) => p.nombre);
  return nombres.length === 1 ? `Envíos a todo ${nombres[0]}` : `Envíos a ${nombres.slice(0, -1).join(', ')} y ${nombres.at(-1)}`;
}
