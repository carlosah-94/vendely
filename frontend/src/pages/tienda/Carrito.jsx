import { Link, useOutletContext } from 'react-router-dom';
import { useCarrito } from '../../context/CarritoContext';
import ListaCarrito from '../../components/tienda/ListaCarrito';
import { formatoMoneda } from '../../utils/formato';

/** Carrito a pantalla completa: cambiar cantidad, quitar, subtotal y "Continuar compra". */
export default function Carrito() {
  const { subdominio } = useOutletContext();
  const { items, subtotal } = useCarrito();

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold">Tu carrito está vacío</h1>
        <Link to={`/tienda/${subdominio}`} className="inline-block rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
          Seguir comprando
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 pt-10">
      <h1 className="mb-4 text-3xl font-semibold tracking-tight">Tu carrito</h1>
      <ListaCarrito subdominio={subdominio} />
      <div className="mt-4 space-y-3 border-t border-[#e3e3e3] pt-4 sm:ml-auto sm:max-w-xs">
        <div className="flex justify-between text-lg font-medium">
          <span>Subtotal</span>
          <span>{formatoMoneda(subtotal)}</span>
        </div>
        <p className="text-xs text-[#616161]">Impuestos incluidos. El envío se calcula al finalizar la compra.</p>
        <Link
          to={`/tienda/${subdominio}/checkout`}
          className="block rounded-md bg-[var(--tienda)] py-3 text-center font-medium text-[var(--tienda-texto)] hover:brightness-90"
        >
          Continuar compra
        </Link>
        <Link to={`/tienda/${subdominio}`} className="block text-center text-sm text-[#616161] underline">
          Seguir comprando
        </Link>
      </div>
    </div>
  );
}
