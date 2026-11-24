/**
 * Las 3 plantillas de la tienda pública. Todas usan el color y el logo de la tienda.
 * - clasica:  portada en una tarjeta grande con el color de la tienda y su producto estrella
 *             (o su imagen de portada con un velo de color). Todos los planes.
 * - moderna:  "escaparate": nombre enorme con letra ancha, collage con fotos de tus productos
 *             (o la imagen de portada),
 *             categorías con foto y catálogo con un producto destacado. Solo Pro.
 * - boutique: "atelier": la imagen de portada (o la foto de tu producto) a pantalla completa, letra con serifa fina,
 *             mucho aire y fotos verticales, como una tienda de moda. Solo Pro.
 * La portada de cada una está en pages/tienda/Vitrina.jsx; la clase "plantilla-..." de
 * index.css da la letra y las esquinas a las demás páginas. La letra, los botones, el fondo y
 * las secciones que elige el dueño se suman encima: ver utils/diseno.js.
 */
export const PLANTILLAS = [
  { valor: 'clasica', nombre: 'Clásica', descripcion: 'Portada con tu color y tu producto estrella. Simple y clara.' },
  { valor: 'moderna', nombre: 'Moderna', descripcion: 'Tu nombre en grande, collage con tus fotos y un producto destacado.' },
  { valor: 'boutique', nombre: 'Boutique', descripcion: 'Tu mejor foto a pantalla completa y letra elegante, como una tienda de moda.' },
];
