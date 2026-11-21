import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { IconoCheck } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { cancelarPagoPlan, confirmarPlan, historialPlan, pagarPlan, verMiPlan } from '../services/planes';
import { formatoFecha, formatoMoneda } from '../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA, etiqueta } from '../utils/panel';

const ESTADO_PAGO = { PAGADA: ['Pagado', 'exito'], PENDIENTE: ['Pendiente', 'aviso'], CANCELADA: ['Cancelado', 'neutro'] };

/** Lo que incluye cada plan, en palabras para el dueño. */
function beneficios(p) {
  return [
    p.maxProductos == null ? 'Productos ilimitados' : `Hasta ${p.maxProductos} productos`,
    `Comisión de Vendely: ${Number(p.comision)} % por venta`,
    p.permitePersonalizar ? 'Tu logo, tu color y barra de anuncio' : 'Colores de Vendely',
    p.permitePlantillas ? 'Elige plantilla: Clásica, Moderna o Boutique' : 'Plantilla Clásica',
    p.permiteMasMetodosPago ? 'PayPal y más métodos de pago' : 'Pago con PayPal',
    p.permiteEnvioInternacional ? 'Envíos internacionales' : 'Solo envíos dentro de tu país',
    p.permitePersonalizar ? 'Sin el sello "Hecho con Vendely"' : 'Sello "Hecho con Vendely" en el pie',
  ];
}

const usd = (monto) => formatoMoneda(monto, 'USD');

/**
 * Pantalla "Plan" del dueño (como "Plan" en la configuración de Shopify):
 * su plan actual, cuánto usa, cuándo vence, la comparación de los 3 planes,
 * el pago con PayPal y el historial con su comprobante.
 *
 * Al volver de PayPal la URL trae ?suscripcion=12 (y &cancelado=1 si canceló):
 * aquí se confirma con el backend, que verifica el pago EN PayPal antes de activar el plan.
 */
