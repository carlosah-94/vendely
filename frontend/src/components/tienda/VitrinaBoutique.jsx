import { Link } from 'react-router-dom';
import { enOferta, formatoMoneda } from '../../utils/formato';
import ImagenProducto from '../ImagenProducto';
import Mensaje from '../Mensaje';
import FiltrosCatalogo from './FiltrosCatalogo';
import Paginador from './Paginador';
import { BotonComprar, tieneVariosPrecios } from './comprar';
import Destacado from './Destacado';
import Garantias from './Garantias';
import { claseGrilla, hayFiltros, productoEstrella } from '../../utils/catalogo';

/**
 * Plantilla Boutique ("atelier", solo plan Pro).
 * Lo memorable es la portada: la imagen de portada del dueño (o, si no subió una, la mejor
 * foto de sus productos) a pantalla completa con su nombre en
 * una serifa fina y grande. El resto es silencioso: fondo blanco, mucho aire, fotos verticales
 * (4:5, como en moda), nombres en serifa y el color de la tienda solo en detalles.
 */
export default function VitrinaBoutique({ tienda, diseno, subdominio, paises, todos, productos, categorias, elegida,
  cargando, error, elegirCategoria, agregarAlCarrito, filtros, total, paginas, cambiarFiltro, irAPagina }) {
  const producto = todos.find((p) => p.imagenUrl);
  const banner = diseno.imagenPortadaUrl;
  const foto = banner || producto?.imagenUrl;
  const base = `/tienda/${subdominio}`;
  const unico = todos.length === 1 && !hayFiltros(filtros);

  return (
    <div className="text-[#222]">
      {/* Portada: foto a pantalla completa (o el color de la tienda si todavía no hay fotos) */}
      <section className="relative isolate flex min-h-[78vh] items-end overflow-hidden bg-[var(--tienda)]">
        {foto && <img src={foto} alt="" className="absolute inset-0 -z-10 h-full w-full object-cover" />}
        {foto && <div className="absolute inset-0 -z-10 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />}
        <div className={`mx-auto w-full max-w-6xl px-6 pb-14 sm:pb-20 ${foto ? 'text-white' : 'text-[var(--tienda-texto)]'}`}>
          <h1 className="letra-serifa max-w-4xl text-[clamp(3rem,9vw,7.5rem)] font-normal leading-[0.9]">{tienda.nombre}</h1>
          <div className="mt-8 flex flex-wrap items-center gap-6">
            <a href="#catalogo" className="boton-tienda border border-current px-9 py-3.5 text-sm tracking-[0.12em] transition hover:bg-white hover:text-[#222]">
              {unico ? 'Comprar ahora' : 'Ver la colección'}
            </a>
            {!banner && producto && (
              <Link to={`${base}/producto/${producto.idProducto}`} className="text-sm text-white/85 underline-offset-4 hover:underline">
                En la foto: {producto.nombre}
              </Link>
            )}
          </div>
        </div>
      </section>

      {/* La descripción de la tienda, como una carta de presentación */}
      {tienda.descripcion && (
        <section className="mx-auto max-w-3xl px-6 py-20 text-center">
          <p className="letra-serifa text-[clamp(1.6rem,3.2vw,2.4rem)] leading-snug text-[#2a2a2a]">{tienda.descripcion}</p>
          <span className="mx-auto mt-8 block h-px w-16 bg-[var(--acento)]" />
        </section>
      )}

      {diseno.mostrarGarantias && <Garantias tienda={tienda} paises={paises} estilo="boutique" />}

      {/* Categorías: retratos verticales con el nombre debajo */}
      {categorias.length > 0 && !unico && diseno.mostrarCategorias && (
        <section className="mx-auto max-w-6xl px-6 pb-20">
          <div className="grid grid-cols-2 gap-6 md:grid-cols-3">
            {categorias.slice(0, 6).map((c) => {
              const foto = todos.find((p) => p.imagenUrl && (p.categorias ?? []).includes(c.idCategoria));
              const activa = elegida?.slug === c.slug;
              return (
                <button
                  key={c.slug}
                  type="button"
                  onClick={() => {
                    elegirCategoria(activa ? '' : c.slug);
                    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
                  }}
                  className="group text-left"
                >
                  <span className="tarjeta-tienda block overflow-hidden bg-[var(--fondo-suave)]">
                    {foto ? (
                      <img src={foto.imagenUrl} alt="" className="aspect-[4/5] w-full object-cover transition-transform duration-700 group-hover:scale-[1.03]" />
                    ) : (
                      <span className="block aspect-[4/5] w-full bg-[var(--tienda)] opacity-20" />
                    )}
                  </span>
                  <span className={`letra-serifa mt-3 block text-2xl ${activa ? 'underline decoration-[var(--tienda)] decoration-1 underline-offset-8' : ''}`}>
                    {c.nombre}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      )}

      {unico ? (
        <Destacado id="catalogo" producto={todos[0]} tienda={tienda} paises={paises} subdominio={subdominio}
          onAgregar={agregarAlCarrito} estilo="boutique" etiqueta="La pieza" />
      ) : (
      <>
      {todos.length >= 4 && diseno.mostrarDestacado && (
        <div className="border-t border-[var(--fondo-borde)]">
          <Destacado producto={productoEstrella(todos)} tienda={tienda} paises={paises} subdominio={subdominio}
            onAgregar={agregarAlCarrito} estilo="boutique" etiqueta="Pieza destacada" />
        </div>
      )}
      {/* Colección */}
      <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 border-t border-[var(--fondo-borde)] px-6 pt-16">
        <div className="mb-10 flex flex-wrap items-baseline justify-between gap-4">
          <h2 className="text-4xl">{elegida ? elegida.nombre : 'La colección'}</h2>
          {categorias.length > 0 && (
            <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#6b6b6b]">
              {[{ slug: '', nombre: 'Todo' }, ...categorias].map((c) => {
                const activa = (elegida?.slug ?? '') === c.slug;
                return (
                  <button
                    key={c.slug || 'todo'}
                    type="button"
                    onClick={() => elegirCategoria(c.slug)}
                    className={activa ? 'text-[#222] underline decoration-[var(--tienda)] underline-offset-8' : 'hover:text-[#222]'}
                  >
                    {c.nombre}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <FiltrosCatalogo filtros={filtros} total={cargando ? null : total} onCambiar={cambiarFiltro} />
        {error && <Mensaje tipo="error" texto={error} />}
        {cargando ? (
          <p className="text-[#6b6b6b]">Cargando la colección...</p>
        ) : productos.length === 0 && !error ? (
          <p className="text-[#6b6b6b]">
            {hayFiltros(filtros) ? 'Ninguna pieza coincide con esos filtros.' : 'La colección se está preparando. Vuelve pronto.'}
          </p>
        ) : (
          <>
          <div className={`grid gap-x-6 gap-y-14 ${claseGrilla(productos.length, 3)}`}>
            {productos.map((p) => (
              <article key={p.idProducto} className="group flex flex-col">
                <Link to={`${base}/producto/${p.idProducto}`} className="tarjeta-tienda relative block overflow-hidden bg-[var(--fondo-suave)]">
                  <ImagenProducto
                    url={p.imagenUrl}
                    nombre={p.nombre}
                    proporcion="aspect-[4/5]"
                    className="transition-transform duration-700 group-hover:scale-[1.03]"
                  />
                  {!p.disponible ? (
                    <span className="absolute left-3 top-3 bg-white px-3 py-1 text-xs text-[#222]">Agotado</span>
                  ) : enOferta(p) && (
                    <span className="absolute left-3 top-3 bg-[var(--acento)] px-3 py-1 text-xs text-[var(--acento-texto)]">Oferta</span>
                  )}
                </Link>
                <Link to={`${base}/producto/${p.idProducto}`} className="mt-4 block text-center">
                  <h3 className="text-xl leading-tight">{p.nombre}</h3>
                  <p className="mt-1 text-sm text-[#6b6b6b]">
                    {tieneVariosPrecios(p) && 'Desde '}{formatoMoneda(p.precio)}
                    {enOferta(p) && <s className="ml-2 text-[#a3a3a3]">{formatoMoneda(p.precioComparacion)}</s>}
                  </p>
                </Link>
                <BotonComprar
                  producto={p}
                  subdominio={subdominio}
                  onAgregar={agregarAlCarrito}
                  className="mx-auto mt-4 border-b border-[#222] pb-0.5 text-sm text-[#222] opacity-100 transition hover:border-[var(--tienda)] hover:text-[var(--tienda)] disabled:cursor-not-allowed disabled:border-transparent disabled:text-[#a3a3a3] md:opacity-0 md:group-hover:opacity-100 md:focus-visible:opacity-100"
                />
              </article>
            ))}
            </div>
            <Paginador pagina={filtros.pagina} paginas={paginas} onIr={irAPagina} />
          </>
        )}
      </section>
      </>
      )}
    </div>
  );
}
