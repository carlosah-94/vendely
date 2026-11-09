package curso.fullstack.shopify.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte cualquier error en una respuesta JSON clara (ApiError) y lo deja anotado en el log.
 * Nunca envía al frontend detalles internos (SQL, rutas de clases, etc.).
 *
 * Reglas del log:
 * - 4xx (error del usuario): una línea INFO con método, ruta, código y mensaje.
 * - 5xx (error del sistema): ERROR con el detalle completo para poder corregirlo.
 * - Nunca se registran los valores enviados (podrían ser contraseñas), solo los nombres de los campos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> negocio(ApiException ex, HttpServletRequest req) {
        return respuesta(ex.getStatus(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        log.info("{} {} -> 400 datos inválidos en: {}", req.getMethod(), req.getRequestURI(), campos.keySet());
        return cuerpo(HttpStatus.BAD_REQUEST, "Hay datos inválidos", req, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", req, null);
    }

    // Falta un parámetro obligatorio de la URL, por ejemplo ?pagina=
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + ex.getParameterName() + "'", req, null);
    }

    // Se envió algo que no es JSON (por ejemplo, texto plano o un formulario)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> formatoNoSoportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como JSON", req, null);
    }

    // Un UNIQUE, CHECK, FK o trigger de la base rechazó la operación
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("{} {} -> 409 regla de la base de datos: {}", req.getMethod(), req.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return cuerpo(HttpStatus.CONFLICT, "La operación no cumple las reglas de la base de datos", req, null);
    }

    // @PreAuthorize u otra regla de permisos rechazó la petición
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> sinPermiso(AccessDeniedException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> noAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión", req, null);
    }

    // Un dato de la ruta con el tipo equivocado, por ejemplo /api/panel/tiendas/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "El valor de '" + ex.getName() + "' no es válido", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noExiste(NoResourceFoundException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, "La ruta no existe", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido en esta ruta", req, null);
    }

    // Cualquier otro error: es un fallo del sistema, se registra completo (con la traza) para corregirlo
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest req) {
        log.error("{} {} -> 500 error no controlado", req.getMethod(), req.getRequestURI(), ex);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", req, null);
    }

    /** Deja una línea en el log y arma la respuesta. */
    private ResponseEntity<ApiError> respuesta(HttpStatus status, String mensaje,
                                               HttpServletRequest req, Map<String, String> campos) {
        log.info("{} {} -> {} {}", req.getMethod(), req.getRequestURI(), status.value(), mensaje);
        return cuerpo(status, mensaje, req, campos);
    }

    private ResponseEntity<ApiError> cuerpo(HttpStatus status, String mensaje,
                                            HttpServletRequest req, Map<String, String> campos) {
        ApiError error = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                mensaje, req.getRequestURI(), campos);
        return ResponseEntity.status(status).body(error);
    }
}
