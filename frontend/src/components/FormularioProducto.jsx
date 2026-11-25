import { useEffect, useRef, useState } from 'react';
import { mensajeDeError } from '../services/api';
import { listarCategorias } from '../services/categorias';
import { borrarImagen, revisarArchivo, subirImagen } from '../services/imagenes';
import { actualizarProducto, crearProducto } from '../services/productos';
import { ejemploMonto, esMontoValido, montoVacio, simboloMoneda } from '../utils/formato';
import { claveVariante, combinaciones, MAX_VARIANTES } from '../utils/variantes';
import EditorVariantes from './EditorVariantes';
import { IconoCerrar } from './Iconos';
import ImagenProducto from './ImagenProducto';

const MAX_DESCRIPCION = 500;
const MAX_IMAGENES = 5;
const PATRON_URL = /^https?:\/\/\S+$/;

const aNumero = (texto) => Number(texto.trim().replace(',', '.'));

// Mismas reglas que el backend (ProductoRequest): el error se ve al instante,
// sin esperar al servidor. El backend vuelve a validar igual.
/** Filas iniciales de las variantes de un producto que ya existe, por combinación. */
function filasIniciales(producto) {
  const filas = {};
  if (!producto?.opciones?.length) return filas;
  for (const v of producto.variantes ?? []) {
    filas[claveVariante(v.valores)] = {
      idVariante: v.idVariante,
      precio: v.precio != null ? String(v.precio) : '',
      precioComparacion: v.precioComparacion != null ? String(v.precioComparacion) : '',
      stock: String(v.stock ?? 0),
      sku: v.sku ?? '',
    };
  }
  return filas;
}

/** Reglas de las opciones y variantes (las mismas que revisa el backend). */
function validarVariantes(opciones, filas, precioBase) {
  const e = {};
  const nombres = new Set();
  opciones.forEach((o, i) => {
    const nombre = o.nombre.trim().toLowerCase();
    if (!nombre) e[`opcion${i}`] = 'Escribe el nombre de la opción';
    else if (nombres.has(nombre)) e[`opcion${i}`] = 'Esta opción está repetida';
    else if (o.valores.length === 0) e[`opcion${i}`] = 'Agrega al menos un valor';
    nombres.add(nombre);
  });
  const combos = combinaciones(opciones);
  if (combos.length > MAX_VARIANTES) e.variantes = `Máximo ${MAX_VARIANTES} variantes`;
  for (const valores of combos) {
    const clave = claveVariante(valores);
    const f = filas[clave] ?? {};
    const precio = (f.precio ?? '').trim() || precioBase.trim();
    const comparacion = (f.precioComparacion ?? '').trim();
    let error = '';
    if (!esMontoValido(precio) || Number(precio.replace(',', '.')) <= 0) error = 'Precio no válido';
    else if (comparacion && (!esMontoValido(comparacion)
      || Number(comparacion.replace(',', '.')) <= Number(precio.replace(',', '.')))) error = 'El precio "antes" debe ser mayor';
    else if ((f.stock ?? '').trim() && !/^\d+$/.test(f.stock.trim())) error = 'Stock no válido';
    else if ((f.sku ?? '').trim().length > 60) error = 'SKU muy largo';
    if (error) e[`fila:${clave}`] = error;
  }
  return e;
}

