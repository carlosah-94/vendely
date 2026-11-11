package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.PaisResponse;
import curso.fullstack.shopify.service.EnvioService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/public/paises → todos los países, para los selectores de dirección y de tarifas. */
@RestController
@RequestMapping("/api/public/paises")
public class PaisController {

    private final EnvioService envioService;

    public PaisController(EnvioService envioService) {
        this.envioService = envioService;
    }

    @GetMapping
    public List<PaisResponse> listar() {
        return envioService.paises();
    }
}
