package release_mail_generator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de extremo a extremo de autenticación, autorización y CSRF.
 *
 * Usa credenciales de prueba fijas (usuario {@code testadmin}, contraseña
 * {@code test-password-123}) definidas aquí con {@code @TestPropertySource}, que tiene
 * mayor precedencia que las variables de entorno del sistema operativo. Esto es
 * intencional: si solo se dependiera de {@code src/test/resources/application.properties},
 * una variable de entorno real como {@code APP_SECURITY_USERNAME} definida en la máquina
 * (p. ej. para desarrollo local) la sobrescribiría silenciosamente y el test fallaría de
 * forma no determinista según el entorno de quien lo ejecute.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.username=testadmin",
        "app.security.password-hash=$2a$10$fIpsCenZP9IH4bNuq1qGhOFsnrKYGcAQCcHKPgeTLBTm7619BbnOy",
        "app.security.role=USER"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Acceso anónimo ───────────────────────────────────────────────────

    @Test
    void anonimo_homeRedirigeALogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonimo_puedeVerPaginaDeLogin() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void anonimo_recursoEstaticoAccesible() throws Exception {
        mockMvc.perform(get("/js/dark-mode.js")).andExpect(status().isOk());
    }

    @Test
    void anonimo_listadoRfcBloqueado() throws Exception {
        mockMvc.perform(get("/rfc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonimo_historialBloqueado() throws Exception {
        mockMvc.perform(get("/history/api/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void anonimo_busquedaGlobalBloqueada() throws Exception {
        mockMvc.perform(get("/api/search?q=rfc"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Login ────────────────────────────────────────────────────────────

    @Test
    void login_credencialesValidas_autenticaYRedirige() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "testadmin")
                        .param("password", "test-password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void login_credencialesInvalidas_regresaALoginConError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "testadmin")
                        .param("password", "contraseña-incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_usuarioInexistente_regresaALoginConError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "no-existe")
                        .param("password", "lo-que-sea"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    // ── CSRF ───────────────────────────────────────────────────────────────

    @Test
    void autenticado_sinCsrf_operacionMutanteEsRechazada() throws Exception {
        mockMvc.perform(post("/generate").with(user("testadmin").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void autenticado_sinCsrf_guardarRfcEsRechazado() throws Exception {
        mockMvc.perform(post("/rfc/save").with(user("testadmin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rfcNumber\":\"RFC-SIN-CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Operaciones autenticadas (con CSRF) ────────────────────────────────

    @Test
    void autenticado_generarCorreoLiberacion_exitoso() throws Exception {
        mockMvc.perform(post("/generate").with(user("testadmin").roles("USER")).with(csrf())
                        .param("version", "1.2.3"))
                .andExpect(status().isOk());
    }

    @Test
    void autenticado_guardarRfc_exitoso() throws Exception {
        mockMvc.perform(post("/rfc/save").with(user("testadmin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rfcNumber\":\"RFC-SEC-TEST-001\",\"changeName\":\"Prueba de seguridad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void autenticado_exportarMarkdownLiberacion_exitoso() throws Exception {
        mockMvc.perform(post("/release/export/markdown").with(user("testadmin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void autenticado_generarVoboUat_exitoso() throws Exception {
        mockMvc.perform(post("/generate-uat").with(user("testadmin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rfcNumber\":\"RFC-UAT-1\",\"rfcName\":\"Prueba\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void autenticado_listadoRfcAccesible() throws Exception {
        mockMvc.perform(get("/rfc").with(user("testadmin").roles("USER")))
                .andExpect(status().isOk());
    }
}
