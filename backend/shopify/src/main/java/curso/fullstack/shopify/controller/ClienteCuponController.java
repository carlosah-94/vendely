package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.CuponAplicadoResponse;
import curso.fullstack.shopify.dto.ValidarCuponRequest;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.CuponService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Checkout de la tienda: ¿sirve este código y cuánto rebaja?
 * POST /api/cliente/cupones/validar  { "codigo": "VERANO10", "subtotal": 120.00 }
 * 200 con el descuento, o 400 con el motivo ("ya venció", "es para compras desde...").
 */
@RestController
@RequestMapping("/api/cliente/cupones")
public class ClienteCuponController {

    private final CuponService cuponService;

    public ClienteCuponController(CuponService cuponService) {
        this.cuponService = cuponService;
    }

    @PostMapping("/validar")
    public CuponAplicadoResponse validar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                         @Valid @RequestBody ValidarCuponRequest r) {
        return cuponService.validar(yo, r.codigo(), r.subtotal());
    }
}
