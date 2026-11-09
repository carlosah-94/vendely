package curso.fullstack.shopify.controller;

import curso.fullstack.shopify.dto.ClienteActualizarRequest;
import curso.fullstack.shopify.dto.ClienteCrearRequest;
import curso.fullstack.shopify.dto.ClienteResponse;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.service.ClienteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD de clientes de una tienda, desde el panel del dueño (issue #9).
 *
 * Va bajo /api/panel/** porque esa ruta ya exige el rol ADMIN_TIENDA (SecurityConfig).
 * La ruta lleva {idTienda} porque un dueño puede tener varias tiendas; el service comprueba
 * que esa tienda sea suya. El controller solo recibe la petición y delega: las reglas están
 * en ClienteService.
 *
 * Nota: el registro público de un cliente (cuando él mismo se registra en la tienda) es otro
 * endpoint: POST /api/auth/registro/cliente. Este es el que usa el dueño para gestionarlos.
 */
@RestController
@RequestMapping("/api/panel/tiendas/{idTienda}/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteResponse> listar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                        @PathVariable Long idTienda) {
        return clienteService.listar(yo, idTienda);
    }

    @GetMapping("/{idCliente}")
    public ClienteResponse ver(@AuthenticationPrincipal UsuarioAutenticado yo,
                               @PathVariable Long idTienda, @PathVariable Long idCliente) {
        return clienteService.ver(yo, idTienda, idCliente);
    }

    /** 201 Created: se creó un recurso nuevo (el cliente con su dirección). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                                 @PathVariable Long idTienda,
                                 @Valid @RequestBody ClienteCrearRequest r) {
        return clienteService.crear(yo, idTienda, r);
    }

    @PutMapping("/{idCliente}")
    public ClienteResponse actualizar(@AuthenticationPrincipal UsuarioAutenticado yo,
                                      @PathVariable Long idTienda, @PathVariable Long idCliente,
                                      @Valid @RequestBody ClienteActualizarRequest r) {
        return clienteService.actualizar(yo, idTienda, idCliente, r);
    }

    /** 204 No Content: se borró y no hay nada que devolver. */
    @DeleteMapping("/{idCliente}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado yo,
                         @PathVariable Long idTienda, @PathVariable Long idCliente) {
        clienteService.eliminar(yo, idTienda, idCliente);
    }
}
