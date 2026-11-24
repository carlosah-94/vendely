import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Campanita from './Campanita';
import MenuCuenta from './MenuCuenta';
import { ToastProvider } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import {
  IconoBuscar, IconoCategoria, IconoCheck, IconoClientes, IconoDescuento, IconoEmpleados, IconoInforme, IconoEnlaceExterno, IconoEnvio, IconoInicio,
  IconoInventario, IconoMenu, IconoPedidos, IconoProductos, IconoTienda,
} from './Iconos';
import { esDuenoDe, puede } from '../utils/permisos';
import MarcaVendely from './marca/MarcaVendely';

/**
 * Menú del panel de la tienda (dueño y empleados).
 * sub = opción que cuelga de la anterior (como Inventario bajo Productos en Shopify).
 * permiso = la ve el dueño o un empleado con ese permiso · soloDueno = solo la ve el dueño.
 * iconoSolo = el ícono de una sub-opción cuando su opción de arriba no se muestra (por ejemplo,
 * un empleado con Inventario pero sin Productos): entonces va como opción principal.
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
  { ruta: '/panel/tiendas', texto: 'Mis tiendas', icono: IconoTienda },
  { ruta: '/panel/empleados', texto: 'Empleados', icono: IconoEmpleados, soloDueno: true },
  { ruta: '/panel/plan', texto: 'Plan', icono: IconoCheck, soloDueno: true },
];

/** Opciones del menú según el rol de la cuenta. */
const MENU = {
  ADMIN_TIENDA: MENU_TIENDA,
  EMPLEADO: MENU_TIENDA,
  SUPERADMIN: [
    { ruta: '/admin/tiendas', texto: 'Tiendas de Vendely', icono: IconoTienda },
    { ruta: '/admin/cuentas', texto: 'Cuentas', icono: IconoClientes },
    { ruta: '/admin/planes', texto: 'Planes', icono: IconoCheck },
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
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');

  const esPanelTienda = usuario.rol === 'ADMIN_TIENDA' || usuario.rol === 'EMPLEADO';
  // El superadmin no tiene tiendas: no se piden (el backend le respondería 403).
  const { tienda } = useTiendaActual(esPanelTienda);
  // Mientras carga la tienda elegida, el dueño ve su menú completo; un empleado, solo lo común.
  const duenoAqui = usuario.rol === 'ADMIN_TIENDA' && (!tienda || esDuenoDe(tienda));
  const permite = (permiso) => usuario.rol === 'ADMIN_TIENDA' ? duenoAqui : puede(tienda, permiso);
  const visible = (o) => (o.soloDueno ? duenoAqui : o.permiso ? permite(o.permiso) : true);
  const opciones = [];
  let padreVisible = false;
  for (const o of MENU[usuario.rol] ?? []) {
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
      {duenoAqui && [['/panel/temas', 'Temas'], ['/panel/personalizar', 'Personalizar']].map(([ruta, texto]) => (
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
        {/* Barra superior oscura */}
        <header className="flex h-14 shrink-0 items-center gap-3 px-3 md:px-4">
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
              <span className="hidden text-right leading-tight lg:block">
                <span className="block text-[13px] font-medium text-white">{usuario.nombres} {usuario.primerApellido}</span>
                <span className="block text-[12px] text-[#8a8a8a]">{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</span>
              </span>
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--v-mango)] text-[13px] font-bold text-[var(--v-tinta)]">
                {inicial}
              </span>
            </Link>
          </div>
        </header>

        <div className="flex min-h-0 flex-1">
          {/* Menú lateral (escritorio) */}
          {/* Fijo al alto de la ventana: el selector de tienda de abajo siempre se ve */}
          <aside className="hidden w-60 shrink-0 px-3 pb-3 pt-1 md:sticky md:top-0 md:block md:h-[calc(100vh-3.5rem)] md:self-start">{menu}</aside>

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
                <Outlet />
              </div>
            </div>
          </main>
        </div>
      </div>
    </ToastProvider>
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
