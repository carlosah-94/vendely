import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { IconoCandado, IconoCheck, IconoCuenta, IconoMas, IconoSalir, IconoSelector, IconoTienda } from './Iconos';
import { colorTextoSobre } from '../utils/formato';

const NOMBRE_ROL = {
  ADMIN_TIENDA: 'Dueño de tienda',
  EMPLEADO: 'Empleado',
  SUPERADMIN: 'Administrador de Vendely',
};

/** "Moda Lima" → "ML"; "Kova" → "KO". */
function iniciales(nombre) {
  const palabras = (nombre ?? '?').trim().split(/\s+/).filter(Boolean);
  const texto = palabras.length > 1 ? palabras[0][0] + palabras[1][0] : (palabras[0] ?? '?').slice(0, 2);
  return texto.toUpperCase();
}

/** El logo de la tienda, o sus iniciales sobre su color principal. */
export function AvatarTienda({ tienda, tamano = 28 }) {
  const estilo = { width: tamano, height: tamano, fontSize: Math.round(tamano * 0.38) };
  if (tienda?.logoUrl) {
    return <img src={tienda.logoUrl} alt="" style={estilo} className="shrink-0 rounded-md bg-white object-contain" />;
  }
  const fondo = tienda?.colorPrincipal || '#5c5c5c';
  return (
    <span
      style={{ ...estilo, background: fondo, color: colorTextoSobre(fondo) }}
      className="flex shrink-0 items-center justify-center rounded-md font-bold"
      aria-hidden="true"
    >
      {iniciales(tienda?.nombre)}
    </span>
  );
}

/**
 * Selector de tienda y menú de la cuenta, abajo del menú lateral (como en el admin de Shopify).
 * El botón muestra la tienda con la que se trabaja; al abrirlo, la lista de tiendas para cambiar
 * (el dueño también puede crear una), y debajo la cuenta: Mi perfil, Seguridad y Cerrar sesión.
 * Se cierra con Escape o al hacer clic fuera.
 */
