import { api } from './api';

/**
 * Informe de ventas de la tienda entre dos fechas ("2026-11-01"). Solo el dueño.
 * Los días se cuentan en la zona horaria del navegador.
 */
export async function obtenerInforme(idTienda, desde, hasta) {
  const zona = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const parametros = new URLSearchParams({ desde, hasta });
  if (zona) parametros.set('zona', zona);
  return (await api.get(`/panel/tiendas/${idTienda}/informes?${parametros}`)).data;
}
