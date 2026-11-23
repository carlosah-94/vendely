package curso.fullstack.shopify.exception;

import org.springframework.http.HttpStatus;

/** Error de negocio con su código HTTP (400, 401, 404, 409...). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final java.util.Map<String, String> campos;

    public ApiException(HttpStatus status, String mensaje) {
        this(status, mensaje, null);
    }

    /**
     * Con "campos" el frontend recibe un dato que puede leer sin interpretar el texto del
     * mensaje. Lo usa el login con doble autenticacion: campos.codigo = "requerido".
     */
    public ApiException(HttpStatus status, String mensaje, java.util.Map<String, String> campos) {
        super(mensaje);
        this.status = status;
        this.campos = campos;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public java.util.Map<String, String> getCampos() {
        return campos;
    }
}
