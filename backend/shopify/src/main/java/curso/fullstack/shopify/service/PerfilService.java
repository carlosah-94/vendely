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
            p.setRazonSocial(vacioANull(r.razonSocial()));
            p.setTelefonoComercial(vacioANull(r.telefonoComercial()));
            propietarioRepository.saveAndFlush(p);
        });
        log.info("Perfil actualizado: idLogin={}", l.getIdLogin());
        return new Guardado(respuesta(l, propietario), UsuarioResponse.desde(l));
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
            return new PerfilResponse.Negocio(tipo.isEmpty() ? null : tipo.get(0)[0],
                    tipo.isEmpty() ? null : tipo.get(0)[1], p.getNumeroDocumento(), p.getRazonSocial(),
                    p.getTelefonoComercial());
        }).orElse(null);
        return new PerfilResponse(l.getIdLogin(), l.getUsuario(), l.getCorreo(), l.getNombres(),
                l.getPrimerApellido(), l.getSegundoApellido(), l.getTelefono(), l.getRol().name(),
                l.getFechaCreacion(), l.getContrasenaCambiadaEn(), Boolean.TRUE.equals(l.getTotpActivo()), negocio);
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
