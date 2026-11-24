package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.PerfilRequest;
import curso.fullstack.shopify.dto.PerfilResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Mi perfil (cualquier cuenta con sesión):
 * GET /api/cuenta/perfil   los datos de la cuenta (y del negocio si es dueño)
 * PUT /api/cuenta/perfil   cambia nombre, apellidos, teléfono, razón social y teléfono del negocio
 */
@RestController
@RequestMapping("/api/cuenta/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    public PerfilResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return perfilService.ver(yo);
    }

    @PutMapping
    public PerfilService.Guardado guardar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody PerfilRequest r) {
        return perfilService.guardar(yo, r);
    }
}
