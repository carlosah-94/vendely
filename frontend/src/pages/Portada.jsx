import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const BENEFICIOS = [
  {
    titulo: 'Tu tienda en minutos',
    texto: 'Creas tu cuenta, eliges el nombre de tu tienda y recibes tu propia dirección: moda-lima.vendely.pe.',
  },
  {
    titulo: 'Todo en un solo panel',
    texto: 'Productos con precio y stock, clientes con su dirección y pedidos con su estado de pago.',
  },
  {
    titulo: 'Cobros en línea',
    texto: 'Tus clientes pagan con PayPal y el pedido se marca como pagado automáticamente.',
  },
];

const PASOS = [
  ['1', 'Crea tu cuenta', 'Solo tu nombre, correo y contraseña.'],
  ['2', 'Agrega tus productos', 'Nombre, precio y stock. Se publican en tu tienda al activarlos.'],
  ['3', 'Comparte tu enlace', 'Tus clientes se registran en tu tienda, compran y pagan.'],
];

/**
 * Página principal pública de Vendely (lo que ve cualquiera en "/"), al estilo de
 * shopify.com: fondo negro, un título grande y un botón para empezar gratis.
 */
export default function Portada() {
  const { usuario } = useAuth();
  const tienePanel = usuario && (usuario.rol === 'ADMIN_TIENDA' || usuario.rol === 'SUPERADMIN');

  return (
    <div className="min-h-screen bg-black text-white">
      {/* Barra de navegación */}
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-5 md:px-6">
        <Link to="/" className="text-xl font-semibold tracking-tight">Vendely</Link>
        <nav className="flex items-center gap-2 text-sm">
          {tienePanel ? (
            <Link to="/inicio" className="rounded-full bg-white px-4 py-2 font-medium text-black hover:bg-[#e3e3e3]">
              Ir a mi panel
            </Link>
          ) : (
            <>
              <Link to="/login" className="rounded-full px-4 py-2 text-[#dcdcdc] hover:text-white">
                Iniciar sesión
              </Link>
              <Link to="/registro" className="rounded-full bg-white px-4 py-2 font-medium text-black hover:bg-[#e3e3e3]">
                Comenzar gratis
              </Link>
            </>
          )}
        </nav>
      </header>

      {/* Portada */}
      <section className="mx-auto max-w-6xl px-4 pb-16 pt-14 md:px-6 md:pt-24">
        <h1 className="max-w-4xl text-5xl font-light leading-[1.05] tracking-tight md:text-7xl lg:text-[88px]">
          Vende en línea con tu propia tienda
        </h1>
        <p className="mt-6 max-w-xl text-lg text-[#b5b5b5]">
          Vendely es la plataforma para crear tu tienda online, publicar tus productos y recibir pagos,
          desde un solo panel.
        </p>
        <div className="mt-9 flex flex-wrap items-center gap-4">
          <Link
            to={tienePanel ? '/inicio' : '/registro'}
            className="rounded-full bg-white px-6 py-3 text-[15px] font-semibold text-black hover:bg-[#e3e3e3]"
          >
            {tienePanel ? 'Ir a mi panel' : 'Comenzar gratis'}
          </Link>
          <span className="text-sm text-[#8a8a8a]">Sin tarjeta de crédito.</span>
        </div>

        {/* Vista previa del panel, dibujada con cajas (no es una imagen) */}
        <div className="mt-16 overflow-hidden rounded-2xl border border-[#262626] bg-[#0f0f0f] p-2 md:mt-20">
          <div className="flex gap-2">
            <div className="hidden w-44 shrink-0 space-y-2 p-3 md:block">
              {['Inicio', 'Pedidos', 'Productos', 'Clientes'].map((t, i) => (
                <div key={t} className={`rounded-lg px-2 py-1 text-xs ${i === 0 ? 'bg-[#2b2b2b] text-white' : 'text-[#8a8a8a]'}`}>
                  {t}
                </div>
              ))}
            </div>
            <div className="flex-1 rounded-xl bg-[#f1f1f1] p-4 text-[#303030] md:p-6">
              <p className="text-sm font-semibold">Inicio</p>
              <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-4">
                {[['Ventas de hoy', 'S/ 1,250.00'], ['Pedidos', '18'], ['Productos activos', '42'], ['Clientes', '96']].map(([t, v]) => (
                  <div key={t} className="rounded-xl bg-white p-3 shadow-[0_1px_0_rgba(0,0,0,0.08)]">
                    <p className="text-[11px] text-[#616161]">{t}</p>
                    <p className="mt-1 text-lg font-semibold">{v}</p>
                  </div>
                ))}
              </div>
              <div className="mt-3 space-y-2 rounded-xl bg-white p-3 shadow-[0_1px_0_rgba(0,0,0,0.08)]">
                {[['#1018', 'Pagado', 'S/ 129.90'], ['#1017', 'Pendiente', 'S/ 59.00'], ['#1016', 'Pagado', 'S/ 210.50']].map(([n, e, t]) => (
                  <div key={n} className="flex items-center justify-between text-xs">
                    <span className="font-medium">{n}</span>
                    <span className={`rounded-full px-2 py-0.5 ${e === 'Pagado' ? 'bg-[#e3e3e3]' : 'bg-[#fff5c5] text-[#4f4700]'}`}>{e}</span>
                    <span>{t}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Beneficios */}
      <section className="border-t border-[#1f1f1f]">
        <div className="mx-auto grid max-w-6xl gap-10 px-4 py-16 md:grid-cols-3 md:px-6 md:py-20">
          {BENEFICIOS.map((b) => (
            <div key={b.titulo}>
              <h2 className="text-xl font-medium">{b.titulo}</h2>
              <p className="mt-2 text-[#b5b5b5]">{b.texto}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Cómo funciona */}
      <section className="bg-white text-[#101010]">
        <div className="mx-auto max-w-6xl px-4 py-16 md:px-6 md:py-20">
          <h2 className="text-4xl font-light tracking-tight md:text-5xl">Empieza en tres pasos</h2>
          <div className="mt-10 grid gap-6 md:grid-cols-3">
            {PASOS.map(([n, titulo, texto]) => (
              <div key={n} className="rounded-2xl border border-[#e3e3e3] p-6">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#101010] text-sm font-semibold text-white">{n}</span>
                <h3 className="mt-4 text-lg font-medium">{titulo}</h3>
                <p className="mt-1 text-[#4a4a4a]">{texto}</p>
              </div>
            ))}
          </div>
          {!tienePanel && (
            <Link to="/registro" className="mt-10 inline-block rounded-full bg-[#101010] px-6 py-3 font-semibold text-white hover:bg-[#303030]">
              Crear mi tienda
            </Link>
          )}
        </div>
      </section>

      {/* Pie */}
      <footer className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-8 text-sm text-[#8a8a8a] md:px-6">
        <span>Vendely, proyecto del curso Desarrollo Full Stack (UTP)</span>
        <div className="flex gap-4">
          <Link to="/login" className="hover:text-white">Iniciar sesión</Link>
          <Link to="/registro" className="hover:text-white">Crear cuenta</Link>
        </div>
      </footer>
    </div>
  );
}
