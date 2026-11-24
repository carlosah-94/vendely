import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import EtiquetaPago from '../components/EtiquetaPago';
import { IconoCheck, IconoClientes, IconoPedidos, IconoProductos } from '../components/Iconos';
import Mensaje from '../components/Mensaje';
import SelectorTienda from '../components/SelectorTienda';
import { useAuth } from '../context/AuthContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { api, mensajeDeError } from '../services/api';
import { formatoFecha, formatoMoneda } from '../utils/formato';
import { esDuenoDe, puede } from '../utils/permisos';

/** "Buenos días", "Buenas tardes" o "Buenas noches" según la hora. */
function saludo() {
  const h = new Date().getHours();
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
}

/** Inicio del panel: cuatro números y los últimos 5 pedidos, con las listas que ya existen. */
export default function InicioPanel() {
  const { tiendas, tienda, idTienda, cambiarTienda, cargando: cargandoTiendas, error: errorTiendas } = useTiendaActual();
  const { usuario } = useAuth();
  const [datos, setDatos] = useState({ idTienda: null, pedidos: [], productos: [], clientes: [], inventario: [], error: '' });

  useEffect(() => {
    if (!idTienda) {
      return undefined;
    }
    let vigente = true;
    const base = `/panel/tiendas/${idTienda}`;
    // Un empleado solo pide las listas que su permiso le deja leer; las demás quedan vacías.
    const pedir = (permitido, ruta) => (permitido ? api.get(`${base}/${ruta}`) : Promise.resolve({ data: [] }));
    Promise.all([
      pedir(puede(tienda, 'PEDIDOS'), 'pedidos'),
      pedir(puede(tienda, 'PRODUCTOS', 'PEDIDOS', 'INVENTARIO'), 'productos'),
      pedir(puede(tienda, 'CLIENTES', 'PEDIDOS'), 'clientes'),
      pedir(puede(tienda, 'INVENTARIO', 'PRODUCTOS'), 'inventario'),
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
  }, [idTienda, tienda]);

  if (errorTiendas) {
    return <Mensaje tipo="error" texto={errorTiendas} />;
  }
  if (!cargandoTiendas && tiendas.length === 0) {
    // Un empleado sin tiendas: nunca lo agregaron, o el dueño lo suspendió o lo quitó.
    return usuario?.rol === 'EMPLEADO' ? (
      <Mensaje
        tipo="info"
        texto="Ahora no tienes acceso a ninguna tienda. Si el dueño te suspendió o te quitó de su tienda, pídele que te vuelva a activar."
      />
    ) : (
      <Mensaje tipo="info" texto="Todavía no tienes tiendas. Crea una para empezar a vender." />
    );
  }

  const dueno = esDuenoDe(tienda);

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

  // Cada tarjeta se muestra solo si la cuenta puede ver ese dato en esta tienda.
  const tarjetas = [
    puede(tienda, 'PEDIDOS') && ['Ventas de hoy (pagadas)', formatoMoneda(ventasHoy), IconoCheck, '#fff1d1', '#8a5a00'],
    puede(tienda, 'PEDIDOS') && ['Pedidos', datos.pedidos.length, IconoPedidos, '#e8e6ff', '#3b2fc9'],
    puede(tienda, 'PRODUCTOS', 'PEDIDOS', 'INVENTARIO') && ['Productos activos', activos, IconoProductos, '#ffe4ee', '#b0174f'],
    puede(tienda, 'CLIENTES', 'PEDIDOS') && ['Clientes', datos.clientes.length, IconoClientes, '#dcf5ea', '#106a43'],
  ].filter(Boolean);

  return (
    <section className="max-w-5xl space-y-4">
      {/* Saludo con la tienda elegida y el enlace para verla como la ven los clientes */}
      <div className="relative isolate overflow-hidden rounded-2xl bg-[var(--v-tinta)] px-6 py-6 text-white md:px-8 md:py-7">
        <span className="absolute -right-10 -top-16 -z-10 h-56 w-56 rounded-full bg-[var(--v-mango)] opacity-90" />
        <span className="absolute right-28 top-14 -z-10 h-24 w-24 rounded-full bg-[var(--v-pitaya)] opacity-80" />
        <p className="text-[13px] text-white/70">{saludo()}, {usuario?.nombres?.trim().split(/\s+/)[0]}</p>
        <h1 className="letra-titular mt-1 max-w-[70%] text-[28px] leading-tight md:text-[34px]">
          {tienda ? tienda.nombre : 'Inicio'}
        </h1>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          {tiendas.length > 1 && (
            <div className="rounded-lg bg-white px-2 text-[#303030]">
              <SelectorTienda tiendas={tiendas} idTienda={idTienda} onCambiar={cambiarTienda} />
            </div>
          )}
          {tienda?.subdominio && (
            <a
              href={`${window.location.origin}/tienda/${tienda.subdominio}`}
              target="_blank"
              rel="noreferrer"
              className="rounded-full bg-white px-4 py-2 text-[13px] font-semibold text-[var(--v-tinta)] hover:bg-[var(--v-papel)]"
            >
              Ver mi tienda
            </a>
          )}
          {dueno && (
            <Link to="/panel/personalizar" className="rounded-full px-4 py-2 text-[13px] font-semibold text-white ring-1 ring-white/30 hover:bg-white/10">
              Personalizar
            </Link>
          )}
        </div>
      </div>

      {datos.error && <Mensaje tipo="error" texto={datos.error} />}

      {!dueno && tienda && (
        <p className="text-[13px] text-[#616161]">
          Trabajas en {tienda.nombre} como empleado. Ves las secciones que el dueño te permitió.
        </p>
      )}

      {!cargando && dueno && completados < pasos.length && (
        <div className="rounded-xl bg-white shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
          <div className="border-b border-[#ebebeb] px-4 py-3">
            <h2 className="font-semibold text-[#101010]">Primeros pasos</h2>
            <p className="text-[13px] text-[#616161]">Completa estas tareas para empezar a vender{tienda ? ` con ${tienda.nombre}` : ''}.</p>
            <div className="mt-3 flex items-center gap-3">
              <span className="text-[12px] text-[#616161]">{completados} de {pasos.length} tareas completadas</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#ebebeb]">
                <div className="h-full rounded-full bg-[var(--v-mango)]" style={{ width: `${(completados / pasos.length) * 100}%` }} />
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

      {!cargando && puede(tienda, 'INVENTARIO') && porReponer.length > 0 && (
        <Link
          to="/panel/productos/inventario"
          className="flex items-center justify-between gap-3 rounded-xl border border-[#ffd79d] bg-[#fff5c5] px-4 py-3 text-[13px] text-[#4f4700] hover:brightness-[0.98]"
        >
          <span>
            {/* Cuenta variantes, no productos: "Polo (M / Blanco)" y "Polo (L / Blanco)" son dos. */}
            <b>{porReponer.length} {porReponer.length === 1 ? 'variante necesita' : 'variantes necesitan'} reposición</b>
            {' · '}{porReponer.slice(0, 3).map((f) => (f.variante && f.variante !== 'Única' ? `${f.producto} (${f.variante})` : f.producto)).join(', ')}
            {porReponer.length > 3 ? '...' : ''}
          </span>
          <span className="shrink-0 font-medium underline">Ver inventario</span>
        </Link>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {tarjetas.map(([titulo, valor, Icono, fondo, color]) => (
          <div key={titulo} className="rounded-2xl bg-white p-4 shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl" style={{ background: fondo, color }}>
              <Icono tamano={18} />
            </span>
            <p className="mt-3 text-xs text-[#616161]">{titulo}</p>
            <p className="mt-0.5 text-2xl font-semibold text-[#101010]">{cargando ? '…' : valor}</p>
          </div>
        ))}
      </div>

      {puede(tienda, 'PEDIDOS') && (
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
      )}
    </section>
  );
}
