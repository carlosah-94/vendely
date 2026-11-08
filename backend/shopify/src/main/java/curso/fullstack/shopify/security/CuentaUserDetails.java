package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.Login;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * "Ficha" de una cuenta en el formato que entiende Spring Security.
 * Envuelve una fila de la tabla login: su contraseña (hash BCrypt),
 * su rol y si está activa.
 */
public class CuentaUserDetails implements UserDetails {

    private final Login login;

    public CuentaUserDetails(Login login) {
        this.login = login;
    }

    /** La cuenta completa, para generar el token después del inicio de sesión. */
    public Login getLogin() {
        return login;
    }

    /** El rol como "permiso": ROLE_ADMIN_TIENDA, ROLE_CLIENTE o ROLE_SUPERADMIN. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + login.getRol().name()));
    }

    /** El hash guardado en la base; Spring lo compara con BCrypt. */
    @Override
    public String getPassword() {
        return login.getContrasenaHash();
    }

    @Override
    public String getUsername() {
        return login.getCorreo();
    }

    /** estado = 1 en la tabla login. Una cuenta desactivada no puede entrar. */
    @Override
    public boolean isEnabled() {
        return login.estaActivo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}