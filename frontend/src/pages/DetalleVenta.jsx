import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import Mensaje from '../components/Mensaje';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha, formatoMoneda } from '../utils/formato';

/** Detalle de una venta con todos sus productos (RF3.2), y eliminarla (RF3.4). */
export default function DetalleVenta() {
  const { idPedido } = useParams();
  const { idTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const location = useLocation();
  const navigate = useNavigate();

  const [datos, setDatos] = useState({ clave: null, venta: null, cliente: null, error: '' });
  const [aviso, setAviso] = useState(location.state?.aviso ?? '');
  const [confirmando, setConfirmando] = useState(false);
  const [eliminando, setEliminando] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');

  const clave = `${idTienda}-${idPedido}`;

  useEffect(() => {
    if (!idTienda) {
      return;
    }
    let vigente = true;
    const actual = `${idTienda}-${idPedido}`;
    api.get(`/panel/tiendas/${idTienda}/pedidos/${idPedido}`)
      .then(async ({ data: venta }) => {
        // La venta trae el id del cliente; su nombre y dirección vienen de la ficha del cliente.
        let cliente = null;
        try {
          cliente = (await api.get(`/panel/tiendas/${idTienda}/clientes/${venta.idLogin}`)).data;
        } catch {
          // Si no se puede leer el cliente, igual se muestra la venta.
        }
        if (vigente) setDatos({ clave: actual, venta, cliente, error: '' });
      })
      .catch((e) => {
        if (vigente) setDatos({ clave: actual, venta: null, cliente: null, error: mensajeDeError(e) });
      });
    return () => {
      vigente = false;
    };
  }, [idTienda, idPedido]);

  async function eliminar() {
    setEliminando(true);
    setErrorAccion('');
    try {
      await api.delete(`/panel/tiendas/${idTienda}/pedidos/${idPedido}`);
      navigate('/panel/ventas', {
        replace: true,
        state: { aviso: `Venta #${datos.venta.numeroPedido} eliminada. Su stock volvió al inventario.` },
      });
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
      setEliminando(false);
      setConfirmando(false);
    }
  }

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (cargandoTiendas || datos.clave !== clave) {
    return <p className="text-slate-500">Cargando venta...</p>;
  }
  if (datos.error) {
    return (
      <section className="space-y-4">
        <Mensaje tipo="error" texto={datos.error} />
        <Link to="/panel/ventas" className="text-violet-600 hover:underline">Volver a ventas</Link>
      </section>
    );
  }

  const { venta, cliente } = datos;

  return (
    <section className="max-w-4xl space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-sm text-slate-500">
            <Link to="/panel/ventas" className="hover:underline">Ventas</Link> / Pedido #{venta.numeroPedido}
          </p>
          <h1 className="text-2xl font-bold text-slate-800">Pedido #{venta.numeroPedido}</h1>
          <p className="flex items-center gap-2 text-sm text-slate-600">
            {formatoFecha(venta.fechaCreacion)} <EtiquetaPago estado={venta.estadoPago} />
          </p>
        </div>
        {confirmando ? (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-slate-700">¿Eliminar esta venta? El stock vuelve al inventario.</span>
            <button
              onClick={eliminar}
              disabled={eliminando}
              className="rounded-md bg-red-600 px-3 py-2 text-white hover:bg-red-700 disabled:opacity-50"
            >
              Sí, eliminar
            </button>
            <button
              onClick={() => setConfirmando(false)}
              disabled={eliminando}
              className="rounded-md border border-slate-300 px-3 py-2 text-slate-700 hover:bg-slate-100"
            >
              No
            </button>
          </div>
        ) : (
          <button
            onClick={() => setConfirmando(true)}
            className="rounded-md border border-red-300 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
          >
            Eliminar venta
          </button>
        )}
      </div>

      {aviso && <Mensaje tipo="exito" texto={aviso} onCerrar={() => setAviso('')} />}
      {errorAccion && <Mensaje tipo="error" texto={errorAccion} onCerrar={() => setErrorAccion('')} />}

      {/* Cliente */}
      <div className="rounded-lg bg-white p-5 text-sm shadow-sm">
        <h2 className="mb-2 font-semibold text-slate-800">Cliente</h2>
        {cliente ? (
          <>
            <p className="text-slate-800">{cliente.nombres} {cliente.primerApellido} {cliente.segundoApellido ?? ''}</p>
            <p className="text-slate-600">{cliente.correo}</p>
            {cliente.direccion && (
              <p className="text-slate-600">{cliente.direccion.direccion}, {cliente.direccion.distrito}</p>
            )}
          </>
        ) : (
          <p className="text-slate-600">Cliente #{venta.idLogin}</p>
        )}
      </div>

      {/* Productos de la venta (detalle_pedido) */}
      <div className="overflow-x-auto rounded-lg bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
            <tr>
              <th className="px-4 py-3">Producto</th>
              <th className="px-4 py-3 text-right">Cantidad</th>
              <th className="px-4 py-3 text-right">Precio unitario</th>
              <th className="px-4 py-3 text-right">Importe</th>
            </tr>
          </thead>
          <tbody>
            {venta.lineas.map((l) => (
              <tr key={l.idVariante} className="border-b border-slate-100 last:border-0">
                <td className="px-4 py-3">
                  <p className="text-slate-800">{l.nombreProducto}</p>
                  {l.nombreVariante && <p className="text-xs text-slate-500">{l.nombreVariante}</p>}
                </td>
                <td className="px-4 py-3 text-right">{l.cantidad}</td>
                <td className="px-4 py-3 text-right">{formatoMoneda(l.precioUnitario, venta.moneda)}</td>
                <td className="px-4 py-3 text-right font-medium">{formatoMoneda(l.importe, venta.moneda)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Totales: los precios ya incluyen el impuesto */}
        <dl className="ml-auto max-w-xs space-y-1 border-t border-slate-200 p-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-600">Subtotal</dt>
            <dd>{formatoMoneda(venta.subtotal, venta.moneda)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-600">Envío</dt>
            <dd>{formatoMoneda(venta.costoEnvio, venta.moneda)}</dd>
          </div>
          <div className="flex justify-between text-xs text-slate-500">
            <dt>Impuesto incluido</dt>
            <dd>{formatoMoneda(venta.montoImpuestos, venta.moneda)}</dd>
          </div>
          <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-bold text-slate-800">
            <dt>Total</dt>
            <dd>{formatoMoneda(venta.total, venta.moneda)}</dd>
          </div>
        </dl>
      </div>
    </section>
  );
}
