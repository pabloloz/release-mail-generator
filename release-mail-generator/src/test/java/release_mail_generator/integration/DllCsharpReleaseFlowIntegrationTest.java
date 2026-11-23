package release_mail_generator.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de la DLL C# en el correo de liberación: formulario (nombres de campo
 * exactos del HTML), JSON usado por Telegram y exportes. Valida que lo capturado en el
 * correo llegue al mensaje de Telegram.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.username=testadmin",
        "app.security.password-hash=$2a$10$fIpsCenZP9IH4bNuq1qGhOFsnrKYGcAQCcHKPgeTLBTm7619BbnOy",
        "app.security.role=USER"
})
class DllCsharpReleaseFlowIntegrationTest {

    private static final String PATH = "\\\\ntdesarrollo.evolution.corp\\sprints$\\RFC'S Semana 47 2026\\DLL C#";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void formularioDeCorreo_enlazaCamposDllCsharpYGeneraElCorreo() throws Exception {
        mockMvc.perform(post("/generate").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("version", "26.10.8")
                        .param("hasDllCsharp", "true")
                        .param("pathDllCsharp", PATH)
                        .param("versionDllCsharp", "26.6.11.2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("generatedEmail", allOf(
                        containsString("<b>DLL C#</b>"),
                        containsString("DLL C%23\""),
                        containsString("Versión DLL C#"),
                        containsString("26.6.11.2"),
                        not(containsString("Branch de Compilación")))));
    }

    @Test
    void formularioDeCorreo_sinMarcarDllCsharp_noLaIncluye() throws Exception {
        mockMvc.perform(post("/generate").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("version", "26.10.8")
                        .param("hasDll", "true")
                        .param("pathDll", "\\\\srv\\SFyCWSDLL")
                        .param("pathDllCsharp", PATH))
                .andExpect(status().isOk())
                .andExpect(model().attribute("generatedEmail", not(containsString("<b>DLL C#</b>"))));
    }

    @Test
    void telegram_conCamposImportadosDesdeElCorreo_generaMensajeConDllCsharp() throws Exception {
        // Mismo JSON que arma collectReleaseMessageData() tras "Importar desde Correo de Liberación"
        String json = """
                {"telegramModules":["Cartera","DLL C#"],
                 "telegramScripts":["5721update.sql"],
                 "publishDate":"2026-11-20",
                 "telegramVersionModule":"26.10.8",
                 "telegramVersionDllCsharp":"26.6.11.2",
                 "telegramRollbackVersion":"26.10.1",
                 "telegramBranchDllName":"origin/release",
                 "telegramBranchDllRepoUrl":"https://github.com/org/winterX.git"}
                """;

        mockMvc.perform(post("/generate-release-message").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("```"),
                        containsString("\uD83D\uDCE6 Cartera\n\uD83D\uDCE6 DLL C#"),
                        containsString("\uD83C\uDFF7\uFE0F Versión DLL C#: 26.6.11.2"),
                        containsString("\uD83C\uDF3F Branch compilación DLL C#: https://github.com/org/winterX.git  branch origin/release"))));
    }

    @Test
    void telegram_conJsonDelCorreoSinCamposTelegram_usaRespaldoDelCorreo() throws Exception {
        // Incluye los campos de branch que ya no existen: borradores/clientes viejos no deben romper
        String json = """
                {"hasDllCsharp":true,"versionDllCsharp":"26.6.11.2","version":"26.10.8",
                 "branchDllCsharp":"origin/release","branchDllCsharpRepoUrl":"https://github.com/org/winterX.git"}
                """;

        mockMvc.perform(post("/generate-release-message").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("\uD83D\uDCE6 DLL C#"),
                        containsString("Versión DLL C#: 26.6.11.2"),
                        not(containsString("Branch compilación DLL C#")))));
    }

    @Test
    void exporteMarkdown_incluyeDllCsharp() throws Exception {
        String json = """
                {"version":"26.10.8","hasDllCsharp":true,"pathDllCsharp":"C:\\\\srv\\\\DLL C#",
                 "versionDllCsharp":"26.6.11.2"}
                """;

        byte[] body = mockMvc.perform(post("/release/export/markdown").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String md = new String(body, StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(md)
                .contains("| Versión DLL C# | 26.6.11.2 |")
                .contains("- **DLL C#:** `C:\\srv\\DLL C#`");
    }

    @Test
    void exportePdf_conDllCsharp_generaPdfValido() throws Exception {
        String json = """
                {"version":"26.10.8","hasDllCsharp":true,"pathDllCsharp":"C:\\\\srv\\\\DLL C#",
                 "versionDllCsharp":"26.6.11.2"}
                """;

        byte[] body = mockMvc.perform(post("/release/export/pdf").with(user("testadmin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(new String(body, 0, 4, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");
    }
}
