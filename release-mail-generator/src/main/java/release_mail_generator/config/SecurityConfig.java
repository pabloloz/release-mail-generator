package release_mail_generator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de autenticación y autorización.
 *
 * <p>La cuenta se define por completo mediante variables de entorno:
 * {@code APP_SECURITY_USERNAME} y {@code APP_SECURITY_PASSWORD_HASH} (hash BCrypt
 * de la contraseña real — nunca la contraseña en texto plano). No existen
 * credenciales por defecto: si las variables no están presentes, el arranque de
 * la aplicación falla explícitamente en lugar de exponer un usuario predecible.</p>
 *
 * <p>Usa {@code PasswordHashGenerator} (en {@code util}) para generar el hash a
 * partir de una contraseña elegida, sin escribirla nunca en el repositorio.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.security.username}")
    private String appUsername;

    @Value("${app.security.password-hash}")
    private String appPasswordHash;

    @Value("${app.security.role:USER}")
    private String appRole;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        if (appUsername == null || appUsername.isBlank()) {
            throw new IllegalStateException(
                    "APP_SECURITY_USERNAME no está configurado. Define la variable de entorno antes de iniciar la aplicación.");
        }
        if (appPasswordHash == null || !appPasswordHash.startsWith("$2")) {
            throw new IllegalStateException(
                    "APP_SECURITY_PASSWORD_HASH no está configurado o no es un hash BCrypt válido (debe iniciar con $2a$/$2b$/$2y$).");
        }
        UserDetails user = User.withUsername(appUsername.trim())
                .password(appPasswordHash)
                .roles(appRole.trim())
                .build();
        log.info("Usuario de aplicación configurado desde variables de entorno (username enmascarado): {}***",
                appUsername.trim().length() > 2 ? appUsername.trim().substring(0, 2) : "");
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/login", "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/error", "/h2-console/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // El H2 console (solo perfil dev) se sirve en un <frame> propio y no
            // puede validar el token CSRF de Spring Security con sus formularios.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent event) {
        log.info("Login exitoso: usuario={}", event.getAuthentication().getName());
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("Login fallido: usuario={}, motivo={}",
                event.getAuthentication().getName(), event.getException().getClass().getSimpleName());
    }
}
