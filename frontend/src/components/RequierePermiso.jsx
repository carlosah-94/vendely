import { Outlet } from 'react-router-dom';
import Mensaje from './Mensaje';
import { useTiendaActual } from '../hooks/useTiendaActual';
import { esDuenoDe, esSoporte, puede } from '../utils/permisos';

/**
 * Portero de las secciones del panel según la tienda elegida.
 * - permiso: la sección la usa el dueño o un empleado con ese permiso (o alguno de la lista).
 * - soloDueno: la sección es del dueño (personalizar, envíos...). Soporte de Vendely, con
 *   permiso del dueño, también entra.
 * - sinSoporte: soporte de Vendely no entra (plan, empleados, el propio permiso de soporte).
 * Sin tiendas todavía, deja pasar: cada página ya muestra su propio aviso.
 */
export default function RequierePermiso({ permiso, soloDueno = false, sinSoporte = false }) {
  const { tiendas, tienda, cargando } = useTiendaActual();

  if (cargando) {
    return <p className="text-[#616161]">Cargando...</p>;
  }
  if (tiendas.length === 0 || !tienda) {
    return <Outlet />;
  }
  if (sinSoporte && esSoporte(tienda)) {
    return (
      <Mensaje
        tipo="info"
        texto={`Esta sección la decide el dueño de ${tienda.nombre}: soporte de Vendely no puede entrar aquí.`}
      />
    );
  }
  const lista = Array.isArray(permiso) ? permiso : [permiso];
  const permitido = soloDueno ? esDuenoDe(tienda) || esSoporte(tienda)
    : permiso ? puede(tienda, ...lista) : true;
  if (!permitido) {
    return (
      <Mensaje
        tipo="info"
        texto={soloDueno
          ? `Esta sección solo la usa el dueño de ${tienda.nombre}.`
          : `Tu cuenta de empleado no tiene acceso a esta sección en ${tienda.nombre}. Pídeselo al dueño de la tienda.`}
      />
    );
  }
  return <Outlet />;
}
