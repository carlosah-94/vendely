/**
 * Paginador del catálogo (issue #4). No dibuja nada si todo cabe en una página.
 *
 * La página también vive en la URL (?pagina=2): recargar o compartir el enlace cae en la
 * misma página. Al cambiarla, la portada sube el scroll al catálogo.
 */
export default function Paginador({ pagina, paginas, onIr }) {
  if (!paginas || paginas <= 1) {
    return null;
  }
  const boton = 'rounded-md border border-[#d9d9d9] px-4 py-2 text-sm hover:border-[#303030] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[#d9d9d9]';

  return (
    <nav className="mt-10 flex items-center justify-center gap-4" aria-label="Páginas del catálogo">
      <button type="button" className={boton} disabled={pagina <= 1} onClick={() => onIr(pagina - 1)}>
        Anterior
      </button>
      <span className="text-sm text-[#616161]">
        Página {pagina} de {paginas}
      </span>
      <button type="button" className={boton} disabled={pagina >= paginas} onClick={() => onIr(pagina + 1)}>
        Siguiente
      </button>
    </nav>
  );
}
