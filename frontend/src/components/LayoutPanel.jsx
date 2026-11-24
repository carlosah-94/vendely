import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Campanita from './Campanita';
import MenuCuenta from './MenuCuenta';
import { ToastProvider } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import {
  IconoBuscar, IconoCategoria, IconoCheck, IconoClientes, IconoDescuento, IconoEmpleados, IconoInforme, IconoEnlaceExterno, IconoEnvio, IconoInicio,
  IconoInventario, IconoMenu, IconoPedidos, IconoProductos, IconoSoporte, IconoTienda,
} from './Iconos';
import { esDuenoDe, esSoporte, puede } from '../utils/permisos';
import { formatoFecha } from '../utils/formato';
import MarcaVendely from './marca/MarcaVendely';

/**
 * Menú del panel de la tienda (dueño y empleados).
 * sub = opción que cuelga de la anterior (como Inventario bajo Productos en Shopify).
 * permiso = la ve el dueño o un empleado con ese permiso · soloDueno = solo la ve el dueño.
 * iconoSolo = el ícono de una sub-opción cuando su opción de arriba no se muestra (por ejemplo,
 * un empleado con Inventario pero sin Productos): entonces va como opción principal.
 * sinSoporte = soporte de Vendely no la ve aunque entre con permiso del dueño (son decisiones del dueño).
 * Sin permiso ni soloDueno, la ven todos.
 */
const MENU_TIENDA = [
  { ruta: '/panel', texto: 'Inicio', icono: IconoInicio, exacta: true },
  { ruta: '/panel/ventas', texto: 'Pedidos', icono: IconoPedidos, permiso: 'PEDIDOS' },
  { ruta: '/panel/productos', texto: 'Productos', icono: IconoProductos, exacta: true, permiso: 'PRODUCTOS' },
  { ruta: '/panel/productos/inventario', texto: 'Inventario', sub: true, permiso: 'INVENTARIO', iconoSolo: IconoInventario },
  { ruta: '/panel/productos/categorias', texto: 'Categorías', sub: true, permiso: 'PRODUCTOS', iconoSolo: IconoCategoria },
  { ruta: '/panel/clientes', texto: 'Clientes', icono: IconoClientes, permiso: 'CLIENTES' },
  { ruta: '/panel/descuentos', texto: 'Descuentos', icono: IconoDescuento, permiso: 'PRODUCTOS' },
  { ruta: '/panel/informes', texto: 'Informes', icono: IconoInforme, soloDueno: true },
  { ruta: '/panel/envios', texto: 'Envíos', icono: IconoEnvio, soloDueno: true },
  { ruta: '/panel/tiendas', texto: 'Mis tiendas', icono: IconoTienda, sinSoporte: true },
  { ruta: '/panel/empleados', texto: 'Empleados', icono: IconoEmpleados, soloDueno: true, sinSoporte: true },
  { ruta: '/panel/plan', texto: 'Plan', icono: IconoCheck, soloDueno: true, sinSoporte: true },
  { ruta: '/panel/soporte', texto: 'Soporte de Vendely', icono: IconoSoporte, soloDueno: true, sinSoporte: true },
];

/** Opciones del menú según el rol de la cuenta. */
const MENU = {
  ADMIN_TIENDA: MENU_TIENDA,
  EMPLEADO: MENU_TIENDA,
  SUPERADMIN: [
    { ruta: '/admin/tiendas', texto: 'Tiendas de Vendely', icono: IconoTienda },
    { ruta: '/admin/cuentas', texto: 'Cuentas', icono: IconoClientes },
    { ruta: '/admin/planes', texto: 'Planes', icono: IconoCheck },
    { ruta: '/admin/soporte', texto: 'Soporte a tiendas', icono: IconoSoporte },
  ],
};

const NOMBRE_ROL = {
  ADMIN_TIENDA: 'Dueño de tienda',
  EMPLEADO: 'Empleado',
  SUPERADMIN: 'Administrador de Vendely',
};

/**
 * Marco del panel al estilo del admin de Shopify: un marco oscuro con el menú a la
 * izquierda y la página elegida dentro de una tarjeta clara con bordes redondeados.
 * En el celular el menú se esconde y se abre con el botón de tres rayas.
 */
