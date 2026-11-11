import { useState } from 'react';
import { IconoImagen } from './Iconos';

/**
 * Foto de un producto. Con url muestra la foto; sin url (o si no carga) muestra un recuadro gris
 * con un ícono y las dos primeras letras del producto, para que la tienda nunca se vea rota.
 */
export default function ImagenProducto({ url, nombre = '', className = '' }) {
  const [fallo, setFallo] = useState(false);

  if (url && !fallo) {
    return (
      <img
        src={url}
        alt={nombre}
        loading="lazy"
        onError={() => setFallo(true)}
        className={`aspect-square w-full object-cover ${className}`}
      />
    );
  }
  const iniciales = nombre.trim().slice(0, 2).toUpperCase();
  return (
    <div
      role="img"
      aria-label={nombre}
      className={`flex aspect-square w-full flex-col items-center justify-center gap-1 bg-[#f3f3f3] text-[#b5b5b5] ${className}`}
    >
      <IconoImagen tamano={28} />
      <span className="text-2xl font-semibold tracking-wide">{iniciales}</span>
    </div>
  );
}
