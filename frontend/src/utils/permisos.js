/**
 * Lo que una cuenta puede hacer en una tienda del panel.
 * El dueño puede todo; un empleado, solo lo que su dueño le marcó (issue de empleados).
 * El backend vuelve a revisarlo en cada petición: esto solo decide qué se muestra.
 */
export const PERMISOS = [
  { clave: 'PEDIDOS', campo: 'puedePedidos', texto: 'Pedidos', ayuda: 'Ver, registrar, enviar, entregar y cancelar pedidos' },
  { clave: 'PRODUCTOS', campo: 'puedeProductos', texto: 'Productos', ayuda: 'Crear y editar productos, fotos y categorías' },
  { clave: 'INVENTARIO', campo: 'puedeInventario', texto: 'Inventario', ayuda: 'Cambiar el stock' },
  { clave: 'CLIENTES', campo: 'puedeClientes', texto: 'Clientes', ayuda: 'Ver, crear y editar clientes' },
];

/** true si es el dueño de la tienda. Sin el dato (respuesta antigua), se asume dueño. */
export function esDuenoDe(tienda) {
  return Boolean(tienda) && tienda.esDueno !== false;
}

/** true si quien mira la tienda es soporte de Vendely, entrando con permiso de su dueño. */
export function esSoporte(tienda) {
  return Boolean(tienda?.soporte);
}

/** true si en esta tienda puede usar ALGUNO de estos permisos. */
export function puede(tienda, ...permisos) {
  if (!tienda) {
    return false;
  }
  return esDuenoDe(tienda) || permisos.some((p) => (tienda.permisos ?? []).includes(p));
}
