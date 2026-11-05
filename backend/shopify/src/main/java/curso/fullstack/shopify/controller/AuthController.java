package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.AuthResponse;
import curso.fullstack.shopify.dto.CambioContrasenaRequest;
import curso.fullstack.shopify.dto.LoginRequest;
import curso.fullstack.shopify.dto.RegistroClienteRequest;
import curso.fullstack.shopify.dto.RegistroDuenoRequest;
import curso.fullstack.shopify.dto.UsuarioResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/registro/dueno     crear cuenta de dueño de tienda      (abierto)
 * POST /api/auth/registro/cliente   crear cuenta de cliente en una tienda (abierto)
 * POST /api/auth/login              iniciar sesión → token               (abierto)
 * GET  /api/auth/yo                 datos de quien inició sesión         (con token)
 * POST /api/auth/cambiar-contrasena cambiar la contraseña → token nuevo  (con token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro/dueno")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registrarDueno(@Valid @RequestBody RegistroDuenoRequest request) {
        return authService.registrarDueno(request);
    }

    @PostMapping("/registro/cliente")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registrarCliente(@Valid @RequestBody RegistroClienteRequest request) {
        return authService.registrarCliente(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.iniciarSesion(request);
    }

    @GetMapping("/yo")
    public UsuarioResponse yo(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return authService.miCuenta(yo);
    }

    @PostMapping("/cambiar-contrasena")
    public AuthResponse cambiarContrasena(@AuthenticationPrincipal UsuarioAutenticado yo,
                                          @Valid @RequestBody CambioContrasenaRequest request) {
        return authService.cambiarContrasena(yo, request);
    }
}
