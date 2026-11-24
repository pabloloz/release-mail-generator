package release_mail_generator.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estado inicial del formulario VoBo UAT: las secciones colapsables arrancan cerradas y sin ejemplo precreado. */
class UatTemplateMarkupTest {

    private static String index() throws Exception {
        return new ClassPathResource("templates/index.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void lasSeccionesColapsablesArrancanCerradas() throws Exception {
        String html = index();
        for (String id : new String[]{"uatMore", "uatExamples", "uatAdvanced", "uatNotes"}) {
            Matcher m = Pattern.compile("<details\\b[^>]*\\bid=\"" + id + "\"[^>]*>").matcher(html);
            assertTrue(m.find(), "falta el details " + id);
            assertFalse(Pattern.compile("\\sopen(\\s|=|>)").matcher(m.group()).find(), id + " no debe llevar el atributo open: " + m.group());
        }
    }

    @Test
    void laListaDeEjemplosNoTraeNingunEjemploPrecreado() throws Exception {
        Matcher m = Pattern.compile("<div id=\"uatExList\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(index());
        assertTrue(m.find());
        assertTrue(m.group(1).isBlank(), "uatExList debe estar vacio en el HTML inicial");
    }
}
