package release_mail_generator.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTest {

    @Test
    void imgConOnerrorSinComillasSeNeutraliza() {
        String out = HtmlSanitizer.sanitize("<b>x</b><img src=x onerror=alert(1)>");
        assertFalse(out.toLowerCase().contains("onerror"), out);
        assertFalse(out.contains("<img"), out);
        assertTrue(out.contains("<b>x</b>"), out);
    }

    @Test
    void imgConOnerrorEntreComillasYMayusculasSeNeutraliza() {
        String out = HtmlSanitizer.sanitize("<IMG SRC=\"data:image/png;base64,AAAA\" OnError=\"alert(1)\" onload='x()'>");
        assertFalse(out.toLowerCase().contains("onerror"), out);
        assertFalse(out.toLowerCase().contains("onload"), out);
        assertTrue(out.contains("<img src=\"data:image/png;base64,AAAA\">"), out);
    }

    @Test
    void hrefJavascriptSeElimina() {
        String out = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">clic</a>");
        assertFalse(out.toLowerCase().contains("javascript"), out);
        assertTrue(out.contains("clic"), out);
    }

    @Test
    void hrefJavascriptOfuscadoSeElimina() {
        assertFalse(HtmlSanitizer.sanitize("<a href=\"  JaVa\tScRiPt:alert(1)\">a</a>").toLowerCase().contains("script"));
        assertFalse(HtmlSanitizer.sanitize("<a href=\"jav&#x61;script&colon;alert(1)\">a</a>").toLowerCase().contains("script"));
        assertFalse(HtmlSanitizer.sanitize("<a href=\"data:text/html;base64,PHNjcmlwdD4=\">a</a>").contains("data:"));
    }

    @Test
    void hrefHttpsSeConserva() {
        String out = HtmlSanitizer.sanitize("<a href=\"https://ejemplo.com/a?b=1&c=2\">sitio</a>");
        assertTrue(out.contains("href=\"https://ejemplo.com/a?b=1&amp;c=2\""), out);
        assertTrue(out.contains("rel=\"noopener noreferrer\""), out);
    }

    @Test
    void scriptYIframeSeEliminanConContenido() {
        String out = HtmlSanitizer.sanitize("a<script>alert(1)</script>b<iframe src=x>hola</iframe>c<style>p{}</style>d");
        assertEquals("abcd", out);
    }

    @Test
    void svgYEtiquetasDesconocidasNoSobreviven() {
        String out = HtmlSanitizer.sanitize("<svg onload=alert(1)><circle/></svg><marquee>hola</marquee>");
        assertFalse(out.contains("svg"), out);
        assertFalse(out.contains("onload"), out);
        assertFalse(out.contains("marquee"), out);
        assertTrue(out.contains("hola"), out);
    }

    @Test
    void estilosPeligrososSeFiltranYLosSegurosSeConservan() {
        String out = HtmlSanitizer.sanitize(
                "<span style=\"color:#ff0000; background:url(javascript:alert(1)); width:expression(alert(1)); font-weight:bold\">t</span>");
        assertTrue(out.contains("color:#ff0000;"), out);
        assertTrue(out.contains("font-weight:bold;"), out);
        assertFalse(out.contains("url("), out);
        assertFalse(out.contains("expression"), out);
    }

    @Test
    void imagenSvgOSrcNoDataSeDescarta() {
        assertFalse(HtmlSanitizer.sanitize("<img src=\"data:image/svg+xml;base64,AAAA\">").contains("<img"));
        assertFalse(HtmlSanitizer.sanitize("<img src=\"https://x/y.png\">").contains("<img"));
    }

    @Test
    void textoSueltoSeEscapaYElHtmlSeBalancea() {
        assertEquals("a &lt; b &amp; c", HtmlSanitizer.sanitize("a < b & c"));
        assertEquals("<b>x</b>", HtmlSanitizer.sanitize("<b>x"));
        assertEquals("<p>uno</p>", HtmlSanitizer.sanitize("<p>uno</p></div>"));
        assertEquals("&lt;img src=x onerror=alert(1)", HtmlSanitizer.sanitize("<img src=x onerror=alert(1)"));
    }

    @Test
    void comentariosSeEliminan() {
        assertEquals("ab", HtmlSanitizer.sanitize("a<!-- <script>x</script> -->b"));
    }

    @Test
    void dataUriSegura() {
        assertEquals("data:image/png;base64,AAAA", HtmlSanitizer.safeImageDataUri("data:image/png;base64,AAAA"));
        assertEquals("", HtmlSanitizer.safeImageDataUri("data:image/png;base64,AAAA\" onerror=\"alert(1)"));
        assertEquals("", HtmlSanitizer.safeImageDataUri("data:image/svg+xml;base64,AAAA"));
        assertEquals("", HtmlSanitizer.safeImageDataUri("javascript:alert(1)"));
        assertEquals("", HtmlSanitizer.safeImageDataUri(null));
    }
}
