import { enOferta, formatoMoneda } from '../../utils/formato';

/**
 * Precio de un producto en la tienda. Si tiene precio de comparación, muestra el precio
 * anterior tachado al lado, como hacen las tiendas de Shopify.
 */
export default function PrecioTienda({ producto, grande = false, desde = false }) {
  const oferta = enOferta(producto);
  return (
    <p className={`flex flex-wrap items-baseline gap-x-2 ${grande ? 'text-2xl' : 'text-sm'}`}>
      <span className={oferta ? 'text-[#303030]' : grande ? '' : 'text-[#616161]'}>
        {desde && 'Desde '}{formatoMoneda(producto.precio)}
      </span>
      {oferta && (
        <span className={`text-[#8a8a8a] line-through ${grande ? 'text-lg' : 'text-xs'}`}>
          {formatoMoneda(producto.precioComparacion)}
        </span>
      )}
    </p>
  );
}
