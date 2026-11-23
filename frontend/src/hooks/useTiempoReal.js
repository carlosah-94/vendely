import { useEffect, useRef } from 'react';
import { suscribir } from '../services/tiempoReal';

/**
 * Escucha un canal de tiempo real mientras la pantalla está abierta (issue #4).
 *
 *   useTiempoReal(canales.pedidosDeTienda(idTienda), (aviso) => { ... });
 *
 * Con destino null no hace nada (por ejemplo, mientras todavía no se sabe qué tienda es).
 *
 * La función que recibe el aviso se guarda en un ref: así la pantalla puede pasar una función
 * nueva en cada render (usando su estado actual) sin que eso desuscriba y vuelva a suscribir.
 */
export function useTiempoReal(destino, alRecibir) {
  const ultima = useRef(alRecibir);

  useEffect(() => {
    ultima.current = alRecibir;
  });

  useEffect(() => {
    if (!destino) return undefined;
    return suscribir(destino, (aviso) => ultima.current(aviso));
  }, [destino]);
}
