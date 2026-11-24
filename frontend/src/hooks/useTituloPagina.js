import { useEffect } from 'react';

/**
 * El título de la pestaña del navegador. En la tienda pública debe decir el nombre de la tienda
 * (y del producto), no "Vendely": es lo que el cliente ve en su pestaña y al compartir el enlace.
 * Al salir de la página vuelve el título que había antes (el de la tienda, o el de Vendely).
 * Con texto vacío no cambia nada.
 */
export function useTituloPagina(texto) {
  useEffect(() => {
    if (!texto) return undefined;
    const antes = document.title;
    document.title = texto;
    return () => {
      document.title = antes;
    };
  }, [texto]);
}
