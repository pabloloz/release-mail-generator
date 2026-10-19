package release_mail_generator.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import release_mail_generator.config.RequestIdFilter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo centralizado de errores.
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} representa validaciones esperadas (ej. número de
 *       RFC duplicado): se conserva el mensaje original, útil para el usuario.</li>
 *   <li>Cualquier otra excepción se considera inesperada: se registra completa en el
 *       servidor (con el identificador de solicitud) y al usuario solo se le muestra un
 *       mensaje genérico junto con ese identificador, nunca el detalle interno
 *       (stack trace, mensajes de JDBC/IO, etc.).</li>
 * </ul>
 *
 * <p>La respuesta se adapta según el tipo de endpoint que falló: JSON para controladores
 * {@code @RestController} o métodos {@code @ResponseBody}, vista HTML de error para el
 * resto (formularios server-rendered).</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_MESSAGE =
            "Ocurrió un error inesperado al procesar tu solicitud. Intenta de nuevo; si el problema persiste, reporta este identificador a soporte.";

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleValidation(IllegalArgumentException ex, HttpServletRequest request, HandlerMethod handlerMethod) {
        String requestId = requestId(request);
        log.warn("Validación rechazada [requestId={}] en {} {}: {}",
                requestId, request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(handlerMethod, HttpStatus.BAD_REQUEST, ex.getMessage(), requestId);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request, HandlerMethod handlerMethod) {
        String requestId = requestId(request);
        // Se registra el detalle completo (incl. stack trace) solo en el log del servidor.
        // Nunca se exponen stack traces, mensajes de excepción internos, contraseñas,
        // documentos completos o imágenes base64 en la respuesta al cliente.
        log.error("Error inesperado [requestId={}] en {} {}", requestId, request.getMethod(), request.getRequestURI(), ex);
        return respond(handlerMethod, HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_MESSAGE, requestId);
    }

    private Object respond(HandlerMethod handlerMethod, HttpStatus status, String message, String requestId) {
        if (isJson(handlerMethod)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", message);
            body.put("requestId", requestId);
            return ResponseEntity.status(status).body(body);
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", message);
        mav.addObject("requestId", requestId);
        return mav;
    }

    private boolean isJson(HandlerMethod handlerMethod) {
        if (handlerMethod == null) return true;
        return handlerMethod.hasMethodAnnotation(ResponseBody.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RestController.class);
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return id != null ? id.toString() : "desconocido";
    }
}
