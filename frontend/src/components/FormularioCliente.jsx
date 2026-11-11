import { useEffect, useState } from 'react';
import { mensajeDeError } from '../services/api';
import { actualizarCliente, buscarDistritos, crearCliente } from '../services/clientes';

// Mismas reglas que el backend (ClienteCrearRequest): así el error se ve al instante,
// sin esperar la respuesta del servidor. El backend vuelve a validar igual.
const RE_CORREO = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const RE_TELEFONO = /^\+[1-9][0-9]{6,14}$/;

function validar(datos, esNuevo, nivel) {
  const e = {};
  const largo = (v) => v.trim().length;
  if (largo(datos.nombres) < 3 || largo(datos.nombres) > 80) e.nombres = 'Debe tener entre 3 y 80 caracteres';
  if (largo(datos.primerApellido) < 3 || largo(datos.primerApellido) > 80) e.primerApellido = 'Debe tener entre 3 y 80 caracteres';
  if (largo(datos.segundoApellido) > 80) e.segundoApellido = 'Máximo 80 caracteres';
  if (!RE_CORREO.test(datos.correo.trim())) e.correo = 'El correo no es válido';
  if (!RE_TELEFONO.test(datos.telefono.trim())) e.telefono = 'Formato internacional, por ejemplo +51987654321';
  // Al crear la contraseña es obligatoria; al editar, vacía = no se cambia.
  if (esNuevo || datos.contrasena) {
    if (datos.contrasena.length < 6 || datos.contrasena.length > 72) e.contrasena = 'Debe tener entre 6 y 72 caracteres';
  }
  if (esNuevo) {
    if (!datos.idDistrito) e['direccion.idDistrito'] = `Elige tu ${nivel.toLowerCase()} de la lista`;
    if (!datos.direccion.trim()) e['direccion.direccion'] = 'La dirección es obligatoria';
  }
  return e;
}

/**
 * Formulario para crear o editar un cliente, en una ventana sobre la tabla (tarea #17).
 * - cliente = null → crear (pide contraseña y dirección).
 * - cliente = objeto → editar (contraseña opcional; la dirección no se edita aquí,
 *   igual que en el backend: una dirección usada en un pedido no se puede cambiar).
 * Los errores se muestran junto a cada campo; los que no son de un campo (por ejemplo,
 * correo repetido) arriba del formulario.
 */
