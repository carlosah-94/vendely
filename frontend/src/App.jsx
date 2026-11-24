import { Navigate, Route, Routes } from 'react-router-dom';
import LayoutPanel from './components/LayoutPanel';
import RequierePermiso from './components/RequierePermiso';
import RutaProtegida from './components/RutaProtegida';
import AccesoDenegado from './pages/AccesoDenegado';
import AdminCuentas from './pages/admin/AdminCuentas';
import AdminPlanes from './pages/admin/AdminPlanes';
import AdminTiendas from './pages/admin/AdminTiendas';
import Clientes from './pages/Clientes';
import Descuentos from './pages/Descuentos';
import DetalleVenta from './pages/DetalleVenta';
import Empleados from './pages/Empleados';
import Informes from './pages/Informes';
import Inicio from './pages/Inicio';
import InicioPanel from './pages/InicioPanel';
import Login from './pages/Login';
import MisTiendas from './pages/MisTiendas';
import Categorias from './pages/Categorias';
import Envios from './pages/Envios';
import Inventario from './pages/Inventario';
import NuevaVenta from './pages/NuevaVenta';
import PersonalizarTienda from './pages/PersonalizarTienda';
import Temas from './pages/Temas';
import Perfil from './pages/Perfil';
import Plan from './pages/Plan';
import Portada from './pages/Portada';
import Productos from './pages/Productos';
import Registro from './pages/Registro';
import Seguridad from './pages/Seguridad';
import Ventas from './pages/Ventas';
import LayoutTienda from './components/tienda/LayoutTienda';
import RequiereCliente from './components/tienda/RequiereCliente';
import Carrito from './pages/tienda/Carrito';
import Checkout from './pages/tienda/Checkout';
import DetalleProducto from './pages/tienda/DetalleProducto';
import IngresarCliente from './pages/tienda/IngresarCliente';
import MisPedidos from './pages/tienda/MisPedidos';
import ResultadoPago from './pages/tienda/ResultadoPago';
import Vitrina from './pages/tienda/Vitrina';

export default function App() {
  return (
    <Routes>
      {/* Públicas */}
      <Route path="/login" element={<Login />} />
      <Route path="/registro" element={<Registro />} />
      <Route path="/acceso-denegado" element={<AccesoDenegado />} />
      <Route path="/" element={<Portada />} />
      {/* Después de iniciar sesión: lleva a cada cuenta a su panel según su rol */}
      <Route path="/inicio" element={<Inicio />} />

      {/* Tienda pública del cliente: el subdominio va en la ruta (/tienda/moda-lima) */}
      <Route path="/tienda/:subdominio" element={<LayoutTienda />}>
        <Route index element={<Vitrina />} />
        <Route path="producto/:idProducto" element={<DetalleProducto />} />
        <Route path="carrito" element={<Carrito />} />
        <Route path="ingresar" element={<IngresarCliente />} />
        <Route element={<RequiereCliente />}>
          <Route path="checkout" element={<Checkout />} />
          <Route path="pago/resultado" element={<ResultadoPago />} />
          <Route path="mis-pedidos" element={<MisPedidos />} />
        </Route>
      </Route>

      {/* Panel del dueño de tienda */}
      {/* Panel de la tienda: el dueño ve todo; un empleado, solo las secciones que su dueño
          le permitió en la tienda elegida (RequierePermiso). El backend lo vuelve a revisar. */}
      <Route element={<RutaProtegida roles={['ADMIN_TIENDA', 'EMPLEADO']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/panel" element={<InicioPanel />} />
          <Route path="/panel/tiendas" element={<MisTiendas />} />
          <Route element={<RequierePermiso permiso="PRODUCTOS" />}>
            <Route path="/panel/productos" element={<Productos />} />
            <Route path="/panel/productos/categorias" element={<Categorias />} />
            <Route path="/panel/descuentos" element={<Descuentos />} />
          </Route>
          <Route element={<RequierePermiso permiso="INVENTARIO" />}>
            <Route path="/panel/productos/inventario" element={<Inventario />} />
          </Route>
          <Route element={<RequierePermiso permiso="CLIENTES" />}>
            <Route path="/panel/clientes" element={<Clientes />} />
          </Route>
          <Route element={<RequierePermiso permiso="PEDIDOS" />}>
            <Route path="/panel/ventas" element={<Ventas />} />
            <Route path="/panel/ventas/nueva" element={<NuevaVenta />} />
            <Route path="/panel/ventas/:idPedido" element={<DetalleVenta />} />
          </Route>
          <Route element={<RequierePermiso soloDueno />}>
            <Route path="/panel/envios" element={<Envios />} />
            <Route path="/panel/informes" element={<Informes />} />
            <Route path="/panel/temas" element={<Temas />} />
            <Route path="/panel/personalizar" element={<PersonalizarTienda />} />
            <Route path="/panel/plan" element={<Plan />} />
            <Route path="/panel/empleados" element={<Empleados />} />
          </Route>
        </Route>
      </Route>

      {/* Mi cuenta: la usan todas las cuentas del panel (issue #4) */}
      <Route element={<RutaProtegida roles={['ADMIN_TIENDA', 'EMPLEADO', 'SUPERADMIN']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/cuenta/seguridad" element={<Seguridad />} />
          <Route path="/cuenta/perfil" element={<Perfil />} />
        </Route>
      </Route>

      {/* Administración de Vendely */}
      <Route element={<RutaProtegida roles={['SUPERADMIN']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/admin/tiendas" element={<AdminTiendas />} />
          <Route path="/admin/cuentas" element={<AdminCuentas />} />
          <Route path="/admin/planes" element={<AdminPlanes />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
