package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.InventarioRequest;
import curso.fullstack.shopify.dto.InventarioResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.InventarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Inventario (panel del dueño).
 * GET   /api/panel/tiendas/{idTienda}/inventario                 stock de cada variante
 * PATCH /api/panel/tiendas/{idTienda}/inventario/{idVariante}    {"recibir": 20} | {"cantidad": 15} | {"stockMinimo": 5}
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping
    public List<InventarioResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return inventarioService.listar(yo, idTienda);
    }

    @PatchMapping("/{idVariante}")
    public InventarioResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                         @PathVariable Long idVariante, @Valid @RequestBody InventarioRequest r) {
        return inventarioService.actualizar(yo, idTienda, idVariante, r);
    }
}
