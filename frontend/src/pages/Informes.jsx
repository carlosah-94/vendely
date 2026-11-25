import { useEffect, useState } from 'react';
import GraficoVentas from '../components/GraficoVentas';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { mensajeDeError } from '../services/api';
import { obtenerInforme } from '../services/informes';
import { formatoMoneda } from '../utils/formato';
import { BOTON_SECUNDARIO, TARJETA, inputPanel } from '../utils/panel';

const SERIE = '#2a78d6';

const METODOS = { PAYPAL: 'PayPal', MERCADO_PAGO: 'Mercado Pago', OTRO: 'Otro' };
const ESTADOS = { SIN_ENVIAR: 'Sin enviar', ENVIADO: 'Enviado', ENTREGADO: 'Entregado', CANCELADO: 'Cancelado' };

/** Fecha local en formato "2026-11-24" (la del navegador, no la UTC). */
function isoLocal(fecha) {
  const dos = (n) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`;
}

function haceDias(n) {
  const f = new Date();
  f.setDate(f.getDate() - n);
  return isoLocal(f);
}

const PERIODOS = [
  { clave: '7', texto: 'Últimos 7 días', rango: () => [haceDias(6), haceDias(0)] },
  { clave: '30', texto: 'Últimos 30 días', rango: () => [haceDias(29), haceDias(0)] },
  { clave: '90', texto: 'Últimos 90 días', rango: () => [haceDias(89), haceDias(0)] },
  {
    clave: 'mes', texto: 'Este mes',
    rango: () => {
      const hoy = new Date();
      return [isoLocal(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), isoLocal(hoy)];
    },
  },
];

/** "+12 %" frente al periodo anterior; null si antes no hubo nada con qué comparar. */
function variacion(actual, anterior) {
  const a = Number(actual);
  const b = Number(anterior);
  if (!b) return null;
  return Math.round(((a - b) / b) * 100);
}

/** Variación de la ganancia: solo tiene sentido si el periodo anterior también fue ganancia (> 0). */
function variacionGanancia(actual, anterior) {
  return Number(anterior) > 0 ? variacion(actual, anterior) : null;
}

/**
 * Informes estadísticos de la tienda (solo el dueño): ventas, pedidos, ticket promedio y
 * descuentos frente al periodo anterior, ventas por día, productos más vendidos, métodos de pago,
 * clientes nuevos o recurrentes y pedidos por estado. Solo cuentan los pedidos con el pago aprobado.
 * También las finanzas: costo de lo vendido, ganancia (sin IGV) y margen, y cuánta plata hay en el almacén.
 */
export default function Informes() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [periodo, setPeriodo] = useState('30');
  const [rango, setRango] = useState(() => PERIODOS[1].rango());
  const [datos, setDatos] = useState({ clave: null, informe: null, error: '' });

  const clave = `${idTienda}|${rango[0]}|${rango[1]}`;
  useEffect(() => {
    if (!idTienda || !rango[0] || !rango[1] || rango[0] > rango[1]) return undefined;
    let vigente = true;
    obtenerInforme(idTienda, rango[0], rango[1])
      .then((informe) => vigente && setDatos({ clave, informe, error: '' }))
      .catch((e) => vigente && setDatos({ clave, informe: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idTienda, rango, clave]);

  function elegirPeriodo(c) {
    setPeriodo(c);
    const p = PERIODOS.find((x) => x.clave === c);
    if (p) setRango(p.rango());
  }

  if (errorTiendas) return <Mensaje tipo="error" texto={errorTiendas} />;
  const moneda = tienda?.moneda ?? 'PEN';
  const cargando = cargandoTiendas || datos.clave !== clave;
  const inf = datos.clave === clave ? datos.informe : null;
  const rangoInvalido = rango[0] && rango[1] && rango[0] > rango[1];

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Informes</h1>
          <p className="text-[13px] text-[#616161]">Cómo le va a tu tienda. Solo cuentan los pedidos pagados.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
          {inf && (
            <button type="button" onClick={() => exportarCsv(inf, tienda)} className={BOTON_SECUNDARIO}>
              Exportar CSV
            </button>
          )}
        </div>
      </div>

      {/* Periodo: una fila de controles arriba de todos los gráficos */}
      <div className="flex flex-wrap items-end gap-2 text-[13px]">
        {PERIODOS.map((p) => (
          <button
            key={p.clave}
            type="button"
            onClick={() => elegirPeriodo(p.clave)}
            aria-pressed={periodo === p.clave}
            className={`rounded-lg px-3 py-1.5 font-medium ${
              periodo === p.clave ? 'bg-[#303030] text-white' : 'border border-[#ddd] bg-white text-[#303030] hover:bg-[#f7f7f7]'
            }`}
          >
            {p.texto}
          </button>
        ))}
        <label className="flex items-center gap-1.5">
          <span className="text-[#616161]">Desde</span>
          <input
            type="date"
            value={rango[0]}
            max={rango[1]}
            onChange={(e) => { setPeriodo('otro'); setRango(([, h]) => [e.target.value, h]); }}
            className={`${inputPanel()} w-auto py-1`}
          />
        </label>
        <label className="flex items-center gap-1.5">
          <span className="text-[#616161]">Hasta</span>
          <input
            type="date"
            value={rango[1]}
            min={rango[0]}
            onChange={(e) => { setPeriodo('otro'); setRango(([d]) => [d, e.target.value]); }}
            className={`${inputPanel()} w-auto py-1`}
          />
        </label>
      </div>

      {rangoInvalido && <Mensaje tipo="error" texto="La fecha de inicio debe ser anterior a la de fin." />}
      {datos.error && datos.clave === clave && <Mensaje tipo="error" texto={datos.error} />}
      {!cargandoTiendas && !tienda && <Mensaje tipo="info" texto="Primero crea una tienda." />}
      {tienda && cargando && !rangoInvalido && <p className="text-[13px] text-[#616161]">Cargando informe...</p>}

      {inf && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
            <Indicador titulo="Ventas" valor={formatoMoneda(inf.actual.ventas, moneda)} cambio={variacion(inf.actual.ventas, inf.anterior.ventas)} />
            <Indicador titulo="Pedidos" valor={inf.actual.pedidos} cambio={variacion(inf.actual.pedidos, inf.anterior.pedidos)} />
            <Indicador titulo="Ticket promedio" valor={formatoMoneda(inf.actual.ticketPromedio, moneda)} cambio={variacion(inf.actual.ticketPromedio, inf.anterior.ticketPromedio)} />
            <Indicador titulo="Unidades vendidas" valor={inf.actual.unidades} cambio={variacion(inf.actual.unidades, inf.anterior.unidades)} />
            <Indicador titulo="Descuentos dados" valor={formatoMoneda(inf.actual.descuentos, moneda)} cambio={variacion(inf.actual.descuentos, inf.anterior.descuentos)} neutro />
          </div>

          <Ganancias inf={inf} moneda={moneda} impuesto={tienda?.impuesto ?? 'IGV'} />

          <div className={`${TARJETA} p-4`}>
            <h2 className="font-semibold text-[#101010]">Ventas y ganancia por día</h2>
            <p className="mb-2 text-xs text-[#616161]">
              Ventas: total de los pedidos pagados (productos + envío). Ganancia: lo que queda después del costo y
              sin {tienda?.impuesto ?? 'IGV'}, de los pedidos con costo cargado.
            </p>
            {inf.actual.pedidos === 0
              ? <p className="py-10 text-center text-[13px] text-[#616161]">No hubo ventas pagadas en este periodo.</p>
              : <GraficoVentas dias={inf.porDia} moneda={moneda} />}
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <div className={`${TARJETA} p-4`}>
              <h2 className="mb-3 font-semibold text-[#101010]">Productos más vendidos</h2>
              <Barras
                filas={inf.productos.map((p) => ({
                  clave: p.idProducto, nombre: p.nombre, valor: Number(p.ingresos),
                  texto: formatoMoneda(p.ingresos, moneda),
                  detalle: `${p.unidades} u. · ${p.ganancia == null ? 'sin costo' : `gana ${formatoMoneda(p.ganancia, moneda)}`}`,
                }))}
                vacio="Todavía no hay productos vendidos en este periodo."
              />
            </div>
            <div className={`${TARJETA} p-4`}>
              <h2 className="mb-3 font-semibold text-[#101010]">Métodos de pago</h2>
              <Barras
                filas={inf.metodosPago.map((m) => ({
                  clave: m.metodo, nombre: METODOS[m.metodo] ?? m.metodo, valor: Number(m.monto),
                  texto: formatoMoneda(m.monto, moneda), detalle: `${m.pedidos} pedido${m.pedidos === 1 ? '' : 's'}`,
                }))}
                vacio="Sin pagos en este periodo."
              />
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <div className={`${TARJETA} p-4`}>
              <h2 className="mb-3 font-semibold text-[#101010]">Clientes</h2>
              <div className="grid grid-cols-2 gap-3">
                <Cifra numero={inf.clientes.nuevos} texto="Nuevos" ayuda="Su primera compra fue en este periodo" />
                <Cifra numero={inf.clientes.recurrentes} texto="Recurrentes" ayuda="Ya habían comprado antes" />
              </div>
            </div>
            <div className={`${TARJETA} p-4`}>
              <h2 className="mb-3 font-semibold text-[#101010]">Pedidos pagados por estado</h2>
              {inf.estados.length === 0 ? (
                <p className="text-[13px] text-[#616161]">Sin pedidos pagados en este periodo.</p>
              ) : (
                <ul className="divide-y divide-[#ebebeb] text-[13px]">
                  {inf.estados.map((e) => (
                    <li key={e.estado} className="flex justify-between py-2">
                      <span>{ESTADOS[e.estado] ?? e.estado}</span>
                      <span className="font-semibold">{e.pedidos}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  );
}

/**
 * Las finanzas del periodo. La ganancia se calcula así, y se explica en la pantalla para que no sea una caja negra:
 *   ingresos por productos, sin el impuesto (que es del Estado) y sin el envío (que es del courier)
 *   - lo que costaron esas unidades = ganancia.
 * Solo entran los pedidos cuyos productos tienen costo; si hay pedidos sin costo se avisa cuántos.
 */
function Ganancias({ inf, moneda, impuesto }) {
  const g = inf.actual.ganancia;
  const ga = inf.anterior.ganancia;
  const total = g.pedidosConCosto + g.pedidosSinCosto;
  const inv = inf.inventario;
  return (
    <>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Indicador titulo="Ganancia" valor={formatoMoneda(g.ganancia, moneda)} cambio={variacionGanancia(g.ganancia, ga.ganancia)} destacado />
        <Indicador titulo="Margen" valor={`${Number(g.margen).toFixed(1)} %`} cambio={null} sinCambio="Lo que queda de cada 100 de ingresos" />
        <Indicador titulo="Ingresos sin impuesto" valor={formatoMoneda(g.ingresosNetos, moneda)} cambio={variacionGanancia(g.ingresosNetos, ga.ingresosNetos)} />
        <Indicador titulo="Costo de lo vendido" valor={formatoMoneda(g.costo, moneda)} cambio={variacion(g.costo, ga.costo)} neutro />
        <Indicador titulo={`${impuesto} cobrado`} valor={formatoMoneda(inf.actual.impuestos, moneda)} cambio={variacion(inf.actual.impuestos, inf.anterior.impuestos)} neutro sinCambio="Va para el Estado, no es ganancia" />
      </div>

      {g.pedidosSinCosto > 0 && (
        <Mensaje
          tipo="info"
          texto={`${g.pedidosSinCosto} de ${total} pedidos pagados tienen productos sin costo y no cuentan en la ganancia. Agrega el costo en Productos para que se calcule completa.`}
        />
      )}

      <div className={`${TARJETA} p-4`}>
        <h2 className="font-semibold text-[#101010]">Cómo se calcula tu ganancia</h2>
        <p className="mt-1 text-[13px] text-[#303030]">
          Ingresos por productos sin {impuesto}{' '}
          <strong>{formatoMoneda(g.ingresosNetos, moneda)}</strong> − costo de lo vendido{' '}
          <strong>{formatoMoneda(g.costo, moneda)}</strong> = ganancia{' '}
          <strong>{formatoMoneda(g.ganancia, moneda)}</strong> ({Number(g.margen).toFixed(1)} %).
        </p>
        <p className="mt-1 text-xs text-[#616161]">
          El {impuesto} está incluido en los precios pero es del Estado, por eso se resta. El envío tampoco es tuyo: lo
          cobras para pagarle al courier. Las ofertas y los códigos de descuento ya están descontados. Los pedidos
          reembolsados o cancelados después de cobrar no cuentan.
        </p>
      </div>

      <div className={`${TARJETA} p-4`}>
        <h2 className="mb-1 font-semibold text-[#101010]">Tu inventario hoy</h2>
        <p className="mb-3 text-xs text-[#616161]">
          La plata que ya pusiste en mercadería y lo que ganarías si la vendes toda al precio de hoy (sin {impuesto}).
        </p>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Cifra numero={inv.unidades} texto="Unidades en stock" ayuda="Productos activos y borradores" />
          <Cifra numero={formatoMoneda(inv.capitalInvertido, moneda)} texto="Dinero invertido" ayuda="Stock × costo de cada producto" />
          <Cifra numero={formatoMoneda(inv.valorDeVenta, moneda)} texto="Valor de venta" ayuda={`Stock × precio, con ${impuesto}`} />
          <Cifra numero={formatoMoneda(inv.gananciaPotencial, moneda)} texto="Ganancia potencial" ayuda={`Si vendes todo, sin ${impuesto}`} />
        </div>
        {inv.productosSinCosto > 0 && (
          <p className="mt-3 text-xs text-[#8a5a00]">
            {inv.productosSinCosto === 1 ? '1 producto no tiene' : `${inv.productosSinCosto} productos no tienen`} costo y no entra
            en estas cifras.
          </p>
        )}
      </div>
    </>
  );
}

/** Un número grande con su cambio frente al periodo anterior (flecha + texto, no solo color). */
function Indicador({ titulo, valor, cambio, neutro = false, destacado = false, sinCambio = 'Sin datos del periodo anterior' }) {
  const sube = cambio > 0;
  const color = neutro || cambio === 0 ? 'text-[#616161]' : sube ? 'text-[#0c5132]' : 'text-[#8e1f0b]';
  return (
    <div className={`${TARJETA} p-4 ${destacado ? 'ring-2 ring-[#0c5132]/30' : ''}`}>
      <p className="text-[13px] text-[#616161]">{titulo}</p>
      <p className="mt-1 text-2xl font-bold text-[#101010]">{valor}</p>
      <p className={`mt-1 text-xs ${cambio == null ? 'text-[#8a8a8a]' : color}`}>
        {cambio == null ? sinCambio : `${sube ? '▲' : cambio < 0 ? '▼' : '='} ${Math.abs(cambio)} % vs. periodo anterior`}
      </p>
    </div>
  );
}

function Cifra({ numero, texto, ayuda }) {
  return (
    <div className="rounded-lg bg-[#f7f7f7] p-3">
      <p className="text-2xl font-bold text-[#101010]">{numero}</p>
      <p className="text-[13px] font-medium">{texto}</p>
      <p className="text-xs text-[#616161]">{ayuda}</p>
    </div>
  );
}

/** Barras horizontales de una sola serie; el nombre y el valor van en texto (no dependen del color). */
function Barras({ filas, vacio }) {
  if (filas.length === 0) return <p className="text-[13px] text-[#616161]">{vacio}</p>;
  const maximo = Math.max(...filas.map((f) => f.valor), 1);
  return (
    <ul className="space-y-2.5 text-[13px]">
      {filas.map((f) => (
        <li key={f.clave} title={`${f.nombre}: ${f.texto} · ${f.detalle}`}>
          <div className="flex justify-between gap-3">
            <span className="truncate">{f.nombre}</span>
            <span className="whitespace-nowrap font-semibold">
              {f.texto} <span className="font-normal text-[#616161]">· {f.detalle}</span>
            </span>
          </div>
          <div className="mt-1 h-2 rounded-full bg-[#f1f1f1]">
            <div className="h-2 rounded-full" style={{ width: `${Math.max(2, (f.valor / maximo) * 100)}%`, background: SERIE }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** Descarga el informe en CSV (se abre en Excel): resumen, ventas por día y productos. */
function exportarCsv(inf, tienda) {
  const celda = (v) => {
    const t = String(v ?? '');
    return /[",;\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const filas = [
    ['Informe de ventas', tienda?.nombre ?? ''],
    ['Desde', inf.desde, 'Hasta', inf.hasta],
    [],
    ['Ventas', inf.actual.ventas],
    ['Pedidos', inf.actual.pedidos],
    ['Ticket promedio', inf.actual.ticketPromedio],
    ['Unidades vendidas', inf.actual.unidades],
    ['Descuentos dados', inf.actual.descuentos],
    ['Impuesto (IGV) cobrado', inf.actual.impuestos],
    ['Ingresos sin impuesto (pedidos con costo)', inf.actual.ganancia.ingresosNetos],
    ['Costo de lo vendido', inf.actual.ganancia.costo],
    ['Ganancia', inf.actual.ganancia.ganancia],
    ['Margen %', inf.actual.ganancia.margen],
    ['Pedidos sin costo (no cuentan en la ganancia)', inf.actual.ganancia.pedidosSinCosto],
    ['Dinero invertido en stock', inf.inventario.capitalInvertido],
    ['Ganancia potencial del stock', inf.inventario.gananciaPotencial],
    ['Clientes nuevos', inf.clientes.nuevos],
    ['Clientes recurrentes', inf.clientes.recurrentes],
    [],
    ['Día', 'Pedidos', 'Ventas', 'Ganancia'],
    ...inf.porDia.map((d) => [d.fecha, d.pedidos, d.ventas, d.ganancia]),
    [],
    ['Producto', 'Unidades', 'Ingresos', 'Ganancia'],
    ...inf.productos.map((p) => [p.nombre, p.unidades, p.ingresos, p.ganancia ?? 'sin costo']),
  ];
  // BOM para que Excel lea bien las tildes.
  const texto = '﻿' + filas.map((f) => f.map(celda).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([texto], { type: 'text/csv;charset=utf-8' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = `informe-${tienda?.subdominio ?? 'tienda'}-${inf.desde}-a-${inf.hasta}.csv`;
  enlace.click();
  URL.revokeObjectURL(url);
}
