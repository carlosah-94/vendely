import { Navigate, useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import FormularioRecuperar from '../../components/FormularioRecuperar';
import { useAuth } from '../../context/AuthContext';

/** "¿Olvidaste tu contraseña?" de un cliente, dentro de la tienda en la que compra. */
export default function RecuperarCliente() {
  const { tienda, subdominio } = useOutletContext();
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  if (usuario?.rol === 'CLIENTE') {
    return <Navigate to={`/tienda/${subdominio}`} replace />;
  }

  const ingresar = `/tienda/${subdominio}/ingresar`;
  return (
    <div className="mx-auto max-w-md px-4 pt-12">
      <h1 className="text-3xl font-semibold tracking-tight">Recupera tu contraseña</h1>
      <p className="mb-6 mt-1 text-sm text-[#616161]">en {tienda.nombre}</p>
      <FormularioRecuperar
        subdominio={subdominio}
        correoInicial={location.state?.correo ?? ''}
        claseBoton="w-full rounded-full bg-[var(--tienda)] py-3.5 font-semibold text-[var(--tienda-texto)] transition hover:brightness-95 disabled:opacity-60"
        onVolver={() => navigate(ingresar, { state: location.state })}
        onListo={(correo) => navigate(ingresar, {
          replace: true,
          state: { ...location.state, correo, aviso: 'Listo: tu contraseña cambió. Inicia sesión con la nueva.' },
        })}
      />
    </div>
  );
}
