import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Opciones del menú según el rol de la cuenta. */
const MENU = {
  ADMIN_TIENDA: [
    { ruta: '/panel/tiendas', texto: 'Mis tiendas' },
    { ruta: '/panel/productos', texto: 'Productos' },
    { ruta: '/panel/clientes', texto: 'Clientes' },
    { ruta: '/panel/ventas', texto: 'Ventas' },
  ],
  SUPERADMIN: [
    { ruta: '/admin/tiendas', texto: 'Tiendas de Vendely' },
    { ruta: '/admin/cuentas', texto: 'Cuentas' },
  ],
};

const NOMBRE_ROL = {
  ADMIN_TIENDA: 'Dueño de tienda',
  SUPERADMIN: 'Administrador de Vendely',
};

/** Marco común del panel: menú a la izquierda y la página elegida a la derecha. */
export default function LayoutPanel() {
  const { usuario, cerrarSesion } = useAuth();
  const opciones = MENU[usuario.rol] ?? [];

  return (
    <div className="min-h-screen flex bg-slate-100">
      <aside className="w-60 shrink-0 bg-slate-900 text-slate-100 flex flex-col">
        <div className="px-5 py-4 text-xl font-bold">Vendely</div>
        <nav className="flex-1 px-3 space-y-1">
          {opciones.map((opcion) => (
            <NavLink
              key={opcion.ruta}
              to={opcion.ruta}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm ${
                  isActive ? 'bg-violet-600 text-white' : 'text-slate-300 hover:bg-slate-800'
                }`
              }
            >
              {opcion.texto}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-800 px-5 py-4 text-sm">
          <p className="font-medium">{usuario.nombres} {usuario.primerApellido}</p>
          <p className="text-slate-400">{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</p>
          <button onClick={cerrarSesion} className="mt-3 text-violet-300 hover:text-white">
            Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="flex-1 p-8">
        <Outlet />
      </main>
    </div>
  );
}