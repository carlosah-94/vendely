import { useEffect, useState } from 'react';
import { Link, useOutletContext } from 'react-router-dom';
import BuscadorDistrito from '../../components/BuscadorDistrito';
import Campo from '../../components/Campo';
import ImagenProducto from '../../components/ImagenProducto';
import Mensaje from '../../components/Mensaje';
import { useAuth } from '../../context/AuthContext';
import { useCarrito } from '../../context/CarritoContext';
import { mensajeDeError } from '../../services/api';
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
        <Link to={`/tienda/${subdominio}`} className="inline-block rounded-md bg-[#303030] px-6 py-3 font-medium text-white">
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
        <div className="flex justify-between text-[#616161]">
          <dt>Envío</dt>
          <dd>Se calcula según tu dirección</dd>
        </div>
        <div className="flex items-baseline justify-between pt-2 text-xl font-semibold">
          <dt>Total</dt>
          <dd>
            <span className="mr-2 text-xs font-normal text-[#616161]">PEN</span>
            {formatoMoneda(subtotal)}
          </dd>
        </div>
        <p className="text-xs text-[#616161]">Más el costo de envío. Impuestos incluidos.</p>
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
                          {d.distrito}
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

            {nueva && direcciones && (
              <FormularioDireccion
                idPais={tienda.idPais}
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
            disabled={enviando || !idDireccion || nueva}
            className="w-full rounded-md bg-[#303030] py-4 text-lg font-medium text-white hover:bg-black disabled:cursor-not-allowed disabled:opacity-50"
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

/** Formulario de una dirección nueva, con el buscador de distritos del país de la tienda. */
function FormularioDireccion({ idPais, puedeCancelar, onCancelar, onGuardada }) {
  const [datos, setDatos] = useState({ idDistrito: null, direccion: '', referencia: '', telefono: '+51' });
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    const e = {};
    if (!datos.idDistrito) e.idDistrito = 'Elige un distrito de la lista';
    if (!datos.direccion.trim()) e.direccion = 'La dirección es obligatoria';
    if (!/^\+[1-9][0-9]{6,14}$/.test(datos.telefono.trim())) e.telefono = 'Formato internacional, por ejemplo +51987654321';
    setErrores(e);
    if (Object.keys(e).length > 0) return;

    setGuardando(true);
    setError('');
    try {
      const creada = await crearDireccion({
        idDistrito: datos.idDistrito,
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
      <BuscadorDistrito
        idPais={idPais}
        error={errores.idDistrito}
        onElegir={(id) => {
          setDatos((d) => ({ ...d, idDistrito: id }));
          setErrores((er) => ({ ...er, idDistrito: undefined }));
        }}
      />
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
      <Campo etiqueta="Teléfono de contacto" error={errores.telefono} ayuda="Con código de país: +51987654321">
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
          className="rounded-md bg-[#303030] px-5 py-2.5 text-sm font-medium text-white hover:bg-black disabled:opacity-60"
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
