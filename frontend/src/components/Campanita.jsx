import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { canales } from '../services/tiempoReal';
import { formatoMoneda } from '../utils/formato';

/**
 * La campanita del dueño (issue #4): cuando entra un pedido en la tienda que tiene elegida,
 * aparece un aviso y el contador sube, sin recargar. Al hacer clic va a Pedidos y se limpia.
 *
 * Escucha la tienda elegida: si el dueño cambia de tienda en el selector, deja de escuchar la
 * anterior y escucha la nueva (el canal cambia, y useTiempoReal se resuscribe solo).
 */
export default function Campanita() {
  const { idTienda } = useTiendaActual();
  const { avisar } = useToast();
  const navigate = useNavigate();
  // El contador es de la tienda que se estaba escuchando: al cambiar de tienda vuelve a cero.
  const [nuevos, setNuevos] = useState({ idTienda: null, cuantos: 0 });
  const cuantos = nuevos.idTienda === idTienda ? nuevos.cuantos : 0;

  useTiempoReal(idTienda ? canales.pedidosDeTienda(idTienda) : null, (aviso) => {
    if (aviso.tipo !== 'PEDIDO_CREADO') return;
    setNuevos((n) => ({ idTienda, cuantos: (n.idTienda === idTienda ? n.cuantos : 0) + 1 }));
    avisar(`Nuevo pedido #${aviso.numeroPedido} · ${formatoMoneda(aviso.total, aviso.moneda)}`);
  });

  function verPedidos() {
    setNuevos({ idTienda, cuantos: 0 });
    navigate('/panel/ventas');
  }

  return (
    <button
      type="button"
      onClick={verPedidos}
      aria-label={cuantos ? `${cuantos} pedido(s) nuevo(s)` : 'Pedidos nuevos'}
      className="relative rounded-lg p-1.5 text-[#dcdcdc] hover:bg-[#1f1f1f]"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
        strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
      </svg>
      {cuantos > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#e51c00] px-1 text-[10px] font-semibold text-white">
          {cuantos > 9 ? '9+' : cuantos}
        </span>
      )}
    </button>
  );
}
