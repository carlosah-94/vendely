package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.SoporteResponse;
import curso.fullstack.shopify.entity.RegistroSoporte;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.repository.RegistroSoporteRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso de soporte (V16): el dueño deja entrar al equipo de Vendely a armar su tienda por unos
 * días, y lo puede quitar cuando quiera. Solo el dueño de la tienda lo da, lo quita o lo mira:
 * ni sus empleados ni el propio soporte.
 *
 * Qué puede hacer soporte una vez dentro lo decide VerificadorTienda.
 */
@Slf4j
@Service
public class SoporteService {

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final RegistroSoporteRepository registroRepository;

    public SoporteService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
            RegistroSoporteRepository registroRepository) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.registroRepository = registroRepository;
    }

    @Transactional(readOnly = true)
    public SoporteResponse ver(UsuarioAutenticado yo, Long idTienda) {
        return responder(delDueno(yo, idTienda));
    }

    /** Da (o extiende) el permiso: vence dentro de "dias" días contados desde ahora. */
    @Transactional
    public SoporteResponse permitir(UsuarioAutenticado yo, Long idTienda, int dias) {
        Tienda t = delDueno(yo, idTienda);
        t.setSoporteHasta(OffsetDateTime.now().plusDays(dias));
        tiendaRepository.save(t);
        registroRepository.save(RegistroSoporte.de(idTienda, yo.idLogin(), RegistroSoporte.PERMITIDO));
        log.info("Acceso de soporte permitido: idTienda={}, dias={}", idTienda, dias);
        return responder(t);
    }

    /** Quita el permiso al instante. Si no había uno activo, no hace nada. */
    @Transactional
    public SoporteResponse quitar(UsuarioAutenticado yo, Long idTienda) {
        Tienda t = delDueno(yo, idTienda);
        if (t.getSoporteHasta() != null) {
            boolean estabaActivo = t.soporteActivo();
            t.setSoporteHasta(null);
            tiendaRepository.save(t);
            if (estabaActivo) {
                registroRepository.save(RegistroSoporte.de(idTienda, yo.idLogin(), RegistroSoporte.QUITADO));
                log.info("Acceso de soporte quitado: idTienda={}", idTienda);
            }
        }
        return responder(t);
    }

    // ------------------------------------------------------------------ apoyo

    /** Solo el dueño: un empleado recibe 403, y soporte no se da permiso a sí mismo. */
    private Tienda delDueno(UsuarioAutenticado yo, Long idTienda) {
        return verificadorTienda.soloElDueno(yo, idTienda);
    }

    private SoporteResponse responder(Tienda t) {
        return new SoporteResponse(t.soporteActivo() ? t.getSoporteHasta() : null,
                registroRepository.findTop50ByIdTiendaOrderByFechaDescIdRegistroDesc(t.getIdTienda()).stream()
                        .map(SoporteResponse.Registro::desde).toList());
    }
}
