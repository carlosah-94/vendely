import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import {
  IconoBuscar, IconoClientes, IconoEnlaceExterno, IconoEnvio, IconoInicio, IconoMenu, IconoPedidos, IconoProductos,
  IconoSalir, IconoTienda,
} from './Iconos';

/**
 * Opciones del menú según el rol de la cuenta.
 * sub = opción que cuelga de la anterior (como Inventario bajo Productos en Shopify).
 */
const MENU = {
  ADMIN_TIENDA: [
    { ruta: '/panel', texto: 'Inicio', icono: IconoInicio, exacta: true },
    { ruta: '/panel/ventas', texto: 'Pedidos', icono: IconoPedidos },
    { ruta: '/panel/productos', texto: 'Productos', icono: IconoProductos, exacta: true },
    { ruta: '/panel/productos/categorias', texto: 'Categorías', sub: true },
    { ruta: '/panel/clientes', texto: 'Clientes', icono: IconoClientes },
    { ruta: '/panel/envios', texto: 'Envíos', icono: IconoEnvio },
    { ruta: '/panel/tiendas', texto: 'Mis tiendas', icono: IconoTienda },
  ],
  SUPERADMIN: [
    { ruta: '/admin/tiendas', texto: 'Tiendas de Vendely', icono: IconoTienda },
    { ruta: '/admin/cuentas', texto: 'Cuentas', icono: IconoClientes },
  ],
};

const NOMBRE_ROL = {
  ADMIN_TIENDA: 'Dueño de tienda',
  SUPERADMIN: 'Administrador de Vendely',
};

/**
 * Marco del panel al estilo del admin de Shopify: un marco oscuro con el menú a la
 * izquierda y la página elegida dentro de una tarjeta clara con bordes redondeados.
 * En el celular el menú se esconde y se abre con el botón de tres rayas.
 */
export default function LayoutPanel() {
  const { usuario, cerrarSesion } = useAuth();
  const navigate = useNavigate();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState('');

  const opciones = MENU[usuario.rol] ?? [];
  const esDueno = usuario.rol === 'ADMIN_TIENDA';
  const inicial = (usuario.nombres ?? '?').trim().charAt(0).toUpperCase();

  // La búsqueda de arriba lleva a Productos con el texto escrito (como en Shopify).
  function buscar(evento) {
    evento.preventDefault();
    const texto = busqueda.trim();
    navigate(texto ? `/panel/productos?buscar=${encodeURIComponent(texto)}` : '/panel/productos');
  }

  const menu = (
    <nav className="flex h-full flex-col gap-0.5 text-[13px]">
      {opciones.map(({ ruta, texto, icono: Icono, exacta, sub }) => (
        <NavLink
          key={ruta}
          to={ruta}
          end={exacta}
          onClick={() => setMenuAbierto(false)}
          className={({ isActive }) =>
            `flex h-[30px] items-center gap-2.5 rounded-xl ${sub ? 'pl-[37px] pr-2.5' : 'px-2.5'} ${
              isActive ? 'bg-[#2b2b2b] font-semibold text-[#fcfcfc]' : 'text-[#dcdcdc] hover:bg-[#1f1f1f]'
            }`
          }
        >
          {Icono && <Icono tamano={17} />}
          {texto}
        </NavLink>
      ))}

      {esDueno && (
        <>
          <p className="mt-5 mb-1 px-2.5 text-[12px] font-medium text-[#8a8a8a]">Canales de venta</p>
          <VerTienda />
          <NavLink
            to="/panel/personalizar"
            onClick={() => setMenuAbierto(false)}
            className={({ isActive }) =>
              `flex h-[30px] items-center rounded-xl pl-[37px] pr-2.5 ${
                isActive ? 'bg-[#2b2b2b] font-semibold text-[#fcfcfc]' : 'text-[#dcdcdc] hover:bg-[#1f1f1f]'
              }`
            }
          >
            Personalizar
          </NavLink>
        </>
      )}

      <button
        onClick={cerrarSesion}
        className="mt-auto flex h-[30px] items-center gap-2.5 rounded-xl px-2.5 text-left text-[#dcdcdc] hover:bg-[#1f1f1f]"
      >
        <IconoSalir tamano={17} />
        Cerrar sesión
      </button>
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
          <span className="text-[17px] font-semibold tracking-tight text-white md:w-52">Vendely</span>

          {esDueno ? (
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
            <div className="hidden text-right leading-tight lg:block">
              <p className="text-[13px] font-medium text-white">{usuario.nombres} {usuario.primerApellido}</p>
              <p className="text-[12px] text-[#8a8a8a]">{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</p>
            </div>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#1650d0] text-[13px] font-semibold text-white">
              {inicial}
            </span>
          </div>
        </header>

        <div className="flex min-h-0 flex-1">
          {/* Menú lateral (escritorio) */}
          <aside className="hidden w-60 shrink-0 px-3 pb-4 pt-1 md:block">{menu}</aside>

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
