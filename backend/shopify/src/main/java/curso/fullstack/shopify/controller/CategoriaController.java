package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.CategoriaRequest;
import curso.fullstack.shopify.dto.CategoriaResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.CategoriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Categorías de productos (panel del dueño).
 * GET    /api/panel/tiendas/{idTienda}/categorias
 * POST   /api/panel/tiendas/{idTienda}/categorias
 * PUT    /api/panel/tiendas/{idTienda}/categorias/{idCategoria}
 * DELETE /api/panel/tiendas/{idTienda}/categorias/{idCategoria}
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<CategoriaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return categoriaService.listar(yo, idTienda);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                   @Valid @RequestBody CategoriaRequest r) {
        return categoriaService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idCategoria}")
    public CategoriaResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                        @PathVariable Long idCategoria, @Valid @RequestBody CategoriaRequest r) {
        return categoriaService.actualizar(yo, idTienda, idCategoria, r);
    }

    @DeleteMapping("/{idCategoria}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                         @PathVariable Long idCategoria) {
        categoriaService.eliminar(yo, idTienda, idCategoria);
    }
}