export default function LayoutPanel() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');

  // Modo soporte: el superadmin dentro del panel de una tienda que le dio permiso.
  const enSoporte = usuario.rol === 'SUPERADMIN' && pathname.startsWith('/panel');
  const esPanelTienda = usuario.rol === 'ADMIN_TIENDA' || usuario.rol === 'EMPLEADO' || enSoporte;
  // Fuera del modo soporte el superadmin no trabaja en tiendas: no se piden.
  const { tienda, tiendas, cargando: cargandoTiendas } = useTiendaActual(esPanelTienda);
  // Mientras carga la tienda elegida, el dueño ve su menú completo; un empleado, solo lo común.
  const duenoAqui = usuario.rol === 'ADMIN_TIENDA' && (!tienda || esDuenoDe(tienda));
  const soporteAqui = enSoporte && esSoporte(tienda);
  const permite = (permiso) => usuario.rol === 'ADMIN_TIENDA' ? duenoAqui : puede(tienda, permiso);
  const visible = (o) => {
    if (enSoporte && (o.sinSoporte || !soporteAqui)) return o.ruta === '/panel';
    return o.soloDueno ? duenoAqui || soporteAqui : o.permiso ? permite(o.permiso) : true;
  };
  const opciones = [];
  let padreVisible = false;
  for (const o of (enSoporte ? MENU_TIENDA : MENU[usuario.rol]) ?? []) {
    if (!o.sub) {
      padreVisible = visible(o);
    }
    if (visible(o)) {
      opciones.push(o.sub && !padreVisible ? { ...o, sub: false, icono: o.iconoSolo } : o);
    }
  }
  const inicial = (usuario.nombres ?? '?').trim().charAt(0).toUpperCase();

  // La búsqueda de arriba lleva a Productos con el texto escrito (como en Shopify).
  function buscar(evento) {
    evento.preventDefault();
    const texto = busqueda.trim();
    navigate(texto ? `/panel/productos?buscar=${encodeURIComponent(texto)}` : '/panel/productos');
  }

  const menu = (
    <nav className="flex h-full flex-col text-[13px]">
      {/* Las opciones se desplazan solas si no caben; la tienda y la cuenta quedan siempre abajo, a la vista */}
      <div className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto">
      {opciones.map(({ ruta, texto, icono: Icono, exacta, sub }) => (
        <NavLink
          key={ruta}
          to={ruta}
          end={exacta}
          onClick={() => setMenuAbierto(false)}
          className={({ isActive }) =>
            `flex h-[30px] items-center gap-2.5 rounded-xl ${sub ? 'pl-[37px] pr-2.5' : 'px-2.5'} ${
              isActive ? 'bg-[#2b2b2b] font-semibold text-[#fcfcfc] shadow-[inset_3px_0_0_var(--v-mango)]' : 'text-[#dcdcdc] hover:bg-[#1f1f1f]'
            }`
          }
        >
          {Icono && <Icono tamano={17} />}
          {texto}
        </NavLink>
      ))}

      {esPanelTienda && tienda && (
        <>
          <p className="mt-5 mb-1 px-2.5 text-[12px] font-medium text-[#8a8a8a]">Canales de venta</p>
          <VerTienda />
        </>
      )}
      {/* Bajo "Tienda online": Temas (elegir uno) y Personalizar (ajustar los detalles) */}
      {(duenoAqui || soporteAqui) && [['/panel/temas', 'Temas'], ['/panel/personalizar', 'Personalizar']].map(([ruta, texto]) => (
        <NavLink
          key={ruta}
          to={ruta}
          onClick={() => setMenuAbierto(false)}
          className={({ isActive }) =>
            `flex h-[30px] items-center rounded-xl pl-[37px] pr-2.5 ${
              isActive ? 'bg-[#2b2b2b] font-semibold text-[#fcfcfc] shadow-[inset_3px_0_0_var(--v-mango)]' : 'text-[#dcdcdc] hover:bg-[#1f1f1f]'
            }`
          }
        >
          {texto}
        </NavLink>
      ))}

      </div>

      {/* Abajo: la tienda con la que se trabaja (para cambiarla) y la cuenta (perfil, seguridad, salir) */}
      <div className="mt-2 shrink-0 border-t border-[#262626] pt-2">
        <MenuCuenta alNavegar={() => setMenuAbierto(false)} />
      </div>
    </nav>
  );

  return (
    <ToastProvider>
      <div className="flex min-h-screen flex-col bg-[#0a0a0a] text-[#303030]">
        {/* Barra superior oscura: queda fija arriba aunque la página baje (como en Shopify) */}
        <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 bg-[#0a0a0a] px-3 md:px-4">
          <button
            onClick={() => setMenuAbierto((v) => !v)}
            className="rounded-lg p-1.5 text-[#dcdcdc] hover:bg-[#1f1f1f] md:hidden"
            aria-label="Abrir menú"
          >
            <IconoMenu tamano={20} />
          </button>
          <span className="md:w-52"><MarcaVendely claro a="/inicio" tamano={26} className="text-[15px]" /></span>

          {esPanelTienda && permite('PRODUCTOS') ? (
            <form onSubmit={buscar} className="mx-auto hidden w-full max-w-lg sm:block">
              <label className="flex items-center gap-2 rounded-xl border border-[#333] bg-[#1f1f1f] px-3 py-1.5 text-[#b5b5b5] focus-within:border-[#5c5c5c]">
                <IconoBuscar tamano={16} />
                <input
                  value={busqueda}
                  onChange={(e) => setBusqueda(e.target.value)}
                  placeholder="Buscar productos"
                  aria-label="Buscar productos"
                  className="w-full bg-transparent text-[13px] text-white outline-none placeholder:text-[#8a8a8a]"
                />
              </label>
            </form>
          ) : (
            <div className="mx-auto" />
          )}

          <div className="ml-auto flex items-center gap-2.5 sm:ml-0 md:w-52 md:justify-end">
            {/* Pedidos nuevos en vivo (issue #4). Va dentro del ToastProvider, que muestra el aviso. */}
            {esPanelTienda && permite('PEDIDOS') && <Campanita />}
            <Link to="/cuenta/perfil" title="Mi perfil" className="flex items-center gap-2.5 rounded-xl px-1.5 py-1 hover:bg-[#1f1f1f]">
              {/* Una línea cada dato: un nombre largo se corta con "…" en vez de salirse de la barra */}
              <span className="hidden min-w-0 max-w-[10.5rem] text-right leading-tight lg:block">
                <span className="block truncate text-[13px] font-medium text-white">{usuario.nombres} {usuario.primerApellido}</span>
                <span className="block truncate text-[12px] text-[#8a8a8a]">{enSoporte ? 'Modo soporte' : NOMBRE_ROL[usuario.rol] ?? usuario.rol}</span>
              </span>
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[var(--v-mango)] text-[13px] font-bold text-[var(--v-tinta)]">
                {inicial}
              </span>
            </Link>
          </div>
        </header>

        <div className="flex min-h-0 flex-1">
          {/* Menú lateral (escritorio) */}
          {/* Fijo debajo de la barra superior y al alto de la ventana: no se mueve con el scroll y el
              selector de tienda de abajo siempre se ve */}
          <aside className="hidden w-60 shrink-0 px-3 pb-3 pt-1 md:sticky md:top-14 md:block md:h-[calc(100vh-3.5rem)] md:self-start">{menu}</aside>

          {/* Menú lateral (celular): se abre encima del contenido */}
          {menuAbierto && (
            <div className="fixed inset-0 z-30 md:hidden">
              <button className="absolute inset-0 bg-black/50" onClick={() => setMenuAbierto(false)} aria-label="Cerrar menú" />
              <aside className="relative h-full w-64 bg-[#0a0a0a] px-3 py-4">{menu}</aside>
            </div>
          )}

          {/* La página va dentro de una tarjeta clara con esquinas redondeadas.
              min-w-0: sin esto, una tabla ancha estira toda la página hacia la derecha. */}
          <main className="min-w-0 flex-1 pb-2 pr-2 md:pl-0 pl-2">
            <div className="min-h-full rounded-2xl bg-[#f1f1f1] px-4 py-6 md:px-8 md:py-7">
              <div className="mx-auto max-w-6xl">
                {enSoporte && (
                  <AvisoSoporte
                    tienda={tienda}
                    hayTiendas={tiendas.length > 0}
                    cargando={cargandoTiendas}
                    onSalir={() => navigate('/admin/soporte')}
                  />
                )}
                <Outlet />
              </div>
            </div>
          </main>
        </div>
      </div>
    </ToastProvider>
  );
}

