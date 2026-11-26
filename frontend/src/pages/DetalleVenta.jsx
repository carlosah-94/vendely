import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import Mensaje from '../components/Mensaje';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { esDuenoDe } from '../utils/permisos';
import { api, mensajeDeError } from '../services/api';
import {
  cancelarPedido,
  descargarComprobanteVenta,
  listarNotificaciones,
  marcarEntregado,
  marcarEnviado,
} from '../services/pedidos';
import { formatoFecha, formatoMoneda } from '../utils/formato';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { canales } from '../services/tiempoReal';
import IconoChat from '../components/IconoChat';
import { enlaceWhatsapp, normalizarWhatsapp } from '../utils/whatsapp';
import { entregaDe } from '../utils/entrega';

const NOMBRE_ESTADO = {
  SIN_ENVIAR: 'Sin enviar',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

const NOMBRE_CORREO = {
  PAGO_APROBADO: 'Confirmación al cliente',
  NUEVO_PEDIDO: 'Aviso de venta nueva',
  PEDIDO_ENVIADO: 'Aviso de envío al cliente',
  STOCK_BAJO: 'Aviso de stock bajo',
};

/** Detalle de una venta con todos sus productos (RF3.2), y eliminarla (RF3.4). */
export default function DetalleVenta() {
  const { idPedido } = useParams();
  const { tienda, idTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const location = useLocation();
  const navigate = useNavigate();

  const [datos, setDatos] = useState({ clave: null, venta: null, cliente: null, error: '' });
  // Tiempo real (issue #4): si se paga este pedido (por ejemplo, llega el aviso del webhook
  // mientras el dueño lo mira), se recarga solo. Los avisos de otros pedidos se ignoran.
  const [version, setVersion] = useState(0);
  useTiempoReal(idTienda ? canales.pedidosDeTienda(idTienda) : null, (aviso) => {
    if (String(aviso.idPedido) === String(idPedido)) setVersion((v) => v + 1);
  });
  const [aviso, setAviso] = useState(location.state?.aviso ?? '');
  const [confirmando, setConfirmando] = useState(false);
  const [eliminando, setEliminando] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');

  // Issue #3: seguimiento del envío y correos del pedido.
  const [correos, setCorreos] = useState([]);
  const [envio, setEnvio] = useState({ empresaEnvio: '', numeroSeguimiento: '' });
  const [trabajando, setTrabajando] = useState(false);
  const [confirmandoCancelar, setConfirmandoCancelar] = useState(false);

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
    // Los correos se piden aparte: si eso falla, la venta se muestra igual.
    listarNotificaciones(idTienda, idPedido)
      .then((lista) => vigente && setCorreos(lista))
      .catch(() => vigente && setCorreos([]));
    return () => {
      vigente = false;
    };
  }, [idTienda, idPedido, version]);

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

  /**
   * Las tres acciones del envío comparten el mismo trato: el backend responde con el pedido ya
   * actualizado, así que se reemplaza en pantalla sin recargar, y los correos se vuelven a pedir
   * porque el cambio pudo generar uno nuevo.
   */
  async function accion(ejecutar, mensajeDeExito) {
    setTrabajando(true);
    setErrorAccion('');
    try {
      const venta = await ejecutar();
      setDatos((d) => ({ ...d, venta }));
      setAviso(mensajeDeExito);
      // El correo se manda en segundo plano, asi que normalmente todavia no esta registrado
      // cuando el backend responde: se vuelve a mirar un momento despues para que aparezca
      // sin tener que recargar la pagina.
      await refrescarCorreos();
      setTimeout(refrescarCorreos, 2000);
    } catch (e) {
      setErrorAccion(mensajeDeError(e));
    } finally {
      setTrabajando(false);
      setConfirmandoCancelar(false);
    }
  }

  /** Vuelve a pedir los correos del pedido. Si falla, se deja la lista como estaba. */
  async function refrescarCorreos() {
    try {
      setCorreos(await listarNotificaciones(idTienda, idPedido));
    } catch {
      // La venta ya se actualizó; que no se pueda leer la lista de correos no es motivo de error.
    }
  }

  async function descargar() {
    setErrorAccion('');
    try {
      await descargarComprobanteVenta(idTienda, idPedido, datos.venta?.numeroPedido, datos.venta?.numeroComprobante);
    } catch {
      setErrorAccion('No se pudo descargar el comprobante');
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
  const tipo = venta?.tipoEntrega ?? 'ENVIO';
  const entrega = entregaDe(tipo);
  const pagado = venta.estadoPago === 'APROBADO';
  const sinEnviar = venta.estado === 'SIN_ENVIAR';

  return (
    <section className="max-w-4xl space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-sm text-slate-500">
            <Link to="/panel/ventas" className="hover:underline">Ventas</Link> / Pedido #{venta.numeroPedido}
          </p>
          <h1 className="text-xl font-bold text-[#101010]">Pedido #{venta.numeroPedido}</h1>
          <p className="flex items-center gap-2 text-sm text-slate-600">
            {formatoFecha(venta.fechaCreacion)} <EtiquetaPago estado={venta.estadoPago} />
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700">
              {venta.estado === 'ENVIADO' ? entrega.enviado : NOMBRE_ESTADO[venta.estado] ?? venta.estado}
            </span>
          </p>
          {/* Boleta o factura (V25): el número lo pone la base al aprobarse el pago. */}
          {venta.tipoComprobante && (
            <p className="mt-1 text-sm text-slate-600">
              {venta.tipoComprobante === 'FACTURA' ? 'Factura' : 'Boleta'}{' '}
              {venta.numeroComprobante
                ? <b className="font-mono text-[#101010]">{venta.numeroComprobante}</b>
                : <span className="text-slate-500">(se emite al aprobarse el pago)</span>}
              {venta.clienteDocNumero && (
                <span className="ml-2 text-slate-500">
                  · {venta.clienteDocTipo} {venta.clienteDocNumero}{venta.clienteRazonSocial ? ` · ${venta.clienteRazonSocial}` : ''}
                </span>
              )}
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {/* En un pedido cancelado no se ofrece: el comprobante no dice que se canceló. */}
          {venta.estado !== 'CANCELADO' && (
            <button
              onClick={descargar}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100"
            >
              {venta.tipoComprobante === 'FACTURA' ? 'Descargar factura' : venta.tipoComprobante ? 'Descargar boleta' : 'Descargar comprobante'}
            </button>
          )}
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
          ) : esDuenoDe(tienda) && venta.estadoPago !== 'APROBADO' && (
            // Eliminar lo hace solo el dueño; un empleado con permiso de pedidos puede cancelar.
            // Una venta pagada no se elimina (el backend responde 409): se cancela abajo.
            <button
              onClick={() => setConfirmando(true)}
              className="rounded-md border border-red-300 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
            >
              Eliminar venta
            </button>
          )}
        </div>
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
            {cliente.telefono && (
              <a
                href={enlaceWhatsapp(normalizarWhatsapp(cliente.telefono, tienda?.idPais),
                  `Hola ${cliente.nombres}, te escribimos de ${tienda?.nombre ?? 'la tienda'} por tu pedido #${venta.numeroPedido}.`)}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-[#1f9d55] px-3 py-1.5 text-[13px] font-semibold text-[#17803f] hover:bg-[#effaf3]"
              >
                <IconoChat size={16} />
                Escribir al cliente por WhatsApp
              </a>
            )}
            {cliente.direccion && (
              <p className="text-slate-600">{cliente.direccion.direccion}, {cliente.direccion.distrito ?? [cliente.direccion.ciudad, cliente.direccion.pais].filter(Boolean).join(", ")}</p>
            )}
          </>
        ) : (
          <p className="text-slate-600">Cliente #{venta.idLogin}</p>
        )}
      </div>

      {/* Envío (issue #3). Qué se puede hacer depende del estado del pedido:
          sin enviar y pagado → enviar; sin enviar → cancelar; enviado → entregado. */}
      <div className="space-y-3 rounded-lg bg-white p-5 text-sm shadow-sm">
        <h2 className="font-semibold text-slate-800">Entrega · {entrega.nombre}</h2>

        {venta.fechaEnvio && (
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-slate-700">
            {venta.empresaEnvio && (
              <>
                <dt className="text-slate-500">{tipo === 'DELIVERY' ? 'Lo lleva' : 'Empresa'}</dt><dd>{venta.empresaEnvio}</dd>
              </>
            )}
            {venta.numeroSeguimiento && (
              <>
                <dt className="text-slate-500">Seguimiento</dt><dd className="font-medium">{venta.numeroSeguimiento}</dd>
              </>
            )}
            <dt className="text-slate-500">{entrega.fecha}</dt><dd>{formatoFecha(venta.fechaEnvio)}</dd>
            {venta.fechaEntrega && (
              <>
                <dt className="text-slate-500">Entregado el</dt><dd>{formatoFecha(venta.fechaEntrega)}</dd>
              </>
            )}
          </dl>
        )}

        {venta.estado === 'CANCELADO' && (
          <p className="text-slate-600">
            Pedido cancelado el {formatoFecha(venta.fechaCancelacion)}. El stock volvió al inventario.
          </p>
        )}

        {sinEnviar && !pagado && (
          <Mensaje tipo="info" texto={`Para ${entrega.accion.toLowerCase()}, primero el pedido tiene que estar pagado.`} />
        )}

        {sinEnviar && pagado && (
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              accion(() => marcarEnviado(idTienda, idPedido, envio),
                `Pedido #${venta.numeroPedido}: ${entrega.enviado.toLowerCase()}. Se le avisó al cliente.`);
            }}
          >
            {tipo === 'DELIVERY' && (
              <label className="block">
                <span className="block text-slate-600">Quién lo lleva (opcional)</span>
                <input
                  value={envio.empresaEnvio}
                  onChange={(e) => setEnvio({ ...envio, empresaEnvio: e.target.value })}
                  maxLength={60}
                  placeholder="Juan, moto de la tienda..."
                  className="mt-1 rounded-md border border-slate-300 px-3 py-2"
                />
              </label>
            )}
            {tipo === 'RECOJO' && (
              <p className="w-full text-slate-600">Cuando el pedido esté empacado, márcalo como listo: le avisamos al cliente con la dirección y el horario del local.</p>
            )}
            {tipo === 'ENVIO' && (
            <>
            <label className="block">
              <span className="block text-slate-600">Empresa de envío</span>
              <input
                value={envio.empresaEnvio}
                onChange={(e) => setEnvio({ ...envio, empresaEnvio: e.target.value })}
                required
                maxLength={60}
                placeholder="Olva, Shalom..."
                className="mt-1 rounded-md border border-slate-300 px-3 py-2"
              />
            </label>
            <label className="block">
              <span className="block text-slate-600">Número de seguimiento</span>
              <input
                value={envio.numeroSeguimiento}
                onChange={(e) => setEnvio({ ...envio, numeroSeguimiento: e.target.value })}
                required
                maxLength={60}
                placeholder="ABC123456"
                className="mt-1 rounded-md border border-slate-300 px-3 py-2"
              />
            </label>
            </>
            )}
            <button
              type="submit"
              disabled={trabajando}
              className="rounded-md bg-violet-600 px-4 py-2 font-medium text-white hover:bg-violet-700 disabled:opacity-50"
            >
              {entrega.accion}
            </button>
          </form>
        )}

        <div className="flex flex-wrap items-center gap-2">
          {venta.estado === 'ENVIADO' && (
            <button
              onClick={() => accion(() => marcarEntregado(idTienda, idPedido),
                `Pedido #${venta.numeroPedido} marcado como entregado.`)}
              disabled={trabajando}
              className="rounded-md bg-emerald-600 px-4 py-2 font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              Marcar como entregado
            </button>
          )}
          {sinEnviar && (
            confirmandoCancelar ? (
              <span className="flex flex-wrap items-center gap-2">
                <span className="text-slate-700">
                  ¿Cancelar el pedido? El stock vuelve al inventario
                  {pagado && ' y el pago queda como reembolsado. Devuelve el dinero al cliente desde tu cuenta de PayPal o Mercado Pago'}.
                </span>
                <button
                  onClick={() => accion(() => cancelarPedido(idTienda, idPedido),
                    `Pedido #${venta.numeroPedido} cancelado. Su stock volvió al inventario.`)}
                  disabled={trabajando}
                  className="rounded-md bg-red-600 px-3 py-2 text-white hover:bg-red-700 disabled:opacity-50"
                >
                  Sí, cancelar
                </button>
                <button
                  onClick={() => setConfirmandoCancelar(false)}
                  disabled={trabajando}
                  className="rounded-md border border-slate-300 px-3 py-2 text-slate-700 hover:bg-slate-100"
                >
                  No
                </button>
              </span>
            ) : (
              <button
                onClick={() => setConfirmandoCancelar(true)}
                className="rounded-md border border-red-300 px-4 py-2 text-red-600 hover:bg-red-50"
              >
                Cancelar pedido
              </button>
            )
          )}
        </div>
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
                  {l.datoCliente && (
                    <p className="mt-0.5 inline-block rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-900">Dato del cliente: {l.datoCliente}</p>
                  )}
                </td>
                <td className="px-4 py-3 text-right">{l.cantidad}</td>
                <td className="px-4 py-3 text-right">
                  {/* Con oferta automática: el precio de lista tachado y el cobrado */}
                  {Number(l.descuentoUnitario) > 0 ? (
                    <>
                      <span className="mr-1 text-xs text-slate-400 line-through">{formatoMoneda(l.precioUnitario, venta.moneda)}</span>
                      {formatoMoneda(Number(l.precioUnitario) - Number(l.descuentoUnitario), venta.moneda)}
                      {l.nombreOferta && <p className="text-xs text-emerald-700">{l.nombreOferta}</p>}
                    </>
                  ) : (
                    formatoMoneda(l.precioUnitario, venta.moneda)
                  )}
                </td>
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
          {Number(venta.descuentoCupon) > 0 && (
            <div className="flex justify-between">
              <dt className="text-slate-600">Código {venta.codigoCupon}</dt>
              <dd>- {formatoMoneda(venta.descuentoCupon, venta.moneda)}</dd>
            </div>
          )}
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

      {/* Correos de este pedido (issue #3). SIMULADO = no hay RESEND_API_KEY configurada. */}
      <div className="rounded-lg bg-white p-5 text-sm shadow-sm">
        <h2 className="mb-2 font-semibold text-slate-800">Correos enviados</h2>
        {correos.length === 0 ? (
          <p className="text-slate-500">Todavía no se envió ningún correo por este pedido.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {correos.map((c) => (
              <li key={c.idNotificacion} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <div>
                  <p className="text-slate-800">{NOMBRE_CORREO[c.tipo] ?? c.tipo}</p>
                  <p className="text-xs text-slate-500">
                    {c.destinatario} · {formatoFecha(c.fechaEnvio ?? c.fechaCreacion)}
                  </p>
                  {c.error && <p className="text-xs text-red-600">{c.error}</p>}
                </div>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs ${
                    c.estado === 'ENVIADO'
                      ? 'bg-emerald-100 text-emerald-800'
                      : c.estado === 'FALLIDO'
                        ? 'bg-red-100 text-red-800'
                        : 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {c.estado === 'SIMULADO' ? 'Simulado' : c.estado === 'FALLIDO' ? 'Falló' : 'Enviado'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
