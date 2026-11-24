import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import MarcaVendely from '../components/marca/MarcaVendely';
import MuroTiendas, { MiniTienda } from '../components/marca/MuroTiendas';
import { TIENDAS_EJEMPLO } from '../components/marca/tiendasEjemplo';
import { IconoCheck } from '../components/Iconos';
import { PLANTILLAS } from '../utils/plantillas';

/** Avisos que aparecen en la portada, como la campanita del panel cuando entra un pedido. */
const AVISOS = [
  ['Café Andino', 'Taza de barro', 'S/ 39.00'],
  ['Lima Sneakers', 'Runner Azul', 'S/ 259.90'],
  ['Verde Casa', 'Monstera mini', 'S/ 49.90'],
  ['Tejidos Cusco', 'Chompa de alpaca', 'S/ 189.00'],
];

/**
 * Por qué Vendely: solo cosas que el sistema hace de verdad (sirve para responder
 * "¿por qué comprarían su plataforma y no otra?").
 */
const RAZONES = [
  ['Cobras en tu moneda', 'Soles en Perú, pesos en Chile. Tus clientes pagan con PayPal o Mercado Pago y el pedido se marca como pagado solo.', 'M4 7h16v10H4zM4 11h16M8 15h3'],
  ['Vendes a otros países', 'Pones una tarifa de envío por país y tu cliente ve el costo antes de pagar. Nada de sorpresas al final.', 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z'],
  ['Te enteras al instante', 'Suena la campanita cuando entra un pedido y el stock se actualiza en vivo en la tienda. Nadie compra algo que ya se agotó.', 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0'],
  ['Tu equipo, con permisos', 'Agrega empleados que solo ven lo que les toca: pedidos, productos, inventario o clientes. Tú decides y lo cambias cuando quieras.', 'M9 8a3.5 3.5 0 1 0 0-.01M2.5 20c.8-3.2 3.3-5 6.5-5s5.7 1.8 6.5 5M16 5a3.5 3.5 0 0 1 0 6.5M18 15.2c1.7.7 2.8 2.2 3.5 4.8'],
  ['Tu cuenta, protegida', 'Verificación en dos pasos con Google Authenticator. Aunque alguien sepa tu contraseña, no entra sin tu celular.', 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6zM9 12l2 2 4-4'],
  ['Una tienda que se ve profesional', 'Tres plantillas con tu color y tu logo. Se ven bien desde el primer producto, en el celular y en la computadora.', 'M4 5h16v14H4zM4 9h16M9 9v10'],
];

/** Los 3 planes, con los valores con los que arranca Vendely (el superadmin puede cambiarlos). */
const PLANES = [
  { nombre: 'Gratis', precio: '0', para: 'Para probar tu idea', cosas: ['Hasta 5 productos', 'Comisión de 5% por venta', 'Plantilla Clásica', 'Pagos con PayPal'] },
  { nombre: 'Básico', precio: '15', para: 'Para tu primera tienda en serio', cosas: ['Hasta 100 productos', 'Comisión de 2%', 'Tu color y tu logo, sin sello de Vendely', 'PayPal y Mercado Pago', 'Hasta 2 empleados'] },
  { nombre: 'Pro', precio: '39', para: 'Para crecer y vender afuera', destacado: true, cosas: ['Productos sin límite', 'Comisión de 0.5%', 'Plantillas Moderna y Boutique', 'Envíos internacionales', 'Hasta 15 empleados'] },
];

const PASOS = [
  ['Crea tu cuenta', 'Tu nombre, tu correo y el nombre de tu tienda. Recibes tu dirección: tu-tienda.vendely.pe.'],
  ['Sube tus productos', 'Foto, precio y stock. Con tallas y colores si los tienes. Se publican al activarlos.'],
  ['Comparte y cobra', 'Manda tu enlace por WhatsApp o redes. Tus clientes compran y pagan en línea.'],
];

/**
 * Página principal de Vendely ("/"). Lo memorable es la portada: un muro de tiendas hechas con
 * Vendely que se mueve detrás del título, como un video de fondo.
 */
export default function Portada() {
  const { usuario } = useAuth();
  const tienePanel = usuario && ['ADMIN_TIENDA', 'EMPLEADO', 'SUPERADMIN'].includes(usuario.rol);
  const destino = tienePanel ? '/inicio' : '/registro';

  const [aviso, setAviso] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setAviso((a) => (a + 1) % AVISOS.length), 5000);
    return () => clearInterval(id);
  }, []);
  const [tiendaAviso, productoAviso, montoAviso] = AVISOS[aviso];

  return (
    <div className="bg-white text-[var(--v-tinta)]" style={{ fontSize: 16 }}>
      {/* ------------------------------------------------------------ Portada */}
      <section className="relative isolate flex min-h-[100svh] flex-col overflow-hidden bg-[var(--v-tinta)] text-white">
        <MuroTiendas className="-z-20 opacity-70" />
        {/* Velo para que el título se lea: más oscuro a la izquierda y abajo */}
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,rgba(21,18,59,0.97)_0%,rgba(21,18,59,0.88)_42%,rgba(21,18,59,0.35)_100%)]" />
        <div className="absolute inset-0 -z-10 bg-[var(--v-tinta)]/55 sm:hidden" />
        <div className="absolute inset-x-0 bottom-0 -z-10 h-40 bg-gradient-to-t from-[var(--v-tinta)] to-transparent" />

        <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-5 md:px-6">
          <MarcaVendely claro />
          <nav className="flex items-center gap-1 text-sm sm:gap-2">
            <a href="#planes" className="hidden rounded-full px-4 py-2 text-white/80 hover:text-white sm:block">Planes</a>
            {tienePanel ? (
              <Link to="/inicio" className="rounded-full bg-white px-4 py-2 font-semibold text-[var(--v-tinta)] hover:bg-[var(--v-papel)]">
                Ir a mi panel
              </Link>
            ) : (
              <>
                <Link to="/login" className="rounded-full px-4 py-2 text-white/80 hover:text-white">Iniciar sesión</Link>
                <Link to="/registro" className="rounded-full bg-white px-4 py-2 font-semibold text-[var(--v-tinta)] hover:bg-[var(--v-papel)]">
                  Crear tienda
                </Link>
              </>
            )}
          </nav>
        </header>

        <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col justify-center px-4 pb-24 pt-10 md:px-6">
          <h1 className="letra-titular max-w-3xl text-[clamp(3rem,8.5vw,6.75rem)] leading-[0.92]">
            Tu tienda online, abierta hoy mismo.
          </h1>
          <p className="mt-7 max-w-xl text-lg leading-relaxed text-white/80 md:text-xl">
            Crea tu tienda, sube tus productos y cobra con PayPal o Mercado Pago. Todo desde un panel que se entiende a la primera.
          </p>
          <div className="mt-10 flex flex-wrap items-center gap-4">
            <Link
              to={destino}
              className="rounded-full bg-[var(--v-mango)] px-8 py-4 text-[17px] font-bold text-[var(--v-tinta)] shadow-[0_10px_30px_-8px_rgba(255,179,31,0.7)] transition hover:bg-[var(--v-mango-2)] focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-white/50"
            >
              {tienePanel ? 'Ir a mi panel' : 'Crear mi tienda gratis'}
            </Link>
            <a href="#plantillas" className="rounded-full px-5 py-4 font-semibold text-white underline-offset-8 hover:underline">
              Ver cómo se ven las tiendas
            </a>
          </div>
          <p className="mt-5 text-sm text-white/60">Empiezas gratis. No pedimos tarjeta.</p>
        </div>

        {/* Aviso de pedido nuevo (el mismo que recibe el dueño en su panel) */}
        <div className="pointer-events-none absolute bottom-8 right-4 hidden md:right-8 sm:block">
          <div key={aviso} className="aviso-pedido flex items-center gap-3 rounded-2xl bg-white/95 p-3 pr-5 text-[var(--v-tinta)] shadow-2xl backdrop-blur">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--v-mango)]">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0" strokeLinejoin="round" />
              </svg>
            </span>
            <div className="text-sm leading-tight">
              <p className="font-semibold">Nuevo pedido en {tiendaAviso}</p>
              <p className="text-[#55527a]">{productoAviso}, {montoAviso}</p>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Por qué Vendely */}
      <section className="bg-white">
        <div className="mx-auto grid max-w-6xl gap-12 px-4 py-20 md:grid-cols-[0.9fr_1.1fr] md:px-6 md:py-28">
          <div className="md:sticky md:top-10 md:self-start">
            <h2 className="letra-titular text-[clamp(2.3rem,5vw,3.75rem)] leading-[0.98]">
              Hecho para vender en Latinoamérica desde el primer día.
            </h2>
            <p className="mt-6 max-w-md text-lg text-[#4b4870]">
              No necesitas saber de programación ni contratar a nadie. Lo que una tienda necesita para cobrar, enviar y crecer ya viene incluido.
            </p>
            <Link to={destino} className="mt-8 inline-block rounded-full bg-[var(--v-tinta)] px-7 py-3.5 font-semibold text-white hover:bg-[var(--v-tinta-2)]">
              {tienePanel ? 'Ir a mi panel' : 'Empezar gratis'}
            </Link>
          </div>
          <ul className="divide-y divide-[#e6e4f5] border-y border-[#e6e4f5]">
            {RAZONES.map(([titulo, texto, trazo]) => (
              <li key={titulo} className="flex gap-5 py-7">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[var(--v-papel)] text-[var(--v-tinta)]">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d={trazo} />
                  </svg>
                </span>
                <div>
                  <h3 className="text-xl font-semibold">{titulo}</h3>
                  <p className="mt-1.5 leading-relaxed text-[#4b4870]">{texto}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ------------------------------------------------------------ Plantillas */}
      <section id="plantillas" className="scroll-mt-4 bg-[var(--v-papel)]">
        <div className="mx-auto max-w-6xl px-4 py-20 md:px-6 md:py-28">
          <h2 className="letra-titular max-w-2xl text-[clamp(2.3rem,5vw,3.75rem)] leading-[0.98]">Elige cómo se ve tu tienda.</h2>
          <p className="mt-5 max-w-xl text-lg text-[#4b4870]">
            Cambias de plantilla con un clic y tus productos se acomodan solos. Tu color y tu logo van en todas.
          </p>
          <div className="mt-12 grid gap-8 md:grid-cols-3">
            {PLANTILLAS.map((p, i) => (
              <figure key={p.valor}>
                <div className="rounded-[1.75rem] bg-white p-3 shadow-[0_30px_60px_-30px_rgba(21,18,59,0.45)]">
                  <MiniTienda tienda={TIENDAS_EJEMPLO.find((t) => t.plantilla === p.valor) ?? TIENDAS_EJEMPLO[i]} className="shadow-none" />
                </div>
                <figcaption className="mt-5 px-1">
                  <p className="flex items-center gap-2 text-xl font-semibold">
                    {p.nombre}
                    {p.valor !== 'clasica' && (
                      <span className="rounded-full bg-[var(--v-tinta)] px-2.5 py-0.5 text-xs font-semibold text-white">Plan Pro</span>
                    )}
                  </p>
                  <p className="mt-1 text-[#4b4870]">{p.descripcion}</p>
                </figcaption>
              </figure>
            ))}
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Cómo empezar */}
      <section className="bg-white">
        <div className="mx-auto max-w-6xl px-4 py-20 md:px-6 md:py-28">
          <h2 className="letra-titular text-[clamp(2.3rem,5vw,3.75rem)] leading-[0.98]">Vendes en tres pasos.</h2>
          <ol className="mt-12 grid gap-10 md:grid-cols-3">
            {PASOS.map(([titulo, texto], i) => (
              <li key={titulo} className="border-t-2 border-[var(--v-tinta)] pt-6">
                <span className="letra-titular text-5xl text-[var(--v-pitaya)]">{i + 1}</span>
                <h3 className="mt-3 text-xl font-semibold">{titulo}</h3>
                <p className="mt-1.5 leading-relaxed text-[#4b4870]">{texto}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ------------------------------------------------------------ Planes */}
      <section id="planes" className="scroll-mt-4 bg-[var(--v-tinta)] text-white">
        <div className="mx-auto max-w-6xl px-4 py-20 md:px-6 md:py-28">
          <h2 className="letra-titular max-w-2xl text-[clamp(2.3rem,5vw,3.75rem)] leading-[0.98]">Paga solo cuando tu tienda crezca.</h2>
          <p className="mt-5 max-w-xl text-lg text-white/70">Precios en dólares al mes. Cambias de plan desde tu panel cuando quieras.</p>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {PLANES.map((p) => (
              <div
                key={p.nombre}
                className={`flex flex-col rounded-[1.75rem] p-7 ${
                  p.destacado ? 'bg-[var(--v-mango)] text-[var(--v-tinta)]' : 'bg-white/[0.06] ring-1 ring-white/15'
                }`}
              >
                <div className="flex items-center justify-between">
                  <h3 className="text-2xl font-semibold">{p.nombre}</h3>
                  {p.destacado && <span className="rounded-full bg-[var(--v-tinta)] px-3 py-1 text-xs font-semibold text-white">El más completo</span>}
                </div>
                <p className={p.destacado ? 'text-[var(--v-tinta)]/75' : 'text-white/65'}>{p.para}</p>
                <p className="mt-6 flex items-baseline gap-1">
                  <span className="letra-titular text-6xl">${p.precio}</span>
                  <span className={p.destacado ? 'text-[var(--v-tinta)]/70' : 'text-white/60'}>al mes</span>
                </p>
                <ul className="mt-6 flex-1 space-y-2.5">
                  {p.cosas.map((c) => (
                    <li key={c} className="flex gap-2.5">
                      <IconoCheck tamano={20} className="mt-0.5 shrink-0" />
                      {c}
                    </li>
                  ))}
                </ul>
                <Link
                  to={destino}
                  className={`mt-8 rounded-full py-3.5 text-center font-semibold transition ${
                    p.destacado ? 'bg-[var(--v-tinta)] text-white hover:bg-[var(--v-tinta-2)]' : 'bg-white text-[var(--v-tinta)] hover:bg-[var(--v-papel)]'
                  }`}
                >
                  {tienePanel ? 'Ir a mi panel' : p.precio === '0' ? 'Empezar gratis' : `Empezar con ${p.nombre}`}
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ Cierre */}
      <section className="bg-[var(--v-mango)]">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-8 px-4 py-16 md:flex-row md:items-center md:justify-between md:px-6">
          <h2 className="letra-titular max-w-2xl text-[clamp(2rem,4.5vw,3.25rem)] leading-[1]">Tu próxima venta puede llegar esta noche.</h2>
          <Link to={destino} className="shrink-0 rounded-full bg-[var(--v-tinta)] px-8 py-4 text-[17px] font-bold text-white hover:bg-[var(--v-tinta-2)]">
            {tienePanel ? 'Ir a mi panel' : 'Crear mi tienda gratis'}
          </Link>
        </div>
      </section>

      <footer className="bg-[var(--v-tinta)] text-white/60">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-10 text-sm md:px-6">
          <MarcaVendely claro tamano={24} />
          <span>Proyecto del curso Desarrollo Full Stack, UTP 2026</span>
          <div className="flex gap-5">
            <Link to="/login" className="hover:text-white">Iniciar sesión</Link>
            <Link to="/registro" className="hover:text-white">Crear cuenta</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
