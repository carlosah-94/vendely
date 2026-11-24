import { useEffect, useMemo, useState } from 'react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import FiltrosCatalogo from '../../components/tienda/FiltrosCatalogo';
import Paginador from '../../components/tienda/Paginador';
import PrecioTienda from '../../components/tienda/PrecioTienda';
import VitrinaBoutique from '../../components/tienda/VitrinaBoutique';
import VitrinaModerna from '../../components/tienda/VitrinaModerna';
import { BotonComprar, tieneVariosPrecios } from '../../components/tienda/comprar';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { categoriasPublicas } from '../../services/categorias';
import { listarProductos } from '../../services/tienda';
import { useTiempoReal } from '../../hooks/useTiempoReal';
import { canales } from '../../services/tiempoReal';
import { enOferta, formatoMoneda } from '../../utils/formato';
import { claseGrilla, hayFiltros, productoEstrella } from '../../utils/catalogo';
import Destacado from '../../components/tienda/Destacado';
import Garantias from '../../components/tienda/Garantias';

/**
 * Portada de la tienda. Carga productos y categorías y los dibuja con la plantilla elegida:
 * Clásica (aquí abajo), Moderna o Boutique (components/tienda/Vitrina*.jsx).
 *
 * Los filtros (issue #4) viven en la URL y el filtrado lo hace el backend: la tienda pide solo
 * la página que va a mostrar. Antes se traían todos los productos y se filtraban en el navegador.
 */
