package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.CambioEstadoRequest;
import curso.fullstack.shopify.dto.CuponRequest;
import curso.fullstack.shopify.dto.CuponResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.CuponService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Códigos de descuento (panel → Descuentos → Códigos). Requiere el permiso de productos.
 * GET    /api/panel/tiendas/{idTienda}/cupones
 * POST   /api/panel/tiendas/{idTienda}/cupones
 * PUT    /api/panel/tiendas/{idTienda}/cupones/{idCupon}
 * PATCH  /api/panel/tiendas/{idTienda}/cupones/{idCupon}/estado   { "activa": false } = pausar
 * DELETE /api/panel/tiendas/{idTienda}/cupones/{idCupon}          (solo si ningún pedido lo usó)
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/cupones")
public class CuponController {

    private final CuponService cuponService;

    public CuponController(CuponService cuponService) {
        this.cuponService = cuponService;
    }

    @GetMapping
    public List<CuponResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return cuponService.listar(yo, idTienda);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuponResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                               @Valid @RequestBody CuponRequest r) {
        return cuponService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idCupon}")
    public CuponResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                    @PathVariable Long idCupon, @Valid @RequestBody CuponRequest r) {
        return cuponService.actualizar(yo, idTienda, idCupon, r);
    }

    @PatchMapping("/{idCupon}/estado")
    public CuponResponse cambiarEstado(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                       @PathVariable Long idCupon, @Valid @RequestBody CambioEstadoRequest r) {
        return cuponService.cambiarEstado(yo, idTienda, idCupon, r.activa());
    }

    @DeleteMapping("/{idCupon}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                         @PathVariable Long idCupon) {
        cuponService.eliminar(yo, idTienda, idCupon);
    }
}
