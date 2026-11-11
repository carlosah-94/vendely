import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import { IconoCheck, IconoPedidos } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha, formatoMoneda } from '../utils/formato';

/** Inicio del panel: cuatro números y los últimos 5 pedidos, con las listas que ya existen. */
export default function InicioPanel() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [datos, setDatos] = useState({ idTienda: null, pedidos: [], productos: [], clientes: [], inventario: [], error: '' });

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    const base = `/panel/tiendas/${idTienda}`;
    Promise.all([
      api.get(`${base}/pedidos`), api.get(`${base}/productos`), api.get(`${base}/clientes`), api.get(`${base}/inventario`),
    ])
      .then(([pedidos, productos, clientes, inventario]) => {
        if (vigente) {
          setDatos({
            idTienda, pedidos: pedidos.data, productos: productos.data, clientes: clientes.data,
            inventario: inventario.data, error: '',
          });
        }
      })
      .catch((e) => {
        if (vigente) setDatos({ idTienda, pedidos: [], productos: [], clientes: [], inventario: [], error: mensajeDeError(e) });
      });
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    return <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para empezar a vender." />;
  }

  const cargando = cargandoTiendas || datos.idTienda !== idTienda;
  const hoy = new Date().toDateString();
  const ventasHoy = datos.pedidos
    .filter((p) => new Date(p.fechaCreacion).toDateString() === hoy && p.estadoPago === 'APROBADO')
    .reduce((suma, p) => suma + Number(p.total), 0);
  const activos = datos.productos.filter((p) => p.estado === 'ACTIVO').length;
  // Variantes de productos activos que se están acabando (Inventario → Stock bajo / Agotados).
  const porReponer = datos.inventario.filter((f) => f.productoActivo && f.estado !== 'OK');

  // Guía de primeros pasos (como "Configuración" en Shopify): cada paso se marca solo,
  // según lo que ya existe en la base de datos.
  const pasos = [
    { texto: 'Crear tu tienda', hecho: true, ruta: '/panel/tiendas', accion: 'Ver tiendas' },
    { texto: 'Agregar tu primer producto', hecho: datos.productos.length > 0, ruta: '/panel/productos', accion: 'Agregar producto' },
    { texto: 'Activar un producto para que se vea en la tienda', hecho: activos > 0, ruta: '/panel/productos', accion: 'Ir a productos' },
    { texto: 'Registrar tu primer cliente', hecho: datos.clientes.length > 0, ruta: '/panel/clientes', accion: 'Agregar cliente' },
    {
      texto: 'Recibir tu primer pedido pagado',
      hecho: datos.pedidos.some((p) => p.estadoPago === 'APROBADO'),
      ruta: '/panel/ventas',
      accion: 'Ver pedidos',
    },
  ];
  const completados = pasos.filter((p) => p.hecho).length;

  const tarjetas = [
    ['Ventas de hoy (pagadas)', formatoMoneda(ventasHoy)],
    ['Pedidos', datos.pedidos.length],
    ['Productos activos', activos],
    ['Clientes', datos.clientes.length],
  ];

  return (
    <section className="max-w-5xl space-y-4">
      <div>
        <h1 className="text-xl font-bold text-[#101010]">Inicio</h1>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {!cargando && completados < pasos.length && (
        <div className="rounded-xl bg-white shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
          <div className="border-b border-[#ebebeb] px-4 py-3">
            <h2 className="font-semibold text-[#101010]">Primeros pasos</h2>
            <p className="text-[13px] text-[#616161]">Completa estas tareas para empezar a vender{tienda ? ` con ${tienda.nombre}` : ''}.</p>
            <div className="mt-3 flex items-center gap-3">
              <span className="text-[12px] text-[#616161]">{completados} de {pasos.length} tareas completadas</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#ebebeb]">
                <div className="h-full rounded-full bg-[#303030]" style={{ width: `${(completados / pasos.length) * 100}%` }} />
              </div>
            </div>
          </div>
          <ul className="divide-y divide-[#f1f1f1]">
            {pasos.map((p) => (
              <li key={p.texto} className="flex items-center gap-3 px-4 py-2.5">
                {p.hecho ? (
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#303030] text-white">
                    <IconoCheck tamano={12} />
                  </span>
                ) : (
                  <span className="h-5 w-5 rounded-full border-2 border-dashed border-[#b5b5b5]" />
                )}
                <span className={`flex-1 ${p.hecho ? 'text-[#8a8a8a] line-through' : 'text-[#303030]'}`}>{p.texto}</span>
                {!p.hecho && (
                  <Link to={p.ruta} className="rounded-lg border border-[#ddd] px-2.5 py-1 text-[13px] font-medium hover:bg-[#f7f7f7]">
                    {p.accion}
                  </Link>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {!cargando && porReponer.length > 0 && (
        <Link
          to="/panel/productos/inventario"
          className="flex items-center justify-between gap-3 rounded-xl border border-[#ffd79d] bg-[#fff5c5] px-4 py-3 text-[13px] text-[#4f4700] hover:brightness-[0.98]"
        >
          <span>
            <b>{porReponer.length} {porReponer.length === 1 ? 'producto necesita' : 'productos necesitan'} reposición</b>
            {' · '}{porReponer.slice(0, 3).map((f) => f.producto).join(', ')}{porReponer.length > 3 ? '...' : ''}
          </span>
          <span className="shrink-0 font-medium underline">Ver inventario</span>
        </Link>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {tarjetas.map(([titulo, valor]) => (
          <div key={titulo} className="rounded-xl bg-white p-4 shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
            <p className="text-xs text-[#616161]">{titulo}</p>
            <p className="mt-1 text-2xl font-semibold">{cargando ? '…' : valor}</p>
          </div>
        ))}
      </div>

      <div className="overflow-hidden rounded-xl bg-white shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
        <div className="flex items-center justify-between border-b border-[#e3e3e3] px-4 py-3">
          <h2 className="font-semibold">Últimos pedidos</h2>
          <Link to="/panel/ventas" className="text-[13px] text-[#1650d0] hover:underline">Ver todos</Link>
        </div>
        {cargando ? (
          <p className="p-4 text-[#616161]">Cargando...</p>
        ) : datos.pedidos.length === 0 ? (
          <div className="flex flex-col items-center px-6 py-10 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
              <IconoPedidos tamano={24} />
            </span>
            <p className="mt-3 font-medium text-[#101010]">Aún no hay pedidos</p>
            <p className="mt-1 max-w-xs text-[13px] text-[#616161]">
              Cuando un cliente compre en tu tienda o registres una venta, aparecerá aquí.
            </p>
          </div>
        ) : (
          <table className="w-full text-left">
            <tbody>
              {datos.pedidos.slice(0, 5).map((p) => (
                <tr key={p.idPedido} className="border-b border-[#efefef] last:border-0">
                  <td className="px-4 py-2.5 font-medium">
                    <Link to={`/panel/ventas/${p.idPedido}`} className="hover:underline">#{p.numeroPedido}</Link>
                  </td>
                  <td className="px-4 py-2.5 text-[#616161]">{formatoFecha(p.fechaCreacion)}</td>
                  <td className="px-4 py-2.5">{p.cliente}</td>
                  <td className="px-4 py-2.5"><EtiquetaPago estado={p.estadoPago} /></td>
                  <td className="px-4 py-2.5 text-right font-medium">{formatoMoneda(p.total, p.moneda)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
