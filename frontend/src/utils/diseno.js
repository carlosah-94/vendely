import { colorTextoSobre } from './formato';

/**
 * Personalización de la tienda pública (V12): letra, botones, fondo, acento, portada, anuncio
 * y secciones. Llega en tienda.diseno (GET /api/public/tiendas/{sub}) ya filtrada por el plan.
 *
 * Qué entra en cada plan (lo valida TiendaService.actualizar):
 *   - Gratis: mostrar u ocultar secciones.
 *   - Básico: además logo, colores, acento, portada, anuncio, letra y botones.
 *   - Pro:    además las plantillas Moderna y Boutique y el color de fondo.
 *
 * Cómo se aplica: estiloTienda() da clases ("fuente-elegante", "botones-recto") y variables CSS
 * (--tienda, --acento, --fondo...) para el contenedor de la tienda; index.css hace el resto.
 */

/** "auto" = la de la plantilla. muestra: la letra con que se dibuja la opción en Personalizar. */
export const FUENTES = [
  { valor: 'auto', nombre: 'La de la plantilla', muestra: 'Inter, sans-serif' },
  { valor: 'moderna', nombre: 'Moderna', descripcion: 'Inter, limpia y clara', muestra: 'Inter, sans-serif' },
  { valor: 'expresiva', nombre: 'Expresiva', descripcion: 'Bricolage, con carácter', muestra: '"Bricolage Grotesque", sans-serif' },
  { valor: 'elegante', nombre: 'Elegante', descripcion: 'Cormorant con serifa fina', muestra: '"Cormorant Garamond", serif' },
  { valor: 'impacto', nombre: 'Impacto', descripcion: 'Archivo ancha y gruesa', muestra: 'Archivo, sans-serif' },
];

export const BOTONES = [
  { valor: 'auto', nombre: 'Plantilla' },
  { valor: 'redondo', nombre: 'Redondo', radio: '9999px' },
  { valor: 'suave', nombre: 'Suave', radio: '0.5rem' },
  { valor: 'recto', nombre: 'Recto', radio: '0' },
];

/** Tonos claros: el texto oscuro de las plantillas siempre se lee. suave = tarjetas y franjas. */
export const FONDOS = [
  { valor: 'blanco', nombre: 'Blanco', color: '#FFFFFF', suave: '#F5F5F6', borde: '#E6E6E8' },
  { valor: 'hueso', nombre: 'Hueso', color: '#FBF8F2', suave: '#F2ECE1', borde: '#E7DFD1' },
  { valor: 'perla', nombre: 'Perla', color: '#F4F6F8', suave: '#E8ECF0', borde: '#DCE1E7' },
  { valor: 'arena', nombre: 'Arena', color: '#F4EDE3', suave: '#E9DFD1', borde: '#DCCFBD' },
];

/**
 * Diseño avanzado del plan Pro (V15, DisenoPro en el backend). La vitrina recibe el "vacío"
 * (todo apagado) si la tienda no es Pro. Los enlaces solo pueden empezar con /, # o https://.
 */
export const PRO_INICIAL = {
  portada: {
    activa: false, titulo: null, subtitulo: null, boton1Texto: null, boton1Enlace: null,
    boton2Texto: null, boton2Enlace: null, alineacion: 'izquierda', oscurecer: 35, alto: 'alto',
  },
  anuncio: { color: null, mensajes: [] },
  promo: { mostrar: false, titulo: null, texto: null, botonTexto: null, botonEnlace: null, color: null },
  categoriasEnMenu: false,
  mostrarDescuento: false,
};

/** El diseño Pro con los valores por defecto donde falte algo. */
export function proDe(pro) {
  const p = pro ?? {};
  const sinNulos = (o) => Object.fromEntries(Object.entries(o ?? {}).filter(([, v]) => v !== null && v !== undefined));
  return {
    portada: { ...PRO_INICIAL.portada, ...sinNulos(p.portada) },
    anuncio: { ...PRO_INICIAL.anuncio, ...sinNulos(p.anuncio) },
    promo: { ...PRO_INICIAL.promo, ...sinNulos(p.promo) },
    categoriasEnMenu: Boolean(p.categoriasEnMenu),
    mostrarDescuento: Boolean(p.mostrarDescuento),
  };
}

/** Texto de la etiqueta de un producto rebajado: "-25 %" si la tienda lo eligió (Pro), si no "Oferta". */
export function etiquetaOferta(producto, diseno) {
  if (!diseno?.pro?.mostrarDescuento) return 'Oferta';
  const antes = Number(producto.precioComparacion);
  const ahora = Number(producto.precio);
  const porcentaje = antes > 0 ? Math.round((1 - ahora / antes) * 100) : 0;
  return porcentaje > 0 ? `-${porcentaje} %` : 'Oferta';
}

/** ¿El enlace de un botón es seguro? (el backend ya lo valida; esto evita pintar algo raro). */
export function enlaceSeguro(enlace) {
  return typeof enlace === 'string' && /^(\/|#|https?:\/\/)/.test(enlace.trim());
}

export const DISENO_INICIAL = {
  colorAcento: null,
  textoAnuncio: null,
  mostrarAnuncio: true,
  imagenPortadaUrl: null,
  fuente: 'auto',
  estiloBotones: 'auto',
  fondo: 'blanco',
  mostrarGarantias: true,
  mostrarDestacado: true,
  mostrarCategorias: true,
  mostrarSeccionRubro: true,
};

/** El diseño de la tienda con los valores por defecto donde falte algo (tiendas antiguas, null). */
export function disenoDe(tienda) {
  const d = tienda?.diseno ?? {};
  const limpio = Object.fromEntries(Object.entries(d).filter(([, v]) => v !== null && v !== undefined));
  return { ...DISENO_INICIAL, ...limpio, pro: proDe(d.pro) };
}

/** Clases y variables CSS del contenedor de la tienda (LayoutTienda). */
export function estiloTienda(tienda) {
  const d = disenoDe(tienda);
  const principal = tienda?.colorPrincipal || '#303030';
  const acento = d.colorAcento || principal;
  const fondo = FONDOS.find((f) => f.valor === d.fondo) ?? FONDOS[0];
  return {
    className: [
      'tienda-diseno',
      `plantilla-${tienda?.plantilla ?? 'clasica'}`,
      d.fuente !== 'auto' && `fuente-${d.fuente}`,
      d.estiloBotones !== 'auto' && `botones-${d.estiloBotones}`,
    ].filter(Boolean).join(' '),
    style: {
      '--tienda': principal,
      '--tienda-texto': colorTextoSobre(principal),
      '--acento': acento,
      '--acento-texto': colorTextoSobre(acento),
      '--fondo': fondo.color,
      '--fondo-suave': fondo.suave,
      '--fondo-borde': fondo.borde,
    },
  };
}
