package curso.fullstack.shopify.dto;

import curso.fullstack.shopify.repository.CategoriaRepository;

/** Una categoría con cuántos productos tiene. estado: "ACTIVA" u "OCULTA". */
public record CategoriaResponse(Long idCategoria, String nombre, String slug, String descripcion,
                                String estado, long productos) {
    public static CategoriaResponse desde(CategoriaRepository.Fila f) {
        return new CategoriaResponse(f.getIdCategoria(), f.getNombre(), f.getSlug(), f.getDescripcion(),
                f.getEstado() != null && f.getEstado() == 1 ? "ACTIVA" : "OCULTA",
                f.getProductos() == null ? 0 : f.getProductos());
    }
}
