import { useState } from 'react';
import { Navigate, useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import Campo from '../../components/Campo';
import { useAuth } from '../../context/AuthContext';
import { mensajeDeError } from '../../services/api';
import { registrarCliente } from '../../services/tienda';
import { estiloInput } from '../../utils/estilos';

const VACIO = {
  nombres: '', primerApellido: '', segundoApellido: '', usuario: '', correo: '',
  telefono: '+51', contrasena: '',
};

/** Iniciar sesión o crear una cuenta de cliente, siempre dentro de ESTA tienda. */
export default function IngresarCliente() {
  const { tienda, subdominio } = useOutletContext();
  const { usuario, iniciarSesion } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const destino = location.state?.volver ?? `/tienda/${subdominio}`;

  const [modo, setModo] = useState('ingresar'); // 'ingresar' | 'registro'
  const [datos, setDatos] = useState(VACIO);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  if (usuario?.rol === 'CLIENTE' && usuario.idTienda === tienda.idTienda) {
    return <Navigate to={destino} replace />;
  }

  const cambiar = (campo) => (e) => {
    setDatos((d) => ({ ...d, [campo]: e.target.value }));
    setErrores((er) => ({ ...er, [campo]: undefined }));
  };

  async function enviar(evento) {
    evento.preventDefault();
    setError('');
    setErrores({});
    setEnviando(true);
    try {
      if (modo === 'registro') {
        await registrarCliente({
          subdominio,
          usuario: datos.usuario.trim(),
          correo: datos.correo.trim(),
          contrasena: datos.contrasena,
          nombres: datos.nombres.trim(),
          primerApellido: datos.primerApellido.trim(),
          segundoApellido: datos.segundoApellido.trim() || null,
          telefono: datos.telefono.trim() || null,
          aceptaMarketing: false,
        });
      }
      // Registro y login usan el mismo correo y contraseña: tras crear la cuenta se inicia sesión.
      await iniciarSesion({ correo: datos.correo.trim(), contrasena: datos.contrasena, subdominio });
      navigate(destino, { replace: true });
    } catch (e) {
      if (e.response?.data?.campos) {
        setErrores(e.response.data.campos);
      } else {
        setError(mensajeDeError(e));
      }
    } finally {
      setEnviando(false);
    }
  }

  const registro = modo === 'registro';

  return (
    <div className="mx-auto max-w-md px-4 pt-12">
      <h1 className="text-3xl font-semibold tracking-tight">{registro ? 'Crear cuenta' : 'Iniciar sesión'}</h1>
      <p className="mt-1 text-sm text-[#616161]">en {tienda.nombre}</p>

      <form onSubmit={enviar} className="mt-6 space-y-4" noValidate={false}>
        {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        {registro && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Campo etiqueta="Nombres" error={errores.nombres}>
                <input required value={datos.nombres} onChange={cambiar('nombres')} className={estiloInput(errores.nombres)} />
              </Campo>
              <Campo etiqueta="Primer apellido" error={errores.primerApellido}>
                <input required value={datos.primerApellido} onChange={cambiar('primerApellido')} className={estiloInput(errores.primerApellido)} />
              </Campo>
            </div>
            <Campo etiqueta="Segundo apellido (opcional)" error={errores.segundoApellido}>
              <input value={datos.segundoApellido} onChange={cambiar('segundoApellido')} className={estiloInput(errores.segundoApellido)} />
            </Campo>
            <Campo etiqueta="Usuario" error={errores.usuario} ayuda="3 a 50 caracteres: letras, números, punto o guion">
              <input required minLength={3} value={datos.usuario} onChange={cambiar('usuario')} autoComplete="username" className={estiloInput(errores.usuario)} />
            </Campo>
            <Campo etiqueta="Teléfono" error={errores.telefono} ayuda="Con código de país: +51987654321">
              <input required type="tel" value={datos.telefono} onChange={cambiar('telefono')} className={estiloInput(errores.telefono)} />
            </Campo>
          </>
        )}

        <Campo etiqueta="Correo" error={errores.correo}>
          <input required type="email" value={datos.correo} onChange={cambiar('correo')} autoComplete="email" className={estiloInput(errores.correo)} />
        </Campo>
        <Campo
          etiqueta="Contraseña"
          error={errores.contrasena}
          ayuda={registro ? 'Mínimo 8 caracteres, con al menos una letra y un número' : undefined}
        >
          <input
            required
            type="password"
            value={datos.contrasena}
            onChange={cambiar('contrasena')}
            autoComplete={registro ? 'new-password' : 'current-password'}
            className={estiloInput(errores.contrasena)}
          />
        </Campo>

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-md bg-[#303030] py-3 font-medium text-white hover:bg-black disabled:opacity-60"
        >
          {enviando ? 'Un momento...' : registro ? 'Crear cuenta' : 'Iniciar sesión'}
        </button>
      </form>

      <p className="mt-6 border-t border-[#e3e3e3] pt-5 text-center text-sm text-[#616161]">
        {registro ? '¿Ya tienes cuenta?' : '¿Eres nuevo?'}{' '}
        <button
          type="button"
          onClick={() => {
            setModo(registro ? 'ingresar' : 'registro');
            setError('');
            setErrores({});
          }}
          className="font-medium text-violet-700 hover:underline"
        >
          {registro ? 'Iniciar sesión' : 'Crear cuenta'}
        </button>
      </p>
    </div>
  );
}
