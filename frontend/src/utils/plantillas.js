/**
 * Las 3 plantillas de la tienda pública. Todas usan el color y el logo de la tienda.
 * - clasica:  la de siempre (portada centrada con el color de la tienda). Todos los planes.
 * - moderna:  "escaparate": nombre enorme con letra ancha, collage con fotos de tus productos,
 *             categorías con foto y catálogo con un producto destacado. Solo Pro.
 * - boutique: "atelier": foto de tu producto a pantalla completa, letra con serifa fina,
 *             mucho aire y fotos verticales, como una tienda de moda. Solo Pro.
 * La portada de cada una está en pages/tienda/Vitrina.jsx; la clase "plantilla-..." de
 * index.css da la letra y las esquinas a las demás páginas.
 */
export const PLANTILLAS = [
  { valor: 'clasica', nombre: 'Clásica', descripcion: 'Portada centrada con tu color. Simple y clara.' },
  { valor: 'moderna', nombre: 'Moderna', descripcion: 'Tu nombre en grande, collage con tus fotos y un producto destacado.' },
  { valor: 'boutique', nombre: 'Boutique', descripcion: 'Tu mejor foto a pantalla completa y letra elegante, como una tienda de moda.' },
];
