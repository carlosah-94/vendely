import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import EtiquetaPago from '../../components/EtiquetaPago';
import Mensaje from '../../components/Mensaje';
import { mensajeDeError } from '../../services/api';
import { iniciarPago, listarMisPedidos, verMiPedido } from '../../services/tienda';
import { formatoFecha, formatoMoneda } from '../../utils/formato';

const NOMBRE_ESTADO = {
  SIN_ENVIAR: 'Sin enviar',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Pedidos del cliente en esta tienda, con su detalle (productos, envío y total) y pago. */
export default function MisPedidos() {
  const { subdominio } = useOutletContext();
  const [estado, setEstado] = useState({ cargado: false, pedidos: [], error: '' });
  const [abierto, setAbierto] = useState(null);       // idPedido con el detalle desplegado
  const [detalles, setDetalles] = useState({});       // idPedido → detalle ya cargado
  const [error, setError] = useState('');

  useEffect(() => {
    let vigente = true;
    listarMisPedidos()
      .then((pedidos) => vigente && setEstado({ cargado: true, pedidos, error: '' }))
      .catch((e) => vigente && setEstado({ cargado: true, pedidos: [], error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, []);

  async function alternar(idPedido) {
    if (abierto === idPedido) {
      setAbierto(null);
      return;
    }
    setAbierto(idPedido);
    if (!detalles[idPedido]) {
      try {
        const d = await verMiPedido(idPedido);
        setDetalles((actual) => ({ ...actual, [idPedido]: d }));
      } catch (e) {
        setError(mensajeDeError(e));
      }
    }
  }

  async function pagar(idPedido) {
    setError('');
    try {
      const { urlPago } = await iniciarPago(idPedido);
      window.location.href = urlPago;
    } catch (e) {
      setError(mensajeDeError(e));
    }
  }

  return (
    <div className="mx-auto max-w-3xl px-4 pt-10">
      <h1 className="mb-6 text-3xl font-semibold tracking-tight">Mis pedidos</h1>
      {estado.error && <Mensaje tipo="error" texto={estado.error} />}
      {error && <div className="mb-4"><Mensaje tipo="error" texto={error} onCerrar={() => setError('')} /></div>}

      {!estado.cargado ? (
        <p className="text-[#616161]">Cargando pedidos...</p>
      ) : estado.pedidos.length === 0 && !estado.error ? (
        <div className="space-y-3 py-10 text-center">
          <p className="text-[#616161]">Todavía no tienes pedidos.</p>
          <Link to={`/tienda/${subdominio}`} className="inline-block rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
            Ir a la tienda
          </Link>
        </div>
      ) : (
        <ul className="space-y-3">
          {estado.pedidos.map((p) => {
            const d = detalles[p.idPedido];
            const sinPagar = p.estadoPago !== 'APROBADO' && p.estadoPago !== 'REEMBOLSADO';
            return (
              <li key={p.idPedido} className="rounded-lg border border-[#e3e3e3]">
                <div className="flex flex-wrap items-center justify-between gap-3 p-4">
                  <div>
                    <p className="font-medium">Pedido #{p.numeroPedido}</p>
                    <p className="text-sm text-[#616161]">{formatoFecha(p.fechaCreacion)}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <EtiquetaPago estado={p.estadoPago} />
                    <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700">
                      {NOMBRE_ESTADO[p.estado] ?? p.estado}
                    </span>
                    <span className="font-medium">{formatoMoneda(p.total, p.moneda)}</span>
                    <button type="button" onClick={() => alternar(p.idPedido)} className="text-sm text-violet-700 hover:underline">
                      {abierto === p.idPedido ? 'Ocultar' : 'Ver detalle'}
                    </button>
                  </div>
                </div>

                {abierto === p.idPedido && (
                  <div className="space-y-3 border-t border-[#e3e3e3] bg-[#fafafa] p-4 text-sm">
                    {!d ? (
                      <p className="text-[#616161]">Cargando detalle...</p>
                    ) : (
                      <>
                        <ul className="divide-y divide-[#e3e3e3]">
                          {d.lineas.map((l) => (
                            <li key={l.idVariante} className="flex justify-between gap-4 py-2">
                              <span>
                                {l.nombreProducto} <span className="text-[#616161]">× {l.cantidad}</span>
                              </span>
                              <span>{formatoMoneda(l.importe, d.moneda)}</span>
                            </li>
                          ))}
                        </ul>
                        <dl className="ml-auto max-w-xs space-y-1">
                          <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatoMoneda(d.subtotal, d.moneda)}</dd></div>
                          <div className="flex justify-between"><dt>Envío</dt><dd>{formatoMoneda(d.costoEnvio, d.moneda)}</dd></div>
                          <div className="flex justify-between border-t border-[#e3e3e3] pt-1 font-semibold">
                            <dt>Total</dt><dd>{formatoMoneda(d.total, d.moneda)}</dd>
                          </div>
                        </dl>
                      </>
                    )}
                    {sinPagar && (
                      <button
                        type="button"
                        onClick={() => pagar(p.idPedido)}
                        className="rounded-md bg-[var(--tienda)] px-5 py-2 text-sm font-medium text-[var(--tienda-texto)] hover:brightness-90"
                      >
                        Pagar con Mercado Pago
                      </button>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
