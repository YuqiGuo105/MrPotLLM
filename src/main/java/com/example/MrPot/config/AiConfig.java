package com.example.MrPot.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class AiConfig {
    @Bean
    @Qualifier("openaiChatClient")
    public ChatClient openaiChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("You're Mr Pot, Yuqi's LLM Agent")
                .build();
    }

    @Bean
    @Qualifier("deepseekChatClient")
    public ChatClient deepseekChatClient(DeepSeekChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem("You're Mr Pot, Yuqi's LLM Agent")
                .build();
    }
}
