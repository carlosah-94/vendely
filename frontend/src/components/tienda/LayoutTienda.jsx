import { useEffect, useState } from 'react';
import { Link, Outlet, useMatch, useParams } from 'react-router-dom';
import { CarritoProvider, useCarrito } from '../../context/CarritoContext';
import { useAuth } from '../../context/AuthContext';
import { mensajeDeError } from '../../services/api';
import { paisesDeEnvio } from '../../services/envios';
import { usarMoneda } from '../../utils/formato';
import { disenoDe, estiloTienda } from '../../utils/diseno';
import { obtenerTienda } from '../../services/tienda';
import { IconoBuscar, IconoCarrito, IconoCuenta } from '../Iconos';
import CarritoDrawer from './CarritoDrawer';

/** "Envíos a todo Perú y a España y Chile" según las tarifas de la tienda. */
function textoEnvios(paises) {
  const local = paises.find((p) => !p.internacional);
  const fuera = paises.filter((p) => p.internacional).map((p) => p.nombre);
  if (!local) return 'Envíos a todo el país';
  if (fuera.length === 0) return `Envíos a todo ${local.nombre}`;
  if (fuera.length > 3) return `Envíos a todo ${local.nombre} y a ${fuera.length} países más`;
  const lista = fuera.length === 1 ? fuera[0] : `${fuera.slice(0, -1).join(', ')} y ${fuera.at(-1)}`;
  return `Envíos a todo ${local.nombre} y a ${lista}`;
}

/** Mensaje con el que Personalizar tienda manda el borrador a la vista previa (un iframe). */
export const MENSAJE_VISTA_PREVIA = 'vendely:vista-previa';

/**
 * Vista previa de Personalizar tienda: la tienda se abre dentro de un iframe del panel y el panel
 * le manda, mientras el dueño edita, lo que todavía no guardó (nombre, colores, plantilla, diseño).
 * Solo se aceptan mensajes del mismo origen y solo si la tienda está dentro de un iframe: un
 * visitante normal nunca ve un borrador. No se guarda nada; al recargar vuelve lo publicado.
 */
function useBorradorVistaPrevia() {
  const [borrador, setBorrador] = useState(null);
  useEffect(() => {
    if (window.parent === window) return undefined;
    function recibir(e) {
      if (e.origin !== window.location.origin || e.source !== window.parent) return;
      if (e.data?.tipo === MENSAJE_VISTA_PREVIA) setBorrador(e.data.tienda ?? null);
    }
    window.addEventListener('message', recibir);
    // Avisa al panel que ya puede mandar el borrador.
    window.parent.postMessage({ tipo: `${MENSAJE_VISTA_PREVIA}:lista` }, window.location.origin);
    return () => window.removeEventListener('message', recibir);
  }, []);
  return borrador;
}

/**
 * Marco de la tienda pública: barra de anuncio, cabecera con carrito, pie y carrito lateral.
 * Carga los datos públicos de la tienda; si no existe, muestra "Tienda no encontrada".
 * Las páginas reciben { tienda, subdominio } con useOutletContext().
 */
export default function LayoutTienda() {
  const { subdominio } = useParams();
  // key: al cambiar de tienda se descarta el carrito en memoria y se lee el de la nueva.
  return (
    <CarritoProvider key={subdominio} subdominio={subdominio}>
      <Marco subdominio={subdominio} />
    </CarritoProvider>
  );
}

