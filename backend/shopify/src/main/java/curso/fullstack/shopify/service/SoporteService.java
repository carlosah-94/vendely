package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.BandejaSoporteResponse;
import curso.fullstack.shopify.dto.MensajeSoporteResponse;
import curso.fullstack.shopify.dto.SoporteResponse;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.MensajeSoporte;
import curso.fullstack.shopify.entity.RegistroSoporte;
import curso.fullstack.shopify.entity.RolUsuario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.MensajeSoporteRepository;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.repository.RegistroSoporteRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import curso.fullstack.shopify.security.VerificadorTienda;
import curso.fullstack.shopify.tiemporeal.TiempoRealSoporte;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Acceso de soporte (V16): el dueño deja entrar al equipo de Vendely a armar su tienda por unos
 * días, y lo puede quitar cuando quiera. Solo el dueño de la tienda lo da, lo quita o lo mira:
 * ni sus empleados ni el propio soporte.
 *
 * Mensajes (V17): el dueño explica qué quiere (al dar el acceso o después) y soporte le responde
 * desde la misma tienda. Mientras el acceso esté activo escriben los dos; leer la conversación lo
 * puede siempre el dueño, y soporte solo mientras tenga acceso. A cada lado le llega un correo.
 *
 * Qué puede hacer soporte una vez dentro lo decide VerificadorTienda.
 */
@Slf4j
@Service
public class SoporteService {

    static final String DA_ACCESO_PRIMERO =
            "Primero da acceso a soporte de Vendely: así el equipo puede leer tu mensaje y entrar a tu tienda";

    private final VerificadorTienda verificadorTienda;
    private final TiendaRepository tiendaRepository;
    private final RegistroSoporteRepository registroRepository;
    private final MensajeSoporteRepository mensajeRepository;
    private final LoginRepository loginRepository;
    private final PropietarioRepository propietarioRepository;
    private final AvisoSoporte aviso;
    private final TiempoRealSoporte enVivo;

