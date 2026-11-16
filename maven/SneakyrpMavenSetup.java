import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import javax.swing.*;

/** Sets up this machine's Gradle to read maven.sneakyrp.com. Asks for the token itself; prints no secret. */
public class SneakyrpMavenSetup {
    static final String INIT_SCRIPT = """
        // Team Sneakymouse: credentials for maven.sneakyrp.com.
        // Every Maven repository on that host that a build reads from gets the read credentials from
        // gradle.properties, so build files need no change. Publishing repositories keep their own.

        def sneakyrpHost = 'maven.sneakyrp.com'
        def sneakyrpWarned = new java.util.concurrent.atomic.AtomicBoolean()

        def sneakyrpAuth = { repositories, providers, logger ->
            repositories.withType(MavenArtifactRepository).configureEach { repo ->
                if (repo.url?.host != sneakyrpHost) return
                def user = providers.gradleProperty('sneakyrpReadUsername').orElse(providers.gradleProperty('sneakyrpUsername')).orNull
                def pass = providers.gradleProperty('sneakyrpReadPassword').orElse(providers.gradleProperty('sneakyrpPassword')).orNull
                if (!user || !pass) {
                    if (sneakyrpWarned.compareAndSet(false, true)) {
                        logger.warn("maven.sneakyrp.com needs credentials: set sneakyrpReadUsername and sneakyrpReadPassword in ~/.gradle/gradle.properties")
                    }
                    return
                }
                try {
                    if (repo.credentials.username != null) return // the build file brought its own
                    repo.credentials { username = user; password = pass }
                    repo.authentication { create('basic', org.gradle.authentication.http.BasicAuthentication) }
                } catch (Exception ignored) {
                    // The build file declared credentials in its own way: leave them alone.
                }
            }
        }

        beforeSettings { settings ->
            sneakyrpAuth(settings.pluginManagement.repositories, settings.providers, logger)
            sneakyrpAuth(settings.dependencyResolutionManagement.repositories, settings.providers, logger)
        }
        allprojects { project ->
            sneakyrpAuth(project.buildscript.repositories, project.providers, project.logger)
            sneakyrpAuth(project.repositories, project.providers, project.logger)
        }
        """;

    public static void main(String[] args) throws Exception {
        String override = System.getenv("GRADLE_USER_HOME");
        Path home = override != null && !override.isBlank() ? Path.of(override) : Path.of(System.getProperty("user.home"), ".gradle");
        Path script = home.resolve("init.d").resolve("sneakyrp-maven.init.gradle");
        Path properties = home.resolve("gradle.properties");

        String[] token = ask();
        if (token == null) fail("Cancelled: nothing was changed.");
        String name = token[0].trim(), secret = token[1].trim();
        if (name.isEmpty() || secret.isEmpty()) fail("Token name or secret was empty: nothing was changed.");

        // One attempt only: a rejected token is not retried.
        String basic = Base64.getEncoder().encodeToString((name + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://maven.sneakyrp.com/api/auth/me"))
            .header("Authorization", "Basic " + basic).header("User-Agent", "sneakyrp-maven-setup").build();
        int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status != 200) fail("The server answered " + status + " for token '" + name + "': nothing was changed. Do not retry; ask whoever gave you the token.");

        Files.createDirectories(script.getParent());
        Files.writeString(script, INIT_SCRIPT);

        List<String> lines = Files.exists(properties) ? new ArrayList<>(Files.readAllLines(properties)) : new ArrayList<>();
        boolean created = !Files.exists(properties);
        lines.removeIf(line -> line.matches("\\s*sneakyrpRead(Username|Password)\\s*[=:].*"));
        lines.add("sneakyrpReadUsername=" + name);
        lines.add("sneakyrpReadPassword=" + secret.replace("\\", "\\\\"));
        Files.write(properties, lines);
        if (created) {
            try {
                Files.setPosixFilePermissions(properties, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException windows) {
                // No POSIX permissions here; the file sits in the user's profile.
            }
        }

        System.out.println("Token '" + name + "' accepted (HTTP 200).");
        System.out.println("Wrote " + script);
        System.out.println((created ? "Created " : "Updated ") + properties + " (sneakyrpReadUsername, sneakyrpReadPassword)");
        System.exit(0);
    }

    /** A window with a hidden secret field, or the terminal when there is no display. Null when cancelled. */
    static String[] ask() {
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            JTextField name = new JTextField(24);
            JPasswordField secret = new JPasswordField(24);
            Object[] form = {"Read token for maven.sneakyrp.com", " ", "Token name (for example dev-yourname):", name, "Token secret:", secret};
            JFrame owner = new JFrame("Sneakymouse Maven setup");
            owner.setAlwaysOnTop(true);
            owner.setLocationRelativeTo(null);
            int choice = JOptionPane.showConfirmDialog(owner, form, "Sneakymouse Maven setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            owner.dispose();
            return choice == JOptionPane.OK_OPTION ? new String[] {name.getText(), new String(secret.getPassword())} : null;
        }
        java.io.Console console = System.console();
        if (console == null) fail("No display and no terminal: run `java SneakyrpMavenSetup.java` yourself in a terminal.");
        String name = console.readLine("Token name: ");
        char[] secret = console.readPassword("Token secret (hidden): ");
        return name == null || secret == null ? null : new String[] {name, new String(secret)};
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
