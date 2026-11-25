import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useMatch, useNavigate, useParams } from 'react-router-dom';
import { CarritoProvider, useCarrito } from '../../context/CarritoContext';
import { useTituloPagina } from '../../hooks/useTituloPagina';
import { useAuth } from '../../context/AuthContext';
import { mensajeDeError } from '../../services/api';
import { paisesDeEnvio } from '../../services/envios';
import { colorTextoSobre, usarMoneda } from '../../utils/formato';
import { disenoDe, estiloTienda } from '../../utils/diseno';
import { obtenerTienda } from '../../services/tienda';
import { categoriasPublicas } from '../../services/categorias';
import { IconoBuscar, IconoCarrito, IconoCuenta } from '../Iconos';
import CarritoDrawer from './CarritoDrawer';
import BotonWhatsapp from './BotonWhatsapp';
import { whatsappLegible } from '../../utils/whatsapp';

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
  const { pathname } = useLocation();

  // Al pasar a otra página de la tienda (un producto, el carrito) se empieza desde arriba, como
  // en una web normal. Con #catalogo no: ahí la vitrina baja sola al catálogo cuando carga.
  // Solo al cambiar de página: cambiar un filtro (la búsqueda de la URL) no mueve la pantalla.
  useEffect(() => {
    if (!window.location.hash) window.scrollTo(0, 0);
  }, [pathname]);

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

  useTituloPagina(estado.subdominio === subdominio ? estado.tienda?.nombre : null);

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
      {diseno.mostrarAnuncio && <BarraAnuncio tienda={tienda} diseno={diseno} paises={paises} />}
      <Cabecera tienda={tienda} subdominio={subdominio} diseno={diseno} />
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
        {tienda.whatsapp && <p className="mt-1">WhatsApp {whatsappLegible(tienda.whatsapp)}</p>}
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
      <BotonWhatsapp tienda={tienda} />
    </div>
  );
}

/**
 * Barra de anuncio. Plan Pro: su propio color y hasta 3 mensajes que van rotando (con flechas
 * para pasarlos). Si no, el texto del dueño (Básico y Pro) o a dónde envía la tienda.
 */
function BarraAnuncio({ tienda, diseno, paises }) {
  const pro = diseno.pro.anuncio;
  const mensajes = pro.mensajes.length > 0 ? pro.mensajes : [diseno.textoAnuncio || textoEnvios(paises)];
  const [indice, setIndice] = useState(0);
  const total = mensajes.length;

  useEffect(() => {
    if (total < 2) return undefined;
    const id = setInterval(() => setIndice((i) => (i + 1) % total), 5000);
    return () => clearInterval(id);
  }, [total]);

  const estilo = pro.color ? { background: pro.color, color: colorTextoSobre(pro.color) } : undefined;
  const clases = pro.color
    ? ''
    : tienda.plantilla === 'boutique'
      ? 'border-b border-[var(--fondo-borde)] text-[#6b6b6b]'
      : 'bg-[var(--tienda)] text-[var(--tienda-texto)]';
  const actual = mensajes[indice % total];
  const flecha = 'px-2 text-sm opacity-70 hover:opacity-100';

  return (
    <div className={`px-4 py-2 text-center text-xs tracking-wide ${clases}`} style={estilo}>
      <div className="mx-auto flex max-w-3xl items-center justify-center gap-3">
        {total > 1 && (
          <button type="button" onClick={() => setIndice((i) => (i - 1 + total) % total)} aria-label="Anuncio anterior" className={flecha}>‹</button>
        )}
        <p aria-live="polite" className="min-w-0 flex-1 truncate">{actual}</p>
        {total > 1 && (
          <button type="button" onClick={() => setIndice((i) => (i + 1) % total)} aria-label="Anuncio siguiente" className={flecha}>›</button>
        )}
      </div>
    </div>
  );
}

/** Plan Pro: las categorías de la tienda como enlaces del menú (hasta 5). */
function useCategoriasMenu(subdominio, activo) {
  const [categorias, setCategorias] = useState([]);
  useEffect(() => {
    if (!activo) return undefined;
    let vigente = true;
    categoriasPublicas(subdominio)
      .then((lista) => vigente && setCategorias(lista.slice(0, 5)))
      .catch(() => vigente && setCategorias([]));
    return () => {
      vigente = false;
    };
  }, [subdominio, activo]);
  return activo ? categorias : [];
}

function Cabecera({ tienda, subdominio, diseno }) {
  const { usuario, cerrarSesion } = useAuth();
  const { cantidadTotal, abrir } = useCarrito();
  const esCliente = usuario?.rol === 'CLIENTE' && usuario.idTienda === tienda.idTienda;
  const base = `/tienda/${subdominio}`;
  const location = useLocation();
  const navigate = useNavigate();
  const enPortada = location.pathname === base || location.pathname === `${base}/`;

  // Inicio: la portada desde arriba y sin filtros. Si ya está en la portada, el enlace no
  // cambiaría nada: se limpia la búsqueda y se sube.
  function irAInicio(e) {
    if (!enPortada) return;
    e.preventDefault();
    if (location.search || location.hash) navigate(base);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // Catálogo: navegación dentro de la app (sin recargar la tienda). Desde otra página, la
  // vitrina baja al catálogo cuando termina de cargar (#catalogo); en la portada, baja ya.
  function irAlCatalogo(e) {
    if (!enPortada) return;
    e.preventDefault();
    document.getElementById('catalogo')?.scrollIntoView({ behavior: 'smooth' });
    if (location.hash !== '#catalogo') navigate({ search: location.search, hash: '#catalogo' }, { replace: true });
  }
  const alCatalogo = { pathname: base, hash: '#catalogo' };
  const categoriasMenu = useCategoriasMenu(subdominio, diseno.pro.categoriasEnMenu);
  const categoriaActual = new URLSearchParams(location.search).get('categoria');

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--fondo-borde)] bg-[var(--fondo)]/95 backdrop-blur">
      <div className="mx-auto grid max-w-6xl grid-cols-[1fr_auto_1fr] items-center gap-2 px-4 py-4">
        <nav className="hidden gap-5 text-sm sm:flex">
          <Link to={base} onClick={irAInicio} className="hover:underline">Inicio</Link>
          <Link to={alCatalogo} onClick={irAlCatalogo} className="hover:underline">Catálogo</Link>
          {categoriasMenu.map((c) => (
            <Link
              key={c.idCategoria}
              to={{ pathname: base, search: `?categoria=${encodeURIComponent(c.slug)}`, hash: '#catalogo' }}
              className={`hidden hover:underline lg:inline ${enPortada && categoriaActual === c.slug ? 'font-semibold underline' : ''}`}
            >
              {c.nombre}
            </Link>
          ))}
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
          <Link to={alCatalogo} onClick={irAlCatalogo} aria-label="Buscar" className="hidden sm:block"><IconoBuscar /></Link>
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
