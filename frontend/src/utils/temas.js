import { DISENO_INICIAL, disenoDe } from './diseno';

/**
 * Temas de Vendely (Tienda online → Temas). Un tema = una de las 3 plantillas + un estilo ya armado
 * (colores, letra, botones y fondo). Aplicarlo guarda esos valores con el mismo PUT de Personalizar,
 * así que el backend valida el plan con los campos de siempre; además guarda el nombre (V13).
 *
 * plan: el plan mínimo que lo permite.
 *   Gratis → solo la plantilla Clásica con los valores por defecto.
 *   Básico → letra, botones y colores propios (PERSONALIZAR).
 *   Pro    → plantillas Moderna y Boutique y fondo de color (PLANTILLAS).
 *
 * colorPrincipal null = el tema respeta el color que ya tiene la tienda.
 * Al aplicar un tema NO se tocan el logo, la imagen de portada, el anuncio ni las secciones.
 */
export const TEMAS = [
  {
    valor: 'esencial',
    nombre: 'Esencial',
    descripcion: 'La plantilla Clásica tal cual: limpia, clara y con tu color.',
    plan: 'Gratis',
    plantilla: 'clasica',
    colorPrincipal: null,
    estilo: { colorAcento: null, fuente: 'auto', estiloBotones: 'auto', fondo: 'blanco' },
  },
  {
    valor: 'mercado',
    nombre: 'Mercado',
    descripcion: 'Cálido y cercano, con letra expresiva. Ideal para comida, café o artesanía.',
    plan: 'Básico',
    plantilla: 'clasica',
    colorPrincipal: '#C2410C',
    estilo: { colorAcento: '#F59E0B', fuente: 'expresiva', estiloBotones: 'suave', fondo: 'blanco' },
  },
  {
    valor: 'minimal',
    nombre: 'Minimal',
    descripcion: 'Negro sobre blanco, botones rectos y nada que distraiga de tus productos.',
    plan: 'Básico',
    plantilla: 'clasica',
    colorPrincipal: '#111111',
    estilo: { colorAcento: null, fuente: 'moderna', estiloBotones: 'recto', fondo: 'blanco' },
  },
  {
    valor: 'escaparate',
    nombre: 'Escaparate',
    descripcion: 'Tu nombre enorme, collage con tus fotos y una cinta que corre. Con tu color.',
    plan: 'Pro',
    plantilla: 'moderna',
    colorPrincipal: null,
    estilo: { colorAcento: null, fuente: 'impacto', estiloBotones: 'redondo', fondo: 'blanco' },
  },
  {
    valor: 'pop',
    nombre: 'Pop',
    descripcion: 'Azul eléctrico con acento rosado sobre fondo perla. Para marcas jóvenes.',
    plan: 'Pro',
    plantilla: 'moderna',
    colorPrincipal: '#1D4ED8',
    estilo: { colorAcento: '#FF4F8B', fuente: 'expresiva', estiloBotones: 'redondo', fondo: 'perla' },
  },
  {
    valor: 'atelier',
    nombre: 'Atelier',
    descripcion: 'Foto a pantalla completa, serifa fina y fondo hueso. Como una tienda de moda.',
    plan: 'Pro',
    plantilla: 'boutique',
    colorPrincipal: null,
    estilo: { colorAcento: null, fuente: 'elegante', estiloBotones: 'auto', fondo: 'hueso' },
  },
  {
    valor: 'arena',
    nombre: 'Arena',
    descripcion: 'Tonos tierra y fondo arena. Para cerámica, deco y productos hechos a mano.',
    plan: 'Pro',
    plantilla: 'boutique',
    colorPrincipal: '#8A5A3B',
    estilo: { colorAcento: '#B4532A', fuente: 'elegante', estiloBotones: 'recto', fondo: 'arena' },
  },
];

/**
 * Plantillas por rubro (V22, TemaRubro.java: los valores deben ser los mismos). No son solo colores:
 * la vitrina (components/tienda/VitrinaRubro.jsx) cambia la portada, los beneficios, la sección
 * propia del rubro y las tarjetas. La de SU rubro la usa cualquier plan; la de otro rubro, Pro.
 */
export const TEMAS_RUBRO = [
  {
    valor: 'horno', rubro: 'comida', nombre: 'Horno',
    descripcion: 'Cálida y apetitosa: portada tipo carta, sello "Hecho hoy", horario, delivery y pedido por WhatsApp.',
    colorPrincipal: '#C2410C', estilo: { colorAcento: '#F59E0B', fuente: 'expresiva', estiloBotones: 'redondo', fondo: 'blanco' },
  },
  {
    valor: 'circuito', rubro: 'tecnologia', nombre: 'Circuito',
    descripcion: 'Oscura y eléctrica: portada con brillo neón, sellos de garantía, marcas y fichas técnicas.',
    colorPrincipal: '#4F46E5', estilo: { colorAcento: '#22D3EE', fuente: 'moderna', estiloBotones: 'suave', fondo: 'blanco' },
  },
  {
    valor: 'impulso', rubro: 'deporte', nombre: 'Impulso',
    descripcion: 'Energía pura: letras gigantes en diagonal, verde lima, cinta en movimiento y categorías numeradas.',
    colorPrincipal: '#111111', estilo: { colorAcento: '#C6F432', fuente: 'impacto', estiloBotones: 'recto', fondo: 'blanco' },
  },
  {
    valor: 'pasarela', rubro: 'moda', nombre: 'Pasarela',
    descripcion: 'Editorial como una revista: tipografía de lujo, collage de fotos y guía de tallas.',
    colorPrincipal: '#1C1917', estilo: { colorAcento: '#B45309', fuente: 'elegante', estiloBotones: 'recto', fondo: 'blanco' },
  },
  {
    valor: 'glow', rubro: 'belleza', nombre: 'Glow',
    descripcion: 'Suave y luminosa: degradados rosados, arcos con tus productos, rutina en pasos y preguntas frecuentes.',
    colorPrincipal: '#BE185D', estilo: { colorAcento: '#F9A8D4', fuente: 'elegante', estiloBotones: 'redondo', fondo: 'blanco' },
  },
  {
    valor: 'nido', rubro: 'hogar', nombre: 'Nido',
    descripcion: 'Natural y acogedora: tonos salvia, formas de arco y ambientes para inspirar.',
    colorPrincipal: '#4D6B4F', estilo: { colorAcento: '#D97706', fuente: 'expresiva', estiloBotones: 'suave', fondo: 'blanco' },
  },
  {
    valor: 'huellitas', rubro: 'mascotas', nombre: 'Huellitas',
    descripcion: 'Alegre y juguetona: colores vivos, huellitas, burbujas y cuidados para cada engreído.',
    colorPrincipal: '#7C3AED', estilo: { colorAcento: '#F97316', fuente: 'expresiva', estiloBotones: 'redondo', fondo: 'blanco' },
  },
].map((t) => ({ ...t, plan: 'Rubro', plantilla: 'clasica' }));

