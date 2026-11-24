import { useEffect, useState } from 'react';
import { Link, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import PrecioTienda from '../../components/tienda/PrecioTienda';
import { disenoDe, etiquetaOferta } from '../../utils/diseno';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { enOferta } from '../../utils/formato';
import { obtenerProducto } from '../../services/tienda';
import { useTiempoReal } from '../../hooks/useTiempoReal';
import { useTituloPagina } from '../../hooks/useTituloPagina';
import { canales } from '../../services/tiempoReal';
import { IconoGarantia, garantiasDe } from '../../components/tienda/Garantias';

/** Valores iniciales: los de la primera variante con stock (o la primera, si todas están agotadas). */
function valoresIniciales(p) {
  const v = p.variantes?.find((x) => x.stock > 0) ?? p.variantes?.[0];
  return v ? v.valores : [];
}

const mismos = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);

/**
 * Página de un producto: imagen a la izquierda; nombre, precio, opciones (talla, color),
 * cantidad y botones a la derecha. Cada combinación es una variante con su precio y su stock.
 */
export default function DetalleProducto() {
  const { subdominio, tienda, paises } = useOutletContext();
  const { idProducto } = useParams();
  const { agregar, abrir } = useCarrito();
  const navigate = useNavigate();

  const clave = `${subdominio}-${idProducto}`;
  const [estado, setEstado] = useState({ clave: null, producto: null, error: '' });
  // Tiempo real (issue #4): si cambia el stock de alguna variante de ESTE producto, se recarga.
  // Asi el limite de cantidad y el "Agotado" estan al dia aunque otro cliente compre mientras tanto.
  const [version, setVersion] = useState(0);
  useTiempoReal(tienda?.idTienda ? canales.stockDeTienda(tienda.idTienda) : null, (aviso) => {
    const p = estado.producto;
    if (p && (p.idVariante === aviso.idVariante || (p.variantes ?? []).some((v) => v.idVariante === aviso.idVariante))) {
      setVersion((v) => v + 1);
    }
  });
  const [cantidad, setCantidad] = useState(1);
  const [fotoElegida, setFotoElegida] = useState(0);
  // Valores elegidos de cada opción, del producto de esta página: { clave, valores }.
  const [eleccion, setEleccion] = useState({ clave: null, valores: [] });

  useEffect(() => {
    let vigente = true;
    obtenerProducto(subdominio, idProducto)
      .then((producto) => vigente && setEstado({ clave, producto, error: '' }))
      .catch((e) => vigente && setEstado({ clave, producto: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio, idProducto, clave, version]);

  useTituloPagina(estado.clave === clave && estado.producto && tienda
    ? `${estado.producto.nombre} | ${tienda.nombre}` : null);

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
  const opciones = p.opciones ?? [];
  const valores = eleccion.clave === clave ? eleccion.valores : valoresIniciales(p);
  const variante = p.variantes?.find((v) => mismos(v.valores, valores)) ?? null;
  // Lo que se muestra y se agrega al carrito es la variante elegida (precio y stock propios).
  const elegido = variante
    ? {
      ...p,
      idVariante: variante.idVariante,
      precio: variante.precio,
      precioComparacion: variante.precioComparacion,
      stock: variante.stock,
      disponible: variante.stock > 0,
      nombre: opciones.length ? `${p.nombre} - ${variante.nombre}` : p.nombre,
    }
    : { ...p, disponible: false, stock: 0 };
  const tope = Math.max(elegido.stock, 1);

  function elegir(indice, valor) {
    const nuevos = valores.map((v, i) => (i === indice ? valor : v));
    setEleccion({ clave, valores: nuevos });
    setCantidad(1);
  }

  /** ¿Hay una variante con este valor (y las demás opciones como están) y con stock? */
  function estadoValor(indice, valor) {
    const combo = valores.map((v, i) => (i === indice ? valor : v));
    const v = p.variantes?.find((x) => mismos(x.valores, combo));
    return !v ? 'no-existe' : v.stock > 0 ? 'disponible' : 'agotado';
  }
  const fotos = p.imagenes?.length ? p.imagenes : [p.imagenUrl];
  const foto = fotos[Math.min(fotoElegida, fotos.length - 1)];

  return (
    <div className="mx-auto grid max-w-6xl gap-10 px-4 pt-10 md:grid-cols-2 md:gap-14">
      {/* Galería: foto grande y, si hay varias, miniaturas para cambiarla */}
      <div className="space-y-3">
        <div className="tarjeta-tienda overflow-hidden bg-[var(--fondo-suave)]">
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

      <div className="space-y-5 md:sticky md:top-24 md:self-start">
        {p.marca && <p className="text-sm uppercase tracking-wide text-[#616161]">{p.marca}</p>}
        <h1 className="text-[clamp(1.9rem,3.5vw,2.6rem)] font-semibold leading-tight tracking-tight">{p.nombre}</h1>
        <div>
          <div className="flex items-center gap-3">
            <PrecioTienda producto={elegido} grande />
            {enOferta(elegido) && (
              <span className="rounded-full bg-[var(--acento)] px-2.5 py-0.5 text-xs font-medium text-[var(--acento-texto)]">{etiquetaOferta(elegido, disenoDe(tienda))}</span>
            )}
          </div>
          {/* Oferta automática del panel (Descuentos): su nombre, como "Fin de temporada" */}
          {p.oferta && enOferta(elegido) && <p className="text-sm font-medium">{p.oferta.nombre}</p>}
          <p className="text-sm text-[#616161]">Impuestos incluidos.</p>
        </div>

        {/* Opciones: un botón por valor; tachado si esa combinación está agotada */}
        {opciones.map((o, i) => (
          <div key={o.nombre}>
            <p className="mb-2 text-sm">
              {o.nombre}: <span className="font-medium">{valores[i]}</span>
            </p>
            <div className="flex flex-wrap gap-2">
              {o.valores.map((valor) => {
                const estadoV = estadoValor(i, valor);
                const activo = valores[i] === valor;
                return (
                  <button
                    key={valor}
                    type="button"
                    onClick={() => elegir(i, valor)}
                    disabled={estadoV === 'no-existe'}
                    aria-pressed={activo}
                    className={`boton-tienda min-w-12 border px-4 py-2 text-sm ${
                      activo ? 'border-[var(--tienda)] bg-[var(--tienda)] text-[var(--tienda-texto)]' : 'border-[#c9c9c9] hover:border-[#303030]'
                    } ${estadoV !== 'disponible' ? 'line-through opacity-50' : ''} disabled:cursor-not-allowed`}
                  >
                    {valor}
                  </button>
                );
              })}
            </div>
          </div>
        ))}

        {elegido.disponible ? (
          <>
            <p className="text-sm text-[#616161]">{elegido.stock} disponibles</p>
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
                  agregar(elegido, cantidad);
                  abrir();
                }}
                className="boton-tienda w-full border-2 border-[#303030] py-3.5 font-semibold transition hover:bg-[#f3f3f3]"
              >
                Agregar al carrito
              </button>
              <button
                type="button"
                onClick={() => {
                  agregar(elegido, cantidad);
                  navigate(`/tienda/${subdominio}/checkout`);
                }}
                className="boton-tienda w-full bg-[var(--tienda)] py-3.5 font-semibold text-[var(--tienda-texto)] shadow-[0_12px_30px_-12px_var(--tienda)] transition hover:brightness-95"
              >
                Comprar ahora
              </button>
            </div>
          </>
        ) : (
          <button type="button" disabled className="w-full cursor-not-allowed rounded-md bg-[#e3e3e3] py-3 font-medium text-[#8a8a8a]">
            {variante ? 'Agotado' : 'Combinación no disponible'}
          </button>
        )}

        {/* Lo que ofrece la tienda, junto a los botones: es donde el cliente duda */}
        <ul className="space-y-2.5 tarjeta-tienda bg-[var(--fondo-suave)] p-4 text-sm text-[#444]">
          {garantiasDe(tienda, paises ?? []).map((g) => (
            <li key={g.icono} className="flex items-center gap-3">
              <span className="text-[var(--tienda)]"><IconoGarantia icono={g.icono} tamano={20} /></span>
              <span><b className="font-medium text-[#1a1a1a]">{g.titulo}.</b> {g.texto}</span>
            </li>
          ))}
        </ul>

        {p.descripcion && <p className="whitespace-pre-line border-t border-[#e3e3e3] pt-5 text-[#303030]/85">{p.descripcion}</p>}
      </div>
    </div>
  );
}
