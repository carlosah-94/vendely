package curso.fullstack.shopify.service;

import curso.fullstack.shopify.entity.PermisoTienda;
import curso.fullstack.shopify.dto.InventarioRequest;
import curso.fullstack.shopify.dto.InventarioResponse;
import curso.fullstack.shopify.entity.Inventario;
import curso.fullstack.shopify.evento.StockCambiadoEvent;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.InventarioRepository;
import curso.fullstack.shopify.repository.VarianteRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventario de una tienda: el stock de cada variante, el aviso de stock bajo y la reposición.
 * Funciona como el cuaderno del almacén: "recibir" suma lo que llegó; "cantidad" corrige
 * el número después de contar; "stockMinimo" define cuándo avisar que queda poco.
 */
@Slf4j
@Service
public class InventarioService {

    private final VerificadorTienda verificadorTienda;
    private final InventarioRepository inventarioRepository;
    private final VarianteRepository varianteRepository;
    private final ApplicationEventPublisher eventos;

    public InventarioService(VerificadorTienda verificadorTienda, InventarioRepository inventarioRepository,
                             VarianteRepository varianteRepository, ApplicationEventPublisher eventos) {
        this.eventos = eventos;
        this.verificadorTienda = verificadorTienda;
        this.inventarioRepository = inventarioRepository;
        this.varianteRepository = varianteRepository;
    }

    @Transactional(readOnly = true)
    public List<InventarioResponse> listar(UsuarioAutenticado yo, Long idTienda) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.INVENTARIO, PermisoTienda.PRODUCTOS);
        return inventarioRepository.filasDeTienda(idTienda).stream().map(InventarioResponse::desde).toList();
    }

    @Transactional
    public InventarioResponse actualizar(UsuarioAutenticado yo, Long idTienda, Long idVariante, InventarioRequest r) {
        verificadorTienda.conPermiso(yo, idTienda, PermisoTienda.INVENTARIO);
        // La variante debe ser de un producto de ESTA tienda (si es de otra, 404: no se revela que existe).
        varianteRepository.buscarInfo(idVariante)
                .filter(i -> idTienda.equals(i.getIdTienda()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La variante no existe en esta tienda"));
        if (r.recibir() == null && r.cantidad() == null && r.stockMinimo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica cuánto recibiste, el stock exacto o el stock mínimo");
        }
        if (r.recibir() != null && r.cantidad() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Envía \"recibir\" o \"cantidad\", no los dos");
        }

        // Si la variante todavía no tiene fila de inventario, se crea con 0.
        Inventario i = inventarioRepository.findById(idVariante).orElseGet(() -> {
            Inventario nuevo = new Inventario();
            nuevo.setIdVariante(idVariante);
            nuevo.setCantidadDisponible(0);
            return inventarioRepository.saveAndFlush(nuevo);
        });

        if (r.cantidad() != null) {
            i.setCantidadDisponible(r.cantidad());
        }
        if (r.stockMinimo() != null) {
            i.setStockMinimo(r.stockMinimo());
        }
        inventarioRepository.saveAndFlush(i);

        if (r.recibir() != null) {
            // Se suma en la base, después de guardar lo anterior (ver InventarioRepository.recibir).
            inventarioRepository.recibir(idVariante, r.recibir());
            log.info("Mercadería recibida: tienda={}, variante={}, +{}", idTienda, idVariante, r.recibir());
        } else {
            log.info("Inventario actualizado: tienda={}, variante={}", idTienda, idVariante);
        }

        Integer quedan = inventarioRepository.cantidadDe(idVariante);
        eventos.publishEvent(new StockCambiadoEvent(idTienda, idVariante, quedan == null ? 0 : quedan));

        return inventarioRepository.filasDeTienda(idTienda).stream()
                .filter(f -> f.getIdVariante().equals(idVariante))
                .findFirst().map(InventarioResponse::desde)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La variante no existe en esta tienda"));
    }
}