function Marco({ subdominio }) {
  const [estado, setEstado] = useState({ subdominio: null, tienda: null, error: '' });
  const [paises, setPaises] = useState([]);
  const enCheckout = useMatch('/tienda/:subdominio/checkout');
  const borrador = useBorradorVistaPrevia();

  // Países a los que vende la tienda: para la barra de anuncio y el pie.
  useEffect(() => {
    let vigente = true;
    paisesDeEnvio(subdominio)
      .then((lista) => vigente && setPaises(lista))
      .catch(() => vigente && setPaises([]));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  useEffect(() => {
    let vigente = true;
    obtenerTienda(subdominio)
      .then((tienda) => vigente && setEstado({ subdominio, tienda, error: '' }))
      .catch((e) => vigente && setEstado({ subdominio, tienda: null, error: mensajeDeError(e) }));
    return () => {
      vigente = false;
    };
  }, [subdominio]);

  if (estado.subdominio !== subdominio) {
    return <p className="p-10 text-center text-[#616161]">Cargando tienda...</p>;
  }
  if (!estado.tienda) {
    return (
      <div className="mx-auto max-w-md p-10 text-center">
        <h1 className="text-2xl font-semibold text-[#303030]">Tienda no encontrada</h1>
        <p className="mt-2 text-[#616161]">Revisa el enlace: la tienda “{subdominio}” no existe o no está disponible.</p>
      </div>
    );
  }

  const tienda = borrador ? { ...estado.tienda, ...borrador } : estado.tienda;
  const contexto = { tienda, subdominio, paises };
  const diseno = disenoDe(tienda);
  // Precios, carrito y checkout en la moneda de la tienda: soles en Perú, pesos en Chile.
  usarMoneda(tienda.moneda);
  // Plantilla, letra, botones y colores (--tienda, --acento, --fondo...) como clases y variables
  // CSS: las páginas usan bg-[var(--tienda)], bg-[var(--fondo)]... Ver utils/diseno.js.
  const estilo = estiloTienda(tienda);

  // El checkout va sin cabecera ni pie: solo el nombre de la tienda arriba (lo pone la propia página).
  if (enCheckout) {
    return (
      <div className={`min-h-screen text-[#303030] ${estilo.className}`} style={estilo.style}>
        <Outlet context={contexto} />
      </div>
    );
  }

  return (
    <div className={`flex min-h-screen flex-col text-[#303030] ${estilo.className}`} style={{ fontSize: 16, ...estilo.style }}>
      {/* Barra de anuncio: el texto del dueño (Básico y Pro) o, si no hay, a dónde envía */}
      {diseno.mostrarAnuncio && (
        <div
          className={`px-4 py-2 text-center text-xs tracking-wide ${
            tienda.plantilla === 'boutique'
              ? 'border-b border-[var(--fondo-borde)] text-[#6b6b6b]'
              : 'bg-[var(--tienda)] text-[var(--tienda-texto)]'
          }`}
        >
          {diseno.textoAnuncio || textoEnvios(paises)}
        </div>
      )}
      <Cabecera tienda={tienda} subdominio={subdominio} />
      <main className="flex-1">
        <Outlet context={contexto} />
      </main>
      <footer
        className={`mt-16 px-4 py-10 text-center text-sm ${
          tienda.plantilla === 'moderna'
            ? 'bg-[#141414] text-white/70'
            : tienda.plantilla === 'boutique'
              ? 'border-t border-[var(--fondo-borde)] text-[#6b6b6b]'
              : 'bg-[var(--fondo-suave)] text-[#616161]'
        }`}
      >
        <p
          className={
            tienda.plantilla === 'moderna'
              ? 'letra-ancha text-2xl text-white'
              : tienda.plantilla === 'boutique'
                ? 'letra-serifa text-3xl text-[#222]'
                : 'text-base font-semibold text-[#303030]'
          }
        >
          {tienda.nombre}
        </p>
        {tienda.correoContacto && <p className="mt-1">{tienda.correoContacto}</p>}
        {paises.length > 0 && (
          <p className="mt-3 text-xs">Enviamos a: {paises.map((p) => p.nombre).join(' · ')}</p>
        )}
        <p className="mt-4 text-xs">
          © 2026 · {tienda.nombre}
          {/* Plan Gratis: el sello de Vendely en el pie (los planes pagados no lo llevan) */}
          {tienda.selloVendely && <> · Hecho con <b>Vendely</b></>}
        </p>
      </footer>
      <CarritoDrawer subdominio={subdominio} />
    </div>
  );
}

function Cabecera({ tienda, subdominio }) {
  const { usuario, cerrarSesion } = useAuth();
  const { cantidadTotal, abrir } = useCarrito();
  const esCliente = usuario?.rol === 'CLIENTE' && usuario.idTienda === tienda.idTienda;
  const base = `/tienda/${subdominio}`;

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--fondo-borde)] bg-[var(--fondo)]/95 backdrop-blur">
      <div className="mx-auto grid max-w-6xl grid-cols-[1fr_auto_1fr] items-center gap-2 px-4 py-4">
        <nav className="hidden gap-5 text-sm sm:flex">
          <Link to={base} className="hover:underline">Inicio</Link>
          <a href={`${base}#catalogo`} className="hover:underline">Catálogo</a>
        </nav>
        <Link
          to={base}
          className={`col-start-2 flex items-center justify-center gap-2 text-center ${
            tienda.plantilla === 'moderna'
              ? 'letra-ancha text-xl sm:text-2xl'
              : tienda.plantilla === 'boutique'
                ? 'letra-serifa text-2xl font-medium sm:text-3xl'
                : 'text-xl font-semibold tracking-tight sm:text-2xl'
          }`}
        >
          {tienda.logoUrl && <img src={tienda.logoUrl} alt="" className="h-9 w-9 rounded-md object-contain" />}
          {tienda.nombre}
        </Link>
        <div className="col-start-3 flex items-center justify-end gap-4">
          <a href={`${base}#catalogo`} aria-label="Buscar" className="hidden sm:block"><IconoBuscar /></a>
          {esCliente ? (
            <>
              {usuario.nombres && (
                <span className="hidden text-sm lg:inline">Hola, {usuario.nombres.trim().split(/\s+/)[0]}</span>
              )}
              <Link to={`${base}/mis-pedidos`} className="flex items-center gap-1 text-sm hover:underline">
                <IconoCuenta /> <span className="hidden md:inline">Mis pedidos</span>
              </Link>
              <button type="button" onClick={cerrarSesion} className="hidden text-sm hover:underline md:block">
                Salir
              </button>
            </>
          ) : (
            <Link to={`${base}/ingresar`} className="flex items-center gap-1 text-sm hover:underline">
              <IconoCuenta /> <span className="hidden md:inline">Ingresar</span>
            </Link>
          )}
          <button type="button" onClick={abrir} aria-label={`Carrito, ${cantidadTotal} productos`} className="relative">
            <IconoCarrito />
            {cantidadTotal > 0 && (
              <span className="absolute -right-2 -top-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-[var(--tienda)] px-1 text-[10px] font-medium text-[var(--tienda-texto)]">
                {cantidadTotal}
              </span>
            )}
          </button>
        </div>
      </div>
    </header>
  );
}
