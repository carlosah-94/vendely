import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import PrecioTienda from '../../components/tienda/PrecioTienda';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { listarProductos } from '../../services/tienda';
import { enOferta } from '../../utils/formato';

/** Portada de la tienda: hero con su color y la grilla de productos activos. */
export default function Vitrina() {
  const { tienda, subdominio } = useOutletContext();
  const { agregar, abrir } = useCarrito();
  const [estado, setEstado] = useState({ subdominio: null, productos: [], error: '' });

  useEffect(() => {
    let vigente = true;
    listarProductos(subdominio)
      .then((productos) => vigente && setEstado({ subdominio, productos, error: '' }))
      .catch((e) => vigente && setEstado({ subdominio, productos: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  const cargando = estado.subdominio !== subdominio;

  return (
    <>
      {/* Portada */}
      <section className="px-4 py-16 text-center text-white sm:py-24" style={{ background: tienda.colorPrincipal || '#303030' }}>
        <div className="mx-auto max-w-2xl space-y-4">
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">{tienda.nombre}</h1>
          {tienda.descripcion && <p className="text-lg text-white/85">{tienda.descripcion}</p>}
          <a href="#catalogo" className="inline-block rounded-md bg-white px-6 py-3 font-medium text-[#303030] hover:bg-white/90">
            Ver productos
          </a>
        </div>
      </section>

      <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 px-4 pt-12">
        <h2 className="mb-6 text-2xl font-semibold">Catálogo</h2>
        {estado.error && <Mensaje tipo="error" texto={estado.error} />}
        {cargando ? (
          <p className="text-[#616161]">Cargando productos...</p>
        ) : estado.productos.length === 0 && !estado.error ? (
          <p className="text-[#616161]">Esta tienda todavía no tiene productos.</p>
        ) : (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-4">
            {estado.productos.map((p) => (
              <article key={p.idProducto} className="group flex flex-col">
                <Link to={`/tienda/${subdominio}/producto/${p.idProducto}`} className="block">
                  <div className="relative overflow-hidden rounded-md">
                    <ImagenProducto
                      url={p.imagenUrl}
                      nombre={p.nombre}
                      className="transition-transform duration-300 group-hover:scale-105"
                    />
                    {!p.disponible ? (
                      <span className="absolute left-2 top-2 rounded-full bg-white px-2.5 py-0.5 text-xs font-medium text-[#303030] shadow-sm">
                        Agotado
                      </span>
                    ) : enOferta(p) && (
                      <span className="absolute left-2 top-2 rounded-full bg-[#303030] px-2.5 py-0.5 text-xs font-medium text-white">
                        Oferta
                      </span>
                    )}
                  </div>
                  <h3 className="mt-3 text-sm text-[#303030] group-hover:underline">{p.nombre}</h3>
                  <PrecioTienda producto={p} />
                </Link>
                <button
                  type="button"
                  disabled={!p.disponible}
                  onClick={() => {
                    agregar(p, 1);
                    abrir();
                  }}
                  className="mt-3 rounded-md border border-[#303030] py-2 text-sm font-medium hover:bg-[#303030] hover:text-white disabled:cursor-not-allowed disabled:border-[#c9c9c9] disabled:text-[#9a9a9a] disabled:hover:bg-transparent disabled:hover:text-[#9a9a9a]"
                >
                  {p.disponible ? 'Agregar al carrito' : 'Agotado'}
                </button>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