function validar(datos) {
  const e = {};
  const nombre = datos.nombre.trim();
  if (nombre.length < 3 || nombre.length > 200) e.nombre = 'El nombre debe tener entre 3 y 200 caracteres';
  if (datos.descripcion.length > MAX_DESCRIPCION) e.descripcion = `La descripción puede tener hasta ${MAX_DESCRIPCION} caracteres`;
  if (datos.marca.trim().length > 100) e.marca = 'Máximo 100 caracteres';
  if (datos.preguntaCliente.trim().length > 120) e.preguntaCliente = 'Máximo 120 caracteres';

  // Precio: número mayor que 0 con los decimales de la moneda de la tienda
  // (soles: hasta 2, pesos chilenos: ninguno), como exige la base (T16).
  const precio = datos.precio.trim().replace(',', '.');
  if (!esMontoValido(precio)) e.precio = `Escribe un precio válido, ${ejemploMonto()}`;
  else if (Number(precio) <= 0) e.precio = 'El precio debe ser mayor que 0';

  // Precio de comparación: opcional; si se escribe, debe ser mayor que el precio (si no, no es rebaja).
  const comparacion = datos.precioComparacion.trim().replace(',', '.');
  if (comparacion && !datos.conVariantes) {
    if (!esMontoValido(comparacion)) e.precioComparacion = `Escribe un precio válido, ${ejemploMonto()}`;
    else if (!e.precio && Number(comparacion) <= Number(precio)) e.precioComparacion = 'Debe ser mayor que el precio';
  }

  if (datos.sku.trim().length > 60) e.sku = 'El SKU puede tener hasta 60 caracteres';

  // Stock: entero mayor o igual a 0 (con variantes, el stock va en cada una).
  if (!datos.conVariantes && !/^\d+$/.test(datos.stock.trim())) e.stock = 'El stock debe ser un número entero mayor o igual a 0';
  return e;
}

/**
 * Formulario para crear o editar un producto, en una ventana sobre la tabla.
 * Está ordenado por tarjetas como la ficha de producto de Shopify: datos, fotos, precio,
 * inventario y estado.
 * - producto = null → crear.  - producto = objeto → editar (llega con sus datos actuales).
 * Al editar el precio, el backend cierra el precio anterior y crea uno nuevo: las ventas
 * pasadas conservan el precio con el que se hicieron.
 */
