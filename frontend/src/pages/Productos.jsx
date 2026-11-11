import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import FormularioProducto from '../components/FormularioProducto';
import { IconoBuscar, IconoProductos } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { eliminarProducto, listarProductos } from '../services/productos';
import { formatoMoneda } from '../utils/formato';

const ESTADO = {
  ACTIVO: { texto: 'Activo', estilo: 'bg-[#e8fcd9] text-[#0c5132]' },
  BORRADOR: { texto: 'Borrador', estilo: 'bg-[#eaf4ff] text-[#00527c]' },
  ARCHIVADO: { texto: 'Archivado', estilo: 'bg-[#e3e3e3] text-[#4a4a4a]' },
};

/** Pestañas sobre la tabla, como en Shopify: filtran por estado sin volver a llamar al backend. */
const PESTANAS = [
  { valor: 'TODOS', texto: 'Todos' },
  { valor: 'ACTIVO', texto: 'Activos' },
  { valor: 'BORRADOR', texto: 'Borrador' },
  { valor: 'ARCHIVADO', texto: 'Archivados' },
];

/**
 * Pantalla de productos del panel del dueño (tarea #18).
 * Tabla con nombre, precio y stock, con Editar y Eliminar; botón para crear;
 * mensajes de éxito y de error. Datos del CRUD /api/panel/tiendas/{idTienda}/productos (#10).
 * Usa las piezas comunes del panel, igual que Clientes y Ventas.
 */