export function esTemaRubro(valor) {
  return TEMAS_RUBRO.some((t) => t.valor === valor);
}

/** La plantilla del rubro de la tienda (null para "otro" o sin rubro). */
export function temaDeRubro(rubro) {
  return TEMAS_RUBRO.find((t) => t.rubro === rubro) ?? null;
}

/**
 * ¿La tienda tiene la plantilla de SU rubro sin cambios? (igual que TemaRubro.intacta en el backend).
 * Entonces la vitrina la muestra con sus colores y su letra en cualquier plan.
 */
export function rubroIntacto(tienda) {
  const tema = temaDeRubro(tienda?.rubro);
  if (!tema || tienda.tema !== tema.valor) return false;
  const d = disenoDe(tienda);
  const igual = (a, b) => (a ?? '').toUpperCase() === (b ?? '').toUpperCase();
  return tienda.plantilla === 'clasica' && igual(tienda.colorPrincipal, tema.colorPrincipal)
    && igual(d.colorAcento, tema.estilo.colorAcento) && d.fuente === tema.estilo.fuente
    && d.estiloBotones === tema.estilo.estiloBotones && d.fondo === 'blanco';
}

export function temaPorValor(valor) {
  return TEMAS.find((t) => t.valor === valor) ?? TEMAS_RUBRO.find((t) => t.valor === valor) ?? TEMAS[0];
}

/** ¿El plan de la tienda (TiendaResponse) permite este tema? */
export function planPermiteTema(tema, tienda) {
  // La de su rubro: todos los planes. La de otro rubro: es una plantilla extra (Pro).
  if (tema.plan === 'Rubro') return tema.rubro === tienda.rubro || tienda.permitePlantillas !== false;
  if (tema.plan === 'Pro') return tienda.permitePlantillas !== false;
  if (tema.plan === 'Básico') return tienda.permitePersonalizar !== false;
  return true;
}

/**
 * Borrador para la vista previa de un tema (ver useBorradorVistaPrevia en LayoutTienda): solo lo que
 * cambia el tema. Lo demás (logo, nombre, productos) sale de la tienda publicada, y la portada,
 * el anuncio y las secciones se conservan, como al aplicarlo. Se muestra el tema completo aunque el
 * plan todavía no lo incluya: así el dueño ve lo que obtendría al mejorar.
 */
export function borradorTema(tienda, tema) {
  return {
    plantilla: tema.plantilla,
    tema: tema.valor,
    ...(tema.colorPrincipal && { colorPrincipal: tema.colorPrincipal }),
    diseno: { ...DISENO_INICIAL, ...disenoDe(tienda), ...tema.estilo },
  };
}

/** Cuerpo del PUT /api/panel/tiendas/{id} que aplica el tema. */
export function cuerpoAplicarTema(tienda, tema) {
  return {
    nombre: tienda.nombre,
    descripcion: tienda.descripcion ?? null,
    correoContacto: tienda.correoContacto,
    logoUrl: tienda.logoUrl ?? null,
    colorPrincipal: tema.colorPrincipal ?? tienda.colorPrincipal ?? null,
    permiteInvitados: tienda.permiteInvitados,
    plantilla: tema.plantilla,
    tema: tema.valor,
    colorAcento: tema.estilo.colorAcento ?? '',
    fuente: tema.estilo.fuente,
    estiloBotones: tema.estilo.estiloBotones,
    fondo: tema.estilo.fondo,
  };
}

/**
 * El tema actual y si el dueño lo cambió después en Personalizar ("Esencial (personalizado)").
 * Las tiendas que nunca aplicaron un tema cuentan como Esencial.
 */
export function temaActual(tienda) {
  const tema = temaPorValor(tienda.tema ?? 'esencial');
  const d = disenoDe(tienda);
  const e = tema.estilo;
  const personalizado = tienda.plantilla !== tema.plantilla
    || (tema.colorPrincipal && tema.colorPrincipal !== tienda.colorPrincipal)
    || (e.colorAcento ?? null) !== (d.colorAcento ?? null)
    || e.fuente !== d.fuente || e.estiloBotones !== d.estiloBotones || e.fondo !== d.fondo;
  return { tema, personalizado };
}
