import { Link } from 'react-router-dom';
import { useCarrito } from '../../context/CarritoContext';
import { formatoMoneda } from '../../utils/formato';
import ImagenProducto from '../ImagenProducto';

/** Productos del carrito con cantidad (− y +) y "Eliminar". Lo usan el carrito lateral y la página /carrito. */
export default function ListaCarrito({ subdominio, alNavegar }) {
  const { items, cambiarCantidad, quitar } = useCarrito();

  return (
    <ul className="divide-y divide-[#e3e3e3]">
      {items.map((i) => (
        <li key={i.idVariante} className="flex gap-4 py-4">
          <Link
            to={`/tienda/${subdominio}/producto/${i.idProducto}`}
            onClick={alNavegar}
            className="block w-20 shrink-0 overflow-hidden rounded-md"
          >
            <ImagenProducto url={i.imagenUrl} nombre={i.nombre} />
          </Link>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium text-[#303030]">{i.nombre}</p>
            <p className="text-sm text-[#616161]">{formatoMoneda(i.precio)}</p>
            <div className="mt-2 flex items-center gap-4">
              <div className="inline-flex items-center rounded-md border border-[#c9c9c9]">
                <button
                  type="button"
                  onClick={() => cambiarCantidad(i.idVariante, i.cantidad - 1)}
                  disabled={i.cantidad <= 1}
                  aria-label="Menos"
                  className="h-8 w-8 text-lg leading-none disabled:opacity-30"
                >
                  −
                </button>
                <span className="w-8 text-center text-sm">{i.cantidad}</span>
                <button
                  type="button"
                  onClick={() => cambiarCantidad(i.idVariante, i.cantidad + 1)}
                  disabled={i.cantidad >= i.stock}
                  aria-label="Más"
                  className="h-8 w-8 text-lg leading-none disabled:opacity-30"
                >
                  +
                </button>
              </div>
              <button type="button" onClick={() => quitar(i.idVariante)} className="text-sm text-[#616161] underline hover:text-[#303030]">
                Eliminar
              </button>
            </div>
          </div>
          <p className="text-sm font-medium text-[#303030]">{formatoMoneda(i.precio * i.cantidad)}</p>
        </li>
      ))}
    </ul>
  );
}
