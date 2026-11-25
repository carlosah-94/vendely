import { Link } from 'react-router-dom';
import ImagenProducto from '../ImagenProducto';
import Mensaje from '../Mensaje';
import FiltrosCatalogo from './FiltrosCatalogo';
import Paginador from './Paginador';
import PrecioTienda from './PrecioTienda';
import Destacado from './Destacado';
import { BandaPromo, PortadaPro } from './BloquesPro';
import { BotonComprar, tieneVariosPrecios } from './comprar';
import { claseGrilla, hayFiltros, productoEstrella } from '../../utils/catalogo';
import { enOferta } from '../../utils/formato';
import { etiquetaOferta } from '../../utils/diseno';
import { PORTADAS } from './rubros/Portadas';
import { BeneficiosRubro, SeccionRubro } from './rubros/Secciones';
import { BotonWhatsappRubro, TEXTOS } from './rubros/comun';

/**
 * Vitrina de las plantillas por rubro (V22): Horno, Circuito, Impulso, Pasarela, Glow, Nido y
 * Huellitas. Cada una tiene su portada, su franja de beneficios, su sección propia y su estilo de
 * catálogo. Se elige por tienda.tema (lo manda el backend solo si el plan lo permite).
 *
 * Orden: portada → beneficios → sección del rubro → destacado → catálogo. Las secciones se
 * prenden o apagan en Personalizar, igual que en las otras plantillas.
 */

const ESTILO_CATALOGO = {
  horno: { titulo: 'text-[#2B1609]', pre: 'text-[var(--tienda)]', tarjeta: 'rounded-[1.75rem] bg-[#FFF4E6] p-3', foto: 'rounded-[1.25rem]', boton: 'bg-[var(--tienda)] text-[var(--tienda-texto)] hover:opacity-90' },
  circuito: { titulo: 'text-[#0B0F19]', pre: 'font-mono uppercase tracking-[0.25em] text-[var(--tienda)]', tarjeta: 'rounded-2xl border border-[#E5E7EB] bg-white p-3 transition hover:border-[var(--tienda)] hover:shadow-[0_20px_40px_-24px_var(--tienda)]', foto: 'rounded-xl bg-[#F3F4F6]', boton: 'bg-[#0B0F19] text-white hover:bg-[var(--tienda)]' },
  impulso: { titulo: 'uppercase italic font-extrabold text-[#0A0A0A]', pre: 'uppercase font-extrabold tracking-widest text-[#0A0A0A]/50', tarjeta: 'border-2 border-[#0A0A0A] bg-white p-2 transition hover:-translate-y-1 hover:shadow-[6px_6px_0_0_var(--acento)]', foto: '', boton: 'bg-[#0A0A0A] text-[var(--acento)] uppercase tracking-widest font-extrabold hover:bg-[var(--acento)] hover:text-[var(--acento-texto)]' },
  pasarela: { titulo: 'letra-serifa text-[#1C1917]', pre: 'uppercase tracking-[0.35em] text-[11px] text-[#57534E]', tarjeta: '', foto: 'bg-[#EFE9E0]', boton: 'border border-[#1C1917] text-[#1C1917] uppercase tracking-[0.2em] text-xs hover:bg-[#1C1917] hover:text-white' },
  glow: { titulo: 'italic text-[#3B0A22]', pre: 'uppercase tracking-[0.3em] text-[var(--tienda)]', tarjeta: 'rounded-[2rem] bg-white p-3 shadow-[0_20px_50px_-35px_rgba(190,24,93,0.6)]', foto: 'rounded-t-full rounded-b-[1.5rem] bg-[#FFF1F5]', boton: 'bg-[var(--tienda)] text-[var(--tienda-texto)] hover:opacity-90' },
  nido: { titulo: 'text-[#1F2A1F]', pre: 'uppercase tracking-[0.25em] text-[var(--tienda)]', tarjeta: '', foto: 'rounded-t-[999px] rounded-b-3xl bg-[#EEF2EA]', boton: 'border-2 border-[var(--tienda)] text-[var(--tienda)] hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)]' },
  huellitas: { titulo: 'font-extrabold text-[#2A1B4A]', pre: 'font-bold text-[var(--acento)]', tarjeta: 'rounded-[1.75rem] border-2 border-[#2A1B4A] bg-white p-3 shadow-[5px_5px_0_0_#2A1B4A] transition hover:-translate-y-1', foto: 'rounded-[1.25rem] bg-[#FFF6D6]', boton: 'bg-[var(--tienda)] text-[var(--tienda-texto)] font-bold hover:opacity-90' },
};

