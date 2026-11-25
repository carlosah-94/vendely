import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useOutletContext, useSearchParams } from 'react-router-dom';
import { IconoCerrar, IconoCheck } from '../../components/Iconos';
import { useAuth } from '../../context/AuthContext';
import { useCarrito } from '../../context/CarritoContext';
import { confirmarPagoInvitado, iniciarPagoInvitado } from '../../services/invitado';
import { clavePedido } from '../../utils/compraInvitado';
import { mensajeDeError } from '../../services/api';
import { confirmarPago, iniciarPago } from '../../services/tienda';
import { metodoDelRegreso } from '../../utils/metodosPago';
import { borrarPedidoEnCurso } from '../../utils/pedidoEnCurso';

/**
 * Regreso de la pasarela de pago.
 * - Mercado Pago agrega payment_id y external_reference (id del pedido).
 * - PayPal agrega token (id de la orden); el id del pedido va en "pedido". Si el cliente
 *   canceló en PayPal, vuelve con cancelado=1.
 * NUNCA se cree el "status" de la URL: el backend consulta el pago en la pasarela.
 */
export default function ResultadoPago() {
  const { subdominio, tienda } = useOutletContext();
  const [params] = useSearchParams();
  const { vaciar } = useCarrito();
  const { usuario, cargando } = useAuth();
  const location = useLocation();

  const paymentId = params.get('payment_id') ?? params.get('collection_id') ?? params.get('token');
  const idPedido = params.get('external_reference') ?? params.get('pedido');
  const cancelado = params.get('cancelado') === '1';
  const sinDatos = cancelado || !paymentId || !idPedido || paymentId === 'null';
  // Cliente con sesión, o invitado que compró en este navegador (tiene la clave de ese pedido).
  const esCliente = usuario?.rol === 'CLIENTE' && usuario.idTienda === tienda.idTienda;
  const invitado = !esCliente && Boolean(idPedido && clavePedido(subdominio, idPedido));
  const listo = !cargando && (esCliente || invitado);

  // Sin payment_id el cliente volvió sin pagar: no hay nada que confirmar.
  const [estado, setEstado] = useState({ fase: sinDatos ? 'rechazado' : 'cargando', numero: null, error: '' });
  const [reintentando, setReintentando] = useState(false);
  const [errorReintento, setErrorReintento] = useState('');
  const yaConfirmado = useRef(false);

  useEffect(() => {
    if (sinDatos || !listo || yaConfirmado.current) {
      return;
    }
    yaConfirmado.current = true;
    const confirmar = invitado ? confirmarPagoInvitado(subdominio, idPedido, paymentId) : confirmarPago(idPedido, paymentId);
    confirmar
      .then((r) => {
        if (r.estadoPago === 'APROBADO') {
          vaciar();
          borrarPedidoEnCurso(subdominio);
          setEstado({ fase: 'aprobado', numero: r.numeroPedido, error: '' });
        } else if (r.estadoPago === 'PENDIENTE') {
          setEstado({ fase: 'pendiente', numero: r.numeroPedido, error: '' });
        } else {
          setEstado({ fase: 'rechazado', numero: r.numeroPedido, error: '' });
        }
      })
      .catch((e) => setEstado({ fase: 'rechazado', numero: null, error: mensajeDeError(e) }));
  }, [sinDatos, listo, invitado, idPedido, paymentId, vaciar, subdominio]);

  async function reintentar() {
    setReintentando(true);
    setErrorReintento('');
    try {
      // Se reintenta con la misma pasarela de la que volvió (si se sabe); si no, la de por defecto.
      const { urlPago } = invitado
        ? await iniciarPagoInvitado(subdominio, idPedido, metodoDelRegreso(params))
        : await iniciarPago(idPedido, metodoDelRegreso(params));
      window.location.href = urlPago;
    } catch (e) {
      setErrorReintento(mensajeDeError(e));
      setReintentando(false);
    }
  }

  const base = `/tienda/${subdominio}`;
  // El invitado no tiene "Mis pedidos": va a la página de su pedido (la abre su clave).
  const enlacePedidos = invitado ? `${base}/pedido/${idPedido}` : `${base}/mis-pedidos`;
  const textoPedidos = invitado ? 'Ver mi pedido' : 'Ver mis pedidos';

  if (cargando) {
    return <p className="p-16 text-center text-[#616161]">Cargando...</p>;
  }
  if (!esCliente && !invitado) {
    // Ni sesión ni clave de este pedido (otro navegador): que inicie sesión, o use el enlace del correo.
    return <Navigate to={`${base}/ingresar`} replace state={{ volver: location.pathname + location.search }} />;
  }

  if (estado.fase === 'cargando') {
    return <p className="p-16 text-center text-[#616161]">Confirmando tu pago...</p>;
  }

  if (estado.fase === 'aprobado') {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-16 text-center">
        <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-green-700">
          <IconoCheck tamano={32} />
        </span>
        <h1 className="text-3xl font-semibold tracking-tight">¡Gracias por tu compra!</h1>
        <p className="text-lg font-medium text-green-700">Pago aprobado</p>
        <p className="text-[#616161]">Tu pedido #{estado.numero} fue confirmado. Te enviaremos tu compra a la dirección indicada.</p>
        <div className="flex flex-col gap-3 pt-2 sm:flex-row sm:justify-center">
          <Link to={enlacePedidos} className="rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)] hover:brightness-90">
            {textoPedidos}
          </Link>
          <Link to={base} className="rounded-md border border-[#303030] px-6 py-3 font-medium">
            Seguir comprando
          </Link>
        </div>
      </div>
    );
  }

  if (estado.fase === 'pendiente') {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-16 text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Tu pago está pendiente</h1>
        <p className="text-[#616161]">La pasarela aún no confirma el pago del pedido #{estado.numero}. Revisa más tarde {invitado ? 'en tu pedido' : 'en Mis pedidos'}.</p>
        <Link to={enlacePedidos} className="inline-block rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
          {textoPedidos}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md space-y-4 px-4 py-16 text-center">
      <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100 text-red-700">
        <IconoCerrar tamano={32} />
      </span>
      <h1 className="text-3xl font-semibold tracking-tight">El pago no se completó</h1>
      <p className="text-[#616161]">
        {estado.error || 'No se realizó ningún cobro. Puedes intentarlo de nuevo.'}
      </p>
      {errorReintento && <p role="alert" className="text-sm text-red-700">{errorReintento}</p>}
      <div className="flex flex-col gap-3 pt-2 sm:flex-row sm:justify-center">
        {idPedido && (
          <button
            type="button"
            onClick={reintentar}
            disabled={reintentando}
            className="rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)] hover:brightness-90 disabled:opacity-60"
          >
            {reintentando ? 'Un momento...' : 'Reintentar pago'}
          </button>
        )}
        <Link to={enlacePedidos} className="rounded-md border border-[#303030] px-6 py-3 font-medium">
          {textoPedidos}
        </Link>
      </div>
    </div>
  );
}
