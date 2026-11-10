package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.repository.DireccionRepository;
import curso.fullstack.shopify.repository.DireccionRepository.DistritoFila;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** GET /api/public/paises/PE/distritos?buscar=mira → para el selector del formulario de clientes. */
@RestController
@RequestMapping("/api/public/paises/{idPais}/distritos")
@RequiredArgsConstructor
public class DistritoController {

    private final DireccionRepository direccionRepository;

    @GetMapping
    public List<DistritoFila> buscar(@PathVariable String idPais,
                                     @RequestParam(defaultValue = "") String buscar) {
        return direccionRepository.buscarDistritos(idPais.toUpperCase(Locale.ROOT), buscar.trim());
    }
}