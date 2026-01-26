package at.tori.dmr.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class SpringAiConfig {

  @Bean
  @ConditionalOnMissingBean
  fun openAiApi(
    @Value("\${spring.ai.openai.base-url}") baseUrl: String,
    @Value("\${spring.ai.openai.api-key}") apiKey: String,
    @Value("\${spring.ai.openai.timeout.connect:10}") connectTimeout: Long,
    @Value("\${spring.ai.openai.timeout.read:300}") readTimeout: Long
  ): OpenAiApi {
    val requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(Duration.ofSeconds(connectTimeout))
      setReadTimeout(Duration.ofSeconds(readTimeout))
    }

    val restClientBuilder = RestClient.builder()
      .baseUrl(baseUrl)
      .defaultHeader("Authorization", "Bearer $apiKey")
      .requestFactory(requestFactory)

    val httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (connectTimeout * 1000).toInt())
      .doOnConnected { conn ->
        conn.addHandlerLast(ReadTimeoutHandler(readTimeout, TimeUnit.SECONDS))
        conn.addHandlerLast(WriteTimeoutHandler(readTimeout, TimeUnit.SECONDS))
      }
      .responseTimeout(Duration.ofSeconds(readTimeout))

    val webClientBuilder = WebClient.builder()
      .baseUrl(baseUrl)
      .defaultHeader("Authorization", "Bearer $apiKey")
      .clientConnector(ReactorClientHttpConnector(httpClient))

    return OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder)
  }

  @Bean
  @Primary
  @ConditionalOnProperty(name = ["code-review.ai.provider"], havingValue = "anthropic", matchIfMissing = true)
  fun primaryAnthropicChatModel(@Qualifier("anthropicChatModel") chatModel: ChatModel): ChatModel {
    return chatModel
  }

  @Bean
  @Primary
  @ConditionalOnProperty(name = ["code-review.ai.provider"], havingValue = "openai")
  fun primaryOpenAiChatModel(@Qualifier("openAiChatModel") chatModel: ChatModel): ChatModel {
    return chatModel
  }

  @Bean
  fun chatClient(chatModel: ChatModel): ChatClient {
    return ChatClient.builder(chatModel).build()
  }

  @Bean("lineReviewChatOptions")
  @ConditionalOnProperty(name = ["code-review.ai.provider"], havingValue = "openai", matchIfMissing = false)
  fun lineReviewOpenAiChatOptions(
    @Value("\${code-review.ai.temperature:0.3}") temperature: Double,
    @Value("\${code-review.ai.max-tokens:8192}") maxTokens: Int,
    @Value("\${code-review.ai.top-p:0.95}") topP: Double
  ): OpenAiChatOptions {
    return OpenAiChatOptions.builder()
      .temperature(temperature)
      .maxTokens(maxTokens)
      .topP(topP)
      .build()
  }

  @Bean("lineReviewChatOptions")
  @ConditionalOnProperty(name = ["code-review.ai.provider"], havingValue = "anthropic", matchIfMissing = true)
  fun lineReviewAnthropicChatOptions(
    @Value("\${code-review.ai.temperature:0.3}") temperature: Double,
    @Value("\${code-review.ai.max-tokens:8192}") maxTokens: Int
  ): AnthropicChatOptions {
    return AnthropicChatOptions.builder()
      .temperature(temperature)
      .maxTokens(maxTokens)
      .build()
  }
}
