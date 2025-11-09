package uagrm.software.Parcial1.Config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.generative.GoogleAiChatModel;
import org.springframework.ai.google.generative.GoogleAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleGenAIConfig {

    @Bean
    public GoogleAiChatModel googleAiChatModel() {
        return new GoogleAiChatModel(
            "TU_API_KEY_AQUI",
            GoogleAiChatOptions.builder()
                .withModel("gemini-2.0-flash-exp")
                .withTemperature(0.7)
                .build()
        );
    }

    @Bean
    public ChatClient chatClient(GoogleAiChatModel googleAiChatModel) {
        return ChatClient.builder(googleAiChatModel).build();
    }
}