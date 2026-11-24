import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import FormularioRecuperar from '../components/FormularioRecuperar';
import LayoutAcceso from '../components/LayoutAcceso';
import { useAuth } from '../context/AuthContext';

/** "¿Olvidaste tu contraseña?" del panel (dueños, empleados y superadmin). */
export default function RecuperarContrasena() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  if (usuario) {
    return <Navigate to="/inicio" replace />;
  }

  return (
    <LayoutAcceso>
      <div className="mb-8">
        <h1 className="letra-titular text-4xl text-[var(--v-tinta)]">Recupera tu contraseña</h1>
        <p className="mt-2 text-[15px] text-slate-500">Te ayudamos a entrar de nuevo al panel de tu tienda.</p>
      </div>
      <FormularioRecuperar
        correoInicial={location.state?.correo ?? ''}
        claseBoton="w-full rounded-full bg-[var(--v-tinta)] py-3 font-semibold text-white transition hover:bg-[var(--v-tinta-2)] disabled:opacity-60"
        onVolver={() => navigate('/login')}
        onListo={(correo) => navigate('/login', {
          replace: true,
          state: { correo, aviso: 'Listo: tu contraseña cambió. Inicia sesión con la nueva.' },
        })}
      />
    </LayoutAcceso>
  );
}
