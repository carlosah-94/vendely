import { Link } from 'react-router-dom';
import ImagenProducto from '../ImagenProducto';
import { enOferta, formatoMoneda } from '../../utils/formato';
import { BotonComprar, tieneVariosPrecios } from './comprar';
import { IconoGarantia, garantiasDe } from './Garantias';

/** Cuánto ahorra el cliente, en porcentaje: "-20%". */
function descuento(p) {
  return Math.round((1 - Number(p.precio) / Number(p.precioComparacion)) * 100);
}

/**
 * Un producto en grande, con foto, precio, descripción y botón de compra, sin salir de la portada.
 * Se usa para el producto estrella y, cuando la tienda tiene un solo producto, como todo el catálogo:
 * así una tienda recién creada ya se ve completa.
 *
 * estilo: clasica, moderna o boutique (cada plantilla lo viste a su manera).
 */
export default function Destacado({ producto: p, tienda, paises, subdominio, onAgregar, estilo = 'clasica', etiqueta, id }) {
  if (!p) return null;
  const enlace = `/tienda/${subdominio}/producto/${p.idProducto}`;
  const oferta = enOferta(p);
  const boutique = estilo === 'boutique';
  const moderna = estilo === 'moderna';
  const fotos = (p.imagenes?.length ? p.imagenes : [p.imagenUrl]).filter(Boolean).slice(0, 3);

  return (
    <section id={id} className={`scroll-mt-20 ${moderna ? 'bg-[var(--tienda)] text-[var(--tienda-texto)]' : ''}`}>
      <div className={`mx-auto grid max-w-6xl items-center gap-10 px-5 md:grid-cols-2 md:gap-16 ${boutique ? 'py-20 md:px-6' : 'py-16'}`}>
        {/* Foto principal y, si hay, dos más pequeñas */}
        <div className="relative">
          <Link
            to={enlace}
            className={`group block overflow-hidden ${
              boutique ? 'bg-[#f3f2f0]' : moderna ? 'rotate-[-1.5deg] rounded-[2rem] bg-white shadow-[0_30px_60px_-25px_rgba(0,0,0,0.5)] ring-8 ring-white' : 'rounded-3xl bg-[#f4f4f4]'
            }`}
          >
            <ImagenProducto
              url={fotos[0]}
              nombre={p.nombre}
              proporcion={boutique ? 'aspect-[4/5]' : 'aspect-square'}
              className="transition-transform duration-700 group-hover:scale-[1.03]"
            />
          </Link>
          {oferta && p.disponible && (
            <span
              className={`absolute left-4 top-4 px-3.5 py-1.5 text-sm font-semibold ${
                boutique ? 'bg-white text-[#222]' : moderna ? 'rotate-[-6deg] rounded-full bg-[#141414] text-white' : 'rounded-full bg-[var(--tienda)] text-[var(--tienda-texto)]'
              }`}
            >
              {descuento(p)}% menos
            </span>
          )}
          {fotos.length > 1 && !moderna && (
            <div className="mt-3 grid grid-cols-3 gap-3">
              {fotos.slice(1).map((url) => (
                <Link key={url} to={enlace} className={`overflow-hidden ${boutique ? '' : 'rounded-2xl'}`} aria-label={`Ver ${p.nombre}`}>
                  <ImagenProducto url={url} nombre={p.nombre} proporcion={boutique ? 'aspect-[4/5]' : 'aspect-square'} />
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* Datos y compra */}
        <div className={boutique ? 'md:pr-6' : ''}>
          {etiqueta && (
            <p className={`text-sm ${boutique ? 'text-[#8a8a8a]' : moderna ? 'font-semibold opacity-80' : 'font-semibold text-[#666]'}`}>{etiqueta}</p>
          )}
          <h2
            className={`mt-3 break-words ${
              boutique ? 'text-[clamp(2.4rem,4.5vw,3.75rem)] leading-[1]' : moderna ? 'text-[clamp(2.2rem,4.5vw,3.5rem)] leading-[0.98]' : 'text-[clamp(2rem,4vw,3rem)] font-semibold leading-[1.05] tracking-tight'
            }`}
          >
            <Link to={enlace} className="hover:underline hover:decoration-2 hover:underline-offset-4">{p.nombre}</Link>
          </h2>
          <p className="mt-5 flex flex-wrap items-baseline gap-x-3">
            <span className={boutique ? 'text-2xl' : 'text-3xl font-semibold'}>
              {tieneVariosPrecios(p) && 'Desde '}{formatoMoneda(p.precio)}
            </span>
            {oferta && <s className="text-lg opacity-55">{formatoMoneda(p.precioComparacion)}</s>}
          </p>
          {p.descripcion && (
            <p className={`mt-5 line-clamp-4 whitespace-pre-line leading-relaxed ${moderna ? 'opacity-85' : 'text-[#555]'}`}>{p.descripcion}</p>
          )}

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <BotonComprar
              producto={p}
              subdominio={subdominio}
              onAgregar={onAgregar}
              className={
                boutique
                  ? 'bg-[#222] px-10 py-4 text-sm tracking-[0.08em] text-white transition hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)] disabled:cursor-not-allowed disabled:bg-[#cfcfcf]'
                  : moderna
                    ? 'rounded-full bg-[var(--tienda-texto)] px-9 py-4 font-semibold text-[var(--tienda)] transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50'
                    : 'rounded-full bg-[var(--tienda)] px-9 py-4 font-semibold text-[var(--tienda-texto)] shadow-[0_12px_30px_-12px_var(--tienda)] transition hover:brightness-95 disabled:cursor-not-allowed disabled:bg-[#cfcfcf] disabled:shadow-none'
              }
            />
            <Link to={enlace} className="text-sm font-semibold underline underline-offset-4 opacity-80 hover:opacity-100">
              Ver detalles
            </Link>
          </div>
          {p.disponible && p.stock > 0 && p.stock <= 5 && (
            <p className={`mt-4 text-sm font-medium ${moderna ? '' : 'text-[#b4232a]'}`}>Quedan solo {p.stock} unidades.</p>
          )}

          {/* Las garantías de la tienda, junto al botón: es donde el cliente duda */}
          <ul className={`mt-8 space-y-3 border-t pt-6 text-sm ${moderna ? 'border-current/20' : 'border-[#e8e8e8] text-[#444]'}`}>
            {garantiasDe(tienda, paises).map((g) => (
              <li key={g.icono} className="flex items-center gap-3">
                <span className={moderna ? '' : 'text-[var(--tienda)]'}><IconoGarantia icono={g.icono} tamano={20} /></span>
                {g.titulo}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
