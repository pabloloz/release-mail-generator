package release_mail_generator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import release_mail_generator.service.DocumentHistoryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.username=testadmin",
        "app.security.password-hash=$2a$10$fIpsCenZP9IH4bNuq1qGhOFsnrKYGcAQCcHKPgeTLBTm7619BbnOy",
        "app.security.role=USER"
})
class UatPreviewIntegrationTest {

    private static final String BODY = "{\"rfcNumber\":\"25473\",\"rfcName\":\"Anexo 5\",\"recipient\":\"equipo de UAT\","
            + "\"relatedRfcs\":[\"25554\"],\"requirementsStart\":3,"
            + "\"requirements\":[{\"title\":\"Uno\",\"description\":\"d\",\"bullets\":[\"a\",\"b\"]}],"
            + "\"validationExamples\":[{\"pairs\":[{\"key\":\"Suscriptor\",\"value\":\"513\"}]}],"
            + "\"validationSteps\":[{\"text\":\"Paso\",\"image\":\"@step1\"}]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DocumentHistoryService history;

    @Test
    void previewDevuelveHtmlTextoAsuntoYNoGuardaHistorial() throws Exception {
        int antes = history.listAll().size();
        mockMvc.perform(post("/uat/preview").with(user("testadmin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("3. Uno")))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Suscriptor: 513")))
                .andExpect(jsonPath("$.subject").value("VoBo UAT \u2013 RFC 25473 \u2013 Anexo 5"))
                .andExpect(jsonPath("$.warnings").isArray());
        assertEquals(antes, history.listAll().size(), "la vista previa no debe escribir en el historial");
    }

    @Test
    void generarConPayloadEstructuradoSiGuardaHistorial() throws Exception {
        int antes = history.listAll().size();
        mockMvc.perform(post("/generate-uat").with(user("testadmin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Buenas tardes, equipo de UAT.")));
        assertEquals(antes + 1, history.listAll().size());
    }

    @Test
    void exportesAceptanElPayloadEstructurado() throws Exception {
        for (String tipo : new String[]{"pdf", "markdown", "html"}) {
            mockMvc.perform(post("/uat/export/" + tipo).with(user("testadmin").roles("USER")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anonimoNoPuedeUsarLaVistaPrevia() throws Exception {
        mockMvc.perform(post("/uat/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is3xxRedirection());
    }
}
