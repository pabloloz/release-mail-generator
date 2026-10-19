package release_mail_generator.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.util.Scanner;

/**
 * Utilidad de línea de comandos para generar el hash BCrypt que debe
 * almacenarse en la variable de entorno {@code APP_SECURITY_PASSWORD_HASH}.
 *
 * <p>La contraseña se pide de forma interactiva (no como argumento) para que
 * no quede expuesta en el historial de la terminal ni en la lista de procesos.</p>
 *
 * <p>Uso (tras compilar):</p>
 * <pre>
 *   java -cp target/classes release_mail_generator.util.PasswordHashGenerator
 * </pre>
 */
public final class PasswordHashGenerator {

    private PasswordHashGenerator() { }

    public static void main(String[] args) {
        String password = readPassword();
        if (password == null || password.isBlank()) {
            System.err.println("No se proporcionó ninguna contraseña.");
            System.exit(1);
        }
        String hash = new BCryptPasswordEncoder().encode(password);
        System.out.println();
        System.out.println("APP_SECURITY_PASSWORD_HASH=" + hash);
    }

    private static String readPassword() {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword("Contraseña a hashear: ");
            return chars != null ? new String(chars) : null;
        }
        // Fallback cuando no hay consola interactiva real (ej. algunos IDEs): se lee en texto
        // plano de stdin, igualmente sin pasar por argumentos de línea de comandos.
        System.out.print("Contraseña a hashear (se mostrará en texto plano, no hay consola segura disponible): ");
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }
    }
}
