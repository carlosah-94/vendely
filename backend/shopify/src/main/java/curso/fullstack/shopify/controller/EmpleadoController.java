package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.EmpleadoCrearRequest;
import curso.fullstack.shopify.dto.EmpleadoEditarRequest;
import curso.fullstack.shopify.dto.EmpleadoResponse;
import curso.fullstack.shopify.dto.EmpleadosResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.EmpleadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Empleados de la tienda (solo el dueño).
 * GET    /api/panel/tiendas/{idTienda}/empleados
 * POST   /api/panel/tiendas/{idTienda}/empleados
 * PUT    /api/panel/tiendas/{idTienda}/empleados/{idLogin}
 * DELETE /api/panel/tiendas/{idTienda}/empleados/{idLogin}
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/empleados")
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @GetMapping
    public EmpleadosResponse listar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return empleadoService.listar(yo, idTienda);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpleadoResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                  @Valid @RequestBody EmpleadoCrearRequest r) {
        return empleadoService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idLogin}")
    public EmpleadoResponse editar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                   @PathVariable Long idLogin, @Valid @RequestBody EmpleadoEditarRequest r) {
        return empleadoService.editar(yo, idTienda, idLogin, r);
    }

    @DeleteMapping("/{idLogin}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                       @PathVariable Long idLogin) {
        empleadoService.quitar(yo, idTienda, idLogin);
    }
}
