import { useNavigate } from 'react-router-dom';
import { IconoSoporte } from '../../components/Iconos';
import Mensaje from '../../components/Mensaje';
import { AvatarTienda } from '../../components/MenuCuenta';
import { useTiendaActual } from '../../hooks/useTiendaActual';
import { formatoFecha } from '../../utils/formato';
import { BOTON, BOTON_SECUNDARIO, ENCABEZADO_TABLA, TARJETA } from '../../utils/panel';

/**
 * Soporte a tiendas (superadmin): las tiendas cuyo dueño dio acceso a soporte de Vendely y todavía
 * no vence. "Entrar" abre el panel de esa tienda en modo soporte, para armarla por el cliente.
 */
export default function AdminSoporte() {
  const navigate = useNavigate();
  // Para el superadmin, /api/panel/tiendas devuelve justo estas tiendas.
  const { tiendas, cambiarTienda, recargar, cargando, error } = useTiendaActual();

  function entrar(idTienda) {
    cambiarTienda(idTienda);
    navigate('/panel');
  }

  return (
    <section className="max-w-5xl space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[#101010]">Soporte a tiendas</h1>
          <p className="text-[13px] text-[#616161]">
            Tiendas cuyo dueño te dio acceso para armarla. Lo que cambies queda registrado y el dueño lo ve.
          </p>
        </div>
        <button onClick={recargar} className={`${BOTON_SECUNDARIO} text-[13px]`}>Actualizar</button>
      </div>

      {error && <Mensaje tipo="error" texto={error} />}

      {cargando ? (
        <p className="text-[#616161]">Cargando...</p>
      ) : tiendas.length === 0 ? (
        <div className={`${TARJETA} flex flex-col items-center px-6 py-10 text-center`}>
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#f1f1f1] text-[#616161]">
            <IconoSoporte tamano={24} />
          </span>
          <p className="mt-3 font-medium text-[#101010]">Ninguna tienda te ha dado acceso</p>
          <p className="mt-1 max-w-md text-[13px] text-[#616161]">
            Pídele al dueño que entre a su panel, abra «Soporte de Vendely» y toque «Dar acceso a soporte». Aparecerá aquí
            hasta que venza o lo quite.
          </p>
        </div>
      ) : (
        <div className={`${TARJETA} overflow-x-auto`}>
          <table className="w-full min-w-[640px] text-[13px]">
            <thead className={ENCABEZADO_TABLA}>
              <tr>
                <th className="px-4 py-2.5 font-medium">Tienda</th>
                <th className="px-4 py-2.5 font-medium">Plan</th>
                <th className="px-4 py-2.5 font-medium">Acceso hasta</th>
                <th className="px-4 py-2.5 text-right font-medium">Acción</th>
              </tr>
            </thead>
            <tbody>
              {tiendas.map((t) => (
                <tr key={t.idTienda} className="border-t border-[#ebebeb]">
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-2.5">
                      <AvatarTienda tienda={t} tamano={30} />
                      <span>
                        <span className="block font-medium text-[#101010]">{t.nombre}</span>
                        <span className="block text-[#616161]">/tienda/{t.subdominio} · {t.pais}</span>
                      </span>
                    </span>
                  </td>
                  <td className="px-4 py-3 text-[#303030]">{t.plan ?? 'Gratis'}</td>
                  <td className="px-4 py-3 text-[#303030]">{formatoFecha(t.soporteHasta)}</td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => entrar(t.idTienda)} className={`${BOTON} text-[13px]`}>
                      Entrar a la tienda
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
