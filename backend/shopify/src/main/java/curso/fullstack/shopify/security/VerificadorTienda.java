package curso.fullstack.shopify.security;

import curso.fullstack.shopify.entity.Propietario;
import curso.fullstack.shopify.entity.Tienda;
import curso.fullstack.shopify.exception.ApiException;
import curso.fullstack.shopify.repository.PropietarioRepository;
import curso.fullstack.shopify.repository.TiendaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Comprueba que una tienda sea del dueño que hace la petición.
 *
 * El token del dueño no dice qué tienda está administrando (un dueño puede tener
 * varias), así que TODA ruta del panel que reciba un idTienda debe pasar por aquí:
 *
 *     Tienda tienda = verificadorTienda.delDueno(yo, idTienda);
 *
 * Es como el portero de un edificio: tener llave del edificio (token) no te deja
 * entrar a cualquier departamento, solo al tuyo.
 */
@Component
public class VerificadorTienda {

    private final PropietarioRepository propietarioRepository;
    private final TiendaRepository tiendaRepository;

    public VerificadorTienda(PropietarioRepository propietarioRepository, TiendaRepository tiendaRepository) {
        this.propietarioRepository = propietarioRepository;
        this.tiendaRepository = tiendaRepository;
    }

    /** El propietario de la cuenta que inició sesión (403 si la cuenta no es de un dueño). */
    public Propietario propietario(UsuarioAutenticado yo) {
        return propietarioRepository.findByIdLogin(yo.idLogin())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta no está registrada como dueño de tienda"));
    }

    /** La tienda, solo si es de este dueño: 404 si no existe, 403 si es de otro. */
    public Tienda delDueno(UsuarioAutenticado yo, Long idTienda) {
        Tienda tienda = tiendaRepository.findById(idTienda)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "La tienda no existe"));
        if (!tienda.getIdPropietario().equals(propietario(yo).getIdPropietario())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Esta tienda no es tuya");
        }
        return tienda;
    }
}
