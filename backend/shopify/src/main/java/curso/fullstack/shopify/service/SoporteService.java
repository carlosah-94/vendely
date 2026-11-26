package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.BandejaSoporteResponse;
import curso.fullstack.shopify.dto.ImagenSubidaResponse;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
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
    private final CloudinaryClient cloudinary;
    private final TransactionTemplate transaccion;

    public SoporteService(VerificadorTienda verificadorTienda, TiendaRepository tiendaRepository,
            RegistroSoporteRepository registroRepository, MensajeSoporteRepository mensajeRepository,
            LoginRepository loginRepository, PropietarioRepository propietarioRepository, AvisoSoporte aviso,
            TiempoRealSoporte enVivo, CloudinaryClient cloudinary, TransactionTemplate transaccion) {
        this.verificadorTienda = verificadorTienda;
        this.tiendaRepository = tiendaRepository;
        this.registroRepository = registroRepository;
        this.mensajeRepository = mensajeRepository;
        this.loginRepository = loginRepository;
        this.propietarioRepository = propietarioRepository;
        this.aviso = aviso;
        this.enVivo = enVivo;
        this.cloudinary = cloudinary;
        this.transaccion = transaccion;
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
            guardar(yo, t, texto, null);
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
        Tienda t = puedeEscribir(yo, idTienda);
        String texto = limpio(mensaje);
        if (texto == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escribe tu mensaje");
        }
        guardar(yo, t, texto, null);
        return conversacion(idTienda);
    }

    /**
     * Escribe con un archivo adjunto (V26): el Excel de productos, las fotos, un PDF... El texto
     * es opcional. Mismas reglas que escribir.
     *
     * No es @Transactional a propósito: subir a Cloudinary puede tardar unos segundos y no tiene
     * sentido tener una conexión de la base tomada mientras tanto. Primero se revisan los
     * permisos y el archivo, después se sube, y recién ahí se guarda (en su propia transacción).
     * Si guardar falla, el archivo se borra de Cloudinary para no dejarlo huérfano.
     */
    public List<MensajeSoporteResponse> escribirConAdjunto(UsuarioAutenticado yo, Long idTienda, String mensaje,
                                                           MultipartFile archivo) {
        puedeEscribir(yo, idTienda);
        if (!cloudinary.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adjuntar archivos no está disponible por ahora. Puedes pegar el enlace (Drive, Dropbox...) en tu mensaje");
        }
        ArchivoSoporte.Valido valido = ArchivoSoporte.validar(archivo);
        String texto = limpio(mensaje);

        ImagenSubidaResponse subida;
        try {
            subida = cloudinary.subirArchivo(valido.contenido(), valido.nombre(),
                    "vendely/t" + idTienda + "/soporte", valido.esImagen());
        } catch (RestClientException e) {
            log.error("Cloudinary falló al subir un adjunto de soporte: idTienda={}, motivo={}", idTienda, e.getMessage());
            subida = null;
        }
        if (subida == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo subir el archivo. Inténtalo de nuevo");
        }
        Adjunto adjunto = new Adjunto(subida.url(), subida.publicId(), valido.nombre(), valido.tipo(),
                valido.contenido().length);
        try {
            return transaccion.execute(estado -> {
                // Se vuelve a revisar dentro de la transacción: el acceso pudo vencer mientras subía.
                guardar(yo, puedeEscribir(yo, idTienda), texto, adjunto);
                return conversacion(idTienda);
            });
        } catch (RuntimeException e) {
            cloudinary.borrar(subida.publicId(), valido.esImagen());
            throw e;
        }
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
            return new BandejaSoporteResponse(e.getKey(), sinResponder, ultimo.resumen(), ultimo.getFecha(),
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

    /** El archivo ya subido a Cloudinary que acompaña un mensaje. */
    private record Adjunto(String url, String publicId, String nombre, String tipo, long bytes) {
    }

    /**
     * Quién puede escribir: el dueño con el acceso activo (si no, soporte no podría leerlo), o
     * soporte mientras tenga acceso. Devuelve la tienda.
     */
    private Tienda puedeEscribir(UsuarioAutenticado yo, Long idTienda) {
        Tienda t = duenoOSoporte(yo, idTienda);
        if (yo.rol() != RolUsuario.SUPERADMIN && !t.soporteActivo()) {
            throw new ApiException(HttpStatus.CONFLICT, DA_ACCESO_PRIMERO);
        }
        return t;
    }

    /**
     * Guarda el mensaje (texto puede ser null si hay adjunto) y, cuando la transacción termina
     * bien, avisa en vivo y por correo al otro lado.
     */
    private void guardar(UsuarioAutenticado yo, Tienda t, String texto, Adjunto adjunto) {
        boolean deSoporte = yo.rol() == RolUsuario.SUPERADMIN;
        Long idTienda = t.getIdTienda();
        MensajeSoporte m = MensajeSoporte.de(idTienda, yo.idLogin(), deSoporte, texto);
        if (adjunto != null) {
            m.setAdjuntoUrl(adjunto.url());
            m.setAdjuntoPublicId(adjunto.publicId());
            m.setAdjuntoNombre(adjunto.nombre());
            m.setAdjuntoTipo(adjunto.tipo());
            m.setAdjuntoBytes(adjunto.bytes());
        }
        mensajeRepository.save(m);

        String tienda = t.getNombre();
        String resumen = m.resumen();
        String nombreAdjunto = adjunto == null ? null : adjunto.nombre();
        String urlAdjunto = adjunto == null ? null : adjunto.url();
        if (deSoporte) {
            String correoDueno = correoDelDueno(t);
            despuesDeGuardar(() -> {
                enVivo.mensaje(idTienda, tienda, true, resumen);
                aviso.alDueno(correoDueno, tienda, texto, nombreAdjunto, urlAdjunto);
            });
            log.info("Soporte respondió: idTienda={}, adjunto={}", idTienda, adjunto != null);
        } else {
            Login dueno = loginRepository.findById(yo.idLogin()).orElse(null);
            String nombre = dueno == null ? "El dueño" : (dueno.getNombres() + " " + dueno.getPrimerApellido()).trim();
            String correo = dueno == null ? t.getCorreoContacto() : dueno.getCorreo();
            String subdominio = t.getSubdominio();
            despuesDeGuardar(() -> {
                enVivo.mensaje(idTienda, tienda, false, resumen);
                aviso.alEquipo(tienda, subdominio, nombre, correo, texto, nombreAdjunto, urlAdjunto);
            });
            log.info("Mensaje del dueño a soporte: idTienda={}, adjunto={}", idTienda, adjunto != null);
        }
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
