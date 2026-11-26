import { Fragment, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConversacionSoporte from '../../components/ConversacionSoporte';
import { IconoSoporte } from '../../components/Iconos';
import Mensaje from '../../components/Mensaje';
import { AvatarTienda } from '../../components/MenuCuenta';
import { useBandejaSoporte } from '../../hooks/useAvisosSoporte';
import { useTiendaActual } from '../../hooks/useTiendaActual';
import { formatoFecha } from '../../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA } from '../../utils/panel';

/** Lo máximo que acepta setTimeout (~24,8 días); un vencimiento más lejano se revisa al cumplirse. */
const MAX_ESPERA = 2_147_483_647;

/**
 * Soporte a tiendas (superadmin): las tiendas cuyo dueño dio acceso a soporte de Vendely y todavía
 * no vence. "Entrar" abre el panel de esa tienda en modo soporte, para armarla por el cliente.
 *
 * La lista se mantiene sola, sin botón de actualizar: los mensajes y los cambios de acceso llegan
 * en vivo, y cada tienda desaparece en el momento en que vence su acceso.
 */
export default function AdminSoporte() {
  const navigate = useNavigate();
  // Para el superadmin, /api/panel/tiendas devuelve justo estas tiendas.
  const { tiendas, cambiarTienda, recargar, cargando, error } = useTiendaActual();
  const [abierta, setAbierta] = useState(null);   // la tienda cuya conversación se muestra
  // Mensajes sin responder por tienda, en vivo. Si un dueño da o quita el acceso, la lista cambia.
  const { porTienda, sinResponder, recargar: recargarBandeja } = useBandejaSoporte(true, { alCambiarAcceso: recargar });
  const [ahora, setAhora] = useState(() => Date.now());

  // Al vencer el acceso de la próxima tienda, se la saca de la lista (vencer no genera un aviso en vivo).
  // recargar cambia en cada render: se guarda en un ref para no reiniciar el reloj a cada rato.
  const alVencer = useRef(null);
  useEffect(() => {
    alVencer.current = () => {
      recargar();
      recargarBandeja();
    };
  });
  useEffect(() => {
    const proximo = Math.min(...tiendas.map((t) => new Date(t.soporteHasta).getTime()).filter((v) => v > ahora));
    if (!Number.isFinite(proximo)) return undefined;
    const reloj = setTimeout(() => {
      setAhora(Date.now());
      alVencer.current?.();
    }, Math.min(Math.max(proximo - Date.now(), 0) + 1000, MAX_ESPERA));
    return () => clearTimeout(reloj);
  }, [tiendas, ahora]);

  const vigentes = tiendas.filter((t) => new Date(t.soporteHasta).getTime() > ahora);

  // Primero las que esperan respuesta (la que escribió hace más tiempo arriba); después el resto.
  const ordenadas = [...vigentes].sort((a, b) => {
    const pa = porTienda[a.idTienda];
    const pb = porTienda[b.idTienda];
    const ea = pa?.sinResponder > 0 ? 0 : 1;
    const eb = pb?.sinResponder > 0 ? 0 : 1;
    if (ea !== eb) return ea - eb;
    if (ea === 0) return new Date(pa.ultimaFecha) - new Date(pb.ultimaFecha);
    return 0;
  });

  function entrar(idTienda) {
    cambiarTienda(idTienda);
    navigate('/panel');
  }

  return (
    <section className="max-w-5xl space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Soporte a tiendas</h1>
          <p className="text-[13px] text-[#616161]">
            Tiendas cuyo dueño te dio acceso para armarla. En «Mensajes» ves lo que pidió y le respondes. Lo que cambies queda registrado y el dueño lo ve.
          </p>
          {sinResponder > 0 && (
            <p className="mt-2 inline-flex items-center gap-2 rounded-lg bg-[#fff1ee] px-2.5 py-1 text-[13px] font-medium text-[#8e1f0b]">
              <span className="h-2 w-2 rounded-full bg-[#e51c00]" aria-hidden="true" />
              {sinResponder === 1 ? '1 mensaje espera tu respuesta' : `${sinResponder} mensajes esperan tu respuesta`}
            </p>
          )}
        </div>
      </div>

      {error && <Mensaje tipo="error" texto={error} />}

      {cargando ? (
        <p className="text-[#616161]">Cargando...</p>
      ) : vigentes.length === 0 ? (
        <div className={`${TARJETA} flex flex-col items-center px-6 py-10 text-center`}>
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
            <IconoSoporte tamano={24} />
          </span>
          <p className="mt-3 font-medium text-[#101010]">Ninguna tienda te ha dado acceso</p>
          <p className="mt-1 max-w-md text-[13px] text-[#616161]">
            Pídele al dueño que entre a su panel, abra «Soporte de Vendely» y toque «Dar acceso a soporte». Aparecerá aquí
            hasta que venza o lo quite.
          </p>
        </div>
      ) : (
        <div className={`${TARJETA} overflow-x-auto`}>
          <table className="w-full min-w-[640px] text-[13px]">
            <thead className={ENCABEZADO_TABLA}>
              <tr>
                <th className="px-4 py-2.5 font-medium">Tienda</th>
                <th className="px-4 py-2.5 font-medium">Plan</th>
                <th className="px-4 py-2.5 font-medium">Acceso hasta</th>
                <th className="px-4 py-2.5 text-right font-medium">Acción</th>
              </tr>
            </thead>
            <tbody>
              {ordenadas.map((t) => {
                const b = porTienda[t.idTienda];
                const pendiente = b?.sinResponder > 0;
                return (
                <Fragment key={t.idTienda}>
                <tr className={`border-t border-[#ebebeb] ${pendiente ? 'bg-[#fffaf8]' : ''}`}>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-2.5">
                      <AvatarTienda tienda={t} tamano={30} />
                      <span>
                        <span className="block font-medium text-[#101010]">{t.nombre}</span>
                        <span className="block text-[#616161]">/tienda/{t.subdominio} · {t.pais}</span>
                        {b?.ultimoTexto && (
                          <span className={`mt-1 block max-w-[22rem] truncate text-[12px] ${pendiente ? 'font-medium text-[#303030]' : 'text-[#8a8a8a]'}`}>
                            {b.ultimoDeSoporte ? 'Tú: ' : 'Dueño: '}{b.ultimoTexto}
                            <span className="font-normal text-[#8a8a8a]"> · {formatoFecha(b.ultimaFecha)}</span>
                          </span>
                        )}
                      </span>
                    </span>
                  </td>
                  <td className="px-4 py-3 text-[#303030]">{t.plan ?? 'Gratis'}</td>
                  <td className="px-4 py-3 text-[#303030]">{formatoFecha(t.soporteHasta)}</td>
                  <td className="px-4 py-3 text-right">
                    <span className="inline-flex gap-2">
                      <button
                        onClick={() => setAbierta(abierta === t.idTienda ? null : t.idTienda)}
                        className={`${BOTON_SECUNDARIO} text-[13px]`}
                      >
                        <span className="inline-flex items-center gap-1.5">
                          {abierta === t.idTienda ? 'Ocultar mensajes' : 'Mensajes'}
                          {pendiente && (
                            <span
                              className="flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#e51c00] px-1.5 text-[11px] font-semibold text-white"
                              aria-label={`${b.sinResponder} sin responder`}
                            >
                              {b.sinResponder > 9 ? '9+' : b.sinResponder}
                            </span>
                          )}
                        </span>
                      </button>
                      <button onClick={() => entrar(t.idTienda)} className={`${BOTON} text-[13px]`}>
                        Entrar a la tienda
                      </button>
                    </span>
                  </td>
                </tr>
                {abierta === t.idTienda && (
                  <tr className="bg-[#fafafa]">
                    <td colSpan={4} className="px-4 py-3">
                      <ConversacionSoporte idTienda={t.idTienda} soyDeSoporte compacto alCambiar={recargarBandeja} />
                    </td>
                  </tr>
                )}
                </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