export default function Vitrina() {
  const { tienda, subdominio, paises } = useOutletContext();
  const { agregar, abrir } = useCarrito();
  const [parametros, setParametros] = useSearchParams();

  // null = todavía no llegaron; [] = la tienda no tiene categorías.
  const [categorias, setCategorias] = useState(null);
  const [estado, setEstado] = useState({ clave: null, productos: [], total: 0, paginas: 0, error: '' });
  // Tiempo real (issue #4): si cambia el stock de un producto que se esta mostrando (alguien
  // compro, o el dueño repuso), se vuelve a pedir la pagina. Los demas avisos se ignoran.
  const [version, setVersion] = useState(0);
  useTiempoReal(tienda?.idTienda ? canales.stockDeTienda(tienda.idTienda) : null, (aviso) => {
    const seMuestra = estado.productos.some((p) =>
      p.idVariante === aviso.idVariante || (p.variantes ?? []).some((v) => v.idVariante === aviso.idVariante));
    if (seMuestra) setVersion((v) => v + 1);
  });
  // Una muestra sin filtrar, solo para la decoración de las plantillas (collage, portada y las
  // fotos de cada categoría): con los filtros puestos, esas imágenes no deben desaparecer.
  const [muestra, setMuestra] = useState([]);

  const consulta = parametros.toString();
  const filtros = useMemo(() => ({
    q: parametros.get('q') ?? '',
    categoria: parametros.get('categoria') ?? '',
    min: parametros.get('min') ?? '',
    max: parametros.get('max') ?? '',
    stock: parametros.get('stock') === '1',
    orden: parametros.get('orden') ?? 'nombre',
    pagina: Number(parametros.get('pagina')) || 1,
  }), [parametros]);

  const elegida = (categorias ?? []).find((c) => c.slug === filtros.categoria);

  useEffect(() => {
    let vigente = true;
    categoriasPublicas(subdominio)
      .then((lista) => vigente && setCategorias(lista))
      .catch(() => vigente && setCategorias([]));
    listarProductos(subdominio, { tamano: 48 })
      .then((p) => vigente && setMuestra(p.contenido))
      .catch(() => vigente && setMuestra([]));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  useEffect(() => {
    // Para filtrar por categoría hace falta su id, y eso llega con la lista de categorías.
    if (filtros.categoria && categorias === null) {
      return;
    }
    let vigente = true;
    const clave = `${subdominio}?${consulta}`;
    listarProductos(subdominio, {
      q: filtros.q,
      categoria: elegida?.idCategoria,
      min: filtros.min,
      max: filtros.max,
      stock: filtros.stock ? 1 : '',
      orden: filtros.orden,
      pagina: filtros.pagina,
    })
      .then((p) => {
        if (vigente) {
          setEstado({ clave, productos: p.contenido, total: p.total, paginas: p.paginas, error: '' });
        }
      })
      .catch((e) => {
        if (vigente) {
          setEstado({ clave, productos: [], total: 0, paginas: 0, error: mensajeDeError(e) });
        }
      });
    return () => {
      vigente = false;
    };
  }, [subdominio, consulta, categorias, elegida?.idCategoria, filtros, version]);

  /** Cambia filtros en la URL. Cualquier cambio vuelve a la página 1, salvo el de página. */
  function cambiarFiltro(cambios) {
    const siguiente = { ...filtros, pagina: 1, ...cambios };
    const nuevos = {};
    if (siguiente.q) nuevos.q = siguiente.q;
    if (siguiente.categoria) nuevos.categoria = siguiente.categoria;
    if (siguiente.min) nuevos.min = siguiente.min;
    if (siguiente.max) nuevos.max = siguiente.max;
    if (siguiente.stock) nuevos.stock = '1';
    if (siguiente.orden && siguiente.orden !== 'nombre') nuevos.orden = siguiente.orden;
    if (siguiente.pagina > 1) nuevos.pagina = String(siguiente.pagina);
    setParametros(nuevos);
  }

  const vista = {
    tienda,
    subdominio,
    paises: paises ?? [],
    todos: muestra,
    productos: estado.productos,
    categorias: categorias ?? [],
    elegida,
    cargando: estado.clave !== `${subdominio}?${consulta}`,
    error: estado.error,
    filtros,
    total: estado.total,
    paginas: estado.paginas,
    cambiarFiltro,
    irAPagina: (n) => {
      cambiarFiltro({ pagina: n });
      document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
    },
    elegirCategoria: (slug) => cambiarFiltro({ categoria: slug }),
    agregarAlCarrito: (p) => {
      agregar(p, 1);
      abrir();
    },
  };

  if (tienda.plantilla === 'moderna') return <VitrinaModerna {...vista} />;
  if (tienda.plantilla === 'boutique') return <VitrinaBoutique {...vista} />;
  return <VitrinaClasica {...vista} />;
}

/**
 * Plantilla Clásica: portada con el color de la tienda y, si hay foto, el producto estrella al
 * lado del nombre. Con un solo producto, ese producto se muestra en grande en vez de la grilla.
 */
function VitrinaClasica({ tienda, subdominio, paises, todos, productos, categorias, elegida, cargando, error,
  elegirCategoria, agregarAlCarrito, filtros, total, paginas, cambiarFiltro, irAPagina }) {
  const estrella = productoEstrella(todos);
  const unico = todos.length === 1 && !hayFiltros(filtros);
  const base = `/tienda/${subdominio}`;

  return (
    <>
      <section className="relative isolate overflow-hidden bg-[var(--tienda)] text-[var(--tienda-texto)]">
        {/* Círculos suaves de fondo, con el mismo color de texto de la tienda */}
        <span className="absolute -right-24 -top-24 -z-10 h-96 w-96 rounded-full bg-[var(--tienda-texto)] opacity-[0.07]" />
        <span className="absolute -bottom-32 left-1/3 -z-10 h-80 w-80 rounded-full bg-[var(--tienda-texto)] opacity-[0.05]" />
        {estrella?.imagenUrl ? (
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 py-14 md:grid-cols-2 md:py-20">
            <div className="space-y-6">
              {tienda.logoUrl && <img src={tienda.logoUrl} alt="" className="h-16 w-16 rounded-2xl bg-white object-contain p-1.5" />}
              <h1 className="break-words text-[clamp(2.6rem,6vw,4.5rem)] font-semibold leading-[1.02] tracking-tight">{tienda.nombre}</h1>
              {tienda.descripcion && <p className="max-w-md text-lg leading-relaxed opacity-85">{tienda.descripcion}</p>}
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <a href="#catalogo" className="rounded-full bg-[var(--tienda-texto)] px-8 py-3.5 font-semibold text-[var(--tienda)] transition hover:opacity-90">
                  {unico ? 'Comprar ahora' : 'Ver productos'}
                </a>
                {!unico && (
                  <Link to={`${base}/producto/${estrella.idProducto}`} className="rounded-full px-5 py-3.5 font-semibold underline-offset-4 hover:underline">
                    Ver {estrella.nombre}
                  </Link>
                )}
              </div>
            </div>
            <Link to={`${base}/producto/${estrella.idProducto}`} className="group relative mx-auto block w-full max-w-md">
              <div className="overflow-hidden rounded-[2rem] bg-white shadow-[0_40px_80px_-30px_rgba(0,0,0,0.55)]">
                <img src={estrella.imagenUrl} alt={estrella.nombre} className="aspect-[4/5] w-full object-cover transition-transform duration-700 group-hover:scale-[1.03]" />
              </div>
              <span className="absolute -bottom-5 left-6 right-6 flex items-center justify-between gap-3 rounded-2xl bg-white px-5 py-3.5 text-[#1a1a1a] shadow-xl">
                <span className="truncate font-medium">{estrella.nombre}</span>
                <span className="shrink-0 font-semibold">{formatoMoneda(estrella.precio)}</span>
              </span>
            </Link>
          </div>
        ) : (
          <div className="mx-auto max-w-3xl space-y-5 px-5 py-20 text-center sm:py-28">
            {tienda.logoUrl && <img src={tienda.logoUrl} alt="" className="mx-auto h-16 w-16 rounded-2xl bg-white object-contain p-1.5" />}
            <h1 className="break-words text-[clamp(2.6rem,7vw,4.75rem)] font-semibold leading-[1.02] tracking-tight">{tienda.nombre}</h1>
            {tienda.descripcion && <p className="mx-auto max-w-xl text-lg opacity-85">{tienda.descripcion}</p>}
            <a href="#catalogo" className="inline-block rounded-full bg-[var(--tienda-texto)] px-8 py-3.5 font-semibold text-[var(--tienda)] hover:opacity-90">
              Ver productos
            </a>
          </div>
        )}
      </section>

      <Garantias tienda={tienda} paises={paises} estilo="clasica" />

      {unico ? (
        <Destacado id="catalogo" producto={todos[0]} tienda={tienda} paises={paises} subdominio={subdominio}
          onAgregar={agregarAlCarrito} etiqueta="Nuestro producto" />
      ) : (
        <>
          {todos.length >= 4 && (
            <div className="bg-[#f7f7f8]">
              <Destacado producto={estrella} tienda={tienda} paises={paises} subdominio={subdominio}
                onAgregar={agregarAlCarrito} etiqueta="Producto destacado" />
            </div>
          )}

          <section id="catalogo" className="mx-auto max-w-6xl scroll-mt-20 px-5 pt-14">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
              <h2 className="text-3xl font-semibold tracking-tight">{elegida ? elegida.nombre : 'Catálogo'}</h2>
              {!cargando && total > 0 && <p className="text-sm text-[#666]">{total} {total === 1 ? 'producto' : 'productos'}</p>}
            </div>
            {categorias.length > 0 && (
              <div className="mb-6 flex flex-wrap gap-2">
                {[{ slug: '', nombre: 'Todos' }, ...categorias].map((c) => (
                  <button
                    key={c.slug || 'todos'}
                    type="button"
                    onClick={() => elegirCategoria(c.slug)}
                    className={`rounded-full border px-4 py-2 text-sm font-medium transition ${
                      (elegida?.slug ?? '') === c.slug
                        ? 'border-[var(--tienda)] bg-[var(--tienda)] text-[var(--tienda-texto)]'
                        : 'border-[#dcdcdc] hover:border-[#303030]'
                    }`}
                  >
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
              <p className="rounded-2xl bg-[#f7f7f8] px-6 py-12 text-center text-[#616161]">
                {hayFiltros(filtros) ? 'Ningún producto coincide con esos filtros.' : 'Esta tienda está preparando sus productos. Vuelve pronto.'}
              </p>
            ) : (
              <>
                <div className={`grid gap-x-5 gap-y-10 ${claseGrilla(productos.length)}`}>
                  {productos.map((p) => (
                    <article key={p.idProducto} className="group flex flex-col">
                      <Link to={`${base}/producto/${p.idProducto}`} className="block">
                        <div className="relative overflow-hidden rounded-2xl bg-[#f4f4f4]">
                          <ImagenProducto url={p.imagenUrl} nombre={p.nombre} className="transition-transform duration-500 group-hover:scale-105" />
                          {!p.disponible ? (
                            <span className="absolute left-3 top-3 rounded-full bg-white px-3 py-1 text-xs font-semibold text-[#303030] shadow-sm">Agotado</span>
                          ) : enOferta(p) && (
                            <span className="absolute left-3 top-3 rounded-full bg-[var(--tienda)] px-3 py-1 text-xs font-semibold text-[var(--tienda-texto)]">Oferta</span>
                          )}
                        </div>
                        <h3 className={`mt-3.5 font-medium text-[#1a1a1a] group-hover:underline ${productos.length <= 2 ? 'text-lg' : 'text-[15px]'}`}>{p.nombre}</h3>
                        <PrecioTienda producto={p} desde={tieneVariosPrecios(p)} />
                      </Link>
                      <BotonComprar producto={p} subdominio={subdominio} onAgregar={agregarAlCarrito}
                        className="mt-3.5 rounded-full border-2 border-[var(--tienda)] py-2.5 text-center text-sm font-semibold transition hover:bg-[var(--tienda)] hover:text-[var(--tienda-texto)] disabled:cursor-not-allowed disabled:border-[#d5d5d5] disabled:text-[#9a9a9a] disabled:hover:bg-transparent disabled:hover:text-[#9a9a9a]" />
                    </article>
                  ))}
                </div>
                <Paginador pagina={filtros.pagina} paginas={paginas} onIr={irAPagina} />
              </>
            )}
          </section>
        </>
      )}
    </>
  );
}