export default function MenuCuenta({ alNavegar }) {
  const { usuario, cerrarSesion } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  // Modo soporte: el superadmin trabaja en las tiendas que le dieron permiso (como un empleado).
  const enSoporte = usuario.rol === 'SUPERADMIN' && pathname.startsWith('/panel');
  const esPanelTienda = usuario.rol === 'ADMIN_TIENDA' || usuario.rol === 'EMPLEADO' || enSoporte;
  const { tiendas, tienda, cambiarTienda } = useTiendaActual(esPanelTienda);
  const [abierto, setAbierto] = useState(false);
  const caja = useRef(null);

  useEffect(() => {
    if (!abierto) return undefined;
    const alClic = (e) => {
      if (caja.current && !caja.current.contains(e.target)) setAbierto(false);
    };
    const alTecla = (e) => e.key === 'Escape' && setAbierto(false);
    document.addEventListener('mousedown', alClic);
    document.addEventListener('keydown', alTecla);
    return () => {
      document.removeEventListener('mousedown', alClic);
      document.removeEventListener('keydown', alTecla);
    };
  }, [abierto]);

  function ir(ruta) {
    setAbierto(false);
    alNavegar?.();
    navigate(ruta);
  }

  function elegir(idTienda) {
    cambiarTienda(idTienda);
    setAbierto(false);
    alNavegar?.();
  }

  const nombre = `${usuario.nombres ?? ''} ${usuario.primerApellido ?? ''}`.trim();
  const opcion = 'flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13px] text-[#303030] hover:bg-[#f1f1f1]';

  return (
    <div ref={caja} className="relative">
      {abierto && (
        <div
          role="menu"
          className="absolute bottom-full left-0 z-40 mb-2 w-72 max-w-[calc(100vw-1.5rem)] rounded-xl bg-white p-1.5 shadow-[0_8px_30px_rgba(0,0,0,0.35)]"
        >
          {esPanelTienda && (
            <>
              <p className="px-2.5 pb-1 pt-1.5 text-[11px] font-semibold uppercase tracking-wide text-[#8a8a8a]">
                Tiendas
              </p>
              <ul className="max-h-64 overflow-y-auto">
                {tiendas.map((t) => (
                  <li key={t.idTienda}>
                    <button
                      type="button"
                      role="menuitemradio"
                      aria-checked={t.idTienda === tienda?.idTienda}
                      onClick={() => elegir(t.idTienda)}
                      className={`${opcion} ${t.idTienda === tienda?.idTienda ? 'bg-[#f1f1f1] font-semibold' : ''}`}
                    >
                      <AvatarTienda tienda={t} tamano={26} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate">{t.nombre}</span>
                        <span className="block truncate text-[11px] font-normal text-[#8a8a8a]">
                          {t.soporte ? 'Soporte de Vendely' : t.esDueno === false ? 'Empleado' : `Plan ${t.plan ?? 'Gratis'}`}
                          {t.estado === 'SUSPENDIDA' ? ' · Suspendida' : ''}
                        </span>
                      </span>
                      {t.idTienda === tienda?.idTienda && <IconoCheck tamano={16} />}
                    </button>
                  </li>
                ))}
                {tiendas.length === 0 && <li className="px-2.5 py-2 text-[13px] text-[#616161]">Todavía no tienes tiendas.</li>}
              </ul>
              {usuario.rol === 'ADMIN_TIENDA' && (
                <button type="button" role="menuitem" onClick={() => ir('/panel/tiendas?crear=1')} className={opcion}>
                  <IconoMas tamano={17} /> Crear tienda
                </button>
              )}
              {enSoporte ? (
                <button type="button" role="menuitem" onClick={() => ir('/admin/soporte')} className={opcion}>
                  <IconoTienda tamano={17} /> Salir del modo soporte
                </button>
              ) : (
                <button type="button" role="menuitem" onClick={() => ir('/panel/tiendas')} className={opcion}>
                  <IconoTienda tamano={17} /> Ver todas mis tiendas
                </button>
              )}
              <hr className="my-1.5 border-[#ebebeb]" />
            </>
          )}

          <button type="button" role="menuitem" onClick={() => ir('/cuenta/perfil')} className={`${opcion} py-2.5`}>
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[var(--v-mango)] text-[13px] font-bold text-[var(--v-tinta)]">
              {(usuario.nombres ?? '?').trim().charAt(0).toUpperCase()}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-semibold">{nombre || usuario.correo}</span>
              <span className="block truncate text-[12px] text-[#616161]">{usuario.correo}</span>
            </span>
          </button>
          <button type="button" role="menuitem" onClick={() => ir('/cuenta/perfil')} className={opcion}>
            <IconoCuenta tamano={17} /> Mi perfil
          </button>
          <button type="button" role="menuitem" onClick={() => ir('/cuenta/seguridad')} className={opcion}>
            <IconoCandado tamano={17} /> Seguridad de la cuenta
          </button>
          <button type="button" role="menuitem" onClick={cerrarSesion} className={opcion}>
            <IconoSalir tamano={17} /> Cerrar sesión
          </button>
        </div>
      )}

      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={abierto}
        className="flex w-full items-center gap-2.5 rounded-xl px-2 py-2 text-left text-[13px] text-[#dcdcdc] hover:bg-[#1f1f1f]"
      >
        {esPanelTienda && tienda ? (
          <AvatarTienda tienda={tienda} tamano={26} />
        ) : (
          <span className="flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-md bg-[var(--v-mango)] text-[12px] font-bold text-[var(--v-tinta)]">
            {(usuario.nombres ?? '?').trim().charAt(0).toUpperCase()}
          </span>
        )}
        <span className="min-w-0 flex-1">
          <span className="block truncate font-semibold text-white">
            {esPanelTienda ? (tienda?.nombre ?? 'Elige una tienda') : nombre}
          </span>
          <span className="block truncate text-[11px] text-[#8a8a8a]">{enSoporte ? 'Modo soporte' : NOMBRE_ROL[usuario.rol] ?? usuario.rol}</span>
        </span>
        <IconoSelector tamano={16} />
      </button>
    </div>
  );
}
