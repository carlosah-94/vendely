import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha, formatoMoneda } from '../utils/formato';

/** Inicio del panel: cuatro números y los últimos 5 pedidos, con las listas que ya existen. */
export default function InicioPanel() {
  const { tiendas, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const [datos, setDatos] = useState({ idTienda: null, pedidos: [], productos: [], clientes: [], error: '' });

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    const base = `/panel/tiendas/${idTienda}`;
    Promise.all([api.get(`${base}/pedidos`), api.get(`${base}/productos`), api.get(`${base}/clientes`)])
      .then(([pedidos, productos, clientes]) => {
        if (vigente) setDatos({ idTienda, pedidos: pedidos.data, productos: productos.data, clientes: clientes.data, error: '' });
      })
      .catch((e) => {
        if (vigente) setDatos({ idTienda, pedidos: [], productos: [], clientes: [], error: mensajeDeError(e) });
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

  const tarjetas = [
    ['Ventas de hoy (pagadas)', formatoMoneda(ventasHoy)],
    ['Pedidos', datos.pedidos.length],
    ['Productos activos', activos],
    ['Clientes', datos.clientes.length],
  ];

  return (
    <section className="max-w-5xl space-y-5">
      <div>
        <h1 className="text-2xl font-bold">Inicio</h1>
        {tiendas.length > 0 && <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />}
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {tarjetas.map(([titulo, valor]) => (
          <div key={titulo} className="rounded-xl border border-[#e3e3e3] bg-white p-4">
            <p className="text-xs text-[#616161]">{titulo}</p>
            <p className="mt-1 text-2xl font-semibold">{cargando ? '…' : valor}</p>
          </div>
        ))}
      </div>

      <div className="overflow-hidden rounded-xl border border-[#e3e3e3] bg-white">
        <div className="flex items-center justify-between border-b border-[#e3e3e3] px-4 py-3">
          <h2 className="font-semibold">Últimos pedidos</h2>
          <Link to="/panel/ventas" className="text-violet-700 hover:underline">Ver todos</Link>
        </div>
        {cargando ? (
          <p className="p-4 text-[#616161]">Cargando...</p>
        ) : datos.pedidos.length === 0 ? (
          <p className="p-6 text-center text-[#616161]">Aún no hay pedidos.</p>
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