export default function FormularioCliente({ tienda, cliente, onGuardado, onCerrar }) {
  const esNuevo = !cliente;
  const [datos, setDatos] = useState({
    nombres: cliente?.nombres ?? '',
    primerApellido: cliente?.primerApellido ?? '',
    segundoApellido: cliente?.segundoApellido ?? '',
    correo: cliente?.correo ?? '',
    telefono: cliente?.telefono ?? (tienda.idPais === 'CL' ? '+56' : '+51'),
    contrasena: '',
    idDistrito: null,
    direccion: '',
    referencia: '',
  });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (campo) => (evento) => {
    setDatos((d) => ({ ...d, [campo]: evento.target.value }));
    // Al corregir un campo se quita su error, para no confundir.
    setErrores((e) => ({ ...e, [campo]: undefined }));
  };

  // Cerrar con la tecla Escape, como cualquier ventana.
  useEffect(() => {
    const alPresionar = (e) => e.key === 'Escape' && onCerrar();
    window.addEventListener('keydown', alPresionar);
    return () => window.removeEventListener('keydown', alPresionar);
  }, [onCerrar]);

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const encontrados = validar(datos, esNuevo, tienda.nivelFinal ?? 'Distrito');
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) {
      return;
    }

    // Solo se envía lo que el backend espera; los vacíos opcionales van como null.
    const cuerpo = {
      nombres: datos.nombres.trim(),
      primerApellido: datos.primerApellido.trim(),
      segundoApellido: datos.segundoApellido.trim() || null,
      correo: datos.correo.trim(),
      telefono: datos.telefono.trim(),
      contrasena: datos.contrasena || null,
    };
    if (esNuevo) {
      cuerpo.direccion = {
        idDistrito: datos.idDistrito,
        direccion: datos.direccion.trim(),
        referencia: datos.referencia.trim() || null,
      };
    }

    setEnviando(true);
    try {
      const guardado = esNuevo
        ? await crearCliente(tienda.idTienda, cuerpo)
        : await actualizarCliente(tienda.idTienda, cliente.idLogin, cuerpo);
      onGuardado(guardado, esNuevo);
    } catch (e) {
      // 400 con "campos": cada error va junto a su campo. Otro error (409 correo repetido…): arriba.
      const campos = e.response?.data?.campos;
      const texto = mensajeDeError(e);
      if (campos) {
        setErrores(campos);
      } else if (e.response?.status === 409 && /correo/i.test(texto)) {
        // Correo repetido en la tienda: se muestra junto al campo que hay que corregir.
        setErrores({ correo: texto });
      } else {
        setErrorGeneral(texto);
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="fixed inset-0 z-20 flex items-start justify-center overflow-y-auto bg-slate-900/50 p-4 sm:items-center">
      <form
        onSubmit={enviar}
        noValidate
        className="w-full max-w-2xl rounded-xl bg-white shadow-xl"
        role="dialog"
        aria-labelledby="titulo-formulario-cliente"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 id="titulo-formulario-cliente" className="text-lg font-bold text-slate-800">
            {esNuevo ? 'Nuevo cliente' : `Editar cliente: ${cliente.nombres} ${cliente.primerApellido}`}
          </h2>
          <button type="button" onClick={onCerrar} className="text-2xl leading-none text-slate-400 hover:text-slate-600" aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="space-y-4 px-6 py-5">
          {errorGeneral && (
            <p
              role="alert"
              // Si el formulario está desplazado hacia abajo, se lleva el mensaje a la vista.
              ref={(el) => el?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
              className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            >
              {errorGeneral}
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-3">
            <Campo etiqueta="Nombres" error={errores.nombres}>
              <input value={datos.nombres} onChange={cambiar('nombres')} className={estiloInput(errores.nombres)} />
            </Campo>
            <Campo etiqueta="Primer apellido" error={errores.primerApellido}>
              <input value={datos.primerApellido} onChange={cambiar('primerApellido')} className={estiloInput(errores.primerApellido)} />
            </Campo>
            <Campo etiqueta="Segundo apellido (opcional)" error={errores.segundoApellido}>
              <input value={datos.segundoApellido} onChange={cambiar('segundoApellido')} className={estiloInput(errores.segundoApellido)} />
            </Campo>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo etiqueta="Correo" error={errores.correo}>
              <input type="email" value={datos.correo} onChange={cambiar('correo')} className={estiloInput(errores.correo)} />
            </Campo>
            <Campo etiqueta="Teléfono" error={errores.telefono} ayuda="Con código de país: +51987654321">
              <input type="tel" value={datos.telefono} onChange={cambiar('telefono')} className={estiloInput(errores.telefono)} />
            </Campo>
          </div>

          <Campo
            etiqueta={esNuevo ? 'Contraseña' : 'Nueva contraseña (opcional)'}
            error={errores.contrasena}
            ayuda={esNuevo ? 'Al menos 6 caracteres' : 'Déjala vacía para no cambiarla'}
          >
            <input
              type="password"
              autoComplete="new-password"
              value={datos.contrasena}
              onChange={cambiar('contrasena')}
              className={estiloInput(errores.contrasena)}
            />
          </Campo>

          {esNuevo && (
            <fieldset className="space-y-4 rounded-lg border border-slate-200 p-4">
              <legend className="px-1 text-sm font-semibold text-slate-700">Dirección</legend>
              <BuscadorDistrito
                idPais={tienda.idPais}
                etiqueta={tienda.nivelFinal ?? 'Distrito'}
                error={errores['direccion.idDistrito']}
                onEscribir={() => setErrores((e) => ({ ...e, 'direccion.idDistrito': undefined }))}
                onElegir={(id) => {
                  setDatos((d) => ({ ...d, idDistrito: id }));
                  setErrores((e) => ({ ...e, 'direccion.idDistrito': undefined }));
                }}
              />
              <Campo etiqueta="Dirección" error={errores['direccion.direccion']}>
                <input
                  value={datos.direccion}
                  onChange={(e) => {
                    setDatos((d) => ({ ...d, direccion: e.target.value }));
                    setErrores((er) => ({ ...er, 'direccion.direccion': undefined }));
                  }}
                  placeholder="Av. Larco 123"
                  className={estiloInput(errores['direccion.direccion'])}
                />
              </Campo>
              <Campo etiqueta="Referencia (opcional)" error={errores['direccion.referencia']}>
                <input value={datos.referencia} onChange={cambiar('referencia')} placeholder="Frente al parque" className={estiloInput()} />
              </Campo>
            </fieldset>
          )}
        </div>

        <div className="flex justify-end gap-3 border-t border-slate-200 px-6 py-4">
          <button type="button" onClick={onCerrar} className="rounded-md border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-50">
            Cancelar
          </button>
          <button
            type="submit"
            disabled={enviando}
            className="rounded-md bg-violet-600 px-4 py-2 font-semibold text-white hover:bg-violet-700 disabled:opacity-60"
          >
            {enviando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </div>
  );
}

/** Etiqueta + campo + mensaje de error (o de ayuda) debajo. */
function Campo({ etiqueta, error, ayuda, children }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-slate-700">{etiqueta}</span>
      <div className="mt-1">{children}</div>
      {error ? (
        <span className="mt-1 block text-xs text-red-600">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-xs text-slate-500">{ayuda}</span>
      )}
    </label>
  );
}

function estiloInput(error) {
  return `w-full rounded-md border px-3 py-2 focus:outline-none focus:ring-2 ${
    error ? 'border-red-400 focus:ring-red-400' : 'border-slate-300 focus:ring-violet-500'
  }`;
}

/**
 * Elegir el distrito (Perú) o la comuna (Chile) escribiendo su nombre ("mira" → Miraflores).
 * Se usa la lista oficial del país de la tienda porque el backend exige una división de esa lista.
 * Espera 300 ms después de la última tecla para no llamar al servidor en cada letra.
 */
function BuscadorDistrito({ idPais, etiqueta, error, onElegir, onEscribir }) {
  const [texto, setTexto] = useState('');
  const [resultados, setResultados] = useState([]);
  const [elegido, setElegido] = useState(null);

  useEffect(() => {
    if (elegido || texto.trim().length < 2) {
      return undefined;
    }
    const espera = setTimeout(() => {
      buscarDistritos(idPais, texto.trim())
        .then(setResultados)
        .catch(() => setResultados([]));
    }, 300);
    return () => clearTimeout(espera);
  }, [texto, idPais, elegido]);

  function elegir(distrito) {
    setElegido(distrito);
    setTexto(`${distrito.nombre}${distrito.provincia ? ` (${distrito.provincia})` : ''}`);
    setResultados([]);
    onElegir(distrito.idDistrito);
  }

  return (
    <div className="relative">
      <Campo etiqueta={etiqueta} error={error} ayuda="Escribe al menos 2 letras y elige de la lista">
        <input
          value={texto}
          onChange={(e) => {
            // Si cambia el texto después de elegir, hay que volver a elegir.
            setTexto(e.target.value);
            if (e.target.value.trim().length < 2) {
              setResultados([]);
            }
            onEscribir();
            if (elegido) {
              setElegido(null);
              onElegir(null);
            }
          }}
          placeholder={idPais === 'CL' ? 'Providencia' : 'Miraflores'}
          autoComplete="off"
          className={estiloInput(error)}
        />
      </Campo>
      {resultados.length > 0 && (
        <ul className="absolute z-30 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
          {resultados.map((d) => (
            <li key={d.idDistrito}>
              <button
                type="button"
                onClick={() => elegir(d)}
                className="block w-full px-3 py-2 text-left text-sm hover:bg-violet-50"
              >
                {d.nombre} <span className="text-slate-500">{d.provincia ? `· ${d.provincia}` : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
