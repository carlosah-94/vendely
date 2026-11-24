import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import EtiquetaPago from './EtiquetaPago';
import { useToast } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { useTiempoReal } from '../hooks/useTiempoReal';
import { api } from '../services/api';
import { canales } from '../services/tiempoReal';
import { formatoMoneda } from '../utils/formato';

const MAX_EN_LISTA = 8;

/** El último pedido que el dueño ya vio en esta tienda (se guarda en el navegador). */
function leerVisto(idTienda) {
  try {
    const v = localStorage.getItem(`vendely_pedido_visto_${idTienda}`);
    return v === null ? null : Number(v);
  } catch {
    return null;
  }
}

function guardarVisto(idTienda, idPedido) {
  try {
    localStorage.setItem(`vendely_pedido_visto_${idTienda}`, String(idPedido));
  } catch {
    // Sin almacenamiento solo se pierde el contador al recargar.
  }
}

/** "hace 5 min", "hace 2 h", "ayer", "12 nov.": cuándo entró el pedido, fácil de leer. */
function haceCuanto(fecha) {
  const ms = Date.now() - new Date(fecha).getTime();
  const min = Math.floor(ms / 60000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `hace ${h} h`;
  if (h < 48) return 'ayer';
  return new Date(fecha).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}

/**
 * La campanita del dueño (issue #4), como las notificaciones de Shopify: el número rojo cuenta los
 * pedidos que entraron desde la última vez que la abrió, y al hacer clic se despliega la lista con
 * quién compró, cuánto y cuándo. Un clic en un pedido lleva a Pedidos con ese pedido señalado;
 * desde ahí el dueño decide si lo abre.
 *
 * Cómo sabe cuáles son nuevos: guarda en el navegador el último pedido visto de cada tienda. Así
 * el número sigue ahí aunque recargues la página o entres mañana. Los pedidos llegan en vivo por
 * el canal de la tienda (useTiempoReal) y, al llegar uno, la lista se vuelve a pedir a la API.
 */
export default function Campanita() {
  const { idTienda } = useTiendaActual();
  const { avisar } = useToast();
  const navigate = useNavigate();
  const caja = useRef(null);
  const [abierta, setAbierta] = useState(false);
  // Los pedidos son de la tienda indicada: si no coincide con la elegida, todavía no llegan.
  const [datos, setDatos] = useState({ idTienda: null, pedidos: [], visto: null, error: false });
  // Al abrir se marcan como vistos, pero en esa misma apertura siguen resaltados los que eran nuevos.
  const [resaltarDesde, setResaltarDesde] = useState(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    api.get(`/panel/tiendas/${idTienda}/pedidos`)
      .then(({ data }) => {
        if (!vigente) return;
        const pedidos = [...data].sort((a, b) => b.idPedido - a.idPedido);
        let visto = leerVisto(idTienda);
        // La primera vez en este navegador no se cuentan como nuevos los pedidos de siempre.
        if (visto === null) {
          visto = pedidos[0]?.idPedido ?? 0;
          guardarVisto(idTienda, visto);
        }
        setDatos({ idTienda, pedidos, visto, error: false });
      })
      .catch(() => vigente && setDatos((d) => ({ ...d, idTienda, error: true })));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  useTiempoReal(idTienda ? canales.pedidosDeTienda(idTienda) : null, (aviso) => {
    if (aviso.tipo === 'PEDIDO_CREADO') {
      avisar(`Nuevo pedido #${aviso.numeroPedido} · ${formatoMoneda(aviso.total, aviso.moneda)}`);
    }
    setVersion((v) => v + 1);
  });

  // Se cierra con un clic fuera o con Escape, como cualquier menú.
  useEffect(() => {
    if (!abierta) return undefined;
    function fuera(e) {
      if (caja.current && !caja.current.contains(e.target)) setAbierta(false);
    }
    function tecla(e) {
      if (e.key === 'Escape') setAbierta(false);
    }
    document.addEventListener('mousedown', fuera);
    document.addEventListener('keydown', tecla);
    return () => {
      document.removeEventListener('mousedown', fuera);
      document.removeEventListener('keydown', tecla);
    };
  }, [abierta]);

  const deEstaTienda = datos.idTienda === idTienda;
  const pedidos = deEstaTienda ? datos.pedidos : [];
  const visto = deEstaTienda ? datos.visto ?? 0 : 0;
  const nuevos = pedidos.filter((p) => p.idPedido > visto).length;

  function alternar() {
    if (abierta) {
      setAbierta(false);
      return;
    }
    setResaltarDesde(visto);
    setAbierta(true);
    if (pedidos.length) {
      guardarVisto(idTienda, pedidos[0].idPedido);
      setDatos((d) => ({ ...d, visto: pedidos[0].idPedido }));
    }
  }

  function irAlPedido(p) {
    setAbierta(false);
    navigate(`/panel/ventas?pedido=${p.idPedido}`);
  }

  return (
    <div ref={caja} className="relative">
      <button
        type="button"
        onClick={alternar}
        aria-haspopup="true"
        aria-expanded={abierta}
        aria-label={nuevos ? `Notificaciones: ${nuevos} pedido(s) nuevo(s)` : 'Notificaciones'}
        className={`relative rounded-lg p-1.5 text-[#dcdcdc] hover:bg-[#1f1f1f] ${abierta ? 'bg-[#1f1f1f]' : ''}`}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {nuevos > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#e51c00] px-1 text-[10px] font-semibold text-white">
            {nuevos > 9 ? '9+' : nuevos}
          </span>
        )}
      </button>

      {abierta && (
        <div
          role="dialog"
          aria-label="Notificaciones de pedidos"
          className="fixed inset-x-4 top-14 z-50 overflow-hidden sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-[22rem] rounded-xl bg-white text-[#303030] shadow-[0_0_0_1px_rgba(0,0,0,0.08),0_20px_40px_-12px_rgba(0,0,0,0.35)]"
        >
          <div className="flex items-center justify-between border-b border-[#ececec] px-4 py-3">
            <p className="text-[14px] font-semibold text-[#101010]">Notificaciones</p>
            {resaltarDesde !== null && pedidos.some((p) => p.idPedido > resaltarDesde) && (
              <span className="text-[12px] text-[#616161]">
                {pedidos.filter((p) => p.idPedido > resaltarDesde).length} nuevo(s)
              </span>
            )}
          </div>

          {datos.error && deEstaTienda ? (
            <p className="px-4 py-6 text-center text-[13px] text-[#616161]">No pudimos cargar los pedidos. Intenta de nuevo en un momento.</p>
          ) : !deEstaTienda ? (
            <p className="px-4 py-6 text-center text-[13px] text-[#616161]">Cargando...</p>
          ) : pedidos.length === 0 ? (
            <div className="px-4 py-8 text-center">
              <p className="text-[14px] font-medium text-[#101010]">Aún no tienes pedidos</p>
              <p className="mt-1 text-[12px] text-[#616161]">Cuando alguien compre en tu tienda, te avisamos aquí al instante.</p>
            </div>
          ) : (
            <ul className="max-h-[22rem] overflow-y-auto">
              {pedidos.slice(0, MAX_EN_LISTA).map((p) => {
                const esNuevo = resaltarDesde !== null && p.idPedido > resaltarDesde;
                return (
                  <li key={p.idPedido}>
                    <button
                      type="button"
                      onClick={() => irAlPedido(p)}
                      className={`flex w-full items-start gap-3 border-b border-[#f3f3f3] px-4 py-3 text-left last:border-0 hover:bg-[#f7f7f7] focus-visible:bg-[#f7f7f7] focus-visible:outline-none ${esNuevo ? 'bg-[#f2f7ff]' : ''}`}
                    >
                      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${esNuevo ? 'bg-[#1650d0]' : 'bg-transparent'}`} aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13px] text-[#101010]">
                          <b className="font-semibold">{p.cliente || 'Un cliente'}</b> compró {formatoMoneda(p.total, p.moneda)}
                        </span>
                        <span className="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-[#616161]">
                          Pedido #{p.numeroPedido} · {haceCuanto(p.fechaCreacion)}
                          <EtiquetaPago estado={p.estadoPago} />
                        </span>
                      </span>
                      {esNuevo && <span className="sr-only">Nuevo</span>}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          <Link
            to="/panel/ventas"
            onClick={() => setAbierta(false)}
            className="block border-t border-[#ececec] px-4 py-2.5 text-center text-[13px] font-semibold text-[#1650d0] hover:bg-[#f7f7f7]"
          >
            Ver todos los pedidos
          </Link>
        </div>
      )}
    </div>
  );
}
