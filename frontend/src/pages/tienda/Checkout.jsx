import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import BuscadorDistrito from '../../components/BuscadorDistrito';
import Campo from '../../components/Campo';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import { useAuth } from '../../context/AuthContext';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
import { cotizarEnvio, listarPaises } from '../../services/envios';
import { crearDireccion, crearPedido, iniciarPago, listarDirecciones } from '../../services/tienda';
import { estiloInput } from '../../utils/estilos';
import { formatoMoneda } from '../../utils/formato';

/**
 * Checkout en dos columnas: a la izquierda Contacto, Entrega y Pago; a la derecha el resumen gris.
 * Al pagar: crea el pedido, pide a la pasarela (PayPal en modo de prueba) la página de pago y envía al cliente allí.
 */
export default function Checkout() {
  const { tienda, subdominio } = useOutletContext();
  const { usuario } = useAuth();
  const { items, subtotal } = useCarrito();

  const [direcciones, setDirecciones] = useState(null); // null = cargando
  const [idDireccion, setIdDireccion] = useState(null);
  const [nueva, setNueva] = useState(false);
  const [errorCarga, setErrorCarga] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  // Si el pedido ya se creó pero el pago no pudo iniciarse, al reintentar NO se crea otro pedido.
  const [pedidoCreado, setPedidoCreado] = useState(null);
  // Envío a la dirección elegida: { idDireccion, subtotal, datos } (datos null = calculando).
  const [envio, setEnvio] = useState(null);

  // Cada vez que cambia la dirección (o el subtotal, por el envío gratis) se pide el costo del envío.
  useEffect(() => {
    if (!idDireccion || nueva) return undefined;
    let vigente = true;
    cotizarEnvio(idDireccion, subtotal)
      .then((datos) => vigente && setEnvio({ idDireccion, subtotal, datos, error: '' }))
      .catch((e) => vigente && setEnvio({ idDireccion, subtotal, datos: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [idDireccion, nueva, subtotal]);

  // Solo vale la cotización de la dirección y el subtotal actuales.
  const cotizacion = envio && envio.idDireccion === idDireccion && envio.subtotal === subtotal && !nueva ? envio : null;
  const costoEnvio = cotizacion?.datos?.disponible ? Number(cotizacion.datos.costo) : null;
  const noEnvia = cotizacion?.datos && !cotizacion.datos.disponible;

  useEffect(() => {
    let vigente = true;
    listarDirecciones()
      .then((lista) => {
        if (!vigente) return;
        setDirecciones(lista);
        setIdDireccion(lista[0]?.idDireccion ?? null);
        setNueva(lista.length === 0);
      })
      .catch((e) => vigente && setErrorCarga(mensajeDeError(e)));
    return () => {
      vigente = false;
    };
  }, []);

  async function pagar() {
    setError('');
    setEnviando(true);
    try {
      let pedido = pedidoCreado;
      if (!pedido) {
        pedido = await crearPedido({
          idDireccionEnvio: idDireccion,
          lineas: items.map((i) => ({ idVariante: i.idVariante, cantidad: i.cantidad })),
        });
        setPedidoCreado(pedido);
      }
      const { urlPago } = await iniciarPago(pedido.idPedido);
      window.location.href = urlPago;
    } catch (e) {
      // 409 (stock insuficiente, etc.): se muestra el mensaje y no se continúa.
      setError(mensajeDeError(e));
      setEnviando(false);
    }
  }

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold">Tu carrito está vacío</h1>
        <Link to={`/tienda/${subdominio}`} className="inline-block rounded-md bg-[var(--tienda)] px-6 py-3 font-medium text-[var(--tienda-texto)]">
          Volver a la tienda
        </Link>
      </div>
    );
  }

  const resumen = (
    <div className="space-y-4">
      <ul className="space-y-4">
        {items.map((i) => (
          <li key={i.idVariante} className="flex items-center gap-4">
            <div className="relative w-16 shrink-0">
              <div className="overflow-hidden rounded-md border border-[#e3e3e3]">
                <ImagenProducto url={i.imagenUrl} nombre={i.nombre} />
              </div>
              <span className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-[#616161] px-1 text-xs text-white">
                {i.cantidad}
              </span>
            </div>
            <p className="flex-1 text-sm">{i.nombre}</p>
            <p className="text-sm">{formatoMoneda(i.precio * i.cantidad)}</p>
          </li>
        ))}
      </ul>
      <dl className="space-y-2 border-t border-[#d9d9d9] pt-4 text-sm">
        <div className="flex justify-between">
          <dt>Subtotal</dt>
          <dd>{formatoMoneda(subtotal)}</dd>
        </div>
        <div className="flex justify-between">
          <dt>Envío</dt>
          <dd className={costoEnvio == null ? 'text-[#616161]' : ''}>
            {costoEnvio != null
              ? (costoEnvio === 0 ? 'Gratis' : formatoMoneda(costoEnvio))
              : noEnvia ? 'No disponible' : cotizacion ? '-' : 'Se calcula según tu dirección'}
          </dd>
        </div>
        <div className="flex items-baseline justify-between pt-2 text-xl font-semibold">
          <dt>Total</dt>
          <dd>
            <span className="mr-2 text-xs font-normal text-[#616161]">{tienda.moneda ?? 'PEN'}</span>
            {formatoMoneda(subtotal + (costoEnvio ?? 0))}
          </dd>
        </div>
        <p className="text-xs text-[#616161]">
          {cotizacion?.datos?.internacional
            ? `Venta al extranjero: sin ${tienda.impuesto ?? 'impuesto'}. Los impuestos de importación los pagas al recibir.`
            : costoEnvio != null ? 'Impuestos incluidos.' : 'Más el costo de envío. Impuestos incluidos.'}
        </p>
      </dl>
    </div>
  );

  return (
    <div className="md:grid md:grid-cols-[1fr_minmax(340px,440px)] md:min-h-screen">
      <div className="px-4 py-8 md:flex md:justify-end md:px-10">
        <div className="w-full max-w-xl space-y-8">
          <Link to={`/tienda/${subdominio}`} className="block text-2xl font-semibold tracking-tight">
            {tienda.nombre}
          </Link>

          {/* En celular el resumen se pliega arriba */}
          <details className="rounded-md border border-[#e3e3e3] bg-[#f5f5f5] p-4 md:hidden">
            <summary className="cursor-pointer text-sm font-medium text-violet-700">
              Mostrar resumen del pedido · {formatoMoneda(subtotal)}
            </summary>
            <div className="mt-4">{resumen}</div>
          </details>

          {errorCarga && <Mensaje tipo="error" texto={errorCarga} />}

          <section className="space-y-2">
            <h2 className="text-lg font-semibold">Contacto</h2>
            <p className="rounded-md border border-[#d9d9d9] px-3 py-2.5 text-sm">{usuario.correo}</p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">Entrega</h2>
            {direcciones === null && !errorCarga && <p className="text-sm text-[#616161]">Cargando direcciones...</p>}

            {direcciones && direcciones.length > 0 && (
              <ul className="space-y-2">
                {direcciones.map((d) => (
                  <li key={d.idDireccion}>
                    <label
                      className={`flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm ${
                        idDireccion === d.idDireccion && !nueva ? 'border-[#303030] bg-[#f5f5f5]' : 'border-[#d9d9d9]'
                      } ${pedidoCreado ? 'cursor-not-allowed opacity-60' : ''}`}
                    >
                      <input
                        type="radio"
                        name="direccion"
                        disabled={Boolean(pedidoCreado)}
                        checked={idDireccion === d.idDireccion && !nueva}
                        onChange={() => {
                          setIdDireccion(d.idDireccion);
                          setNueva(false);
                        }}
                        className="mt-1"
                      />
                      <span>
                        {d.direccion}
                        <span className="block text-[#616161]">
                          {textoUbicacion(d, tienda.idPais)}
                          {d.referencia ? ` · ${d.referencia}` : ''}
                        </span>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}

            {direcciones && direcciones.length > 0 && !nueva && !pedidoCreado && (
              <button type="button" onClick={() => setNueva(true)} className="text-sm text-violet-700 hover:underline">
                Usar otra dirección
              </button>
            )}

            {/* Costo y plazo del envío a la dirección elegida */}
            {cotizacion?.datos?.disponible && (
              <div className="rounded-md border border-[#d9d9d9] bg-[#f5f5f5] px-3 py-2.5 text-sm">
                <p className="flex justify-between font-medium">
                  <span>{cotizacion.datos.internacional ? `Envío internacional a ${cotizacion.datos.pais}` : 'Envío estándar'}</span>
                  <span>{costoEnvio === 0 ? 'Gratis' : formatoMoneda(costoEnvio)}</span>
                </p>
                {cotizacion.datos.diasEntregaMax != null && (
                  <p className="text-[#616161]">
                    Llega en {cotizacion.datos.diasEntregaMin ?? 1} a {cotizacion.datos.diasEntregaMax} días hábiles
                  </p>
                )}
                {cotizacion.datos.envioGratisDesde != null && costoEnvio > 0 && (
                  <p className="text-[#616161]">Gratis en compras desde {formatoMoneda(cotizacion.datos.envioGratisDesde)}</p>
                )}
                {cotizacion.datos.mensaje && <p className="mt-1 text-xs text-[#616161]">{cotizacion.datos.mensaje}</p>}
              </div>
            )}
            {noEnvia && <Mensaje tipo="error" texto={cotizacion.datos.mensaje} />}
            {cotizacion?.error && <Mensaje tipo="error" texto={cotizacion.error} />}

            {nueva && direcciones && (
              <FormularioDireccion
                idPais={tienda.idPais}
                nivelTienda={tienda.nivelFinal}
                puedeCancelar={direcciones.length > 0}
                onCancelar={() => setNueva(false)}
                onGuardada={(d) => {
                  setDirecciones((lista) => [d, ...lista]);
                  setIdDireccion(d.idDireccion);
                  setNueva(false);
                }}
              />
            )}
          </section>

          <section className="space-y-2">
            <h2 className="text-lg font-semibold">Pago</h2>
            <p className="rounded-md border border-[#d9d9d9] bg-[#f5f5f5] px-3 py-3 text-sm text-[#616161]">
              Serás redirigido a PayPal para completar tu compra de forma segura. En este modo de
              prueba, el cobro se procesa en dólares (USD).
            </p>
          </section>

          {error && <Mensaje tipo="error" texto={error} />}

          <button
            type="button"
            onClick={pagar}
            disabled={enviando || !idDireccion || nueva || !cotizacion?.datos?.disponible}
            className="w-full rounded-md bg-[var(--tienda)] py-4 text-lg font-medium text-[var(--tienda-texto)] hover:brightness-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {enviando ? 'Procesando...' : 'Pagar ahora'}
          </button>
          {!idDireccion && direcciones && (
            <p className="text-center text-xs text-[#616161]">Guarda una dirección de entrega para continuar.</p>
          )}
        </div>
      </div>

      <aside className="hidden border-l border-[#d9d9d9] bg-[#f5f5f5] px-10 py-8 md:block">
        <div className="max-w-sm">{resumen}</div>
      </aside>
    </div>
  );
}

/**
 * Ubicación de una dirección: el distrito o la comuna de la lista oficial (más el país si no es el
 * de la tienda); si el país no tiene lista, ciudad, región, CP y país.
 */
function textoUbicacion(d, idPaisTienda) {
  const extranjera = d.idPais && d.idPais !== idPaisTienda;
  if (d.distrito) {
    return extranjera ? `${d.distrito}, ${d.pais}` : d.distrito;
  }
  return [d.ciudad, d.region, d.codigoPostal, d.pais].filter(Boolean).join(', ');
}

/**
 * Formulario de una dirección nueva. En un país con lista oficial (el de la tienda, Perú o Chile)
 * se busca el distrito o la comuna; en otro país se escriben la ciudad, la región y el código
 * postal (como en Shopify).
 */
function FormularioDireccion({ idPais, nivelTienda = 'Distrito', puedeCancelar, onCancelar, onGuardada }) {
  const [paises, setPaises] = useState([]);
  const [datos, setDatos] = useState({
    idPais, idDistrito: null, ciudad: '', region: '', codigoPostal: '', direccion: '', referencia: '', telefono: '+',
  });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    listarPaises()
      .then((lista) => {
        setPaises(lista);
        // El teléfono empieza con el prefijo del país de la tienda: +51 en Perú, +56 en Chile.
        const prefijo = lista.find((p) => p.idPais === idPais)?.prefijoTelefono;
        if (prefijo) setDatos((d) => (/^\+\d{0,4}$/.test(d.telefono.trim()) ? { ...d, telefono: prefijo } : d));
      })
      .catch(() => setPaises([]));
  }, [idPais]);

  const pais = paises.find((p) => p.idPais === datos.idPais);
  // Con lista oficial: se elige la división del último nivel (distrito, comuna) en vez de escribir la ciudad.
  const local = datos.idPais === idPais || Boolean(pais?.habilitado);
  const nivel = datos.idPais === idPais ? nivelTienda : (pais?.nivelFinal ?? 'Distrito');

  function cambiarPais(nuevo) {
    const prefijo = paises.find((p) => p.idPais === nuevo)?.prefijoTelefono ?? '+';
    setDatos((d) => ({
      ...d,
      idPais: nuevo,
      idDistrito: null,
      // Si el teléfono solo tenía el prefijo del país anterior, se cambia por el nuevo.
      telefono: /^\+\d{0,4}$/.test(d.telefono.trim()) ? prefijo : d.telefono,
    }));
    setErrores({});
  }

  async function guardar() {
    const e = {};
    if (local && !datos.idDistrito) e.idDistrito = `Elige tu ${nivel.toLowerCase()} de la lista`;
    if (!local && !datos.ciudad.trim()) e.ciudad = 'Escribe la ciudad';
    if (!local && pais?.requiereCodigoPostal && !datos.codigoPostal.trim()) e.codigoPostal = 'El código postal es obligatorio en este país';
    if (!datos.direccion.trim()) e.direccion = 'La dirección es obligatoria';
    if (!/^\+[1-9][0-9]{6,14}$/.test(datos.telefono.trim())) e.telefono = 'Formato internacional, por ejemplo +51987654321';
    setErrores(e);
    if (Object.keys(e).length > 0) return;

    setGuardando(true);
    setError('');
    try {
      const creada = await crearDireccion({
        idPais: datos.idPais,
        idDistrito: local ? datos.idDistrito : null,
        ciudad: local ? null : datos.ciudad.trim(),
        region: local ? null : datos.region.trim() || null,
        codigoPostal: datos.codigoPostal.trim() || null,
        direccion: datos.direccion.trim(),
        referencia: datos.referencia.trim() || null,
        telefono: datos.telefono.trim(),
      });
      onGuardada(creada);
    } catch (err) {
      if (err.response?.data?.campos) {
        setErrores(err.response.data.campos);
      } else {
        setError(mensajeDeError(err));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="space-y-4 rounded-md border border-[#d9d9d9] p-4">
      {error && <Mensaje tipo="error" texto={error} />}
      <Campo etiqueta="País o región">
        <select value={datos.idPais} onChange={(e) => cambiarPais(e.target.value)} className={estiloInput()}>
          {paises.length === 0 && <option value={idPais}>{idPais}</option>}
          {paises.map((p) => (
            <option key={p.idPais} value={p.idPais}>{p.nombre}</option>
          ))}
        </select>
      </Campo>
      {local ? (
        <BuscadorDistrito
          key={datos.idPais}
          idPais={datos.idPais}
          etiqueta={nivel}
          error={errores.idDistrito}
          onElegir={(id) => {
            setDatos((d) => ({ ...d, idDistrito: id }));
            setErrores((er) => ({ ...er, idDistrito: undefined }));
          }}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          <Campo etiqueta="Ciudad" error={errores.ciudad}>
            <input
              value={datos.ciudad}
              onChange={(e) => setDatos((d) => ({ ...d, ciudad: e.target.value }))}
              placeholder="Madrid"
              className={estiloInput(errores.ciudad)}
            />
          </Campo>
          <Campo etiqueta="Provincia o estado" error={errores.region}>
            <input
              value={datos.region}
              onChange={(e) => setDatos((d) => ({ ...d, region: e.target.value }))}
              className={estiloInput(errores.region)}
            />
          </Campo>
          <Campo etiqueta={pais?.requiereCodigoPostal ? 'Código postal' : 'Código postal (opcional)'} error={errores.codigoPostal}>
            <input
              value={datos.codigoPostal}
              onChange={(e) => setDatos((d) => ({ ...d, codigoPostal: e.target.value }))}
              placeholder="28001"
              className={estiloInput(errores.codigoPostal)}
            />
          </Campo>
        </div>
      )}
      <Campo etiqueta="Dirección" error={errores.direccion}>
        <input
          value={datos.direccion}
          onChange={(e) => setDatos((d) => ({ ...d, direccion: e.target.value }))}
          placeholder="Av. Larco 123"
          className={estiloInput(errores.direccion)}
        />
      </Campo>
      <Campo etiqueta="Referencia (opcional)">
        <input
          value={datos.referencia}
          onChange={(e) => setDatos((d) => ({ ...d, referencia: e.target.value }))}
          placeholder="Frente al parque"
          className={estiloInput()}
        />
      </Campo>
      <Campo etiqueta="Teléfono de contacto" error={errores.telefono} ayuda={`Con código de país: ${pais?.prefijoTelefono ?? '+51'}...`}>
        <input
          type="tel"
          value={datos.telefono}
          onChange={(e) => setDatos((d) => ({ ...d, telefono: e.target.value }))}
          className={estiloInput(errores.telefono)}
        />
      </Campo>
      <div className="flex gap-3">
        <button
          type="button"
          onClick={guardar}
          disabled={guardando}
          className="rounded-md bg-[var(--tienda)] px-5 py-2.5 text-sm font-medium text-[var(--tienda-texto)] hover:brightness-90 disabled:opacity-60"
        >
          {guardando ? 'Guardando...' : 'Guardar dirección'}
        </button>
        {puedeCancelar && (
          <button type="button" onClick={onCancelar} className="rounded-md border border-[#c9c9c9] px-5 py-2.5 text-sm">
            Cancelar
          </button>
        )}
      </div>
    </div>
  );
}
