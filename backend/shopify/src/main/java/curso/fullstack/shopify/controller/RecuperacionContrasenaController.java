package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.CodigoCorreoRequest;
import curso.fullstack.shopify.dto.CodigoEnviadoResponse;
import curso.fullstack.shopify.dto.RestablecerContrasenaRequest;
import curso.fullstack.shopify.service.RecuperacionContrasenaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * "¿Olvidaste tu contraseña?" (sin sesión, como el login):
 * POST /api/auth/recuperar/codigo        { correo, subdominio? }  envía un código si la cuenta existe
 * POST /api/auth/recuperar/restablecer   { correo, subdominio?, codigo, contrasena }  204
 * Sin subdominio, cuentas del panel; con subdominio, el cliente de esa tienda.
 */
@RestController
@RequestMapping("/api/auth/recuperar")
public class RecuperacionContrasenaController {

    private final RecuperacionContrasenaService recuperacion;

    public RecuperacionContrasenaController(RecuperacionContrasenaService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @PostMapping("/codigo")
    public CodigoEnviadoResponse codigo(@Valid @RequestBody CodigoCorreoRequest r) {
        return recuperacion.enviarCodigo(r.correo(), r.subdominio());
    }

    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody RestablecerContrasenaRequest r) {
        recuperacion.restablecer(r);
    }
}
