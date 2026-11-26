/* eslint-disable react-refresh/only-export-components */
import { enlaceWhatsapp } from '../../../utils/whatsapp';
import { textoEnvioCorto } from '../../../utils/catalogo';
import { metodosDeTienda, textoMetodos } from '../../../utils/metodosPago';

/**
 * Piezas que comparten las plantillas por rubro (VitrinaRubro.jsx): textos de cada rubro, fotos de
 * los productos para decorar y el enlace de WhatsApp. Todo lo que se promete sale de datos reales
 * de la tienda (envío, métodos de pago, recojo); los textos de marketing son genéricos del rubro.
 */

/** Textos de cada plantilla. */
export const TEXTOS = {
  horno: {
    etiqueta: 'Hecho en casa, todos los días',
    boton: 'Ver la carta',
    sello: 'Hecho hoy',
    anticipacion: '24 horas',
    mostrarFechaEspecial: true,
    mostrarPasos: true,
    pasos: [
      { titulo: 'Elige', texto: 'Mira la carta y elige lo que se te antoja.' },
      { titulo: 'Personaliza', texto: 'Escribe el mensaje o los detalles de tu pedido.' },
      { titulo: 'Recibe', texto: 'Te lo llevamos o pasas a recogerlo.' },
    ],
    catalogo: 'Nuestra carta',
    vacio: { titulo: 'Estamos horneando algo rico', texto: 'Muy pronto verás aquí nuestra carta. Mientras tanto, puedes hacer tu pedido por WhatsApp.' },
  },
  circuito: {
    etiqueta: 'Originales, con garantía y boleta',
    boton: 'Explorar productos',
    garantia: '12 meses',
    mostrarMarcas: true,
    catalogo: 'Todos los productos',
    vacio: { titulo: 'Los equipos están en camino', texto: 'Estamos cargando el catálogo. Escríbenos y te contamos qué llega primero.' },
  },
  impulso: {
    etiqueta: 'Entrena sin límites',
    boton: 'Comprar ahora',
    lema: 'Entrena, Supérate, No pares',
    catalogo: 'Todo el equipo',
    vacio: { titulo: 'Calentando motores', texto: 'El catálogo está por salir. Escríbenos y te asesoramos para tu entrenamiento.' },
  },
  pasarela: {
    etiqueta: 'Nueva colección',
    boton: 'Descubrir la colección',
    tallas: 'ropa',
    catalogo: 'La colección',
    vacio: { titulo: 'La colección se presenta pronto', texto: 'Estamos preparando las prendas. Escríbenos para saber qué llega primero.' },
  },
  glow: {
    etiqueta: 'Belleza que se nota',
    boton: 'Ver favoritos',
    sello: 'Original garantizado',
    mostrarPasos: true,
    pasos: [
      { titulo: 'Limpia', texto: 'Prepara tu piel quitando impurezas.' },
      { titulo: 'Trata', texto: 'Usa el producto según tu necesidad.' },
      { titulo: 'Protege', texto: 'Sella e hidrata para lucir radiante.' },
    ],
    catalogo: 'Nuestros favoritos',
    vacio: { titulo: 'Estamos preparando tu rutina', texto: 'Muy pronto verás aquí nuestros productos. Escríbenos y te recomendamos.' },
  },
  nido: {
    etiqueta: 'Para una casa que se siente tuya',
    boton: 'Ver ambientes',
    catalogo: 'Para tu casa',
    vacio: { titulo: 'Estamos decorando', texto: 'Pronto verás aquí nuestros productos para cada ambiente. Escríbenos si buscas algo.' },
  },
  huellitas: {
    etiqueta: 'Todo para tu engreído',
    boton: 'Ver productos',
    globo1: '¡Guau!',
    globo2: '¡Miau!',
    catalogo: 'Para tu engreído',
    vacio: { titulo: 'Preparando muchos mimos', texto: 'Pronto verás aquí todo para tu mascota. Escríbenos y te ayudamos a elegir.' },
  },
};

/**
 * Los textos de la plantilla de esta tienda: los que el dueño escribió en Personalizar
 * (diseno.contenidoRubro, V23) y, para lo que dejó vacío, los de la plantilla.
 */
