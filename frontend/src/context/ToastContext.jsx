import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';

const ToastContext = createContext({ avisar: () => {} });

/** Avisos oscuros abajo al centro ("Producto guardado") que desaparecen solos a los 3 segundos. */
export function ToastProvider({ children }) {
  const [texto, setTexto] = useState('');
  const temporizador = useRef(null);

  const avisar = useCallback((mensaje) => {
    setTexto(mensaje);
    clearTimeout(temporizador.current);
    temporizador.current = setTimeout(() => setTexto(''), 3000);
  }, []);

  useEffect(() => () => clearTimeout(temporizador.current), []);

  return (
    <ToastContext.Provider value={{ avisar }}>
      {children}
      {texto && (
        <div
          role="status"
          className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-[#303030] px-5 py-3 text-sm text-white shadow-lg"
        >
          {texto}
        </div>
      )}
    </ToastContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  return useContext(ToastContext);
}
