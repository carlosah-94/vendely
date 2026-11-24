package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.InformeResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.InformeService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Informes estadísticos de una tienda (panel → Informes). Solo el dueño.
 * GET /api/panel/tiendas/{idTienda}/informes?desde=2026-11-01&hasta=2026-11-30&zona=America/Lima
 * Sin fechas: los últimos 30 días. zona: la del navegador (los días se cuentan en esa hora).
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/informes")
public class InformeController {

    private final InformeService informeService;

    public InformeController(InformeService informeService) {
        this.informeService = informeService;
    }

    @GetMapping
    public InformeResponse informe(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                   @RequestParam(required = false) String zona) {
        return informeService.informe(yo, idTienda, desde, hasta, zona);
    }
}
