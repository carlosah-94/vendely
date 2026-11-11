import { useEffect, useState } from 'react';
import { Link, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import PrecioTienda from '../../components/tienda/PrecioTienda';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { enOferta } from '../../utils/formato';
import { obtenerProducto } from '../../services/tienda';

/** Página de un producto: imagen a la izquierda; nombre, precio, cantidad y botones a la derecha. */
export default function DetalleProducto() {
  const { subdominio } = useOutletContext();
  const { idProducto } = useParams();
  const { agregar, abrir } = useCarrito();
  const navigate = useNavigate();

  const clave = `${subdominio}-${idProducto}`;
  const [estado, setEstado] = useState({ clave: null, producto: null, error: '' });
  const [cantidad, setCantidad] = useState(1);
  const [fotoElegida, setFotoElegida] = useState(0);

  useEffect(() => {
    let vigente = true;
    obtenerProducto(subdominio, idProducto)
      .then((producto) => vigente && setEstado({ clave, producto, error: '' }))
      .catch((e) => vigente && setEstado({ clave, producto: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio, idProducto, clave]);

  if (estado.clave !== clave) {
    return <p className="p-10 text-center text-[#616161]">Cargando producto...</p>;
  }
  if (!estado.producto) {
    return (
      <div className="mx-auto max-w-md space-y-4 p-10 text-center">
        <Mensaje tipo="error" texto={estado.error} />
        <Link to={`/tienda/${subdominio}`} className="text-sm underline">Volver a la tienda</Link>
      </div>
    );
  }

  const p = estado.producto;
  const tope = Math.max(p.stock, 1);
  const fotos = p.imagenes?.length ? p.imagenes : [p.imagenUrl];
  const foto = fotos[Math.min(fotoElegida, fotos.length - 1)];

  return (
    <div className="mx-auto grid max-w-6xl gap-10 px-4 pt-10 md:grid-cols-2">
      {/* Galería: foto grande y, si hay varias, miniaturas para cambiarla */}
      <div className="space-y-3">
        <div className="overflow-hidden rounded-md">
          <ImagenProducto key={foto} url={foto} nombre={p.nombre} />
        </div>
        {fotos.length > 1 && (
          <div className="grid grid-cols-5 gap-2">
            {fotos.map((url, i) => (
              <button
                key={url}
                type="button"
                onClick={() => setFotoElegida(i)}
                aria-label={`Ver foto ${i + 1}`}
                className={`overflow-hidden rounded-md border-2 ${url === foto ? 'border-[#303030]' : 'border-transparent opacity-70 hover:opacity-100'}`}
              >
                <ImagenProducto url={url} nombre={p.nombre} />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-5">
        {p.marca && <p className="text-sm uppercase tracking-wide text-[#616161]">{p.marca}</p>}
        <h1 className="text-3xl font-semibold tracking-tight">{p.nombre}</h1>
        <div>
          <div className="flex items-center gap-3">
            <PrecioTienda producto={p} grande />
            {enOferta(p) && (
              <span className="rounded-full bg-[var(--tienda)] px-2.5 py-0.5 text-xs font-medium text-[var(--tienda-texto)]">Oferta</span>
            )}
          </div>
          <p className="text-sm text-[#616161]">Impuestos incluidos.</p>
        </div>

        {p.disponible ? (
          <>
            <p className="text-sm text-[#616161]">{p.stock} disponibles</p>
            <div>
              <p className="mb-1 text-sm">Cantidad</p>
              <div className="inline-flex items-center rounded-md border border-[#c9c9c9]">
                <button
                  type="button"
                  onClick={() => setCantidad((c) => Math.max(1, c - 1))}
                  aria-label="Menos"
                  className="h-11 w-11 text-xl leading-none"
                >
                  −
                </button>
                <span className="w-10 text-center">{cantidad}</span>
                <button
                  type="button"
                  onClick={() => setCantidad((c) => Math.min(tope, c + 1))}
                  aria-label="Más"
                  className="h-11 w-11 text-xl leading-none"
                >
                  +
                </button>
              </div>
            </div>
            <div className="space-y-3">
              <button
                type="button"
                onClick={() => {
                  agregar(p, cantidad);
                  abrir();
                }}
                className="w-full rounded-md border border-[#303030] py-3 font-medium hover:bg-[#f3f3f3]"
              >
                Agregar al carrito
              </button>
              <button
                type="button"
                onClick={() => {
                  agregar(p, cantidad);
                  navigate(`/tienda/${subdominio}/checkout`);
                }}
                className="w-full rounded-md bg-[var(--tienda)] py-3 font-medium text-[var(--tienda-texto)] hover:brightness-90"
              >
                Comprar ahora
              </button>
            </div>
          </>
        ) : (
          <button type="button" disabled className="w-full cursor-not-allowed rounded-md bg-[#e3e3e3] py-3 font-medium text-[#8a8a8a]">
            Agotado
          </button>
        )}

        {p.descripcion && <p className="whitespace-pre-line border-t border-[#e3e3e3] pt-5 text-[#303030]/85">{p.descripcion}</p>}
      </div>
    </div>
  );
}
