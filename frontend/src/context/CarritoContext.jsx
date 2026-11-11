import { createContext, useContext, useEffect, useState } from 'react';

const CarritoContext = createContext(null);

/** Lee el carrito guardado; si el navegador no deja o el dato está dañado, empieza vacío. */
function leer(clave) {
  try {
    const datos = JSON.parse(localStorage.getItem(clave) ?? '[]');
    return Array.isArray(datos) ? datos : [];
  } catch {
    return [];
  }
}

/**
 * Carrito de UNA tienda, guardado en el navegador (localStorage) con la clave
 * vendely_carrito_<subdominio>. Cada elemento:
 * { idVariante, idProducto, nombre, precio, cantidad, stock, imagenUrl }
 * El stock del carrito es solo una ayuda: el backend vuelve a validarlo al crear el pedido.
 */
export function CarritoProvider({ subdominio, children }) {
  const clave = `vendely_carrito_${subdominio}`;
  const [items, setItems] = useState(() => leer(clave));
  const [abierto, setAbierto] = useState(false);

  useEffect(() => {
    try {
      localStorage.setItem(clave, JSON.stringify(items));
    } catch {
      // Sin almacenamiento, el carrito solo dura mientras la página está abierta.
    }
  }, [clave, items]);

  /** Agrega un producto sin pasar del stock disponible. */
  function agregar(producto, cantidad = 1) {
    setItems((actual) => {
      const existente = actual.find((i) => i.idVariante === producto.idVariante);
      const tope = Math.max(producto.stock ?? 1, 1);
      if (existente) {
        return actual.map((i) =>
          i.idVariante === producto.idVariante
            ? { ...i, stock: tope, precio: producto.precio, cantidad: Math.min(i.cantidad + cantidad, tope) }
            : i,
        );
      }
      return [
        ...actual,
        {
          idVariante: producto.idVariante,
          idProducto: producto.idProducto,
          nombre: producto.nombre,
          precio: producto.precio,
          stock: tope,
          imagenUrl: producto.imagenUrl ?? null,
          cantidad: Math.min(cantidad, tope),
        },
      ];
    });
  }

  function cambiarCantidad(idVariante, cantidad) {
    setItems((actual) =>
      actual.map((i) =>
        i.idVariante === idVariante ? { ...i, cantidad: Math.max(1, Math.min(cantidad, i.stock)) } : i,
      ),
    );
  }

  function quitar(idVariante) {
    setItems((actual) => actual.filter((i) => i.idVariante !== idVariante));
  }

  function vaciar() {
    setItems([]);
  }

  const cantidadTotal = items.reduce((suma, i) => suma + i.cantidad, 0);
  const subtotal = items.reduce((suma, i) => suma + i.precio * i.cantidad, 0);

  return (
    <CarritoContext.Provider
      value={{
        items, cantidadTotal, subtotal, agregar, cambiarCantidad, quitar, vaciar,
        abierto, abrir: () => setAbierto(true), cerrar: () => setAbierto(false),
      }}
    >
      {children}
    </CarritoContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCarrito() {
  return useContext(CarritoContext);
}
