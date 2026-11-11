import { Navigate, Outlet, useLocation, useOutletContext } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

/**
 * Portero de las pantallas que necesitan un cliente con sesión en ESTA tienda
 * (checkout, resultado del pago, mis pedidos). Si no la hay, manda a /ingresar y
 * guarda a dónde volver después de entrar.
 */
export default function RequiereCliente() {
  const contexto = useOutletContext();
  const { usuario, cargando } = useAuth();
  const location = useLocation();

  if (cargando) {
    return <p className="p-8 text-[#616161]">Cargando...</p>;
  }
  const esCliente = usuario?.rol === 'CLIENTE' && usuario.idTienda === contexto.tienda.idTienda;
  if (!esCliente) {
    return (
      <Navigate
        to={`/tienda/${contexto.subdominio}/ingresar`}
        replace
        state={{ volver: location.pathname + location.search }}
      />
    );
  }
  return <Outlet context={contexto} />;
}
