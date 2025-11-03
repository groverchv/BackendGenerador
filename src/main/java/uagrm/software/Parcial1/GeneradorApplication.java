package uagrm.software.Parcial1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GeneradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeneradorApplication.class, args);
        System.out.println("✅ Aplicación iniciada en: http://localhost:8080");
    }
}
