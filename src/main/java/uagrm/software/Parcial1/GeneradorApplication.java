package uagrm.software.Parcial1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GeneradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeneradorApplication.class, args);
        System.out.println("✅ Aplicación iniciada en: http://localhost:8080");
    }
}
