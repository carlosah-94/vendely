package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.CategoriaRequest;
import curso.fullstack.shopify.dto.CategoriaResponse;
import curso.fullstack.shopify.entity.Categoria;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.CategoriaRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categorías de productos de una tienda ("Polos", "Gorras"), como las colecciones de Shopify.
 * El dueño las crea en el panel y las asigna a sus productos; la tienda pública las usa para filtrar.
 */
@Slf4j
@Service
public class CategoriaService {

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final CategoriaRepository categoriaRepository;

    public CategoriaService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
                            CategoriaRepository categoriaRepository) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.delDueno(yo, idTienda);
        return categoriaRepository.filasDeTienda(idTienda).stream().map(CategoriaResponse::desde).toList();
    }

    @Transactional
    public CategoriaResponse crear(UsuarioAutenticado yo, Long idTienda, CategoriaRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        String slug = slug(r.nombre());
        if (categoriaRepository.existsByIdTiendaAndSlug(idTienda, slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay una categoría con ese nombre");
        }
        Categoria c = new Categoria();
        c.setIdTienda(idTienda);
        copiar(c, r, slug);
        categoriaRepository.saveAndFlush(c);
        log.info("Categoría creada: idCategoria={}, tienda={}", c.getIdCategoria(), idTienda);
        return buscarFila(idTienda, c.getIdCategoria());
    }

    @Transactional
    public CategoriaResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idCategoria, CategoriaRequest r) {
        verificadorTienda.delDueno(yo, idTienda);
        Categoria c = buscar(idTienda, idCategoria);
        String slug = slug(r.nombre());
        if (categoriaRepository.existsByIdTiendaAndSlugAndIdCategoriaNot(idTienda, slug, idCategoria)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay otra categoría con ese nombre");
        }
        copiar(c, r, slug);
        categoriaRepository.saveAndFlush(c);
        log.info("Categoría actualizada: idCategoria={}, tienda={}", idCategoria, idTienda);
        return buscarFila(idTienda, idCategoria);
    }

    /** Borra la categoría. Los productos NO se borran: solo dejan de estar en ella (ON DELETE CASCADE). */
    @Transactional
    public void eliminar(UsuarioAutenticado yo, Long idTienda, Long idCategoria) {
        verificadorTienda.delDueno(yo, idTienda);
        categoriaRepository.delete(buscar(idTienda, idCategoria));
        categoriaRepository.flush();
        log.info("Categoría eliminada: idCategoria={}, tienda={}", idCategoria, idTienda);
    }

    /** Tienda pública: categorías activas con productos activos. */
    @Transactional(readOnly = true)
    public List<CategoriaResponse> publicas(String subdominio) {
        Tienda t = tiendaRepository.findBySubdominio(subdominio.trim().toLowerCase(Locale.ROOT))
                .filter(Tienda::estaActiva)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        return categoriaRepository.filasPublicas(t.getIdTienda()).stream().map(CategoriaResponse::desde).toList();
    }

    /**
     * Deja un producto exactamente en estas categorías (lo usa ProductoService al crear o editar).
     * null = no se tocan. Las categorías deben ser de la misma tienda (la base también lo revisa).
     */
    @Transactional
    public void asignar(Long idTienda, Long idProducto, List<Long> categorias) {
        if (categorias == null) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(categorias);
        ids.remove(null);
        if (!ids.isEmpty() && categoriaRepository.contarDeTienda(idTienda, ids) != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Alguna categoría elegida no existe en esta tienda");
        }
        categoriaRepository.quitarDeProducto(idProducto);
        for (Long idCategoria : ids) {
            categoriaRepository.agregarAProducto(idProducto, idCategoria);
        }
    }

    // ------------------------------------------------------------------ apoyo

    private Categoria buscar(Long idTienda, Long idCategoria) {
        return categoriaRepository.findByIdCategoriaAndIdTienda(idCategoria, idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La categoría no existe en esta tienda"));
    }

    private CategoriaResponse buscarFila(Long idTienda, Long idCategoria) {
        return categoriaRepository.filasDeTienda(idTienda).stream()
                .filter(f -> f.getIdCategoria().equals(idCategoria))
                .findFirst().map(CategoriaResponse::desde)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La categoría no existe en esta tienda"));
    }

    private static void copiar(Categoria c, CategoriaRequest r, String slug) {
        c.setNombre(r.nombre().trim());
        c.setSlug(slug);
        c.setDescripcion(r.descripcion() == null || r.descripcion().isBlank() ? null : r.descripcion().trim());
        c.setEstado(Boolean.FALSE.equals(r.activa()) ? Categoria.OCULTA : Categoria.ACTIVA);
    }

    /** Mismo slug que los productos ("Polos de Algodón" → "polos-de-algodon"), con el largo de la columna. */
    private static String slug(String nombre) {
        String s = ProductoService.slug(nombre);
        return s.length() > 120 ? s.substring(0, 120).replaceAll("-+$", "") : s;
    }
}
