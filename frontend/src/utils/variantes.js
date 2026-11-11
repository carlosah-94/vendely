/**
 * Ayudas para productos con opciones (talla, color).
 * Una "combinación" es la lista de valores de una variante en el orden de las opciones: ['M', 'Negro'].
 */

export const MAX_OPCIONES = 3;
export const MAX_VARIANTES = 100;

/** Todas las combinaciones posibles de los valores (producto cartesiano): S/M × Negro/Blanco = 4. */
export function combinaciones(opciones) {
  const conValores = opciones.filter((o) => o.valores.length > 0);
  if (conValores.length === 0 || conValores.length !== opciones.length) return [];
  return conValores.reduce(
    (acumulado, opcion) => acumulado.flatMap((combo) => opcion.valores.map((valor) => [...combo, valor])),
    [[]],
  );
}

/** Clave de una combinación para encontrarla aunque cambien mayúsculas o espacios: "m|negro". */
export function claveVariante(valores) {
  return valores.map((v) => v.trim().toLowerCase()).join('|');
}

/** Texto de una combinación: "M / Negro". */
export function nombreVariante(valores) {
  return valores.join(' / ');
}
