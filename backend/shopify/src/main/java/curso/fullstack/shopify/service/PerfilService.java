package curso.fullstack.shopify.service;

import curso.fullstack.shopify.dto.PerfilRequest;
import curso.fullstack.shopify.dto.PerfilResponse;
import curso.fullstack.shopify.dto.UsuarioResponse;
import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.LoginRepository;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.security.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mi perfil: ver y editar los datos de la propia cuenta (nombre, apellidos, teléfono y, si es
 * dueño, la razón social y el teléfono del negocio). Sirve para cualquier cuenta con sesión.
 * El correo, el documento y la contraseña no se cambian aquí.
 */
@Slf4j
@Service
public class PerfilService {

    private final LoginRepository loginRepository;
    private final PropietarioRepository propietarioRepository;
    private final JdbcTemplate jdbc;

    public PerfilService(LoginRepository loginRepository, PropietarioRepository propietarioRepository,
                         JdbcTemplate jdbc) {
        this.loginRepository = loginRepository;
        this.propietarioRepository = propietarioRepository;
        this.jdbc = jdbc;
    }

    /** Lo que devuelve el PUT: el perfil y la cuenta, para que el panel actualice el nombre de arriba. */
    public record Guardado(PerfilResponse perfil, UsuarioResponse usuario) {}

    @Transactional(readOnly = true)
    public PerfilResponse ver(UsuarioAutenticado yo) {
        Login l = buscar(yo);
        return respuesta(l, propietarioRepository.findByIdLogin(l.getIdLogin()));
    }

    @Transactional
    public Guardado guardar(UsuarioAutenticado yo, PerfilRequest r) {
        Login l = buscar(yo);
        l.setNombres(r.nombres().trim());
        l.setPrimerApellido(r.primerApellido().trim());
        l.setSegundoApellido(vacioANull(r.segundoApellido()));
        l.setTelefono(vacioANull(r.telefono()));
        loginRepository.saveAndFlush(l);

        Optional<Propietario> propietario = propietarioRepository.findByIdLogin(l.getIdLogin());
        propietario.ifPresent(p -> {
            p.setTelefonoComercial(vacioANull(r.telefonoComercial()));
            datosTributarios(p, l, vacioANull(r.ruc()), vacioANull(r.razonSocial()));
            propietarioRepository.saveAndFlush(p);
        });
        log.info("Perfil actualizado: idLogin={}", l.getIdLogin());
        return new Guardado(respuesta(l, propietario), UsuarioResponse.desde(l));
    }

    /**
     * RUC y razón social del dueño (V24). Las mismas reglas que la base, con mensajes claros:
     *   - Perú: el RUC con su dígito verificador correcto. Si se registró con su RUC, es ese mismo.
     *     Un RUC 10 de una persona con DNI lleva su DNI adentro. Con RUC, la razón social es
     *     obligatoria; con RUC 10, si no la escribe, es su nombre completo.
     *   - Otros países (Chile): su RUT ya es su identificación tributaria; aquí no hay RUC.
     */
    private void datosTributarios(Propietario p, Login l, String ruc, String razonSocial) {
        String pais = paisDelDocumento(p);
        if (!"PE".equals(pais)) {
            p.setRazonSocial(razonSocial);
            return;
        }
        if (p.getTipoDocumento() == 2) {
            // Se registró con su RUC: es ese mismo y no cambia. Una cuenta antigua con un RUC que no
            // pasa el verificador no puede vender; guarda su razón social y lo resuelve con soporte.
            if (!DocumentoTributario.esRucValido(p.getNumeroDocumento())) {
                p.setRazonSocial(razonSocial);
                return;
            }
            ruc = p.getNumeroDocumento();
        }
        if (ruc == null) {
            p.setRuc(null);
            p.setRazonSocial(razonSocial);
            return;
        }
        if (!DocumentoTributario.esRucValido(ruc)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El RUC no es válido: revisa los números (el último dígito no coincide)");
        }
        if (p.getTipoDocumento() == 1 && ruc.startsWith("10") && !ruc.substring(2, 10).equals(p.getNumeroDocumento())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Tu RUC 10 debe contener tu DNI: 10 + " + p.getNumeroDocumento() + " + un dígito");
        }
        if (razonSocial == null && ruc.startsWith("10")) razonSocial = nombreCompleto(l);
        if (razonSocial == null || razonSocial.length() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Escribe la razón social: el nombre legal de tu negocio registrado en la SUNAT");
        }
        if (propietarioRepository.existsByRucAndIdPropietarioNot(ruc, p.getIdPropietario())) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese RUC ya está registrado en otra cuenta de Vendely");
        }
        p.setRuc(ruc);
        p.setRazonSocial(razonSocial);
    }

    private String paisDelDocumento(Propietario p) {
        return jdbc.queryForObject("SELECT id_pais FROM tipos_documento WHERE id_tipo_documento = ?",
                String.class, p.getTipoDocumento());
    }

    private static String nombreCompleto(Login l) {
        return String.join(" ", java.util.stream.Stream.of(l.getNombres(), l.getPrimerApellido(), l.getSegundoApellido())
                .filter(x -> x != null && !x.isBlank()).map(String::trim).toList());
    }

    // ------------------------------------------------------------------ apoyo

    private Login buscar(UsuarioAutenticado yo) {
        return loginRepository.findById(yo.idLogin())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La cuenta no existe"));
    }

    private PerfilResponse respuesta(Login l, Optional<Propietario> propietario) {
        PerfilResponse.Negocio negocio = propietario.map(p -> {
            List<String[]> tipo = jdbc.query(
                    "SELECT codigo, nombre FROM tipos_documento WHERE id_tipo_documento = ?",
                    (rs, i) -> new String[] {rs.getString("codigo"), rs.getString("nombre")},
                    p.getTipoDocumento());
            String pais = paisDelDocumento(p);
            return new PerfilResponse.Negocio(tipo.isEmpty() ? null : tipo.get(0)[0],
                    tipo.isEmpty() ? null : tipo.get(0)[1], p.getNumeroDocumento(), p.getRazonSocial(),
                    p.getTelefonoComercial(), pais, p.getRuc(), !"PE".equals(pais) || p.getRuc() != null);
        }).orElse(null);
        return new PerfilResponse(l.getIdLogin(), l.getUsuario(), l.getCorreo(), l.getNombres(),
                l.getPrimerApellido(), l.getSegundoApellido(), l.getTelefono(), l.getRol().name(),
                l.getFechaCreacion(), l.getContrasenaCambiadaEn(), Boolean.TRUE.equals(l.getTotpActivo()), negocio);
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
