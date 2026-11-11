import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import {
  IconoBuscar, IconoClientes, IconoEnlaceExterno, IconoInicio, IconoPedidos, IconoProductos, IconoTienda,
} from './Iconos';

/** Opciones del menú según el rol de la cuenta. */
const MENU = {
  ADMIN_TIENDA: [
    { ruta: '/panel', texto: 'Inicio', icono: IconoInicio, exacta: true },
    { ruta: '/panel/ventas', texto: 'Pedidos', icono: IconoPedidos },
    { ruta: '/panel/productos', texto: 'Productos', icono: IconoProductos },
    { ruta: '/panel/clientes', texto: 'Clientes', icono: IconoClientes },
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
 * Marco común del panel, al estilo de un panel de comercio: barra superior blanca,
 * menú lateral claro con íconos y la página elegida a la derecha.
 */
export default function LayoutPanel() {
  const { usuario, cerrarSesion } = useAuth();
  const opciones = MENU[usuario.rol] ?? [];
  const inicial = (usuario.nombres ?? '?').trim().charAt(0).toUpperCase();

  return (
    <ToastProvider>
      <div className="flex min-h-screen flex-col bg-[#f1f2f4] text-[#303030]" style={{ fontSize: 14 }}>
        {/* Barra superior */}
        <header className="flex h-14 items-center gap-4 border-b border-[#e3e3e3] bg-white px-4">
          <span className="w-44 text-lg font-bold tracking-tight">Vendely</span>
          <div className="mx-auto flex w-full max-w-md items-center gap-2 rounded-lg border border-[#e3e3e3] bg-[#f7f7f7] px-3 py-1.5 text-[#616161]">
            <IconoBuscar tamano={16} />
            <input
              placeholder="Buscar"
              aria-label="Buscar"
              className="w-full bg-transparent text-sm outline-none placeholder:text-[#8a8a8a]"
            />
          </div>
          <div className="flex w-56 items-center justify-end gap-3">
            <div className="text-right leading-tight">
              <p className="text-sm font-medium">{usuario.nombres} {usuario.primerApellido}</p>
              <p className="text-xs text-[#616161]">{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</p>
            </div>
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#303030] text-sm font-medium text-white">
              {inicial}
            </span>
          </div>
        </header>

        <div className="flex min-h-0 flex-1">
          {/* Menú lateral claro */}
          <aside className="flex w-56 shrink-0 flex-col bg-[#ebebeb] px-3 py-4">
            <nav className="flex-1 space-y-1">
              {opciones.map(({ ruta, texto, icono: Icono, exacta }) => (
                <NavLink
                  key={ruta}
                  to={ruta}
                  end={exacta}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-lg px-3 py-2 ${
                      isActive ? 'bg-white font-semibold shadow-sm' : 'text-[#303030] hover:bg-[#e0e0e0]'
                    }`
                  }
                >
                  <Icono tamano={18} />
                  {texto}
                </NavLink>
              ))}
            </nav>
            <div className="space-y-1 border-t border-[#d6d6d6] pt-3">
              {usuario.rol === 'ADMIN_TIENDA' && <VerTienda />}
              <button
                onClick={cerrarSesion}
                className="w-full rounded-lg px-3 py-2 text-left text-[#616161] hover:bg-[#e0e0e0]"
              >
                Cerrar sesión
              </button>
            </div>
          </aside>

          {/* min-w-0: sin esto, una tabla ancha estira toda la página hacia la derecha
              en lugar de desplazarse dentro de su propio contenedor. */}
          <main className="min-w-0 flex-1 p-8">
            <Outlet />
          </main>
        </div>
      </div>
    </ToastProvider>
  );
}

/** "Ver tienda": abre la tienda pública de la tienda elegida en otra pestaña. */
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
      className="flex items-center gap-3 rounded-lg px-3 py-2 text-[#303030] hover:bg-[#e0e0e0]"
    >
      <IconoEnlaceExterno tamano={18} />
      Ver tienda
    </a>
  );
}