/** Lo que ve el cliente cuando la tienda todavía no tiene productos: "Próximamente", con WhatsApp. */
function Proximamente({ tema, tienda }) {
  const t = TEXTOS[tema];
  const oscuro = tema === 'circuito' || tema === 'impulso';
  return (
    <div className={`relative overflow-hidden px-6 py-16 text-center ${oscuro ? 'bg-[#0B0F19] text-white' : 'bg-[var(--fondo-suave)]'} tarjeta-tienda`}>
      <span className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-[var(--acento)] opacity-20 blur-3xl" />
      <span className="absolute -bottom-16 -left-16 h-56 w-56 rounded-full bg-[var(--tienda)] opacity-20 blur-3xl" />
      <p className={`relative text-xs font-semibold uppercase tracking-[0.35em] ${oscuro ? 'text-[var(--acento)]' : 'text-[var(--tienda)]'}`}>Próximamente</p>
      <h3 className="relative mt-3 text-3xl font-bold tracking-tight sm:text-4xl">{t.vacio.titulo}</h3>
      <p className={`relative mx-auto mt-3 max-w-md ${oscuro ? 'text-white/65' : 'text-[#666]'}`}>{t.vacio.texto}</p>
      <div className="relative mt-7 flex justify-center">
        <BotonWhatsappRubro tienda={tienda} texto="Escribir por WhatsApp" mensaje={`Hola ${tienda.nombre}, ¿cuándo tendrán productos disponibles?`} />
      </div>
    </div>
  );
}

