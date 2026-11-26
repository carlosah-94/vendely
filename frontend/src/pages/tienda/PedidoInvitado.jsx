import { useEffect, useState } from 'react';
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom';
import EtiquetaPago from '../../components/EtiquetaPago';
import Mensaje from '../../components/Mensaje';
import { mensajeDeError } from '../../services/api';
import { descargarComprobanteInvitado, iniciarPagoInvitado, verPedidoInvitado } from '../../services/invitado';
import { clavePedido, guardarClavePedido } from '../../utils/compraInvitado';
import { formatoFecha, formatoMoneda } from '../../utils/formato';
import { METODOS_PAGO, metodosDeTienda } from '../../utils/metodosPago';
import { LineaDeTiempo } from './MisPedidos';

/**
 * Pedido de un invitado (compró sin cuenta). Se abre con el enlace del correo de confirmación
 * (/tienda/<sub>/pedido/<id>?clave=...) o desde este navegador, que guardó la clave al comprar.
 * Sin la clave correcta el backend responde 404: nadie más puede ver el pedido.
 */
export default function PedidoInvitado() {
  const { subdominio, tienda } = useOutletContext();
  const { idPedido } = useParams();
  const [params] = useSearchParams();
  const clave = params.get('clave') ?? clavePedido(subdominio, idPedido);
  const metodos = metodosDeTienda(tienda);

  const [estado, setEstado] = useState({ cargado: false, pedido: null, error: '' });
  const [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    let vigente = true;
    if (!clave) {
      return undefined;
    }
    verPedidoInvitado(subdominio, idPedido, clave)
      .then((pedido) => {
        if (!vigente) return;
        // La clave del enlace sirvió: se guarda para pagar o volver sin el correo.
        guardarClavePedido(subdominio, idPedido, clave);
        setEstado({ cargado: true, pedido, error: '' });
      })
      .catch((e) => vigente && setEstado({ cargado: true, pedido: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio, idPedido, clave]);

  async function pagar(metodo) {
    setOcupado(true);
    setError('');
    try {
      const { urlPago } = await iniciarPagoInvitado(subdominio, idPedido, metodo);
      window.location.assign(urlPago);
    } catch (e) {
      setError(mensajeDeError(e));
      setOcupado(false);
    }
  }

  async function descargar(p) {
    setError('');
    try {
      await descargarComprobanteInvitado(subdominio, idPedido, clave, p.numeroPedido, p.numeroComprobante);
    } catch (e) {
      setError(mensajeDeError(e));
    }
  }

  const base = `/tienda/${subdominio}`;
  if (!clave || (estado.cargado && !estado.pedido)) {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-16 text-center">
        <h1 className="text-2xl font-semibold">No encontramos este pedido</h1>
        <p className="text-[#616161]">
          Abre el enlace del correo de confirmación de tu compra. Si compraste con una cuenta, inicia sesión.
        </p>
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
          <Link to={`${base}/ingresar`} className="rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
            Iniciar sesión
          </Link>
          <Link to={base} className="rounded-md border border-[#303030] px-6 py-3 font-medium">Volver a la tienda</Link>
        </div>
      </div>
    );
  }
  if (!estado.cargado) {
    return <p className="p-16 text-center text-[#616161]">Cargando tu pedido...</p>;
  }

  const p = estado.pedido;
  const pagado = p.estadoPago === 'APROBADO' || p.estadoPago === 'REEMBOLSADO';
  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-10">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Pedido #{p.numeroPedido}</h1>
          {p.numeroComprobante && (
            <p className="font-mono text-sm text-[#616161]">
              {p.tipoComprobante === 'FACTURA' ? 'Factura' : 'Boleta'} {p.numeroComprobante}
            </p>
          )}
          <p className="text-sm text-[#616161]">{formatoFecha(p.fechaCreacion)}</p>
        </div>
        <EtiquetaPago estado={p.estadoPago} />
      </div>

      {error && <Mensaje tipo="error" texto={error} />}

      <div className="space-y-4 rounded-lg border border-[#e3e3e3] bg-[#fafafa] p-5 text-sm">
        <LineaDeTiempo pedido={p} pagado={pagado} />
        <ul className="divide-y divide-[#e3e3e3]">
          {p.lineas.map((l) => (
            <li key={l.idVariante} className="flex justify-between gap-4 py-2">
              <span>
                {l.nombreProducto} <span className="text-[#616161]">× {l.cantidad}</span>
                {l.datoCliente && <span className="block text-xs text-[#616161]">{l.datoCliente}</span>}
              </span>
              <span>{formatoMoneda(l.importe, p.moneda)}</span>
            </li>
          ))}
        </ul>
        <dl className="ml-auto max-w-xs space-y-1">
          <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatoMoneda(p.subtotal, p.moneda)}</dd></div>
          {Number(p.descuentoCupon) > 0 && (
            <div className="flex justify-between"><dt>Código {p.codigoCupon}</dt><dd>- {formatoMoneda(p.descuentoCupon, p.moneda)}</dd></div>
          )}
          <div className="flex justify-between"><dt>Envío</dt><dd>{formatoMoneda(p.costoEnvio, p.moneda)}</dd></div>
          <div className="flex justify-between border-t border-[#e3e3e3] pt-1 font-semibold">
            <dt>Total</dt><dd>{formatoMoneda(p.total, p.moneda)}</dd>
          </div>
        </dl>
      </div>

      <div className="flex flex-wrap gap-3">
        {!pagado && p.estado !== 'CANCELADO' && metodos.map((m) => (
          <button key={m} type="button" disabled={ocupado} onClick={() => pagar(m)}
            className="rounded-md bg-[var(--tienda)] px-5 py-2.5 text-sm font-medium text-[var(--tienda-texto)] hover:brightness-90 disabled:opacity-60">
            Pagar con {METODOS_PAGO[m].nombre}
          </button>
        ))}
        {pagado && p.estado !== 'CANCELADO' && (
          <button type="button" onClick={() => descargar(p)}
            className="rounded-md border border-[#c9c9c9] px-5 py-2.5 text-sm font-medium hover:bg-[#f5f5f5]">
            Descargar comprobante
          </button>
        )}
        <Link to={base} className="rounded-md border border-[#303030] px-5 py-2.5 text-sm font-medium">Seguir comprando</Link>
      </div>
      <p className="text-xs text-[#616161]">
        Compraste sin cuenta. Este enlace es solo tuyo: no lo compartas.
      </p>
    </div>
  );
}