export default function FormularioProducto({ idTienda, impuesto = 'IGV', producto, onGuardado, onCerrar }) {
  const esNuevo = !producto;
  const [datos, setDatos] = useState({
    nombre: producto?.nombre ?? '',
    descripcion: producto?.descripcion ?? '',
    marca: producto?.marca ?? '',
    preguntaCliente: producto?.preguntaCliente ?? '',
    precio: producto?.precio != null ? String(producto.precio) : '',
    precioComparacion: producto?.precioComparacion != null ? String(producto.precioComparacion) : '',
    sku: producto?.sku ?? '',
    stock: producto?.stock != null ? String(producto.stock) : '0',
    // Al crear queda activo; al editar se respeta su estado (BORRADOR = desactivado).
    activo: producto ? producto.estado === 'ACTIVO' : true,
  });
  const [imagenes, setImagenes] = useState(producto?.imagenes ?? []);
  const [categorias, setCategorias] = useState(producto?.categorias ?? []);
  const [opciones, setOpciones] = useState(producto?.opciones ?? []);
  const [filas, setFilas] = useState(() => filasIniciales(producto));
  const conVariantes = opciones.length > 0;
  const [todasCategorias, setTodasCategorias] = useState(null);   // null = cargando
  const [nuevaImagen, setNuevaImagen] = useState('');
  const [subiendo, setSubiendo] = useState(false);
  const [arrastrando, setArrastrando] = useState(false);
  const campoArchivo = useRef(null);
  /**
   * Fotos subidas en este formulario que todavía no se guardaron (url → public_id).
   * Si el dueño quita una antes de guardar, hay que pedir que se borre: si no, queda
   * ocupando espacio en Cloudinary sin que ningún producto la use.
   */
  const subidasSinGuardar = useRef(new Map());
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (campo) => (evento) => {
    const valor = evento.target.value;
    setDatos((d) => ({ ...d, [campo]: valor }));
    setErrores((e) => ({ ...e, [campo]: undefined }));   // al corregir un campo se quita su error
  };

  // Categorías de la tienda, para marcar a cuáles pertenece el producto.
  useEffect(() => {
    let vigente = true;
    listarCategorias(idTienda)
      .then((lista) => vigente && setTodasCategorias(lista))
      .catch(() => vigente && setTodasCategorias([]));
    return () => {
      vigente = false;
    };
  }, [idTienda]);

  function alternarCategoria(id) {
    setCategorias((lista) => (lista.includes(id) ? lista.filter((c) => c !== id) : [...lista, id]));
  }

  // Cerrar con la tecla Escape, como cualquier ventana.
  useEffect(() => {
    const alPresionar = (e) => e.key === 'Escape' && onCerrar();
    window.addEventListener('keydown', alPresionar);
    return () => window.removeEventListener('keydown', alPresionar);
  }, [onCerrar]);

  function agregarImagen() {
    const url = nuevaImagen.trim();
    if (!PATRON_URL.test(url)) {
      setErrores((e) => ({ ...e, imagenes: 'Pega un enlace que empiece con http:// o https://' }));
      return;
    }
    if (url.length > 500) {
      setErrores((e) => ({ ...e, imagenes: 'El enlace puede tener hasta 500 caracteres' }));
      return;
    }
    if (imagenes.includes(url)) {
      setErrores((e) => ({ ...e, imagenes: 'Esa imagen ya está agregada' }));
      return;
    }
    setImagenes((lista) => [...lista, url]);
    setNuevaImagen('');
    setErrores((e) => ({ ...e, imagenes: undefined }));
  }

  /** Sube los archivos elegidos (o arrastrados) y los agrega a la lista, en orden. */
  async function subirArchivos(archivos) {
    const elegidos = Array.from(archivos ?? []).slice(0, MAX_IMAGENES - imagenes.length);
    if (elegidos.length === 0) {
      return;
    }
    setSubiendo(true);
    setErrores((e) => ({ ...e, imagenes: undefined }));
    for (const archivo of elegidos) {
      const problema = revisarArchivo(archivo);
      if (problema) {
        setErrores((e) => ({ ...e, imagenes: problema }));
        continue;
      }
      try {
        const { url, publicId } = await subirImagen(idTienda, archivo);
        subidasSinGuardar.current.set(url, publicId);
        setImagenes((lista) => (lista.includes(url) ? lista : [...lista, url]));
      } catch (e) {
        setErrores((er) => ({ ...er, imagenes: mensajeDeError(e) }));
      }
    }
    setSubiendo(false);
  }

  // La primera foto es la principal: este botón pasa una foto al primer lugar.
  function hacerPrincipal(indice) {
    setImagenes((lista) => [lista[indice], ...lista.filter((_, i) => i !== indice)]);
  }

  function quitarImagen(indice) {
    const url = imagenes[indice];
    const publicId = subidasSinGuardar.current.get(url);
    if (publicId) {
      // Se subió hace un momento y no se llegó a guardar: se pide borrarla. Si falla, no se
      // le dice nada al dueño (él solo quitó una foto; la limpieza es cosa nuestra).
      subidasSinGuardar.current.delete(url);
      borrarImagen(idTienda, publicId).catch(() => {});
    }
    setImagenes((lista) => lista.filter((_, i) => i !== indice));
  }

  async function enviar(evento) {
    evento.preventDefault();
    setErrorGeneral('');
    const encontrados = {
      ...validar({ ...datos, conVariantes }),
      ...(conVariantes ? validarVariantes(opciones, filas, datos.precio) : {}),
    };
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) {
      return;
    }

    const cuerpo = {
      nombre: datos.nombre.trim(),
      descripcion: datos.descripcion.trim() || null,
      marca: datos.marca.trim() || null,
      preguntaCliente: datos.preguntaCliente.trim() || null,
      precio: aNumero(datos.precio),
      // Con variantes, cada una lleva su precio "antes" (columna Antes): no se hereda del producto.
      precioComparacion: !conVariantes && datos.precioComparacion.trim() ? aNumero(datos.precioComparacion) : null,
      sku: conVariantes ? null : datos.sku.trim() || null,
      stock: conVariantes ? 0 : Number(datos.stock.trim()),
      activo: datos.activo,
      imagenes,
      categorias,
      opciones: opciones.map((o) => ({ nombre: o.nombre.trim(), valores: o.valores })),
      // Con opciones: una variante por combinación. Precio o stock vacíos = los del producto / 0.
      variantes: conVariantes
        ? combinaciones(opciones).map((valores, i) => {
          const f = filas[claveVariante(valores)] ?? {};
          // Un producto simple que pasa a tener variantes: su variante actual (con sus ventas e
          // historial de precios) se convierte en la primera combinación, en vez de borrarse.
          const heredada = i === 0 && !esNuevo && !producto.opciones?.length ? producto.idVariante : null;
          return {
            idVariante: f.idVariante ?? heredada,
            valores,
            precio: (f.precio ?? '').trim() ? aNumero(f.precio) : null,
            precioComparacion: (f.precioComparacion ?? '').trim() ? aNumero(f.precioComparacion) : null,
            sku: (f.sku ?? '').trim() || null,
            stock: (f.stock ?? '').trim() ? Number(f.stock.trim()) : 0,
          };
        })
        : null,
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
        // 400: cada error junto a su campo. Los de las fotos llegan como "imagenes[0]".
        const porCampo = {};
        Object.entries(campos).forEach(([clave, mensaje]) => {
          const campo = clave.startsWith('imagenes') ? 'imagenes'
            : clave.startsWith('opciones') || clave.startsWith('variantes') ? 'variantes' : clave;
          porCampo[campo] ??= mensaje;
        });
        setErrores(porCampo);
      } else if (e.response?.status === 409 && /nombre/i.test(texto)) {
        setErrores({ nombre: texto });                        // nombre repetido en la tienda
      } else if (/comparaci/i.test(texto)) {
        setErrores({ precioComparacion: texto });             // precio tachado menor o igual al precio
      } else {
        setErrorGeneral(texto);                               // otro error (p. ej. producto con varias variantes)
      }
    } finally {
      setEnviando(false);
    }
  }

  const precio = Number(datos.precio.replace(',', '.'));
  const comparacion = Number(datos.precioComparacion.replace(',', '.'));
  const descuento = precio > 0 && comparacion > precio ? Math.round((1 - precio / comparacion) * 100) : null;

  return (
    <div className="fixed inset-0 z-20 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:py-10">
      <form
        onSubmit={enviar}
        noValidate
        className="w-full max-w-4xl rounded-2xl bg-[#f1f1f1] shadow-2xl"
        role="dialog"
        aria-labelledby="titulo-formulario-producto"
      >
        <div className="flex items-center justify-between rounded-t-2xl border-b border-[#e3e3e3] bg-white px-5 py-3.5">
          <h2 id="titulo-formulario-producto" className="text-base font-semibold text-[#101010]">
            {esNuevo ? 'Agregar producto' : producto.nombre}
          </h2>
          <button type="button" onClick={onCerrar} className="rounded-lg p-1 text-[#616161] hover:bg-[#f1f1f1]" aria-label="Cerrar">
            <IconoCerrar tamano={18} />
          </button>
        </div>

        <div className="grid gap-4 p-4 md:grid-cols-[minmax(0,1fr)_240px]">
          <div className="space-y-4">
            {errorGeneral && (
              <p
                role="alert"
                ref={(el) => el?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
                className="rounded-lg border border-[#fecdca] bg-[#fee9e8] px-3 py-2 text-[13px] text-[#8e1f0b]"
              >
                {errorGeneral}
              </p>
            )}

            {/* Datos */}
            <Tarjeta>
              <Campo etiqueta="Título" error={errores.nombre}>
                <input value={datos.nombre} onChange={cambiar('nombre')} placeholder="Polo de algodón" className={estiloInput(errores.nombre)} />
              </Campo>
              <Campo
                etiqueta="Descripción"
                error={errores.descripcion}
                ayuda={`${datos.descripcion.length} / ${MAX_DESCRIPCION} caracteres`}
              >
                <textarea rows={4} value={datos.descripcion} onChange={cambiar('descripcion')} className={estiloInput(errores.descripcion)} />
              </Campo>
              <Campo
                etiqueta="Pregunta al cliente (opcional)"
                error={errores.preguntaCliente}
                ayuda="Si necesitas un dato para preparar el pedido. El cliente debe responderla antes de comprar."
              >
                <input
                  value={datos.preguntaCliente}
                  onChange={cambiar('preguntaCliente')}
                  maxLength={120}
                  placeholder="¿Qué texto va en la torta? · ¿Qué nombre grabamos?"
                  className={estiloInput(errores.preguntaCliente)}
                />
              </Campo>
            </Tarjeta>

            {/* Multimedia */}
            <Tarjeta titulo="Multimedia">
              {imagenes.length > 0 && (
                <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
                  {imagenes.map((url, i) => (
                    <li key={url} className={`group relative overflow-hidden rounded-lg border ${i === 0 ? 'border-[#303030]' : 'border-[#e3e3e3]'}`}>
                      <ImagenProducto url={url} nombre={datos.nombre} />
                      {i === 0 && (
                        <span className="absolute bottom-1 left-1 rounded bg-white/90 px-1.5 text-[11px] font-medium">Principal</span>
                      )}
                      <div className="absolute inset-x-0 top-0 flex justify-end gap-1 p-1 opacity-0 transition group-hover:opacity-100 group-focus-within:opacity-100">
                        {i > 0 && (
                          <button type="button" onClick={() => hacerPrincipal(i)} className="rounded bg-white/90 px-1.5 text-[11px] font-medium hover:bg-white">
                            Principal
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => quitarImagen(i)}
                          aria-label="Quitar imagen"
                          className="rounded bg-white/90 p-0.5 text-[#8e1f0b] hover:bg-white"
                        >
                          <IconoCerrar tamano={14} />
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
              {imagenes.length < MAX_IMAGENES ? (
                <div className="space-y-2">
                  {/* Subir desde la computadora: arrastrando o eligiendo el archivo. */}
                  <div
                    onDragOver={(e) => {
                      e.preventDefault();
                      setArrastrando(true);
                    }}
                    onDragLeave={() => setArrastrando(false)}
                    onDrop={(e) => {
                      e.preventDefault();
                      setArrastrando(false);
                      subirArchivos(e.dataTransfer.files);
                    }}
                    className={`rounded-lg border-2 border-dashed p-4 text-center text-[13px] transition ${
                      arrastrando ? 'border-[#1650d0] bg-[#eaf0ff]' : 'border-[#ddd] bg-[#fafafa]'
                    }`}
                  >
                    {subiendo ? (
                      <p className="text-[#616161]">Subiendo...</p>
                    ) : (
                      <>
                        <p className="text-[#303030]">Arrastra tus fotos aquí</p>
                        <button
                          type="button"
                          onClick={() => campoArchivo.current?.click()}
                          className="mt-1.5 rounded-lg border border-[#ddd] bg-white px-3 py-1.5 font-medium hover:bg-[#f7f7f7]"
                        >
                          Elegir del equipo
                        </button>
                        <p className="mt-1.5 text-[12px] text-[#616161]">JPG, PNG o WEBP · hasta 2 MB</p>
                      </>
                    )}
                    <input
                      ref={campoArchivo}
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      multiple
                      hidden
                      onChange={(e) => {
                        subirArchivos(e.target.files);
                        e.target.value = '';   // permite volver a elegir el mismo archivo
                      }}
                    />
                  </div>

                  <div className="rounded-lg border border-dashed border-[#b5b5b5] p-3">
                  <div className="flex gap-2">
                    <input
                      value={nuevaImagen}
                      onChange={(e) => {
                        setNuevaImagen(e.target.value);
                        setErrores((er) => ({ ...er, imagenes: undefined }));
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          agregarImagen();
                        }
                      }}
                      placeholder="https://... enlace de la foto"
                      aria-label="Enlace de la imagen"
                      className={estiloInput(errores.imagenes)}
                    />
                    <button
                      type="button"
                      onClick={agregarImagen}
                      className="shrink-0 rounded-lg border border-[#ddd] bg-white px-3 text-[13px] font-medium hover:bg-[#f7f7f7]"
                    >
                      Agregar
                    </button>
                  </div>
                  <p className={`mt-1.5 text-[12px] ${errores.imagenes ? 'text-[#8e1f0b]' : 'text-[#616161]'}`}>
                    {errores.imagenes ?? `O pega el enlace de una foto (hasta ${MAX_IMAGENES}). La primera es la principal.`}
                  </p>
                  </div>
                </div>
              ) : (
                <p className="text-[12px] text-[#616161]">Llegaste al máximo de {MAX_IMAGENES} imágenes.</p>
              )}
            </Tarjeta>

            {/* Precio */}
            <Tarjeta titulo="Precio">
              <div className="grid gap-3 sm:grid-cols-2">
                <Campo
                  etiqueta={conVariantes ? 'Precio por defecto' : 'Precio'}
                  error={errores.precio}
                  ayuda={conVariantes ? `Para las variantes que dejes sin precio. Incluye ${impuesto}` : `Incluye el impuesto (${impuesto})`}
                >
                  <ConPrefijo>
                    <input inputMode="decimal" value={datos.precio} onChange={cambiar('precio')} placeholder={montoVacio()} className={estiloInput(errores.precio, true)} />
                  </ConPrefijo>
                </Campo>
                {!conVariantes && (
                <Campo
                  etiqueta="Precio de comparación"
                  error={errores.precioComparacion}
                  ayuda={descuento ? `Se mostrará tachado: ${descuento} % de descuento` : 'Opcional. El precio "antes", que se ve tachado'}
                >
                  <ConPrefijo>
                    <input
                      inputMode="decimal"
                      value={datos.precioComparacion}
                      onChange={cambiar('precioComparacion')}
                      placeholder={montoVacio()}
                      className={estiloInput(errores.precioComparacion, true)}
                    />
                  </ConPrefijo>
                </Campo>
                )}
              </div>
              {!esNuevo && (
                <p className="text-[12px] text-[#616161]">Si cambias el precio, las ventas pasadas conservan el precio con que se hicieron.</p>
              )}
            </Tarjeta>

            {/* Variantes: opciones como talla o color, cada combinación con su precio y stock */}
            <Tarjeta titulo="Variantes">
              <EditorVariantes
                opciones={opciones}
                onOpciones={(lista) => {
                  setOpciones(lista);
                  setErrores((er) => ({ ...er, variantes: undefined }));
                }}
                filas={filas}
                onFilas={setFilas}
                precioBase={datos.precio}
                errores={errores}
              />
              {conVariantes && !esNuevo && producto.opciones?.length === 0 && (
                <p className="text-[12px] text-[#616161]">
                  La primera combinación ({combinaciones(opciones)[0]?.join(' / ') ?? '-'}) conserva el historial de ventas del producto.
                </p>
              )}
            </Tarjeta>

            {/* Inventario (solo producto simple: con variantes, cada una tiene su stock y SKU) */}
            {!conVariantes && (
            <Tarjeta titulo="Inventario">
              <div className="grid gap-3 sm:grid-cols-2">
                <Campo etiqueta="Cantidad disponible" error={errores.stock}>
                  <input inputMode="numeric" value={datos.stock} onChange={cambiar('stock')} className={estiloInput(errores.stock)} />
                </Campo>
                <Campo etiqueta="SKU (código de referencia)" error={errores.sku} ayuda="Opcional. Tu código interno">
                  <input value={datos.sku} onChange={cambiar('sku')} placeholder="POLO-NEG-M" className={estiloInput(errores.sku)} />
                </Campo>
              </div>
              <p className="text-[12px] text-[#616161]">Para sumar mercadería que llega, usa Productos → Inventario → Recibir.</p>
            </Tarjeta>
            )}
          </div>

          {/* Columna derecha */}
          <div className="space-y-4">
            <Tarjeta titulo="Estado">
              <select
                value={datos.activo ? 'ACTIVO' : 'BORRADOR'}
                onChange={(e) => setDatos((d) => ({ ...d, activo: e.target.value === 'ACTIVO' }))}
                className={estiloInput()}
                aria-label="Estado"
              >
                <option value="ACTIVO">Activo</option>
                <option value="BORRADOR">Borrador</option>
              </select>
              <p className="text-[12px] text-[#616161]">
                {datos.activo ? 'Se ve en tu tienda online y se puede vender.' : 'No se ve en la tienda ni se puede vender.'}
              </p>
            </Tarjeta>

            <Tarjeta titulo="Organización">
              <Campo etiqueta="Marca" error={errores.marca}>
                <input value={datos.marca} onChange={cambiar('marca')} className={estiloInput(errores.marca)} />
              </Campo>
              <div>
                <p className="text-[13px] text-[#303030]">Categorías</p>
                {todasCategorias === null ? (
                  <p className="mt-1 text-[12px] text-[#616161]">Cargando...</p>
                ) : todasCategorias.length === 0 ? (
                  <p className="mt-1 text-[12px] text-[#616161]">Crea categorías en Productos → Categorías.</p>
                ) : (
                  <ul className="mt-1 max-h-40 space-y-1 overflow-y-auto">
                    {todasCategorias.map((c) => (
                      <li key={c.idCategoria}>
                        <label className="flex items-center gap-2 text-[13px]">
                          <input
                            type="checkbox"
                            checked={categorias.includes(c.idCategoria)}
                            onChange={() => alternarCategoria(c.idCategoria)}
                          />
                          {c.nombre}
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </Tarjeta>
          </div>
        </div>

        <div className="flex justify-end gap-2 rounded-b-2xl border-t border-[#e3e3e3] bg-white px-5 py-3">
          <button type="button" onClick={onCerrar} className="rounded-lg border border-[#ddd] px-3 py-1.5 font-medium text-[#303030] hover:bg-[#f7f7f7]">
            Cancelar
          </button>
          <button
            type="submit"
            disabled={enviando}
            className="rounded-lg bg-[#303030] px-3 py-1.5 font-semibold text-white hover:bg-[#1a1a1a] disabled:opacity-60"
          >
            {enviando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </div>
  );
}

/** Caja blanca con título, como las tarjetas de la ficha de producto de Shopify. */
function Tarjeta({ titulo, children }) {
  return (
    <section className="space-y-3 rounded-xl bg-white p-4 shadow-[0_1px_0_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.05)]">
      {titulo && <h3 className="text-[13px] font-semibold text-[#101010]">{titulo}</h3>}
      {children}
    </section>
  );
}

/** Etiqueta + campo + mensaje de error (o de ayuda) debajo. */
function Campo({ etiqueta, error, ayuda, children }) {
  return (
    <label className="block">
      <span className="text-[13px] text-[#303030]">{etiqueta}</span>
      <div className="mt-1">{children}</div>
      {error ? (
        <span className="mt-1 block text-[12px] text-[#8e1f0b]">{error}</span>
      ) : (
        ayuda && <span className="mt-1 block text-[12px] text-[#616161]">{ayuda}</span>
      )}
    </label>
  );
}

/** Pone el símbolo de la moneda de la tienda ("S/" o "$") delante del input de un monto. */
function ConPrefijo({ children }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[13px] text-[#616161]">{simboloMoneda()}</span>
      {children}
    </div>
  );
}

function estiloInput(error, conPrefijo = false) {
  return `w-full rounded-lg border bg-white py-1.5 text-[13px] focus:outline-none focus:ring-2 ${conPrefijo ? 'pl-8 pr-3' : 'px-3'} ${
    error ? 'border-[#e51c00] focus:ring-[#fecdca]' : 'border-[#8a8a8a]/50 focus:border-[#1650d0] focus:ring-[#c9d7f8]'
  }`;
}
