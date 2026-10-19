package release_mail_generator.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static class PlainViewController {
        public String view() { return "x"; }
    }

    static class JsonMethodController {
        @ResponseBody
        public String json() { return "x"; }
    }

    @RestController
    static class RestApiController {
        public String json() { return "x"; }
    }

    private HandlerMethod handlerMethodOf(Object bean, String methodName) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private HttpServletRequest requestWithId(String id) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("requestId")).thenReturn(id);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/test");
        return req;
    }

    @Test
    void errorInesperado_controladorDeVista_devuelveVistaErrorSinFiltrarDetalle() throws Exception {
        HttpServletRequest req = requestWithId("abcd1234");
        HandlerMethod hm = handlerMethodOf(new PlainViewController(), "view");

        Object result = handler.handleUnexpected(new RuntimeException("detalle interno sensible"), req, hm);

        assertThat(result).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) result;
        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getModel().get("requestId")).isEqualTo("abcd1234");
        assertThat(mav.getModel().get("error").toString()).doesNotContain("detalle interno sensible");
    }

    @Test
    void errorInesperado_metodoResponseBody_devuelveJsonSinFiltrarDetalle() throws Exception {
        HttpServletRequest req = requestWithId("abcd1234");
        HandlerMethod hm = handlerMethodOf(new JsonMethodController(), "json");

        Object result = handler.handleUnexpected(new RuntimeException("detalle interno sensible"), req, hm);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> re = (ResponseEntity<?>) result;
        assertThat(re.getStatusCode().value()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) re.getBody();
        assertThat(body.get("requestId")).isEqualTo("abcd1234");
        assertThat(body.get("error").toString()).doesNotContain("detalle interno sensible");
    }

    @Test
    void errorInesperado_restController_devuelveJson() throws Exception {
        HttpServletRequest req = requestWithId("zzzz9999");
        HandlerMethod hm = handlerMethodOf(new RestApiController(), "json");

        Object result = handler.handleUnexpected(new RuntimeException("x"), req, hm);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void validacionEsperada_conservaMensajeUtil() throws Exception {
        HttpServletRequest req = requestWithId("id1");
        HandlerMethod hm = handlerMethodOf(new JsonMethodController(), "json");

        Object result = handler.handleValidation(new IllegalArgumentException("Ya existe un RFC con ese número"), req, hm);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> re = (ResponseEntity<?>) result;
        assertThat(re.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) re.getBody();
        assertThat(body.get("error")).isEqualTo("Ya existe un RFC con ese número");
        assertThat(body.get("requestId")).isEqualTo("id1");
    }
}
