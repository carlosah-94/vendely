import { useEffect, useState } from 'react';
import { listarPaises } from '../services/envios';

// Si el servidor no responde, al menos se puede crear la tienda en Perú (el país por defecto).
const RESPALDO = [{ idPais: 'PE', nombre: 'Perú', moneda: 'PEN', habilitado: true, nivelFinal: 'Distrito' }];

/**
 * Países donde se pueden crear tiendas: los que tienen su lista oficial de ubicaciones en
 * Vendely (hoy Perú y Chile). Cada uno trae su moneda, que será la de la tienda.
 */
export function usePaisesHabilitados() {
  const [paises, setPaises] = useState(RESPALDO);

  useEffect(() => {
    let vigente = true;
    listarPaises()
      .then((lista) => {
        const habilitados = lista.filter((p) => p.habilitado);
        if (vigente && habilitados.length > 0) setPaises(habilitados);
      })
      .catch(() => {});
    return () => {
      vigente = false;
    };
  }, []);

  return paises;
}

/** "PEN · soles peruanos" para la ayuda del selector. */
export const NOMBRE_MONEDA = { PEN: 'soles', CLP: 'pesos chilenos', USD: 'dólares' };
