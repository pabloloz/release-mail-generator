package release_mail_generator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página de login. La autenticación en sí (POST /login) la procesa el filtro
 * de Spring Security configurado en {@code SecurityConfig}, no este controlador.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
