package com.example.MrPot;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none",
                "spring.ai.openai.api-key=test",
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.ai.vectorstore.pgvector.enabled=false",
                "spring.sql.init.mode=never"
        }
)
@ActiveProfiles("test")
@Import(MrPotApplicationTests.TestAiConfiguration.class)
class MrPotApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class TestAiConfiguration {
        @Bean
        EmbeddingModel embeddingModel() {
            return Mockito.mock(EmbeddingModel.class);
        }

        @Bean
        OpenAiChatModel openAiChatModel() {
            return Mockito.mock(OpenAiChatModel.class);
        }
    }
}
