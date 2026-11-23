package release_mail_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import release_mail_generator.model.UatBlock;
import release_mail_generator.model.UatRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UatEmailServiceTest {

    private UatEmailService service;

    @BeforeEach
    void setUp() {
        service = new UatEmailService();
        ReflectionTestUtils.setField(service, "resourceLoader", new DefaultResourceLoader());
    }

    @Test
    void payloadMinimoGeneraCorreo() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("RFC 24269");
        r.setRfcName("Prueba");
        String html = service.generateEmail(r);
        assertTrue(html.contains("RFC 24269"));
        assertTrue(html.contains("Prueba"));
    }

    @Test
    void contenidoLegacyConImgOnerrorSeNeutraliza() {
        UatRequest r = new UatRequest();
        r.setRfcNumber("RFC 1");
        r.setRfcName("x");
        r.setRequerimientos("<b>Req</b><img src=x onerror=alert(1)>");
        UatBlock b = new UatBlock();
        b.setTexto("<p>Ejemplo</p><a href=\"javascript:alert(1)\">enlace</a>");
        r.setBlocks(List.of(b));
        String html = service.generateEmail(r).toLowerCase();
        assertFalse(html.contains("onerror"), html);
        assertFalse(html.contains("javascript:"), html);
        assertTrue(html.contains("<b>req</b>"), html);
        assertTrue(html.contains("ejemplo"), html);
    }

    @Test
    void imagenConSrcMalicioso_noRompeElAtributo() {
        UatBlock b = new UatBlock();
        b.setImagenBase64("data:image/png;base64,AAAA\" onerror=\"alert(1)");
        UatRequest r = new UatRequest();
        r.setBlocks(List.of(b));
        String html = service.generateEmail(r).toLowerCase();
        assertFalse(html.contains("onerror"), html);
    }

    @Test
    void camposSimplesSeEscapan() {
        UatRequest r = new UatRequest();
        r.setRfcName("<script>alert(1)</script>");
        r.setSaludo("<img src=x onerror=alert(1)>");
        r.setAdjunto("\"><svg onload=alert(1)>");
        String html = service.generateEmail(r);
        assertFalse(html.contains("<script"), html);
        assertFalse(html.contains("<img src=x"), html);
        assertFalse(html.contains("<svg"), html);
    }
}
