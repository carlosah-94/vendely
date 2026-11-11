import { Navigate, Route, Routes } from 'react-router-dom';
import LayoutPanel from './components/LayoutPanel';
import RutaProtegida from './components/RutaProtegida';
import AccesoDenegado from './pages/AccesoDenegado';
import AdminCuentas from './pages/admin/AdminCuentas';
import AdminTiendas from './pages/admin/AdminTiendas';
import Clientes from './pages/Clientes';
import DetalleVenta from './pages/DetalleVenta';
import Inicio from './pages/Inicio';
import InicioPanel from './pages/InicioPanel';
import Login from './pages/Login';
import MisTiendas from './pages/MisTiendas';
import NuevaVenta from './pages/NuevaVenta';
import Portada from './pages/Portada';
import Productos from './pages/Productos';
import Registro from './pages/Registro';
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
      <Route element={<RutaProtegida roles={['ADMIN_TIENDA']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/panel" element={<InicioPanel />} />
          <Route path="/panel/tiendas" element={<MisTiendas />} />
          <Route path="/panel/productos" element={<Productos />} />
          <Route path="/panel/clientes" element={<Clientes />} />
          <Route path="/panel/ventas" element={<Ventas />} />
          <Route path="/panel/ventas/nueva" element={<NuevaVenta />} />
          <Route path="/panel/ventas/:idPedido" element={<DetalleVenta />} />
        </Route>
      </Route>

      {/* Administración de Vendely */}
      <Route element={<RutaProtegida roles={['SUPERADMIN']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/admin/tiendas" element={<AdminTiendas />} />
          <Route path="/admin/cuentas" element={<AdminCuentas />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
