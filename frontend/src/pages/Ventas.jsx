import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha, formatoMoneda } from '../utils/formato';

const NOMBRE_ESTADO = {
  SIN_ENVIAR: 'Sin enviar',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Lista de ventas de la tienda (RF3.2), con eliminar (RF3.4). */
export default function Ventas() {
  const { tiendas, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const location = useLocation();

  // Se guarda de qué tienda son las ventas: si no coincide con la elegida, todavía se están cargando.
  const [datos, setDatos] = useState({ idTienda: null, ventas: [], error: '' });
  const [aviso, setAviso] = useState(location.state?.aviso ?? '');
  const [errorAccion, setErrorAccion] = useState('');
  const [porEliminar, setPorEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

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
  }, [idTienda]);

  const cargando = cargandoTiendas || (idTienda !== null && datos.idTienda !== idTienda);

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
          <h1 className="text-2xl font-bold text-slate-800">Ventas</h1>
          {tiendas.length > 0 && (
            <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
          )}
        </div>
        <Link
          to="/panel/ventas/nueva"
          className="rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700"
        >
          Registrar venta
        </Link>
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}
      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      <div className="overflow-x-auto rounded-lg bg-white shadow-sm">
        {cargando ? (
          <p className="p-6 text-slate-500">Cargando ventas...</p>
        ) : datos.ventas.length === 0 ? (
          <div className="p-10 text-center space-y-3">
            <p className="text-lg font-medium text-slate-800">Aún no hay ventas</p>
            <p className="text-slate-500">Cuando registres una venta, aparecerá aquí.</p>
            <Link to="/panel/ventas/nueva" className="inline-block text-violet-600 hover:underline">
              Registrar la primera venta
            </Link>
          </div>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
              <tr>
                <th className="px-4 py-3">Pedido</th>
                <th className="px-4 py-3">Fecha</th>
                <th className="px-4 py-3">Cliente</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3 text-right">Total</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {datos.ventas.map((v) => (
                <tr key={v.idPedido} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-3 font-medium text-slate-800">#{v.numeroPedido}</td>
                  <td className="px-4 py-3 text-slate-600">{formatoFecha(v.fechaCreacion)}</td>
                  <td className="px-4 py-3 text-slate-800">{v.cliente}</td>
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
                        <button onClick={() => setPorEliminar(v.idPedido)} className="text-red-600 hover:underline">
                          Eliminar
                        </button>
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
