import { useEffect, useState } from 'react';
import CampoPanel from '../components/CampoPanel';
import { IconoBuscar, IconoCerrar, IconoInventario } from '../components/Iconos';
import ImagenProducto from '../components/ImagenProducto';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { actualizarInventario, listarInventario } from '../services/inventario';
import { formatoFecha } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta, inputPanel } from '../utils/panel';

const PESTANAS = [
  { valor: 'TODOS', texto: 'Todos' },
  { valor: 'BAJO', texto: 'Stock bajo' },
  { valor: 'AGOTADO', texto: 'Agotados' },
];

const ESTADO = { OK: ['En stock', 'exito'], BAJO: ['Stock bajo', 'aviso'], AGOTADO: ['Agotado', 'error'] };

/**
 * Inventario: el stock de cada variante, como el cuaderno del almacén.
 * - "Recibir": llegó mercadería y se SUMA a lo que hay (queda la fecha de la última reposición).
 * - "Ajustar": corregir el número exacto después de contar, y fijar el stock mínimo
 *   (al llegar a ese número, el producto se marca con "Stock bajo").
 */
export default function Inventario() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const { avisar } = useToast();
  const [datos, setDatos] = useState({ idTienda: null, filas: [], error: '' });
  const [pestana, setPestana] = useState('TODOS');
  const [busqueda, setBusqueda] = useState('');
  const [editando, setEditando] = useState(null);     // { modo: 'recibir'|'ajustar', fila }

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    listarInventario(idTienda)
      .then((filas) => vigente && setDatos({ idTienda, filas, error: '' }))
      .catch((e) => vigente && setDatos({ idTienda, filas: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  function reemplazar(fila) {
    setDatos((d) => ({ ...d, filas: d.filas.map((f) => (f.idVariante === fila.idVariante ? fila : f)) }));
  }

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;
  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);
  const texto = busqueda.trim().toLowerCase();
  const visibles = datos.filas.filter(
    (f) =>
      (pestana === 'TODOS' || f.estado === pestana) &&
      (!texto || [f.producto, f.variante, f.sku].some((v) => (v ?? '').toLowerCase().includes(texto))),
  );
  const cuantos = (estado) => datos.filas.filter((f) => estado === 'TODOS' || f.estado === estado).length;

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Inventario</h1>
          <p className="text-[13px] text-[#616161]">Stock de cada producto y variante. Registra aquí la mercadería que llega.</p>
        </div>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}

      {tienda && (
        <div className={`${TARJETA} overflow-hidden`}>
          <div className="flex flex-wrap items-center gap-2 border-b border-[#ebebeb] px-2 py-2">
            <div className="flex gap-1">
              {PESTANAS.map((p) => (
                <button
                  key={p.valor}
                  onClick={() => setPestana(p.valor)}
                  className={`rounded-lg px-3 py-1 text-[13px] ${
                    pestana === p.valor ? 'bg-[#ebebeb] font-semibold text-[#101010]' : 'text-[#616161] hover:bg-[#f1f1f1]'
                  }`}
                >
                  {p.texto} {!cargando && <span className="text-[#8a8a8a]">{cuantos(p.valor)}</span>}
                </button>
              ))}
            </div>
            <label className="ml-auto flex w-full items-center gap-2 rounded-lg border border-[#ddd] px-2.5 py-1 text-[#8a8a8a] focus-within:border-[#1650d0] sm:w-64">
              <IconoBuscar tamano={15} />
              <input
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                placeholder="Buscar producto o SKU"
                aria-label="Buscar en el inventario"
                className="w-full bg-transparent text-[13px] text-[#303030] outline-none"
              />
            </label>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full text-[13px]">
              <thead className={ENCABEZADO_TABLA}>
                <tr>
                  <th className="px-4 py-2.5 font-medium">Producto</th>
                  <th className="px-4 py-2.5 font-medium">SKU</th>
                  <th className="px-4 py-2.5 text-right font-medium">Disponible</th>
                  <th className="px-4 py-2.5 text-right font-medium">Mínimo</th>
                  <th className="px-4 py-2.5 font-medium">Estado</th>
                  <th className="px-4 py-2.5 font-medium">Última reposición</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[#ebebeb]">
                {cargando && <tr><td colSpan={7} className="px-4 py-8 text-center text-[#616161]">Cargando inventario...</td></tr>}
                {!cargando && visibles.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-12 text-center text-[#616161]">
                      <span className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1]">
                        <IconoInventario tamano={24} />
                      </span>
                      {datos.filas.length === 0 ? 'Todavía no tienes productos.' : 'Ningún producto coincide con el filtro.'}
                    </td>
                  </tr>
                )}
                {!cargando && visibles.map((f) => {
                  const [textoEstado, tono] = ESTADO[f.estado] ?? ESTADO.OK;
                  return (
                    <tr key={f.idVariante} className="hover:bg-[#f7f7f7]">
                      <td className="px-4 py-2.5">
                        <div className="flex items-center gap-3">
                          <div className="w-9 shrink-0 overflow-hidden rounded-lg border border-[#e3e3e3] [&_span]:text-xs [&_svg]:hidden">
                            <ImagenProducto url={f.imagenUrl} nombre={f.producto} />
                          </div>
                          <div>
                            <p className="font-medium text-[#101010]">{f.producto}</p>
                            <p className="text-xs text-[#616161]">
                              {f.variante}{!f.productoActivo && ' · Borrador'}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-2.5 text-[#616161]">{f.sku ?? '-'}</td>
                      <td className="px-4 py-2.5 text-right text-base font-semibold text-[#101010]">{f.cantidad}</td>
                      <td className="px-4 py-2.5 text-right text-[#616161]">{f.stockMinimo || '-'}</td>
                      <td className="px-4 py-2.5"><span className={etiqueta(tono)}>{textoEstado}</span></td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-[#616161]">
                        {f.ultimaReposicion ? formatoFecha(f.ultimaReposicion) : 'Nunca'}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right">
                        <button onClick={() => setEditando({ modo: 'recibir', fila: f })} className={BOTON}>Recibir</button>
                        <button onClick={() => setEditando({ modo: 'ajustar', fila: f })} className="ml-1 rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
                          Ajustar
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {editando && (
        <VentanaInventario
          modo={editando.modo}
          fila={editando.fila}
          onCerrar={() => setEditando(null)}
          onGuardar={async (cuerpo) => {
            const fila = await actualizarInventario(idTienda, editando.fila.idVariante, cuerpo);
            reemplazar(fila);
            setEditando(null);
            avisar(cuerpo.recibir ? `+${cuerpo.recibir} unidades de ${fila.producto}` : 'Inventario actualizado');
          }}
        />
      )}
    </section>
  );
}

function VentanaInventario({ modo, fila, onCerrar, onGuardar }) {
  const recibir = modo === 'recibir';
  const [d, setD] = useState({
    recibir: '',
    cantidad: String(fila.cantidad),
    stockMinimo: String(fila.stockMinimo),
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    const er = {};
    if (recibir && !/^[1-9]\d{0,5}$/.test(d.recibir.trim())) er.recibir = 'Escribe cuántas unidades llegaron (1 o más)';
    if (!recibir && !/^\d{1,7}$/.test(d.cantidad.trim())) er.cantidad = 'Número entero, 0 o más';
    if (!recibir && !/^\d{1,7}$/.test(d.stockMinimo.trim())) er.stockMinimo = 'Número entero, 0 o más';
    setErrores(er);
    if (Object.keys(er).length) return;
    setGuardando(true);
    setError('');
    try {
      await onGuardar(recibir
        ? { recibir: Number(d.recibir) }
        : { cantidad: Number(d.cantidad), stockMinimo: Number(d.stockMinimo) });
    } catch (ex) {
      setError(mensajeDeError(ex));
      setGuardando(false);
    }
  }

  const despues = recibir && /^\d+$/.test(d.recibir.trim()) ? fila.cantidad + Number(d.recibir) : null;

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-16">
      <form onSubmit={enviar} noValidate className="w-full max-w-sm overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-[#ebebeb] px-5 py-3.5">
          <div>
            <h2 className="font-semibold text-[#101010]">{recibir ? 'Recibir mercadería' : 'Ajustar inventario'}</h2>
            <p className="text-[12px] text-[#616161]">{fila.producto} · {fila.variante}</p>
          </div>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]">
            <IconoCerrar tamano={18} />
          </button>
        </div>
        <div className="space-y-3 p-5 text-[13px]">
          {error && <Mensaje tipo="error" texto={error} />}
          {recibir ? (
            <>
              <CampoPanel etiqueta="¿Cuántas unidades llegaron?" error={errores.recibir}>
                <input
                  autoFocus
                  inputMode="numeric"
                  value={d.recibir}
                  onChange={(e) => setD((v) => ({ ...v, recibir: e.target.value }))}
                  placeholder="20"
                  className={inputPanel(errores.recibir)}
                />
              </CampoPanel>
              <p className="rounded-lg bg-[#f7f7f7] px-3 py-2 text-[#616161]">
                Ahora hay <b>{fila.cantidad}</b>{despues != null && <> · quedarán <b className="text-[#101010]">{despues}</b></>}
              </p>
            </>
          ) : (
            <>
              <CampoPanel etiqueta="Stock exacto" error={errores.cantidad} ayuda="Úsalo después de contar el almacén">
                <input inputMode="numeric" value={d.cantidad} onChange={(e) => setD((v) => ({ ...v, cantidad: e.target.value }))} className={inputPanel(errores.cantidad)} />
              </CampoPanel>
              <CampoPanel etiqueta="Stock mínimo" error={errores.stockMinimo} ayuda="Con esta cantidad o menos se marca como stock bajo (0 = sin aviso)">
                <input inputMode="numeric" value={d.stockMinimo} onChange={(e) => setD((v) => ({ ...v, stockMinimo: e.target.value }))} className={inputPanel(errores.stockMinimo)} />
              </CampoPanel>
            </>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-[#ebebeb] bg-[#f7f7f7] px-5 py-3">
          <button type="button" onClick={onCerrar} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button type="submit" disabled={guardando} className={BOTON}>{guardando ? 'Guardando...' : recibir ? 'Recibir' : 'Guardar'}</button>
        </div>
      </form>
    </div>
  );
}