export default function Plan() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [datos, setDatos] = useState(null);
  const [historial, setHistorial] = useState([]);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState(null);           // { tipo, texto }
  const [pagando, setPagando] = useState(null);       // idPlan que se está pagando
  const [version, setVersion] = useState(0);         // sube para volver a cargar el plan
  const procesado = useRef(false);                    // evita confirmar dos veces el mismo regreso

  useEffect(() => {
    if (!idTienda) return undefined;
    let vigente = true;
    Promise.all([verMiPlan(idTienda), historialPlan(idTienda)])
      .then(([plan, pagos]) => {
        if (!vigente) return;
        setDatos(plan);
        setHistorial(pagos);
        setError('');
      })
      .catch((e) => vigente && setError(mensajeDeError(e)));
    return () => {
      vigente = false;
    };
  }, [idTienda, version]);

  // Regreso desde PayPal: confirmar o cancelar, y limpiar la URL.
  useEffect(() => {
    const idSuscripcion = params.get('suscripcion');
    if (!idTienda || !idSuscripcion || procesado.current) return;
    procesado.current = true;
    const cancelado = params.get('cancelado') === '1';
    (async () => {
      try {
        const s = cancelado
          ? await cancelarPagoPlan(idTienda, idSuscripcion)
          : await confirmarPlan(idTienda, idSuscripcion);
        const programado = new Date(s.fechaInicio).getTime() > Date.now() + 60000;
        if (s.estado === 'PAGADA' && programado) {
          setAviso({
            tipo: 'exito',
            texto: `¡Listo! Tu plan ${s.plan} quedó programado: empieza el ${formatoFecha(s.fechaInicio)} y dura hasta el ${formatoFecha(s.fechaFin)}`,
          });
        } else if (s.estado === 'PAGADA') {
          setAviso({ tipo: 'exito', texto: `¡Listo! Tu plan ${s.plan} está activo hasta el ${formatoFecha(s.fechaFin)}` });
        } else {
          setAviso({ tipo: 'info', texto: 'No se completó el pago. Tu plan no cambió.' });
        }
      } catch (e) {
        setAviso({ tipo: 'error', texto: mensajeDeError(e) });
      } finally {
        navigate('/panel/plan', { replace: true });
        setVersion((v) => v + 1);
      }
    })();
  }, [idTienda, params, navigate]);

  async function elegir(plan) {
    setPagando(plan.idPlan);
    setAviso(null);
    try {
      const { urlPago } = await pagarPlan(idTienda, plan.idPlan);
      window.location.assign(urlPago);                // se va a PayPal; vuelve a /panel/plan?suscripcion=...
    } catch (e) {
      setAviso({ tipo: 'error', texto: mensajeDeError(e) });
      setPagando(null);
    }
  }

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;

  const actual = datos?.plan;
  const usados = datos?.productosUsados ?? 0;
  // Planes ya pagados que empiezan cuando termina el actual (renovación o cambio a un plan menor).
  const finActual = datos?.venceEl ? new Date(datos.venceEl).getTime() : null;
  const programados = historial
    .filter((s) => finActual && s.estado === 'PAGADA' && new Date(s.fechaInicio).getTime() >= finActual)
    .sort((a, b) => new Date(a.fechaInicio) - new Date(b.fechaInicio));
  const finTotal = programados.length ? programados[programados.length - 1].fechaFin : datos?.venceEl;
  const limite = actual?.maxProductos;
  const porcentaje = limite ? Math.min(100, Math.round((usados / limite) * 100)) : 0;

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Plan</h1>
          <p className="text-[13px] text-[#616161]">Elige el plan de Vendely que necesita tu tienda. Se paga con PayPal y dura 30 días.</p>
        </div>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {aviso && <Mensaje tipo={aviso.tipo} texto={aviso.texto} onCerrar={() => setAviso(null)} />}
      {error && <Mensaje tipo="error" texto={error} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}
      {tienda && !datos && !error && <p className="text-[13px] text-[#616161]">Cargando plan...</p>}

      {tienda && actual && (
        <>
          {/* Plan actual y uso */}
          <div className={`${TARJETA} grid gap-4 p-5 sm:grid-cols-3`}>
            <div>
              <p className="text-[12px] text-[#616161]">Plan actual</p>
              <p className="text-2xl font-bold text-[#101010]">{actual.nombre}</p>
              <p className="text-[13px] text-[#616161]">
                {Number(actual.precioUsd) === 0 ? 'Gratis para siempre' : `${usd(actual.precioUsd)} al mes`}
              </p>
            </div>
            <div>
              <p className="text-[12px] text-[#616161]">Productos</p>
              <p className="text-lg font-semibold text-[#101010]">
                {usados} {limite ? `de ${limite}` : '(ilimitados)'}
              </p>
              {limite && (
                <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-[#ebebeb]">
                  <div
                    className={`h-full rounded-full ${porcentaje >= 100 ? 'bg-[#e51c00]' : porcentaje >= 80 ? 'bg-[#ffb800]' : 'bg-[#29845a]'}`}
                    style={{ width: `${porcentaje}%` }}
                  />
                </div>
              )}
            </div>
            <div>
              <p className="text-[12px] text-[#616161]">Vence</p>
              <p className="text-lg font-semibold text-[#101010]">{datos.venceEl ? formatoFecha(datos.venceEl) : 'No vence'}</p>
              <p className="text-[12px] text-[#616161]">
                {!datos.venceEl && `Comisión por venta: ${Number(actual.comision)} %`}
                {datos.venceEl && programados.length === 0 && 'Si no renuevas, vuelve al plan Gratis sin borrar nada.'}
              </p>
              {programados.map((s) => (
                <p key={s.idSuscripcion} className="mt-1 text-[12px] text-[#00527c]">
                  Después: <b>{s.plan}</b> del {formatoFecha(s.fechaInicio)} al {formatoFecha(s.fechaFin)}
                </p>
              ))}
            </div>
          </div>

          {/* Comparación de planes */}
          <div className="grid gap-4 md:grid-cols-3">
            {datos.planes.map((p) => {
              const esActual = p.idPlan === actual.idPlan;
              const gratis = Number(p.precioUsd) === 0;
              const op = datos.opciones?.find((o) => o.idPlan === p.idPlan);
              return (
                <div key={p.idPlan} className={`${TARJETA} flex flex-col p-5 ${esActual ? 'ring-2 ring-[#1650d0]' : ''}`}>
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-[#101010]">{p.nombre}</h2>
                    {esActual && <span className={etiqueta('info')}>Tu plan</span>}
                  </div>
                  <p className="mt-1 text-2xl font-bold text-[#101010]">
                    {gratis ? 'US$ 0' : usd(p.precioUsd)}
                    <span className="text-[13px] font-normal text-[#616161]"> /mes</span>
                  </p>
                  <ul className="mt-4 flex-1 space-y-2 text-[13px] text-[#303030]">
                    {beneficios(p).map((b) => (
                      <li key={b} className="flex gap-2">
                        <span className="mt-0.5 text-[#29845a]"><IconoCheck tamano={15} /></span>
                        {b}
                      </li>
                    ))}
                  </ul>
                  {!gratis && op && (
                    <>
                      <p className="mt-5 rounded-lg bg-[#f7f7f7] px-3 py-2 text-[12px] text-[#616161]">
                        {op.tipo === 'SUBIR' && Number(op.creditoUsd) > 0 && (
                          <>Pagas <b className="text-[#101010]">{usd(op.montoUsd)}</b>: {usd(op.precioUsd)} menos {usd(op.creditoUsd)} de crédito por los días que no usaste. Empieza hoy.</>
                        )}
                        {op.tipo === 'SUBIR' && Number(op.creditoUsd) === 0 && (
                          <>Pagas <b className="text-[#101010]">{usd(op.montoUsd)}</b>. Empieza hoy.</>
                        )}
                        {op.tipo === 'BAJAR' && (
                          <>Pagas <b className="text-[#101010]">{usd(op.montoUsd)}</b>. Empieza el {formatoFecha(op.empiezaEl)}, cuando termina lo que ya pagaste.</>
                        )}
                        {op.tipo === 'RENOVAR' && (
                          <>Pagas <b className="text-[#101010]">{usd(op.montoUsd)}</b>. Los 30 días se suman desde el {formatoFecha(op.empiezaEl)}</>
                        )}
                      </p>
                      <button
                        onClick={() => elegir(p)}
                        disabled={pagando !== null}
                        className={`mt-2 w-full ${op.tipo === 'SUBIR' ? BOTON : BOTON_SECUNDARIO}`}
                      >
                        {pagando === p.idPlan
                          ? 'Abriendo PayPal...'
                          : op.tipo === 'RENOVAR' ? 'Renovar 30 días'
                            : op.tipo === 'SUBIR' ? `Mejorar a ${p.nombre}` : `Cambiar a ${p.nombre}`}
                      </button>
                    </>
                  )}
                  {gratis && !esActual && (
                    <p className="mt-5 text-center text-[12px] text-[#616161]">Vuelves a Gratis cuando vence tu plan.</p>
                  )}
                </div>
              );
            })}
          </div>
          <p className="text-[12px] text-[#616161]">
            Pago de prueba con PayPal Sandbox, en dólares. Mejorar tu plan es inmediato y descuenta los días que no usaste; cambiar a un plan menor o renovar empieza cuando termina lo que ya pagaste, así nunca pierdes días pagados.
            {finTotal && ` Tienes plan pagado hasta el ${formatoFecha(finTotal)}`}
          </p>

          {/* Historial de pagos */}
          <div className={`${TARJETA} overflow-hidden`}>
            <div className="border-b border-[#ebebeb] px-4 py-3">
              <h2 className="font-semibold text-[#101010]">Historial de pagos</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-full text-[13px]">
                <thead className={ENCABEZADO_TABLA}>
                  <tr>
                    <th className="px-4 py-2.5 font-medium">Comprobante</th>
                    <th className="px-4 py-2.5 font-medium">Fecha</th>
                    <th className="px-4 py-2.5 font-medium">Plan</th>
                    <th className="px-4 py-2.5 font-medium">Periodo</th>
                    <th className="px-4 py-2.5 text-right font-medium">Monto</th>
                    <th className="px-4 py-2.5 font-medium">Estado</th>
                    <th className="px-4 py-2.5" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#ebebeb]">
                  {historial.length === 0 && (
                    <tr><td colSpan={7} className="px-4 py-8 text-center text-[#616161]">Todavía no has pagado ningún plan.</td></tr>
                  )}
                  {historial.map((s) => {
                    const [texto, tono] = ESTADO_PAGO[s.estado] ?? ESTADO_PAGO.PENDIENTE;
                    return (
                      <tr key={s.idSuscripcion} className="hover:bg-[#f7f7f7]">
                        <td className="px-4 py-2.5 font-medium text-[#101010]">{s.numeroComprobante}</td>
                        <td className="whitespace-nowrap px-4 py-2.5 text-[#616161]">{formatoFecha(s.fechaCreacion)}</td>
                        <td className="px-4 py-2.5">{s.plan}</td>
                        <td className="whitespace-nowrap px-4 py-2.5 text-[#616161]">
                          {s.estado === 'PAGADA' ? `${formatoFecha(s.fechaInicio)} – ${formatoFecha(s.fechaFin)}` : '-'}
                        </td>
                        <td className="px-4 py-2.5 text-right">{usd(s.montoUsd)}</td>
                        <td className="px-4 py-2.5"><span className={etiqueta(tono)}>{texto}</span></td>
                        <td className="px-4 py-2.5 text-right">
                          {s.estado === 'PAGADA' && (
                            <button onClick={() => imprimirComprobante(s, tienda)} className="rounded-lg px-2 py-1 font-medium hover:bg-[#f1f1f1]">
                              Comprobante
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

/** Texto seguro para meter en el HTML del comprobante. */
function html(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

/**
 * Abre el comprobante en una pestaña nueva lista para imprimir o "Guardar como PDF".
 * Es un comprobante interno de Vendely, no una boleta electrónica de SUNAT.
 */
function imprimirComprobante(s, tienda) {
  const ventana = window.open('', '_blank');
  if (!ventana) return;
  ventana.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>Comprobante ${html(s.numeroComprobante)}</title>
<style>
  body { font-family: Arial, sans-serif; color: #222; max-width: 640px; margin: 40px auto; padding: 0 20px; }
  h1 { margin: 0; font-size: 26px; } .gris { color: #666; font-size: 13px; }
  table { width: 100%; border-collapse: collapse; margin-top: 24px; font-size: 14px; }
  td { padding: 8px 0; border-bottom: 1px solid #eee; } td:last-child { text-align: right; }
  .total td { font-weight: bold; font-size: 16px; border-bottom: none; }
  .nota { margin-top: 32px; padding: 10px; background: #f5f5f5; font-size: 12px; color: #555; }
  @media print { button { display: none; } }
</style></head><body>
  <div style="display:flex;justify-content:space-between;align-items:flex-start">
    <div><h1>Vendely</h1><p class="gris">Plataforma de tiendas online</p></div>
    <div style="text-align:right"><p><b>Comprobante ${html(s.numeroComprobante)}</b></p>
      <p class="gris">${html(new Date(s.fechaCreacion).toLocaleString('es-PE'))}</p></div>
  </div>
  <p style="margin-top:24px"><b>Cliente:</b> ${html(tienda?.nombre)} (${html(tienda?.subdominio)}.vendely.pe)<br>
     <b>Correo:</b> ${html(tienda?.correoContacto)}</p>
  <table>
    <tr><td>Plan ${html(s.plan)} de Vendely, 30 días</td><td>US$ ${html(Number(s.precioPlanUsd ?? s.montoUsd).toFixed(2))}</td></tr>
    ${Number(s.precioPlanUsd) > Number(s.montoUsd)
      ? `<tr><td>Crédito por días no usados del plan anterior</td><td>- US$ ${html((Number(s.precioPlanUsd) - Number(s.montoUsd)).toFixed(2))}</td></tr>`
      : ''}
    <tr><td>Periodo</td><td>${html(new Date(s.fechaInicio).toLocaleDateString('es-PE'))} al ${html(new Date(s.fechaFin).toLocaleDateString('es-PE'))}</td></tr>
    <tr><td>Método de pago</td><td>PayPal (orden ${html(s.referenciaPago)})</td></tr>
    <tr class="total"><td>Total pagado</td><td>US$ ${html(Number(s.montoUsd).toFixed(2))}</td></tr>
  </table>
  <p class="nota">Comprobante interno, no válido como comprobante SUNAT. Pago de prueba realizado en PayPal Sandbox.</p>
  <button onclick="window.print()" style="margin-top:16px;padding:8px 16px">Imprimir o guardar como PDF</button>
</body></html>`);
  ventana.document.close();
}
