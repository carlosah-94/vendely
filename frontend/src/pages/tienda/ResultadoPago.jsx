import { useEffect, useRef, useState } from 'react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import { IconoCerrar, IconoCheck } from '../../components/Iconos';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { confirmarPago, iniciarPago } from '../../services/tienda';

/**
 * Regreso de la pasarela de pago.
 * - Mercado Pago agrega payment_id y external_reference (id del pedido).
 * - PayPal agrega token (id de la orden); el id del pedido va en "pedido". Si el cliente
 *   canceló en PayPal, vuelve con cancelado=1.
 * NUNCA se cree el "status" de la URL: el backend consulta el pago en la pasarela.
 */
export default function ResultadoPago() {
  const { subdominio } = useOutletContext();
  const [params] = useSearchParams();
  const { vaciar } = useCarrito();

  const paymentId = params.get('payment_id') ?? params.get('collection_id') ?? params.get('token');
  const idPedido = params.get('external_reference') ?? params.get('pedido');
  const cancelado = params.get('cancelado') === '1';
  const sinDatos = cancelado || !paymentId || !idPedido || paymentId === 'null';

  // Sin payment_id el cliente volvió sin pagar: no hay nada que confirmar.
  const [estado, setEstado] = useState({ fase: sinDatos ? 'rechazado' : 'cargando', numero: null, error: '' });
  const [reintentando, setReintentando] = useState(false);
  const [errorReintento, setErrorReintento] = useState('');
  const yaConfirmado = useRef(false);

  useEffect(() => {
    if (sinDatos || yaConfirmado.current) {
      return;
    }
    yaConfirmado.current = true;
    confirmarPago(idPedido, paymentId)
      .then((r) => {
        if (r.estadoPago === 'APROBADO') {
          vaciar();
          setEstado({ fase: 'aprobado', numero: r.numeroPedido, error: '' });
        } else if (r.estadoPago === 'PENDIENTE') {
          setEstado({ fase: 'pendiente', numero: r.numeroPedido, error: '' });
        } else {
          setEstado({ fase: 'rechazado', numero: r.numeroPedido, error: '' });
        }
      })
      .catch((e) => setEstado({ fase: 'rechazado', numero: null, error: mensajeDeError(e) }));
  }, [sinDatos, idPedido, paymentId, vaciar]);

  async function reintentar() {
    setReintentando(true);
    setErrorReintento('');
    try {
      const { urlPago } = await iniciarPago(idPedido);
      window.location.href = urlPago;
    } catch (e) {
      setErrorReintento(mensajeDeError(e));
      setReintentando(false);
    }
  }

  const base = `/tienda/${subdominio}`;

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
          <Link to={`${base}/mis-pedidos`} className="rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)] hover:brightness-90">
            Ver mis pedidos
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
        <p className="text-[#616161]">La pasarela aún no confirma el pago del pedido #{estado.numero}. Revisa más tarde en Mis pedidos.</p>
        <Link to={`${base}/mis-pedidos`} className="inline-block rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
          Ver mis pedidos
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
        <Link to={`${base}/mis-pedidos`} className="rounded-md border border-[#303030] px-6 py-3 font-medium">
          Ver mis pedidos
        </Link>
      </div>
    </div>
  );
}
