import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCarrito } from '../../context/CarritoContext';
import { formatoMoneda } from '../../utils/formato';
import { IconoCerrar } from '../Iconos';
import ListaCarrito from './ListaCarrito';

/** Carrito lateral: se abre desde la derecha al agregar un producto o al tocar el ícono del carrito. */
export default function CarritoDrawer({ subdominio }) {
  const { items, subtotal, abierto, cerrar } = useCarrito();
  const navigate = useNavigate();

  useEffect(() => {
    if (!abierto) {
      return undefined;
    }
    const alPresionar = (e) => e.key === 'Escape' && cerrar();
    window.addEventListener('keydown', alPresionar);
    return () => window.removeEventListener('keydown', alPresionar);
  }, [abierto, cerrar]);

  return (
    <div className={`fixed inset-0 z-50 ${abierto ? '' : 'pointer-events-none'}`} aria-hidden={!abierto}>
      <div
        onClick={cerrar}
        className={`absolute inset-0 bg-black/40 transition-opacity ${abierto ? 'opacity-100' : 'opacity-0'}`}
      />
      <aside
        role="dialog"
        aria-label="Carrito"
        className={`absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-xl transition-transform duration-200 ${
          abierto ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <div className="flex items-center justify-between border-b border-[#e3e3e3] px-5 py-4">
          <h2 className="text-lg font-semibold text-[#303030]">Tu carrito</h2>
          <button type="button" onClick={cerrar} aria-label="Cerrar carrito" className="text-[#616161] hover:text-[#303030]">
            <IconoCerrar />
          </button>
        </div>

        {items.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 px-5 text-center">
            <p className="text-[#616161]">Tu carrito está vacío</p>
            <button type="button" onClick={cerrar} className="rounded-md border border-[#303030] px-5 py-2 text-sm font-medium">
              Seguir comprando
            </button>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-5">
              <ListaCarrito subdominio={subdominio} alNavegar={cerrar} />
            </div>
            <div className="space-y-3 border-t border-[#e3e3e3] px-5 py-4">
              <div className="flex justify-between font-medium text-[#303030]">
                <span>Subtotal</span>
                <span>{formatoMoneda(subtotal)}</span>
              </div>
              <p className="text-xs text-[#616161]">Impuestos incluidos. El envío se calcula al finalizar la compra.</p>
              <button
                type="button"
                onClick={() => {
                  cerrar();
                  navigate(`/tienda/${subdominio}/checkout`);
                }}
                className="w-full rounded-md bg-[#303030] py-3 font-medium text-white hover:bg-black"
              >
                Finalizar compra
              </button>
              <Link
                to={`/tienda/${subdominio}/carrito`}
                onClick={cerrar}
                className="block text-center text-sm text-[#616161] underline"
              >
                Ver carrito completo
              </Link>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}