export function textosDe(tienda) {
  const base = TEXTOS[tienda.tema] ?? TEXTOS.horno;
  const propio = tienda.diseno?.contenidoRubro ?? {};
  const t = { ...base };
  Object.entries(propio).forEach(([clave, valor]) => {
    if (valor === null || valor === undefined || valor === '') return;
    if (Array.isArray(valor) && valor.length === 0) return;
    t[clave] = valor;
  });
  t.preguntas = propio.preguntas ?? [];
  // La cinta de Impulso: palabras separadas por coma o por punto.
  t.palabras = String(t.lema ?? '').split(/[,.•]/).map((x) => x.trim().toUpperCase()).filter(Boolean);
  return t;
}

/** Las fotos de los productos (las que tienen imagen), para decorar portadas y collages. */
export function fotosDe(todos, cantidad) {
  return (todos ?? []).filter((p) => p.imagenUrl).slice(0, cantidad);
}

/** Enlace para escribir a la tienda por WhatsApp (null si no puso su número). */
export function whatsappDe(tienda, mensaje) {
  return enlaceWhatsapp(tienda.whatsapp, mensaje ?? `Hola ${tienda.nombre}, quiero hacer un pedido.`);
}

/** Datos reales para los beneficios: envío, pago y recojo. */
export function datosReales(tienda, paises) {
  const metodos = metodosDeTienda(tienda);
  return {
    envio: textoEnvioCorto(paises),
    pago: metodos.length > 0 ? `Paga con ${textoMetodos(metodos)}` : null,
    recojo: tienda.recojoDireccion ? { direccion: tienda.recojoDireccion, horario: tienda.recojoHorario } : null,
    distritos: tienda.distritosDelivery ?? [], // delivery propio por distritos (V19)
  };
}

const TRAZOS = {
  reloj: 'M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z',
  moto: 'M5 17a2 2 0 1 0 0-.01M19 17a2 2 0 1 0 0-.01M7 17h8l2-5h-5l-2-3H7M15 7h3',
  tienda: 'M4 10h16l-1.5-5h-13zM5 10v9h14v-9M9 19v-5h6v5',
  chat: 'M4 5h16v11H8l-4 4z',
  escudo: 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6zM9 12l2 2 4-4',
  camion: 'M3 7h11v9H3zM14 10h4l3 3v3h-7M7 19a1.6 1.6 0 1 0 0-.01M17 19a1.6 1.6 0 1 0 0-.01',
  tarjeta: 'M3 6h18v12H3zM3 10h18M7 15h4',
  rayo: 'M13 2L4 14h7l-1 8 9-12h-7z',
  chispa: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z',
  hoja: 'M5 19c0-8 6-14 14-14 0 8-6 14-14 14zM5 19l7-7',
  corazon: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  regla: 'M3 17L17 3l4 4L7 21zM7 13l2 2M10 10l2 2M13 7l2 2',
  check: 'M5 12l5 5L20 7',
  caja: 'M3 7l9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4M12 11v10',
};

export function Icono({ nombre, tamano = 22, grosor = 1.8, className }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={grosor}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      <path d={TRAZOS[nombre]} />
    </svg>
  );
}

/** Logo de WhatsApp (globo con teléfono), en blanco. */
export function IconoWhatsapp({ tamano = 20 }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.6 0-3.1-.4-4.4-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.1.7.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.1-1.2l-.6-.3z" />
    </svg>
  );
}

/** Botón verde de WhatsApp (no se muestra si la tienda no puso su número). */
export function BotonWhatsappRubro({ tienda, mensaje, texto = 'Pedir por WhatsApp', className = '' }) {
  const enlace = whatsappDe(tienda, mensaje);
  if (!enlace) return null;
  return (
    <a href={enlace} target="_blank" rel="noopener noreferrer"
      className={`boton-tienda inline-flex items-center justify-center gap-2 bg-[#1f9d55] px-6 py-3.5 font-semibold text-white shadow-[0_12px_30px_-10px_rgba(31,157,85,0.8)] transition hover:-translate-y-0.5 hover:bg-[#188a49] ${className}`}>
      <IconoWhatsapp /> {texto}
    </a>
  );
}

/** Imagen de un producto o, si no hay, la ilustración de la plantilla. */
export function FotoO({ foto, children, className = '', alt = '' }) {
  if (foto?.imagenUrl) return <img src={foto.imagenUrl} alt={alt || foto.nombre} className={`h-full w-full object-cover ${className}`} />;
  return children;
}
