package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.EnviosResponse;
import curso.fullstack.shopify.dto.TarifaRequest;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.EnvioService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Envíos de una tienda (panel del dueño, rol ADMIN_TIENDA).
 *
 * GET    /api/panel/tiendas/{idTienda}/envios                          tarifas nacionales e internacionales
 * PUT    /api/panel/tiendas/{idTienda}/envios/nacional/{idDivision}    crear o cambiar la tarifa de una región
 * PUT    /api/panel/tiendas/{idTienda}/envios/internacional/{idPais}   crear o cambiar la tarifa a un país
 * DELETE /api/panel/tiendas/{idTienda}/envios/internacional/{idPais}   dejar de enviar a un país
 *
 * Todos devuelven la configuración completa actualizada, para que la pantalla se redibuje con ella.
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/envios")
public class EnvioController {

    private final EnvioService envioService;

    public EnvioController(EnvioService envioService) {
        this.envioService = envioService;
    }

    @GetMapping
    public EnviosResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda) {
        return envioService.envios(yo, idTienda);
    }

    @PutMapping("/nacional/{idDivision}")
    public EnviosResponse guardarNacional(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                          @PathVariable Long idDivision, @Valid @RequestBody TarifaRequest r) {
        return envioService.guardarNacional(yo, idTienda, idDivision, r);
    }

    @PutMapping("/internacional/{idPais}")
    public EnviosResponse guardarInternacional(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                               @PathVariable String idPais, @Valid @RequestBody TarifaRequest r) {
        return envioService.guardarInternacional(yo, idTienda, idPais, r);
    }

    @DeleteMapping("/internacional/{idPais}")
    public EnviosResponse borrarInternacional(@AuthenticationPrincipal UsuarioAutenticado yo, @PathVariable Long idTienda,
                                              @PathVariable String idPais) {
        return envioService.borrarInternacional(yo, idTienda, idPais);
    }
}
