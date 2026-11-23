import { Link } from 'react-router-dom';
import { BotonComprar, tieneVariosPrecios } from './comprar';
import { enOferta, formatoMoneda } from '../../utils/formato';
import ImagenProducto from '../ImagenProducto';
import Mensaje from '../Mensaje';
import FiltrosCatalogo from './FiltrosCatalogo';
import Paginador from './Paginador';

/** Fotos reales de la tienda: las primeras que tengan imagen. */
function conFoto(productos, cuantas) {
  return productos.filter((p) => p.imagenUrl).slice(0, cuantas);
}

/** "Envíos a todo Perú, Chile y Francia" con los países de la tienda. */
function textoEnvio(paises) {
  if (!paises.length) return 'Envíos a todo el país';
  const nombres = paises.map((p) => p.nombre);
  return nombres.length === 1 ? `Envíos a todo ${nombres[0]}` : `Envíos a ${nombres.slice(0, -1).join(', ')} y ${nombres.at(-1)}`;
}

/**
 * Plantilla Moderna ("escaparate", solo plan Pro).
 * Lo memorable es la portada: el nombre de la tienda enorme con letra ancha sobre su color,
 * y al lado un collage con fotos de sus propios productos. Debajo, categorías con foto,
 * el catálogo con el primer producto destacado en grande y una franja con datos reales
 * de la tienda (a dónde envía, cómo se paga).
 */
