import { useEffect, useState } from 'react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import PrecioTienda from '../../components/tienda/PrecioTienda';
import VitrinaBoutique from '../../components/tienda/VitrinaBoutique';
import VitrinaModerna from '../../components/tienda/VitrinaModerna';
import { BotonComprar, tieneVariosPrecios } from '../../components/tienda/comprar';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { categoriasPublicas } from '../../services/categorias';
import { listarProductos } from '../../services/tienda';
import { enOferta } from '../../utils/formato';

/**
 * Portada de la tienda. Carga productos y categorías y los dibuja con la plantilla elegida:
 * Clásica (aquí abajo), Moderna o Boutique (components/tienda/Vitrina*.jsx).
 */
export default function Vitrina() {
  const { tienda, subdominio, paises } = useOutletContext();
  const { agregar, abrir } = useCarrito();
  const [estado, setEstado] = useState({ subdominio: null, productos: [], error: '' });
  const [categorias, setCategorias] = useState([]);
  // La categoría elegida va en la URL (?categoria=polos) para poder compartir el enlace.
  const [parametros, setParametros] = useSearchParams();
  const slugCategoria = parametros.get('categoria') ?? '';

  useEffect(() => {
    let vigente = true;
    categoriasPublicas(subdominio)
      .then((lista) => vigente && setCategorias(lista))
      .catch(() => vigente && setCategorias([]));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  useEffect(() => {
    let vigente = true;
    listarProductos(subdominio)
      .then((productos) => vigente && setEstado({ subdominio, productos, error: '' }))
      .catch((e) => vigente && setEstado({ subdominio, productos: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  const elegida = categorias.find((c) => c.slug === slugCategoria);
  const vista = {
    tienda,
    subdominio,
    paises: paises ?? [],
    todos: estado.productos,
    productos: elegida
      ? estado.productos.filter((p) => (p.categorias ?? []).includes(elegida.idCategoria))
      : estado.productos,
    categorias,
    elegida,
    cargando: estado.subdominio !== subdominio,
    error: estado.error,
    elegirCategoria: (slug) => setParametros(slug ? { categoria: slug } : {}, { replace: true }),
    agregarAlCarrito: (p) => {
      agregar(p, 1);
      abrir();
    },
  };

  if (tienda.plantilla === 'moderna') return <VitrinaModerna {...vista} />;
  if (tienda.plantilla === 'boutique') return <VitrinaBoutique {...vista} />;
  return <VitrinaClasica {...vista} />;
}

/** Plantilla Clásica: portada centrada con el color de la tienda y grilla de 4 columnas. */
function VitrinaClasica({ tienda, subdominio, productos, categorias, elegida, cargando, error, elegirCategoria, agregarAlCarrito }) {
  return (
    <>
      <section className="bg-[var(--tienda)] px-4 py-16 text-center text-[var(--tienda-texto)] sm:py-24">
        <div className="mx-auto max-w-2xl space-y-4">
          {tienda.logoUrl && (
            <img src={tienda.logoUrl} alt="" className="mx-auto h-16 w-16 rounded-full bg-white object-contain p-1" />
          )}
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">{tienda.nombre}</h1>
          {tienda.descripcion && <p className="text-lg opacity-85">{tienda.descripcion}</p>}
          <a href="#catalogo" className="inline-block rounded-md bg-[var(--tienda-texto)] px-6 py-3 font-medium text-[var(--tienda)] hover:opacity-90">
            Ver productos
          </a>
        </div>
      </section>

      <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 px-4 pt-12">
        <h2 className="mb-4 text-2xl font-semibold">{elegida ? elegida.nombre : 'Catálogo'}</h2>
        {categorias.length > 0 && (
          <div className="mb-6 flex flex-wrap gap-2">
            {[{ slug: '', nombre: 'Todos' }, ...categorias].map((c) => (
              <button
                key={c.slug || 'todos'}
                type="button"
                onClick={() => elegirCategoria(c.slug)}
                className={`rounded-full border px-4 py-1.5 text-sm ${
                  (elegida?.slug ?? '') === c.slug
                    ? 'border-[var(--tienda)] bg-[var(--tienda)] text-[var(--tienda-texto)]'
                    : 'border-[#d9d9d9] hover:border-[#303030]'
                }`}
              >
                {c.nombre}
              </button>
            ))}
          </div>
        )}
        {error && <Mensaje tipo="error" texto={error} />}
        {cargando ? (
          <p className="text-[#616161]">Cargando productos...</p>
        ) : productos.length === 0 && !error ? (
          <p className="text-[#616161]">
            {elegida ? 'No hay productos en esta categoría.' : 'Esta tienda todavía no tiene productos.'}
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-4">
            {productos.map((p) => (
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
                      <span className="absolute left-2 top-2 rounded-full bg-[var(--tienda)] px-2.5 py-0.5 text-xs font-medium text-[var(--tienda-texto)]">
                        Oferta
                      </span>
                    )}
                  </div>
                  <h3 className="mt-3 text-sm text-[#303030] group-hover:underline">{p.nombre}</h3>
                  <PrecioTienda producto={p} desde={tieneVariosPrecios(p)} />
                </Link>
                <BotonComprar producto={p} subdominio={subdominio} onAgregar={agregarAlCarrito}
                  className="mt-3 rounded-md border border-[var(--tienda)] py-2 text-center text-sm font-medium hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)] disabled:cursor-not-allowed disabled:border-[#c9c9c9] disabled:text-[#9a9a9a] disabled:hover:bg-transparent disabled:hover:text-[#9a9a9a]" />
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
