package uagrm.software.Parcial1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class Parcial1Application {

    public static void main(String[] args) {
        SpringApplication.run(Parcial1Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String[] profiles = env.getActiveProfiles();

        System.out.println("✅ ¡Aplicación Parcial1 iniciada!");
        System.out.println("🌐 http://localhost:" + port);
        System.out.println("🧩 Perfil(es) activo(s): " + (profiles.length == 0 ? "default" : String.join(", ", profiles)));
    }
}