export default function VitrinaModerna({ tienda, subdominio, paises, todos, productos, categorias, elegida,
  cargando, error, elegirCategoria, agregarAlCarrito, filtros, total, paginas, cambiarFiltro, irAPagina }) {
  const collage = conFoto(todos, 3);
  const base = `/tienda/${subdominio}`;

  return (
    <div className="bg-white">
      {/* Portada */}
      <section className="overflow-hidden bg-[var(--tienda)] text-[var(--tienda-texto)]">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-5 py-14 md:grid-cols-[1.1fr_1fr] md:py-20">
          <div className="space-y-6">
            {tienda.logoUrl && <img src={tienda.logoUrl} alt="" className="h-14 w-14 rounded-2xl bg-white object-contain p-1.5" />}
            <h1 className="letra-ancha break-words text-[clamp(2.75rem,7vw,5.5rem)] leading-[0.95]">{tienda.nombre}</h1>
            {tienda.descripcion && <p className="max-w-md text-lg leading-relaxed opacity-90">{tienda.descripcion}</p>}
            <div className="flex flex-wrap gap-3">
              <a href="#catalogo" className="rounded-full bg-[var(--tienda-texto)] px-7 py-3.5 font-semibold text-[var(--tienda)] transition hover:opacity-90">
                Ver productos
              </a>
              {categorias[0] && (
                <button
                  type="button"
                  onClick={() => {
                    elegirCategoria(categorias[0].slug);
                    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
                  }}
                  className="rounded-full border-2 border-[var(--tienda-texto)]/40 px-7 py-3 font-semibold transition hover:border-[var(--tienda-texto)]"
                >
                  {categorias[0].nombre}
                </button>
              )}
            </div>
          </div>

          {/* Collage con fotos de la tienda (si todavía no hay fotos, no se muestra) */}
          {collage.length > 0 && (
            <div className="relative mx-auto h-[340px] w-full max-w-[460px] sm:h-[420px]">
              {collage.map((p, i) => (
                <Link
                  key={p.idProducto}
                  to={`${base}/producto/${p.idProducto}`}
                  aria-label={p.nombre}
                  className={[
                    'absolute overflow-hidden rounded-[1.75rem] bg-white shadow-[0_24px_60px_-20px_rgba(0,0,0,0.45)] ring-4 ring-white transition-transform duration-300 hover:-translate-y-1',
                    collage.length === 1 ? 'inset-x-6 inset-y-0' : '',
                    collage.length > 1 && i === 0 ? 'left-0 top-0 h-[78%] w-[62%] -rotate-3' : '',
                    collage.length > 1 && i === 1 ? 'bottom-0 right-0 h-[64%] w-[52%] rotate-3' : '',
                    i === 2 ? 'right-[8%] top-[4%] h-[34%] w-[30%] rotate-6' : '',
                  ].join(' ')}
                >
                  <img src={p.imagenUrl} alt="" className="h-full w-full object-cover" />
                  {i === 0 && (
                    <span className="absolute bottom-3 left-3 rounded-full bg-white px-3 py-1 text-sm font-semibold text-[#141414]">
                      {formatoMoneda(p.precio)}
                    </span>
                  )}
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Franja con datos reales de la tienda */}
      <section className="border-b border-[#ececea] bg-[#f6f6f4]">
        <div className="mx-auto grid max-w-6xl gap-4 px-5 py-5 text-sm text-[#3a3a3a] sm:grid-cols-3">
          <p><b className="text-[#141414]">{textoEnvio(paises)}</b><br />Calculamos el envío antes de pagar.</p>
          <p><b className="text-[#141414]">Paga con PayPal</b><br />Tarjeta o saldo PayPal, en una página segura.</p>
          <p>
            <b className="text-[#141414]">{tienda.permiteInvitados ? 'Compra sin crear cuenta' : 'Sigue tus pedidos'}</b><br />
            {tienda.permiteInvitados ? 'Solo te pedimos tu correo y tu dirección.' : 'Con tu cuenta ves el estado de cada compra.'}
          </p>
        </div>
      </section>

      {/* Categorías con foto */}
      {categorias.length > 0 && (
        <section className="mx-auto max-w-6xl px-5 pt-14">
          <h2 className="mb-5 text-3xl">Compra por categoría</h2>
          <div className="-mx-5 flex snap-x gap-4 overflow-x-auto px-5 pb-2">
            {categorias.map((c) => {
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
                  className={`group relative h-44 w-40 shrink-0 snap-start overflow-hidden rounded-3xl text-left sm:h-52 sm:w-48 ${
                    activa ? 'ring-4 ring-[var(--tienda)] ring-offset-2' : ''
                  }`}
                >
                  {foto ? (
                    <img src={foto.imagenUrl} alt="" className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
                  ) : (
                    <span className="block h-full w-full bg-[var(--tienda)] opacity-80" />
                  )}
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/75 to-transparent p-4 pt-10 text-lg font-semibold text-white">
                    {c.nombre}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      )}

      {/* Catálogo: el primer producto va en grande */}
      <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 px-5 pt-14">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-3xl">{elegida ? elegida.nombre : 'Todo el catálogo'}</h2>
          {elegida && (
            <button type="button" onClick={() => elegirCategoria('')} className="text-sm font-semibold underline underline-offset-4">
              Ver todo
            </button>
          )}
        </div>
        <FiltrosCatalogo filtros={filtros} total={cargando ? null : total} onCambiar={cambiarFiltro} />
        {error && <Mensaje tipo="error" texto={error} />}
        {cargando ? (
          <p className="text-[#616161]">Cargando productos...</p>
        ) : productos.length === 0 && !error ? (
          <p className="text-[#616161]">
            {elegida || filtros.q || filtros.min || filtros.max || filtros.stock
              ? 'Ningún producto coincide con esos filtros.'
              : 'Esta tienda todavía no tiene productos.'}
          </p>
        ) : (
          <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {productos.map((p, i) => (
              <article
                key={p.idProducto}
                className={`group flex flex-col rounded-3xl bg-[#f6f6f4] p-2.5 ${i === 0 && productos.length > 2 ? 'col-span-2 md:row-span-2' : ''}`}
              >
                <Link to={`${base}/producto/${p.idProducto}`} className="relative block overflow-hidden rounded-[1.25rem] bg-white">
                  <ImagenProducto url={p.imagenUrl} nombre={p.nombre} className="transition-transform duration-500 group-hover:scale-[1.04]" />
                  <span className="absolute bottom-3 left-3 rounded-full bg-white px-3 py-1 text-sm font-semibold text-[#141414] shadow-sm">
                    {tieneVariosPrecios(p) && 'Desde '}{formatoMoneda(p.precio)}
                    {enOferta(p) && <s className="ml-1.5 text-xs font-normal text-[#8a8a8a]">{formatoMoneda(p.precioComparacion)}</s>}
                  </span>
                  {!p.disponible ? (
                    <span className="absolute right-3 top-3 rounded-full bg-[#141414] px-3 py-1 text-xs font-semibold text-white">Agotado</span>
                  ) : enOferta(p) && (
                    <span className="absolute right-3 top-3 rounded-full bg-[var(--tienda)] px-3 py-1 text-xs font-semibold text-[var(--tienda-texto)]">Oferta</span>
                  )}
                </Link>
                <div className="flex flex-1 flex-col justify-between gap-3 px-1.5 pb-1 pt-3">
                  <Link to={`${base}/producto/${p.idProducto}`} className={`font-semibold text-[#141414] hover:underline ${i === 0 && productos.length > 2 ? 'text-xl' : 'text-sm'}`}>
                    {p.nombre}
                  </Link>
                  <BotonComprar
                    producto={p}
                    subdominio={subdominio}
                    onAgregar={agregarAlCarrito}
                    textoAgregar="Agregar"
                    className="rounded-full bg-[#141414] py-2.5 text-center text-sm font-semibold text-white transition hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)] disabled:cursor-not-allowed disabled:bg-[#cfcfcf]"
                  />
                </div>
              </article>
            ))}
            </div>
            <Paginador pagina={filtros.pagina} paginas={paginas} onIr={irAPagina} />
          </>
        )}
      </section>
    </div>
  );
}