    public SoporteService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
            RegistroSoporteRepository registroRepository, MensajeSoporteRepository mensajeRepository,
            LoginRepository loginRepository, PropietarioRepository propietarioRepository, AvisoSoporte aviso,
            TiempoRealSoporte enVivo) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.registroRepository = registroRepository;
        this.mensajeRepository = mensajeRepository;
        this.loginRepository = loginRepository;
        this.propietarioRepository = propietarioRepository;
        this.aviso = aviso;
        this.enVivo = enVivo;
    }

    @Transactional(readOnly = true)
    public SoporteResponse ver(UsuarioAutenticado yo, Long idTienda) {
        return responder(delDueno(yo, idTienda));
    }

    /**
     * Da (o extiende) el permiso: vence dentro de "dias" días contados desde ahora. Si trae
     * mensaje, queda como el primero de la conversación y se avisa al equipo.
     */
    @Transactional
    public SoporteResponse permitir(UsuarioAutenticado yo, Long idTienda, int dias, String mensaje) {
        Tienda t = delDueno(yo, idTienda);
        t.setSoporteHasta(OffsetDateTime.now().plusDays(dias));
        tiendaRepository.save(t);
        registroRepository.save(RegistroSoporte.de(idTienda, yo.idLogin(), RegistroSoporte.PERMITIDO));
        log.info("Acceso de soporte permitido: idTienda={}, dias={}", idTienda, dias);
        String texto = limpio(mensaje);
        if (texto != null) {
            guardarDelDueno(yo, t, texto);
        } else {
            String tienda = t.getNombre();
            despuesDeGuardar(() -> enVivo.acceso(idTienda, tienda));
        }
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
                String tienda = t.getNombre();
                despuesDeGuardar(() -> enVivo.acceso(idTienda, tienda));
            }
        }
        return responder(t);
    }

    // ------------------------------------------------------------------ mensajes

    /** La conversación: el dueño siempre; soporte, mientras tenga acceso. */
    @Transactional(readOnly = true)
    public List<MensajeSoporteResponse> mensajes(UsuarioAutenticado yo, Long idTienda) {
        return conversacion(duenoOSoporte(yo, idTienda).getIdTienda());
    }

    /**
     * Escribe en la conversación. El dueño, solo con el acceso activo (si no, soporte no puede
     * leerlo ni entrar); soporte, mientras tenga acceso. Devuelve la conversación completa.
     */
    @Transactional
    public List<MensajeSoporteResponse> escribir(UsuarioAutenticado yo, Long idTienda, String mensaje) {
        Tienda t = duenoOSoporte(yo, idTienda);
        String texto = limpio(mensaje);
        if (texto == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escribe tu mensaje");
        }
        if (yo.rol() == RolUsuario.SUPERADMIN) {
            mensajeRepository.save(MensajeSoporte.de(idTienda, yo.idLogin(), true, texto));
            String correoDueno = correoDelDueno(t);
            String tienda = t.getNombre();
            despuesDeGuardar(() -> {
                enVivo.mensaje(idTienda, tienda, true, texto);
                aviso.alDueno(correoDueno, tienda, texto);
            });
            log.info("Soporte respondió: idTienda={}", idTienda);
        } else {
            if (!t.soporteActivo()) {
                throw new ApiException(HttpStatus.CONFLICT, DA_ACCESO_PRIMERO);
            }
            guardarDelDueno(yo, t, texto);
        }
        return conversacion(idTienda);
    }

    /**
     * La bandeja del equipo (solo superadmin): por cada tienda con acceso activo, cuántos mensajes
     * del dueño esperan respuesta y cuál fue el último. Así se ve de un vistazo a quién contestar.
     */
    @Transactional(readOnly = true)
    public List<BandejaSoporteResponse> bandeja() {
        List<Long> ids = tiendaRepository.findBySoporteHastaAfterOrderBySoporteHastaAsc(OffsetDateTime.now())
                .stream().map(Tienda::getIdTienda).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MensajeSoporte>> porTienda = new LinkedHashMap<>();
        ids.forEach(id -> porTienda.put(id, new ArrayList<>()));
        for (MensajeSoporte m : mensajeRepository.findByIdTiendaInOrderByIdMensajeDesc(ids)) {
            porTienda.get(m.getIdTienda()).add(m);
        }
        return porTienda.entrySet().stream().map(e -> {
            List<MensajeSoporte> lista = e.getValue();   // del más nuevo al más antiguo
            if (lista.isEmpty()) {
                return new BandejaSoporteResponse(e.getKey(), 0, null, null, null);
            }
            int sinResponder = 0;
            for (MensajeSoporte m : lista) {
                if (Boolean.TRUE.equals(m.getDeSoporte())) {
                    break;
                }
                sinResponder++;
            }
            MensajeSoporte ultimo = lista.get(0);
            return new BandejaSoporteResponse(e.getKey(), sinResponder, ultimo.getTexto(), ultimo.getFecha(),
                    Boolean.TRUE.equals(ultimo.getDeSoporte()));
        }).toList();
    }

    // ------------------------------------------------------------------ apoyo

    /** Solo el dueño: un empleado recibe 403, y soporte no se da permiso a sí mismo. */
    private Tienda delDueno(UsuarioAutenticado yo, Long idTienda) {
        return verificadorTienda.soloElDueno(yo, idTienda);
    }

    /** El dueño (no sus empleados), o soporte con acceso activo. */
    private Tienda duenoOSoporte(UsuarioAutenticado yo, Long idTienda) {
        return yo.rol() == RolUsuario.SUPERADMIN
                ? verificadorTienda.acceso(yo, idTienda).tienda()
                : verificadorTienda.soloElDueno(yo, idTienda);
    }

    private void guardarDelDueno(UsuarioAutenticado yo, Tienda t, String texto) {
        mensajeRepository.save(MensajeSoporte.de(t.getIdTienda(), yo.idLogin(), false, texto));
        Login dueno = loginRepository.findById(yo.idLogin()).orElse(null);
        String nombre = dueno == null ? "El dueño" : (dueno.getNombres() + " " + dueno.getPrimerApellido()).trim();
        String correo = dueno == null ? t.getCorreoContacto() : dueno.getCorreo();
        String tienda = t.getNombre();
        String subdominio = t.getSubdominio();
        Long idTienda = t.getIdTienda();
        despuesDeGuardar(() -> {
            enVivo.mensaje(idTienda, tienda, false, texto);
            aviso.alEquipo(tienda, subdominio, nombre, correo, texto);
        });
        log.info("Mensaje del dueño a soporte: idTienda={}", t.getIdTienda());
    }

    /** El correo de la cuenta del dueño; si no se encuentra, el de contacto de la tienda. */
    private String correoDelDueno(Tienda t) {
        return propietarioRepository.findById(t.getIdPropietario())
                .flatMap(p -> loginRepository.findById(p.getIdLogin()))
                .map(Login::getCorreo)
                .orElse(t.getCorreoContacto());
    }

    /** El correo sale solo si el mensaje de verdad se guardó (la transacción terminó bien). */
    private static void despuesDeGuardar(Runnable accion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accion.run();
            }
        });
    }

    private List<MensajeSoporteResponse> conversacion(Long idTienda) {
        return mensajeRepository.findByIdTiendaOrderByFechaAscIdMensajeAsc(idTienda).stream()
                .map(MensajeSoporteResponse::desde).toList();
    }

    /** El texto sin espacios de más; null si quedó vacío. */
    private static String limpio(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.strip();
        return t.length() > MensajeSoporte.MAXIMO ? t.substring(0, MensajeSoporte.MAXIMO) : t;
    }

    private SoporteResponse responder(Tienda t) {
        return new SoporteResponse(t.soporteActivo() ? t.getSoporteHasta() : null,
                registroRepository.findTop50ByIdTiendaOrderByFechaDescIdRegistroDesc(t.getIdTienda()).stream()
                        .map(SoporteResponse.Registro::desde).toList());
    }
}
