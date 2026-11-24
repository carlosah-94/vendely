import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { esDuenoDe } from '../utils/permisos';
import { api, mensajeDeError } from '../services/api';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { canales } from '../services/tiempoReal';
import { formatoFecha, formatoMoneda } from '../utils/formato';

const NOMBRE_ESTADO = {
  SIN_ENVIAR: 'Sin enviar',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Lista de ventas de la tienda (RF3.2), con eliminar (RF3.4). */
export default function Ventas() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  // Eliminar una venta lo hace solo el dueño (un empleado puede cancelarla desde el detalle).
  const puedeEliminar = esDuenoDe(tienda);
  const location = useLocation();

  // Se guarda de qué tienda son las ventas: si no coincide con la elegida, todavía se están cargando.
  const [datos, setDatos] = useState({ idTienda: null, ventas: [], error: '' });
  // Tiempo real (issue #4): cualquier aviso de pedidos de esta tienda (nuevo, pagado, enviado…)
  // vuelve a pedir la lista. Se pide de nuevo en vez de armarla con el aviso, para que lo que se
  // ve pase por la misma API y los mismos permisos que al recargar.
  const [version, setVersion] = useState(0);
  useTiempoReal(idTienda ? canales.pedidosDeTienda(idTienda) : null, () => setVersion((v) => v + 1));
  const [aviso, setAviso] = useState(location.state?.aviso ?? '');
  const [errorAccion, setErrorAccion] = useState('');
  const [porEliminar, setPorEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);
  // Filtro por estado del pedido (issue #3). Se filtra en la pantalla y no en el backend:
  // la lista ya esta completa en memoria, asi que cambiar el filtro no cuesta una peticion.
  const [filtro, setFiltro] = useState('TODOS');
  // ?pedido=ID: viene de la campanita. Se señala ese pedido unos segundos; abrirlo lo decide el dueño.
  const [params] = useSearchParams();
  const pedidoSenalado = Number(params.get('pedido')) || null;
  const [apagado, setApagado] = useState(null);   // pedido cuya luz ya se apagó
  const resaltado = pedidoSenalado && apagado !== pedidoSenalado ? pedidoSenalado : null;
  const filaSenalada = useRef(null);

  useEffect(() => {
    if (!idTienda) {
      return;
    }
    let vigente = true; // evita mostrar la respuesta de una tienda que ya no está elegida
    api.get(`/panel/tiendas/${idTienda}/pedidos`)
      .then(({ data }) => {
        if (vigente) setDatos({ idTienda, ventas: data, error: '' });
      })
      .catch((e) => {
        if (vigente) setDatos({ idTienda, ventas: [], error: mensajeDeError(e) });
      });
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);
  // El pedido señalado se ve aunque el filtro de estado lo escondería.
  const ventas = filtro === 'TODOS'
    ? datos.ventas
    : datos.ventas.filter((v) => v.estado === filtro || v.idPedido === resaltado);

  // Cuando la lista ya está, se lleva la vista hasta el pedido señalado y se ilumina 4 segundos.
  const listaLista = !cargando && datos.idTienda === idTienda;
  useEffect(() => {
    if (!resaltado || !listaLista) return undefined;
    const marco = requestAnimationFrame(() =>
      filaSenalada.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    const fin = setTimeout(() => setApagado(resaltado), 4000);
    return () => {
      cancelAnimationFrame(marco);
      clearTimeout(fin);
    };
  }, [resaltado, listaLista]);


  async function eliminar(venta) {
    setEliminando(true);
    setErrorAccion('');
    try {
      await api.delete(`/panel/tiendas/${idTienda}/pedidos/${venta.idPedido}`);
      setDatos((d) => ({ ...d, ventas: d.ventas.filter((v) => v.idPedido !== venta.idPedido) }));
      setAviso(`Venta #${venta.numeroPedido} eliminada. Su stock volvió al inventario.`);
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
    } finally {
      setEliminando(false);
      setPorEliminar(null);
    }
  }

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para registrar ventas." />;
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Pedidos</h1>
          {tiendas.length > 0 && (
            <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
          )}
        </div>
        <div className="flex items-center gap-3">
          <label className="text-sm text-slate-600">
            Estado{' '}
            <select
              value={filtro}
              onChange={(e) => setFiltro(e.target.value)}
              className="rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-800"
            >
              <option value="TODOS">Todos</option>
              {Object.entries(NOMBRE_ESTADO).map(([clave, nombre]) => (
                <option key={clave} value={clave}>{nombre}</option>
              ))}
            </select>
          </label>
          <Link
            to="/panel/ventas/nueva"
            className="rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700"
          >
            Registrar venta
          </Link>
        </div>
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      <div className="overflow-x-auto rounded-lg bg-white shadow-sm">
        {cargando ? (
          <p className="p-6 text-slate-500">Cargando ventas...</p>
        ) : ventas.length === 0 ? (
          <div className="p-10 text-center space-y-3">
            {filtro === 'TODOS' ? (
              <>
                <p className="text-lg font-medium text-slate-800">Aún no hay ventas</p>
                <p className="text-slate-500">Cuando registres una venta, aparecerá aquí.</p>
                <Link to="/panel/ventas/nueva" className="inline-block text-violet-600 hover:underline">
                  Registrar la primera venta
                </Link>
              </>
            ) : (
              <>
                <p className="text-lg font-medium text-slate-800">
                  Ningún pedido está {NOMBRE_ESTADO[filtro].toLowerCase()}
                </p>
                <button onClick={() => setFiltro('TODOS')} className="text-violet-600 hover:underline">
                  Ver todos los pedidos
                </button>
              </>
            )}
          </div>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Pedido</th>
                <th className="px-4 py-3">Fecha</th>
                <th className="px-4 py-3">Cliente</th>
                <th className="px-4 py-3">Pago</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3 text-right">Total</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {ventas.map((v) => (
                <tr
                  key={v.idPedido}
                  ref={v.idPedido === pedidoSenalado ? filaSenalada : undefined}
                  aria-current={v.idPedido === pedidoSenalado ? 'true' : undefined}
                  className={`border-b border-slate-100 transition-colors duration-700 last:border-0 ${
                    v.idPedido === resaltado ? 'bg-amber-50 shadow-[inset_3px_0_0_#f59e0b]' : ''
                  }`}
                >
                  <td className="px-4 py-3 font-medium text-slate-800">#{v.numeroPedido}</td>
                  <td className="px-4 py-3 text-slate-600">{formatoFecha(v.fechaCreacion)}</td>
                  <td className="px-4 py-3 text-slate-800">{v.cliente}</td>
                  <td className="px-4 py-3"><EtiquetaPago estado={v.estadoPago} /></td>
                  <td className="px-4 py-3 text-slate-600">{NOMBRE_ESTADO[v.estado] ?? v.estado}</td>
                  <td className="px-4 py-3 text-right font-medium text-slate-800">
                    {formatoMoneda(v.total, v.moneda)}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {porEliminar === v.idPedido ? (
                      <span className="space-x-2">
                        <span className="text-slate-600">¿Eliminar?</span>
                        <button
                          onClick={() => eliminar(v)}
                          disabled={eliminando}
                          className="rounded bg-red-600 px-2 py-1 text-white hover:bg-red-700 disabled:opacity-50"
                        >
                          Sí, eliminar
                        </button>
                        <button
                          onClick={() => setPorEliminar(null)}
                          disabled={eliminando}
                          className="rounded border border-slate-300 px-2 py-1 text-slate-700 hover:bg-slate-100"
                        >
                          No
                        </button>
                      </span>
                    ) : (
                      <span className="space-x-3">
                        <Link to={`/panel/ventas/${v.idPedido}`} className="text-violet-600 hover:underline">
                          Ver detalle
                        </Link>
                        {/* Con el pago aprobado el dinero ya se cobró: el backend no deja borrarla (409),
                            así que el botón ni aparece. Se puede cancelar desde el detalle. */}
                        {puedeEliminar && v.estadoPago !== 'APROBADO' && (
                          <button onClick={() => setPorEliminar(v.idPedido)} className="text-red-600 hover:underline">
                            Eliminar
                          </button>
                        )}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
