/**
 * Qué vende la tienda (V17). Al crearla, el backend (KitRubro.java) le arma categorías, una
 * descripción y un anuncio pensados para ese negocio. Todo se puede cambiar después.
 */
export const RUBROS = [
  { id: 'moda', nombre: 'Ropa y moda', kit: 'Polos, Pantalones, Vestidos, Accesorios' },
  { id: 'comida', nombre: 'Comida y postres', kit: 'Tortas, Postres, Bocaditos, Pedidos especiales' },
  { id: 'tecnologia', nombre: 'Tecnología', kit: 'Celulares, Accesorios, Audio, Cómputo' },
  { id: 'belleza', nombre: 'Belleza', kit: 'Maquillaje, Cuidado de la piel, Cabello, Fragancias' },
  { id: 'hogar', nombre: 'Hogar y deco', kit: 'Cocina, Decoración, Dormitorio, Organización' },
  { id: 'deporte', nombre: 'Deporte', kit: 'Ropa deportiva, Calzado, Suplementos, Accesorios' },
  { id: 'mascotas', nombre: 'Mascotas', kit: 'Alimento, Juguetes, Higiene, Accesorios' },
  { id: 'otro', nombre: 'Otra cosa', kit: 'Empiezas en blanco y creas tus categorías' },
];

export function nombreRubro(id) {
  return RUBROS.find((r) => r.id === id)?.nombre ?? null;
}
