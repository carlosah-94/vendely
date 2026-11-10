import { useCallback, useEffect, useState } from 'react';
import FormularioProducto from '../components/FormularioProducto';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { eliminarProducto, listarProductos } from '../services/productos';
import { formatoMoneda } from '../utils/formato';

const ESTADO = {
  ACTIVO: { texto: 'Activo', estilo: 'bg-emerald-100 text-emerald-700' },
  BORRADOR: { texto: 'Borrador', estilo: 'bg-amber-100 text-amber-700' },
  ARCHIVADO: { texto: 'Archivado', estilo: 'bg-slate-200 text-slate-600' },
};

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
          <h1 className="text-2xl font-bold text-slate-800">Productos</h1>
          {tiendas.length > 0 && (
            <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
          )}
        </div>
        {tienda && (
          <button
            onClick={() => setFormulario('nuevo')}
            className="rounded-md bg-violet-600 px-4 py-2 font-semibold text-white hover:bg-violet-700"
          >
            + Nuevo producto
          </button>
        )}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {tienda && (
        <div className="overflow-x-auto rounded-xl bg-white shadow">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-4 py-3 font-semibold">Nombre</th>
                <th className="px-4 py-3 text-right font-semibold">Precio</th>
                <th className="px-4 py-3 text-right font-semibold">Stock</th>
                <th className="px-4 py-3 font-semibold">Estado</th>
                <th className="px-4 py-3 text-right font-semibold">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {cargando && (
                <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-500">Cargando productos...</td></tr>
              )}
              {!cargando && datos.productos.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-500">Esta tienda todavía no tiene productos.</td></tr>
              )}
              {!cargando && datos.productos.map((p) => (
                <tr key={p.idProducto} className="hover:bg-slate-50">
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
