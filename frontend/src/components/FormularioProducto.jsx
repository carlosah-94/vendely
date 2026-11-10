import { useEffect, useState } from 'react';
import { mensajeDeError } from '../services/api';
import { actualizarProducto, crearProducto } from '../services/productos';

const MAX_DESCRIPCION = 500;

// Mismas reglas que el backend (ProductoRequest, RF2.1.1): el error se ve al instante,
// sin esperar al servidor. El backend vuelve a validar igual.
function validar(datos) {
  const e = {};
  const nombre = datos.nombre.trim();
  if (nombre.length < 3 || nombre.length > 200) e.nombre = 'El nombre debe tener entre 3 y 200 caracteres';
  if (datos.descripcion.length > MAX_DESCRIPCION) e.descripcion = `La descripción puede tener hasta ${MAX_DESCRIPCION} caracteres`;
  if (datos.marca.trim().length > 100) e.marca = 'Máximo 100 caracteres';

  // Precio: número mayor que 0 con hasta 2 decimales (céntimos), como NUMERIC(12,2).
  const precio = datos.precio.trim().replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(precio)) e.precio = 'Escribe un precio válido, con hasta 2 decimales (ej. 49.90)';
  else if (Number(precio) <= 0) e.precio = 'El precio debe ser mayor que 0';

  // Stock: entero mayor o igual a 0.
  if (!/^\d+$/.test(datos.stock.trim())) e.stock = 'El stock debe ser un número entero mayor o igual a 0';
  return e;
}

/**
 * Formulario para crear o editar un producto simple, en una ventana sobre la tabla (tarea #18).
 * - producto = null → crear.  - producto = objeto → editar (llega con su precio y stock actuales).
 * Al editar el precio, el backend cierra el precio anterior y crea uno nuevo: las ventas
 * pasadas conservan el precio con el que se hicieron.
 */
export default function FormularioProducto({ idTienda, producto, onGuardado, onCerrar }) {
  const esNuevo = !producto;
  const [datos, setDatos] = useState({
    nombre: producto?.nombre ?? '',
    descripcion: producto?.descripcion ?? '',
    marca: producto?.marca ?? '',
    precio: producto?.precio != null ? String(producto.precio) : '',
    stock: producto?.stock != null ? String(producto.stock) : '0',
    // Al crear queda activo; al editar se respeta su estado (BORRADOR = desactivado).
    activo: producto ? producto.estado === 'ACTIVO' : true,
  });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (campo) => (evento) => {
    const valor = evento.target.type === 'checkbox' ? evento.target.checked : evento.target.value;
    setDatos((d) => ({ ...d, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));   // al corregir un campo se quita su error
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
    const encontrados = validar(datos);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) {
      return;
    }

    const cuerpo = {
      nombre: datos.nombre.trim(),
      descripcion: datos.descripcion.trim() || null,
      marca: datos.marca.trim() || null,
      precio: Number(datos.precio.trim().replace(',', '.')),
      stock: Number(datos.stock.trim()),
      activo: datos.activo,
    };

    setEnviando(true);
    try {
      const guardado = esNuevo
        ? await crearProducto(idTienda, cuerpo)
        : await actualizarProducto(idTienda, producto.idProducto, cuerpo);
      onGuardado(guardado, esNuevo);
    } catch (e) {
      const campos = e.response?.data?.campos;
      const texto = mensajeDeError(e);
      if (campos) {
        setErrores(campos);                                   // 400: cada error junto a su campo
      } else if (e.response?.status === 409 && /nombre/i.test(texto)) {
        setErrores({ nombre: texto });                        // nombre repetido en la tienda
      } else {
        setErrorGeneral(texto);                               // otro error (p. ej. producto con varias variantes)
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
        className="w-full max-w-xl rounded-xl bg-white shadow-xl"
        role="dialog"
        aria-labelledby="titulo-formulario-producto"
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 id="titulo-formulario-producto" className="text-lg font-bold text-slate-800">
            {esNuevo ? 'Nuevo producto' : `Editar producto: ${producto.nombre}`}
          </h2>
          <button type="button" onClick={onCerrar} className="text-2xl leading-none text-slate-400 hover:text-slate-600" aria-label="Cerrar">
            ×
          </button>
        </div>

        <div className="space-y-4 px-6 py-5">
          {errorGeneral && (
            <p
              role="alert"
              ref={(el) => el?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
              className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            >
              {errorGeneral}
            </p>
          )}

          <Campo etiqueta="Nombre" error={errores.nombre}>
            <input value={datos.nombre} onChange={cambiar('nombre')} placeholder="Zapatilla Runner" className={estiloInput(errores.nombre)} />
          </Campo>

          <Campo
            etiqueta="Descripción (opcional)"
            error={errores.descripcion}
            ayuda={`${datos.descripcion.length} / ${MAX_DESCRIPCION} caracteres`}
          >
            <textarea
              rows={3}
              value={datos.descripcion}
              onChange={cambiar('descripcion')}
              className={estiloInput(errores.descripcion)}
            />
          </Campo>

          <Campo etiqueta="Marca (opcional)" error={errores.marca}>
            <input value={datos.marca} onChange={cambiar('marca')} className={estiloInput(errores.marca)} />
          </Campo>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo
              etiqueta="Precio"
              error={errores.precio}
              ayuda={esNuevo ? 'Incluye el impuesto (IGV)' : 'Si lo cambias, las ventas pasadas conservan su precio'}
            >
              <input inputMode="decimal" value={datos.precio} onChange={cambiar('precio')} placeholder="49.90" className={estiloInput(errores.precio)} />
            </Campo>
            <Campo etiqueta="Stock" error={errores.stock} ayuda="Unidades disponibles">
              <input inputMode="numeric" value={datos.stock} onChange={cambiar('stock')} className={estiloInput(errores.stock)} />
            </Campo>
          </div>

          <label className="flex items-start gap-3 rounded-lg border border-slate-200 p-3">
            <input type="checkbox" checked={datos.activo} onChange={cambiar('activo')} className="mt-1 h-4 w-4 accent-violet-600" />
            <span className="text-sm">
              <span className="font-medium text-slate-700">Activo</span>
              <span className="block text-slate-500">
                Si lo desmarcas, queda como borrador: no se ve en el catálogo ni se puede vender.
              </span>
            </span>
          </label>
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
