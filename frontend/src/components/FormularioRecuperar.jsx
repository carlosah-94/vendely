import { useEffect, useState } from 'react';
import Campo from './Campo';
import { mensajeDeError } from '../services/api';
import { pedirCodigoRecuperacion, restablecerContrasena } from '../services/recuperar';
import { estiloInput } from '../utils/estilos';

const CONTRASENA = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

/**
 * "¿Olvidaste tu contraseña?" en dos pasos:
 *  1. el correo → se envía un código de 6 dígitos (si hay una cuenta con ese correo);
 *  2. el código y la contraseña nueva (dos veces) → onListo(correo).
 * subdominio: null para el panel; el de la tienda para un cliente.
 * claseBoton: el estilo del botón principal (el del panel o el color de la tienda).
 */
export default function FormularioRecuperar({ subdominio = null, correoInicial = '', claseBoton, onListo, onVolver }) {
  const [paso, setPaso] = useState('correo');
  const [correo, setCorreo] = useState(correoInicial);
  const [codigo, setCodigo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [repetir, setRepetir] = useState('');
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [espera, setEspera] = useState(0);   // segundos para poder pedir otro código

  useEffect(() => {
    if (espera <= 0) return undefined;
    const id = setTimeout(() => setEspera((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [espera]);

  async function pedir() {
    setError('');
    setEnviando(true);
    try {
      const r = await pedirCodigoRecuperacion(correo.trim(), subdominio);
      setEspera(r.reenviarEnSegundos ?? 60);
      setPaso('codigo');
    } catch (e) {
      if (e.response?.data?.campos?.correo) setErrores({ correo: e.response.data.campos.correo });
      else setError(mensajeDeError(e));
    } finally {
      setEnviando(false);
    }
  }

  async function enviarCorreo(e) {
    e.preventDefault();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo.trim())) {
      setErrores({ correo: 'Escribe un correo válido' });
      return;
    }
    setErrores({});
    await pedir();
  }

  async function enviarNueva(e) {
    e.preventDefault();
    const er = {};
    if (!/^\d{6}$/.test(codigo)) er.codigo = 'El código tiene 6 dígitos';
    if (!CONTRASENA.test(contrasena)) er.contrasena = 'Mínimo 8 caracteres, con al menos una letra y un número';
    else if (contrasena !== repetir) er.repetir = 'Las contraseñas no coinciden';
    setErrores(er);
    if (Object.keys(er).length) return;
    setError('');
    setEnviando(true);
    try {
      await restablecerContrasena({ correo: correo.trim(), subdominio, codigo, contrasena });
      onListo(correo.trim());
    } catch (ex) {
      const campos = ex.response?.data?.campos;
      if (campos?.codigoVerificacion) setErrores({ codigo: campos.codigoVerificacion });
      else if (campos) setErrores(campos);
      else setError(mensajeDeError(ex));
      setEnviando(false);
    }
  }

  const alerta = error && (
    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>
  );

  if (paso === 'correo') {
    return (
      <form onSubmit={enviarCorreo} noValidate className="space-y-4">
        {alerta}
        <p className="text-sm text-slate-600">
          Escribe el correo de tu cuenta y te enviaremos un código para crear una contraseña nueva.
        </p>
        <Campo etiqueta="Correo" error={errores.correo}>
          <input
            type="email"
            required
            autoFocus
            autoComplete="email"
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
            className={estiloInput(errores.correo)}
          />
        </Campo>
        <button type="submit" disabled={enviando} className={claseBoton}>
          {enviando ? 'Enviando...' : 'Enviar código'}
        </button>
        {onVolver && (
          <button type="button" onClick={onVolver} className="w-full text-sm text-slate-600 hover:underline">
            Volver a iniciar sesión
          </button>
        )}
      </form>
    );
  }

  return (
    <form onSubmit={enviarNueva} noValidate className="space-y-4">
      {alerta}
      <p className="text-sm text-slate-600">
        Si hay una cuenta con <strong>{correo.trim()}</strong>, te llegó un código de 6 dígitos. Revisa también la
        carpeta de spam. Vence en 10 minutos.
      </p>
      <Campo etiqueta="Código" error={errores.codigo}>
        <input
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          maxLength={6}
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))}
          className={`${estiloInput(errores.codigo)} text-center text-lg tracking-[0.4em]`}
        />
      </Campo>
      <Campo etiqueta="Contraseña nueva" error={errores.contrasena} ayuda="Mínimo 8 caracteres, con al menos una letra y un número">
        <input
          type="password"
          autoComplete="new-password"
          value={contrasena}
          onChange={(e) => setContrasena(e.target.value)}
          className={estiloInput(errores.contrasena)}
        />
      </Campo>
      <Campo etiqueta="Repite la contraseña" error={errores.repetir}>
        <input
          type="password"
          autoComplete="new-password"
          value={repetir}
          onChange={(e) => setRepetir(e.target.value)}
          className={estiloInput(errores.repetir)}
        />
      </Campo>
      <button type="submit" disabled={enviando} className={claseBoton}>
        {enviando ? 'Guardando...' : 'Guardar contraseña nueva'}
      </button>
      <div className="flex justify-between text-sm text-slate-600">
        <button type="button" onClick={() => { setPaso('correo'); setError(''); setErrores({}); }} className="hover:underline">
          Cambiar correo
        </button>
        <button type="button" disabled={espera > 0 || enviando} onClick={pedir} className="hover:underline disabled:opacity-60 disabled:no-underline">
          {espera > 0 ? `Reenviar código en ${espera} s` : 'Reenviar código'}
        </button>
      </div>
    </form>
  );
}
