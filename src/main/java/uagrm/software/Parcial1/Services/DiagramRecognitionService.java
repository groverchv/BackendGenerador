package uagrm.software.Parcial1.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagramRecognitionService {

    private final ChatClient chatClient;

    public String recognizeDiagram(MultipartFile imageFile) throws IOException {
        // Convertir la imagen a base64
        byte[] imageBytes = imageFile.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        
        // Obtener el tipo MIME del archivo
        String mimeType = imageFile.getContentType();
        if (mimeType == null) {
            mimeType = "image/png"; // Valor por defecto
        }

        // Crear el mensaje del sistema con instrucciones mejoradas
        SystemMessage systemMessage = new SystemMessage(
            "Eres un experto en análisis de diagramas UML con experiencia en diagramas de clases. Tu tarea es analizar diagramas de clases UML y extraer la información estructurada en formato JSON.\n\n" +
            "REGLAS DE ANÁLISIS:\n" +
            "1. Identifica CLASES, INTERFACES, ENUMERACIONES y CLASES ABSTRACTAS\n" +
            "2. Extrae atributos con su tipo y modificador de acceso (+ public, - private, # protected)\n" +
            "3. Extrae métodos con su tipo de retorno, parámetros y modificador de acceso\n" +
            "4. Identifica relaciones: HERENCIA (triángulo vacío), IMPLEMENTACIÓN (triángulo hueco), ASOCIACIÓN (línea simple), COMPOSICIÓN (rombo lleno), AGREGACIÓN (rombo hueco), DEPENDENCIA (flecha punteada)\n" +
            "5. Incluye multiplicidades cuando estén presentes (1, 0..1, *, 1..*)\n" +
            "6. Genera IDs únicos usando el formato: 'class_[nombre]', 'attr_[nombre]', 'method_[nombre]', 'edge_[tipo]_[contador]'\n\n" +
            "FORMATO JSON EXACTO:\n" +
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\n" +
            "      \"id\": \"class_usuario\",\n" +
            "      \"type\": \"custom\",\n" +
            "      \"position\": { \"x\": 100, \"y\": 100 },\n" +
            "      \"data\": {\n" +
            "        \"label\": \"Usuario\",\n" +
            "        \"type\": \"class\",\n" +
            "        \"attributes\": [\n" +
            "          \"- id: Long\",\n" +
            "          \"- nombre: String\",\n" +
            "          \"+ getNombre(): String\"\n" +
            "        ],\n" +
            "        \"methods\": [\n" +
            "          \"+ constructor(): void\",\n" +
            "          \"+ getNombre(): String\",\n" +
            "          \"+ setNombre(nombre: String): void\"\n" +
            "        ]\n" +
            "      }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"edges\": [\n" +
            "    {\n" +
            "      \"id\": \"edge_composicion_1\",\n" +
            "      \"source\": \"class_usuario\",\n" +
            "      \"target\": \"class_direccion\",\n" +
            "      \"type\": \"custom\",\n" +
            "      \"data\": { \n" +
            "        \"label\": \"tiene\",\n" +
            "        \"relationshipType\": \"composition\",\n" +
            "        \"multiplicity\": \"1\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "TIPOS DE ELEMENTOS:\n" +
            "- \"class\": Clases regulares\n" +
            "- \"interface\": Interfaces\n" +
            "- \"enum\": Enumeraciones\n" +
            "- \"abstract\": Clases abstractas\n\n" +
            "TIPOS DE RELACIONES:\n" +
            "- \"inheritance\": Herencia (triángulo vacío)\n" +
            "- \"implementation\": Implementación (triángulo hueco)\n" +
            "- \"association\": Asociación (línea simple)\n" +
            "- \"composition\": Composición (romdo lleno)\n" +
            "- \"aggregation\": Agregación (rombo hueco)\n" +
            "- \"dependency\": Dependencia (flecha punteada)\n\n" +
            "NOTAS IMPORTANTES:\n" +
            "1. Si un elemento es ambiguo, clasifícalo como 'class' y añade una nota en el atributo 'notes'\n" +
            "2. Si una relación no está clara, usa 'dependency' como tipo por defecto\n" +
            "3. Normaliza los nombres: elimina espacios extra, usa convención camelCase para IDs\n" +
            "4. Si la imagen contiene texto no relacionado con el diagrama, ignóralo\n" +
            "5. Asegúrate de que todos los nodos y aristas tengan IDs únicos"
        );

        // Crear el mensaje de usuario con la imagen y las instrucciones mejoradas
        UserMessage userMessage = new UserMessage(
            "Analiza la siguiente imagen de un diagrama de clases UML. Extrae todas las clases, interfaces, enumeraciones, atributos, métodos y las relaciones entre ellas. " +
            "Sigue las reglas y formato JSON especificados en el sistema. La imagen está codificada en base64: " + base64Image.substring(0, 100) + "..."
        );

        // Usar ChatClient para enviar el mensaje y obtener la respuesta
        return chatClient.prompt()
            .messages(systemMessage, userMessage)
            .call()
            .content();
    }
}