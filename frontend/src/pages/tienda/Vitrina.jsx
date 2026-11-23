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
import { enOferta } from '../../utils/formato';

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

/** Plantilla Clásica: portada centrada con el color de la tienda y grilla de 4 columnas. */
function VitrinaClasica({ tienda, subdominio, productos, categorias, elegida, cargando, error,
  elegirCategoria, agregarAlCarrito, filtros, total, paginas, cambiarFiltro, irAPagina }) {
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
            <Paginador pagina={filtros.pagina} paginas={paginas} onIr={irAPagina} />
          </>
        )}
      </section>
    </>
  );
}
