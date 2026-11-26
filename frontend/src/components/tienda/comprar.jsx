import { Link } from 'react-router-dom';

/* Piezas de compra que comparten las 3 plantillas de la portada. */

/** true si las variantes tienen precios distintos: se muestra "Desde S/ ...". */
// eslint-disable-next-line react-refresh/only-export-components
export function tieneVariosPrecios(p) {
  return new Set((p.variantes ?? []).map((v) => Number(v.precio))).size > 1;
}

/**
 * Botón de compra de una tarjeta: con talla o color lleva a elegir opciones; si no, agrega al carrito.
 * Lo usan las 3 plantillas, cada una con sus clases.
 */
export function BotonComprar({ producto: p, subdominio, onAgregar, className, textoAgregar = 'Agregar al carrito' }) {
  // Sin onAgregar: la tienda todavía no recibe pedidos (falta el RUC del dueño, V24).
  if (!onAgregar) {
    return <button type="button" disabled className={className}>Muy pronto</button>;
  }
  if ((p.variantes?.length ?? 0) > 1) {
    return (
      <Link to={`/tienda/${subdominio}/producto/${p.idProducto}`} className={className}>
        {p.disponible ? 'Elegir opciones' : 'Agotado'}
      </Link>
    );
  }
  return (
    <button type="button" disabled={!p.disponible} onClick={() => onAgregar(p)} className={className}>
      {p.disponible ? textoAgregar : 'Agotado'}
    </button>
  );
}