export default function Productos() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();

  // Se guarda de qué tienda son los productos: si no coincide con la elegida, todavía se están cargando.
  const [datos, setDatos] = useState({ idTienda: null, productos: [], error: '' });
  const [version, setVersion] = useState(0);   // al sumar 1 se vuelve a pedir la lista
  const [aviso, setAviso] = useState('');
  const [errorAccion, setErrorAccion] = useState('');
  const [formulario, setFormulario] = useState(null);   // null · 'nuevo' · producto a editar
  const [porEliminar, setPorEliminar] = useState(null);
  const [pestana, setPestana] = useState('TODOS');
  // El texto puede venir de la búsqueda de la barra superior (?buscar=polo).
  const [parametros, setParametros] = useSearchParams();
  const busqueda = parametros.get('buscar') ?? '';

  function cambiarBusqueda(texto) {
    setParametros(texto ? { buscar: texto } : {}, { replace: true });
  }

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true; // evita mostrar la respuesta de una tienda que ya no está elegida
    listarProductos(idTienda)
      .then((productos) => {
        if (vigente) setDatos({ idTienda, productos, error: '' });
      })
      .catch((e) => {
        if (vigente) setDatos({ idTienda, productos: [], error: mensajeDeError(e) });
      });
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);

  const texto = busqueda.trim().toLowerCase();
  const visibles = datos.productos.filter(
    (p) =>
      (pestana === 'TODOS' || p.estado === pestana) &&
      (!texto || p.nombre.toLowerCase().includes(texto) || (p.marca ?? '').toLowerCase().includes(texto)),
  );

  // El aviso de éxito se oculta solo a los 4 segundos; los errores se quedan hasta cerrarlos.
  useEffect(() => {
    if (!aviso) {
      return undefined;
    }
    const t = setTimeout(() => setAviso(''), 4000);
    return () => clearTimeout(t);
  }, [aviso]);

  const cerrarFormulario = useCallback(() => setFormulario(null), []);

  function alGuardar(guardado, esNuevo) {
    setFormulario(null);
    setErrorAccion('');
    setAviso(`${esNuevo ? 'Producto guardado' : 'Cambios guardados'}: ${guardado.nombre}`);
    setVersion((v) => v + 1);
  }

  async function eliminar(producto) {
    setPorEliminar(null);
    setErrorAccion('');
    try {
      await eliminarProducto(idTienda, producto.idProducto);
      setAviso(`Producto eliminado: ${producto.nombre}`);
      setVersion((v) => v + 1);
    } catch (e) {
      // Por ejemplo, 409 "El producto ya tiene ventas: no se puede eliminar (puedes desactivarlo)"
      setErrorAccion(mensajeDeError(e));
    }
  }

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para registrar sus productos." />;
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-xl font-bold text-[#101010]">Productos</h1>
          {tiendas.length > 0 && (
            <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
          )}
        </div>
        {tienda && (
          <button
            onClick={() => setFormulario('nuevo')}
            className="rounded-lg bg-[#303030] px-3 py-1.5 font-semibold text-white shadow-[inset_0_-1px_0_rgba(0,0,0,0.6)] hover:bg-[#1a1a1a]"
          >
            Agregar producto
          </button>
        )}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {tienda && !cargando && datos.productos.length === 0 && !datos.error && (
        <div className="flex flex-col items-center rounded-xl bg-white px-6 py-14 text-center shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
            <IconoProductos tamano={28} />
          </span>
          <h2 className="mt-4 text-base font-semibold text-[#101010]">Agrega tus productos</h2>
          <p className="mt-1 max-w-sm text-[#616161]">
            Empieza a armar tu catálogo. Los productos activos se muestran en tu tienda online.
          </p>
          <button
            onClick={() => setFormulario('nuevo')}
            className="mt-5 rounded-lg bg-[#303030] px-3 py-1.5 font-semibold text-white hover:bg-[#1a1a1a]"
          >
            Agregar producto
          </button>
        </div>
      )}

      {tienda && (cargando || datos.productos.length > 0) && (
        <div className="overflow-hidden rounded-xl bg-white shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
          <div className="flex flex-wrap items-center gap-2 border-b border-[#ebebeb] px-2 py-2">
            <div className="flex gap-1">
              {PESTANAS.map((t) => (
                <button
                  key={t.valor}
                  onClick={() => setPestana(t.valor)}
                  className={`rounded-lg px-3 py-1 text-[13px] ${
                    pestana === t.valor ? 'bg-[#ebebeb] font-semibold text-[#101010]' : 'text-[#616161] hover:bg-[#f1f1f1]'
                  }`}
                >
                  {t.texto}
                </button>
              ))}
            </div>
            <label className="ml-auto flex w-full items-center gap-2 rounded-lg border border-[#ddd] px-2.5 py-1 text-[#8a8a8a] focus-within:border-[#1650d0] sm:w-64">
              <IconoBuscar tamano={15} />
              <input
                value={busqueda}
                onChange={(e) => cambiarBusqueda(e.target.value)}
                placeholder="Buscar por nombre o marca"
                aria-label="Buscar productos"
                className="w-full bg-transparent text-[13px] text-[#303030] outline-none"
              />
            </label>
          </div>
          <div className="overflow-x-auto">
          <table className="min-w-full text-[13px]">
            <thead className="bg-[#f7f7f7] text-left text-[#616161]">
              <tr>
                <th className="px-4 py-3 font-medium">Nombre</th>
                <th className="px-4 py-3 text-right font-medium">Precio</th>
                <th className="px-4 py-3 text-right font-medium">Stock</th>
                <th className="px-4 py-3 font-medium">Estado</th>
                <th className="px-4 py-3 text-right font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#ebebeb]">
              {cargando && (
                <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-500">Cargando productos...</td></tr>
              )}
              {!cargando && visibles.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-[#616161]">Ningún producto coincide con el filtro.</td></tr>
              )}
              {!cargando && visibles.map((p) => (
                <tr key={p.idProducto} className="hover:bg-[#f7f7f7]">
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-800">{p.nombre}</p>
                    {p.marca && <p className="text-xs text-slate-500">{p.marca}</p>}
                  </td>
                  {/* Soles (por ahora solo Perú está habilitado para crear tiendas). */}
                  <td className="whitespace-nowrap px-4 py-3 text-right text-slate-700">{formatoMoneda(p.precio)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    {/* Sin stock: se resalta en rojo para que el dueño lo note. */}
                    {p.stock > 0 ? (
                      <span className="text-slate-700">{p.stock}</span>
                    ) : (
                      <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700">Agotado</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${ESTADO[p.estado]?.estilo ?? ''}`}>
                      {ESTADO[p.estado]?.texto ?? p.estado}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <button onClick={() => setFormulario(p)} className="rounded-md px-3 py-1 font-medium text-violet-700 hover:bg-violet-50">
                      Editar
                    </button>
                    <button onClick={() => setPorEliminar(p)} className="ml-1 rounded-md px-3 py-1 font-medium text-red-600 hover:bg-red-50">
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          {!cargando && (
            <p className="border-t border-[#ebebeb] px-4 py-2.5 text-[12px] text-[#616161]">
              Mostrando {visibles.length} de {datos.productos.length} productos
            </p>
          )}
        </div>
      )}

      {porEliminar && (
        <div className="fixed inset-0 z-20 flex items-center justify-center bg-slate-900/50 p-4">
          <div role="alertdialog" aria-labelledby="titulo-eliminar-producto" className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-xl">
            <h2 id="titulo-eliminar-producto" className="text-lg font-bold text-slate-800">¿Eliminar producto?</h2>
            <p className="text-sm text-slate-600">
              Se eliminará <span className="font-semibold">{porEliminar.nombre}</span> con su precio y su stock.
              Esta acción no se puede deshacer. Si ya tiene ventas no se podrá eliminar, pero puedes desactivarlo.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setPorEliminar(null)} className="rounded-md border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-50">
                Cancelar
              </button>
              <button onClick={() => eliminar(porEliminar)} className="rounded-md bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-700">
                Sí, eliminar
              </button>
            </div>
          </div>
        </div>
      )}

      {formulario && tienda && (
        <FormularioProducto
          idTienda={idTienda}
          producto={formulario === 'nuevo' ? null : formulario}
          onGuardado={alGuardar}
          onCerrar={cerrarFormulario}
        />
      )}
    </section>
  );
}
