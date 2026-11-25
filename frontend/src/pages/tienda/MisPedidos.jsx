import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import EtiquetaPago from '../../components/EtiquetaPago';
import Mensaje from '../../components/Mensaje';
import { mensajeDeError } from '../../services/api';
import { descargarMiComprobante, iniciarPago, listarMisPedidos, verMiPedido } from '../../services/tienda';
import { formatoFecha, formatoMoneda } from '../../utils/formato';
import { METODOS_PAGO, metodosDeTienda } from '../../utils/metodosPago';
import { useTiempoReal } from '../../hooks/useTiempoReal';
import { canales } from '../../services/tiempoReal';
import { entregaDe } from '../../utils/entrega';

const NOMBRE_ESTADO = {
  SIN_ENVIAR: 'Sin enviar',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Pedidos del cliente en esta tienda, con su detalle (productos, envío y total) y pago. */
export default function MisPedidos() {
  const { subdominio, tienda } = useOutletContext();
  const metodos = metodosDeTienda(tienda);
  const [estado, setEstado] = useState({ cargado: false, pedidos: [], error: '' });
  // Tiempo real (issue #4): el backend avisa a esta cuenta cuando uno de sus pedidos se paga,
  // se envia, se entrega o se cancela. Se recarga la lista y, si ese pedido esta abierto, su
  // detalle (la linea de tiempo); si esta cerrado, se olvida el detalle guardado para no mostrar
  // uno viejo al abrirlo.
  const [version, setVersion] = useState(0);
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
  }, [version]);

  useTiempoReal(canales.misPedidos, (aviso) => {
    setVersion((v) => v + 1);
    if (!aviso.idPedido) return;
    if (abierto === aviso.idPedido) {
      verMiPedido(aviso.idPedido)
        .then((d) => setDetalles((actual) => ({ ...actual, [aviso.idPedido]: d })))
        .catch(() => {});
    } else {
      setDetalles((actual) => {
        const copia = { ...actual };
        delete copia[aviso.idPedido];
        return copia;
      });
    }
  });

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

  async function pagar(idPedido, metodo) {
    setError('');
    try {
      const { urlPago } = await iniciarPago(idPedido, metodo);
      window.location.assign(urlPago);   // se va a la pasarela (como en Plan.jsx)
    } catch (e) {
      setError(mensajeDeError(e));
    }
  }

  async function descargar(idPedido, numeroPedido) {
    setError('');
    try {
      await descargarMiComprobante(idPedido, numeroPedido);
    } catch {
      setError('No se pudo descargar el comprobante');
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
                      {p.estado === 'ENVIADO' ? entregaDe(p.tipoEntrega).enviado : NOMBRE_ESTADO[p.estado] ?? p.estado}
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
                        <LineaDeTiempo pedido={d} pagado={!sinPagar} />
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
                          {Number(d.descuentoCupon) > 0 && (
                            <div className="flex justify-between"><dt>Código {d.codigoCupon}</dt><dd>- {formatoMoneda(d.descuentoCupon, d.moneda)}</dd></div>
                          )}
                          <div className="flex justify-between"><dt>Envío</dt><dd>{formatoMoneda(d.costoEnvio, d.moneda)}</dd></div>
                          <div className="flex justify-between border-t border-[#e3e3e3] pt-1 font-semibold">
                            <dt>Total</dt><dd>{formatoMoneda(d.total, d.moneda)}</dd>
                          </div>
                        </dl>
                      </>
                    )}
                    <div className="flex flex-wrap items-center gap-3">
                      {/* Un botón por cada método que ofrece la tienda (antes decía siempre
                          "Mercado Pago", aunque el backend cobrara con PayPal). */}
                      {sinPagar && p.estado !== 'CANCELADO' && metodos.map((m) => (
                        <button
                          key={m}
                          type="button"
                          onClick={() => pagar(p.idPedido, m)}
                          className="rounded-md bg-[var(--tienda)] px-5 py-2 text-sm font-medium text-[var(--tienda-texto)] hover:brightness-90"
                        >
                          Pagar con {METODOS_PAGO[m].nombre}
                        </button>
                      ))}
                      {/* En un pedido cancelado no se ofrece: el comprobante no dice que se
                          canceló, así que entregarlo confundiría más de lo que ayuda. */}
                      {!sinPagar && p.estado !== 'CANCELADO' && (
                        <button
                          type="button"
                          onClick={() => descargar(p.idPedido, p.numeroPedido)}
                          className="rounded-md border border-[#c9c9c9] px-5 py-2 text-sm font-medium text-[#101010] hover:bg-white"
                        >
                          Descargar comprobante
                        </button>
                      )}
                    </div>
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

/**
 * Línea de tiempo del pedido (issue #3): en qué va y cuándo pasó cada cosa.
 *
 * Los pasos cumplidos se marcan con el color de la tienda; los que faltan quedan en gris. Un
 * pedido cancelado corta la línea: solo se muestran los pasos hasta ahí y el aviso de que se
 * canceló, porque los demás ya no van a ocurrir.
 */
function LineaDeTiempo({ pedido, pagado }) {
  const cancelado = pedido.estado === 'CANCELADO';
  const pasos = cancelado
    ? [
        { nombre: 'Pedido creado', fecha: pedido.fechaCreacion, hecho: true },
        { nombre: 'Cancelado', fecha: pedido.fechaCancelacion, hecho: true },
      ]
    : [
        { nombre: 'Pedido creado', fecha: pedido.fechaCreacion, hecho: true },
        { nombre: 'Pago confirmado', fecha: null, hecho: pagado },
        {
          nombre: entregaDe(pedido.tipoEntrega).enviado,
          fecha: pedido.fechaEnvio,
          hecho: pedido.estado === 'ENVIADO' || pedido.estado === 'ENTREGADO',
          detalle: pedido.tipoEntrega === 'RECOJO'
            ? 'Recojo en tienda: menciona tu número de pedido al llegar'
            : pedido.numeroSeguimiento
              ? `${pedido.empresaEnvio} · seguimiento ${pedido.numeroSeguimiento}`
              : pedido.empresaEnvio ? `Lo lleva: ${pedido.empresaEnvio}` : null,
        },
        { nombre: 'Entregado', fecha: pedido.fechaEntrega, hecho: pedido.estado === 'ENTREGADO' },
      ];

  return (
    <ol className="space-y-2 border-b border-[#e3e3e3] pb-3">
      {pasos.map((paso) => (
        <li key={paso.nombre} className="flex items-start gap-3">
          <span
            className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${
              paso.hecho ? 'bg-[var(--tienda)]' : 'bg-[#d4d4d4]'
            }`}
            aria-hidden="true"
          />
          <span>
            <span className={paso.hecho ? 'font-medium' : 'text-[#9e9e9e]'}>{paso.nombre}</span>
            {paso.fecha && <span className="text-[#616161]"> · {formatoFecha(paso.fecha)}</span>}
            {paso.detalle && <span className="block text-xs text-[#616161]">{paso.detalle}</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}