function Catalogo({ tema, tienda, diseno, subdominio, productos, categorias, elegida, cargando, error, filtros,
  total, paginas, cambiarFiltro, irAPagina, elegirCategoria, agregarAlCarrito }) {
  const e = ESTILO_CATALOGO[tema];
  const base = `/tienda/${subdominio}`;
  const t = TEXTOS[tema];
  return (
    <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 px-5 pb-6 pt-16">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className={`text-sm font-semibold ${e.pre}`}>{tienda.nombre}</p>
          <h2 className={`text-4xl tracking-tight ${e.titulo}`}>{elegida ? elegida.nombre : t.catalogo}</h2>
        </div>
        {!cargando && total > 0 && <p className="text-sm text-[#666]">{total} {total === 1 ? 'producto' : 'productos'}</p>}
      </div>
      {categorias.length > 0 && diseno.mostrarCategorias && (
        <div className="-mx-5 mb-6 flex gap-2 overflow-x-auto px-5 pb-1">
          {[{ slug: '', nombre: 'Todos' }, ...categorias].map((c) => (
            <button key={c.slug || 'todos'} type="button" onClick={() => elegirCategoria(c.slug)}
              className={`boton-tienda shrink-0 border px-4 py-2 text-sm font-medium transition ${
                (elegida?.slug ?? '') === c.slug
                  ? 'border-[var(--tienda)] bg-[var(--tienda)] text-[var(--tienda-texto)]'
                  : 'border-[var(--fondo-borde)] bg-[var(--fondo)] hover:border-[#303030]'
              }`}>
              {c.nombre}
            </button>
          ))}
        </div>
      )}
      <FiltrosCatalogo filtros={filtros} total={cargando ? null : total} onCambiar={cambiarFiltro} />
      {error && <Mensaje tipo="error" texto={error} />}
      {cargando ? (
        <p className="text-[#616161]">Cargando productos...</p>
      ) : productos.length === 0 && !error ? (
        hayFiltros(filtros)
          ? <p className="tarjeta-tienda bg-[var(--fondo-suave)] px-6 py-12 text-center text-[#616161]">Ningún producto coincide con esos filtros.</p>
          : <Proximamente tema={tema} tienda={tienda} />
      ) : (
        <>
          <div className={`grid gap-x-5 gap-y-10 ${claseGrilla(productos.length)}`}>
            {productos.map((p) => (
              <article key={p.idProducto} className={`group flex flex-col ${e.tarjeta}`}>
                <Link to={`${base}/producto/${p.idProducto}`} className="block">
                  <div className={`relative overflow-hidden ${e.foto}`}>
                    <ImagenProducto url={p.imagenUrl} nombre={p.nombre} className="transition-transform duration-500 group-hover:scale-105" />
                    {!p.disponible ? (
                      <span className="absolute left-3 top-3 rounded-full bg-white px-3 py-1 text-xs font-semibold text-[#303030] shadow-sm">Agotado</span>
                    ) : enOferta(p) && (
                      <span className="absolute left-3 top-3 rounded-full bg-[var(--acento)] px-3 py-1 text-xs font-bold text-[var(--acento-texto)]">{etiquetaOferta(p, diseno)}</span>
                    )}
                    {tema === 'circuito' && p.marca && (
                      <span className="absolute right-3 top-3 rounded-md bg-[#0B0F19]/85 px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-white">{p.marca}</span>
                    )}
                  </div>
                  <div className="px-1">
                    <h3 className={`mt-3.5 text-[15px] font-medium group-hover:underline ${tema === 'pasarela' ? 'letra-serifa text-xl' : 'text-[#1a1a1a]'}`}>{p.nombre}</h3>
                    <PrecioTienda producto={p} desde={tieneVariosPrecios(p)} />
                  </div>
                </Link>
                <BotonComprar producto={p} subdominio={subdominio} onAgregar={agregarAlCarrito}
                  className={`boton-tienda mt-3.5 py-2.5 text-center text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${e.boton}`} />
              </article>
            ))}
          </div>
          <Paginador pagina={filtros.pagina} paginas={paginas} onIr={irAPagina} />
        </>
      )}
    </section>
  );
}

export default function VitrinaRubro(vista) {
  const { tienda, diseno, subdominio, paises, todos, filtros, agregarAlCarrito } = vista;
  const tema = tienda.tema;
  const Portada = PORTADAS[tema];
  const unico = todos.length === 1 && !hayFiltros(filtros);

  return (
    <>
      {diseno.pro.portada.activa ? <PortadaPro tienda={tienda} diseno={diseno} /> : <Portada {...vista} />}
      <BandaPromo diseno={diseno} />
      {diseno.mostrarGarantias && <BeneficiosRubro tema={tema} tienda={tienda} paises={paises} />}
      {diseno.mostrarSeccionRubro !== false && <SeccionRubro tema={tema} {...vista} />}
      {unico ? (
        <Destacado id="catalogo" producto={todos[0]} tienda={tienda} paises={paises} subdominio={subdominio}
          onAgregar={agregarAlCarrito} etiqueta="Nuestro producto" />
      ) : (
        <>
          {todos.length >= 4 && diseno.mostrarDestacado && (
            <div className="bg-[var(--fondo-suave)]">
              <Destacado producto={productoEstrella(todos)} tienda={tienda} paises={paises} subdominio={subdominio}
                onAgregar={agregarAlCarrito} etiqueta="Producto destacado" />
            </div>
          )}
          <Catalogo tema={tema} {...vista} />
        </>
      )}
      <div className="h-10" />
    </>
  );
}
