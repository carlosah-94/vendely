package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.Login;
import curso.fullstack.shopify.repository.LoginRepository;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El "recepcionista" de Spring Security: recibe un nombre de usuario,
 * busca la cuenta en la tabla login y devuelve su ficha (CuentaUserDetails).
 *
 * En Vendely el mismo correo puede tener cuenta en el panel y en cada tienda,
 * así que el nombre de usuario es una clave:
 *   "maria@mail.pe"      → cuenta del panel (dueño o superadmin)
 *   "15/maria@mail.pe"   → cuenta de cliente de la tienda 15
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final String SEPARADOR = "/";

    private final LoginRepository loginRepository;

    public UsuarioDetailsService(LoginRepository loginRepository) {
        this.loginRepository = loginRepository;
    }

    /** Arma la clave que se le pasa al AuthenticationManager. */
    public static String clave(String correo, Long idTienda) {
        return idTienda == null ? correo : idTienda + SEPARADOR + correo;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String clave) throws UsernameNotFoundException {
        Optional<Login> cuenta;
        int i = clave.indexOf(SEPARADOR);
        String prefijo = i > 0 ? clave.substring(0, i) : "";

        if (!prefijo.isEmpty() && prefijo.chars().allMatch(Character::isDigit)) {
            Long idTienda = Long.valueOf(prefijo);
            cuenta = loginRepository.findByCorreoAndIdTienda(clave.substring(i + 1), idTienda);
        } else {
            cuenta = loginRepository.findByCorreoAndIdTiendaIsNull(clave);
        }

        return cuenta.map(CuentaUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no encontrada"));
    }
}