/**
 * Franja de arriba en modo soporte: recuerda que se trabaja en la tienda de un cliente, con su
 * permiso y hasta cuándo, y que lo que se cambie queda registrado para el dueño.
 */
function AvisoSoporte({ tienda, hayTiendas, cargando, onSalir }) {
  if (cargando) {
    return null;
  }
  return (
    <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#f0c94d] bg-[#fff8db] px-4 py-3 text-[13px] text-[#4f4700]">
      <div className="flex items-start gap-2.5">
        <span className="mt-0.5 text-[#8a6d00]"><IconoSoporte tamano={18} /></span>
        {tienda && esSoporte(tienda) ? (
          <p>
            <span className="font-semibold text-[#3d3700]">Modo soporte: {tienda.nombre}.</span>{' '}
            Entras con permiso de su dueño (vence el {formatoFecha(tienda.soporteHasta)}). Lo que cambies
            queda registrado y el dueño lo ve.
          </p>
        ) : (
          <p>
            <span className="font-semibold text-[#3d3700]">{hayTiendas ? 'Elige una tienda.' : 'Ninguna tienda te ha dado acceso.'}</span>{' '}
            El dueño tiene que entrar a Soporte de Vendely en su panel y tocar "Dar acceso".
          </p>
        )}
      </div>
      <button onClick={onSalir} className="rounded-lg border border-[#d8b73a] bg-white px-3 py-1.5 font-semibold text-[#3d3700] hover:bg-[#fffdf2]">
        Salir del modo soporte
      </button>
    </div>
  );
}

/** "Tienda online": abre la tienda pública de la tienda elegida en otra pestaña. */
function VerTienda() {
  const { tienda } = useTiendaActual();
  if (!tienda) {
    return null;
  }
  return (
    <a
      href={`${window.location.origin}/tienda/${tienda.subdominio}`}
      target="_blank"
      rel="noreferrer"
      className="flex h-[30px] items-center gap-2.5 rounded-xl px-2.5 text-[#dcdcdc] hover:bg-[#1f1f1f]"
    >
      <IconoTienda tamano={17} />
      <span className="flex-1">Tienda online</span>
      <IconoEnlaceExterno tamano={14} />
    </a>
  );
}
