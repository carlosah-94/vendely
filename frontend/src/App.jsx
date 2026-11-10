import { Navigate, Route, Routes } from 'react-router-dom';
import LayoutPanel from './components/LayoutPanel';
import RutaProtegida from './components/RutaProtegida';
import AccesoDenegado from './pages/AccesoDenegado';
import Clientes from './pages/Clientes';
import EnConstruccion from './pages/EnConstruccion';
import Inicio from './pages/Inicio';
import Login from './pages/Login';

export default function App() {
  return (
    <Routes>
      {/* Públicas */}
      <Route path="/login" element={<Login />} />
      <Route path="/acceso-denegado" element={<AccesoDenegado />} />
      <Route path="/" element={<Inicio />} />

      {/* Panel del dueño de tienda */}
      <Route element={<RutaProtegida roles={['ADMIN_TIENDA']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/panel/tiendas" element={<EnConstruccion titulo="Mis tiendas" />} />
          <Route path="/panel/productos" element={<EnConstruccion titulo="Productos" />} />
          <Route path="/panel/clientes" element={<Clientes />} />
          <Route path="/panel/ventas" element={<EnConstruccion titulo="Ventas" />} />
        </Route>
      </Route>

      {/* Administración de Vendely */}
      <Route element={<RutaProtegida roles={['SUPERADMIN']} />}>
        <Route element={<LayoutPanel />}>
          <Route path="/admin/tiendas" element={<EnConstruccion titulo="Tiendas de Vendely" />} />
          <Route path="/admin/cuentas" element={<EnConstruccion titulo="Cuentas" />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}