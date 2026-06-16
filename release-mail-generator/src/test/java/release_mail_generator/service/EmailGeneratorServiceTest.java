package release_mail_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import release_mail_generator.model.RdlItem;
import release_mail_generator.model.RdlReleaseRequest;
import release_mail_generator.model.ReleaseRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailGeneratorServiceTest {

    @InjectMocks
    private EmailGeneratorService service;

    @BeforeEach
    void init() throws Exception {
        // Invocar @PostConstruct manualmente ya que no hay contexto Spring
        var method = EmailGeneratorService.class.getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(service);
    }

    // ── generateEmail ──────────────────────────────────────────────────────

    @Test
    void generateEmail_retornaHtmlConSaludo() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setPathModules("\\\\servidor\\ruta\\Modulos");

        String html = service.generateEmail(r);

        assertThat(html).contains("Buen día Operaciones");
        assertThat(html).contains("<b>Módulos</b>");
    }

    @Test
    void generateEmail_escapaHtmlEnRuta() {
        ReleaseRequest r = new ReleaseRequest();
        r.setHasModules(true);
        r.setPathModules("\\\\servidor\\ruta\\<script>alert(1)</script>");

        String html = service.generateEmail(r);

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void generateEmail_conVersion_incluyeVersionEnHtml() {
        ReleaseRequest r = new ReleaseRequest();
        r.setVersion("2.5.1");
        r.setPublishDate("20/06/2026");

        String html = service.generateEmail(r);

        assertThat(html).contains("2.5.1");
        assertThat(html).contains("20/06/2026");
    }

    @Test
    void generateEmail_conNota_incluyeNotaEnHtml() {
        ReleaseRequest r = new ReleaseRequest();
        r.setNotes("Requiere reinicio de servicio.");

        String html = service.generateEmail(r);

        assertThat(html).contains("NOTA IMPORTANTE");
        assertThat(html).contains("Requiere reinicio de servicio.");
    }

    @Test
    void generateEmail_sinArtefactos_noIncluye_ul() {
        ReleaseRequest r = new ReleaseRequest();

        String html = service.generateEmail(r);

        assertThat(html).contains("Buen día Operaciones");
        assertThat(html).doesNotContain("<li");
    }

    // ── generateRdlEmail ───────────────────────────────────────────────────

    @Test
    void generateRdlEmail_conUnRdl_retornaHtmlValido() {
        RdlItem item = new RdlItem();
        item.setRdlReportName("CAR_CuentasMaestras");
        item.setRdlUrlMegang("http://megang-612/Reports/report/Modulos/Cartera/CAR_CuentasMaestras");

        RdlReleaseRequest r = new RdlReleaseRequest();
        r.setRdls(List.of(item));

        String html = service.generateRdlEmail(r);

        assertThat(html).contains("CAR_CuentasMaestras");
        assertThat(html).contains("MEGANG-612");
        assertThat(html).contains("NTRS02");
    }

    @Test
    void generateRdlEmail_listaVacia_noFalla() {
        RdlReleaseRequest r = new RdlReleaseRequest();

        String html = service.generateRdlEmail(r);

        assertThat(html).isNotNull();
        assertThat(html).contains("Buen día Operaciones");
    }
}
