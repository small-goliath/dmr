package at.tori.dmr.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.retry.annotation.EnableRetry
import org.springframework.validation.annotation.Validated
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@ConfigurationProperties(prefix = "gitlab")
@Validated
data class GitLabProperties(
  @field:NotBlank(message = "GitLab URL은 비어있을 수 없습니다")
  @field:Pattern(regexp = "^https?://.*", message = "GitLab URL은 유효한 HTTP(S) URL이어야 합니다")
  val url: String,

  @field:NotBlank(message = "GitLab 토큰은 비어있을 수 없습니다")
  val token: String,

  @field:Valid
  val webhook: WebhookProperties = WebhookProperties(),

  @field:Valid
  val api: ApiProperties = ApiProperties()
)

data class WebhookProperties(
  @field:NotBlank(message = "GitLab 웹훅 시크릿 토큰은 비어있을 수 없습니다")
  val secretToken: String = ""
)

data class ApiProperties(
  @field:NotNull
  val timeout: Duration = Duration.ofSeconds(30),

  @field:Min(0, message = "최대 재시도 횟수는 0 이상이어야 합니다")
  @field:Max(10, message = "최대 재시도 횟수는 10 이하여야 합니다")
  val maxRetries: Int = 3,

  @field:NotNull
  val retryDelay: Duration = Duration.ofSeconds(1)
)

@ConfigurationProperties(prefix = "google.chat")
@Validated
data class GoogleChatProperties(
  val webhookUrl: String = "",
  val enabled: Boolean = true,

  @field:NotNull
  val timeout: Duration = Duration.ofSeconds(10)
)

@Configuration
@EnableRetry
@EnableConfigurationProperties(
  GitLabProperties::class,
  CodeReviewProperties::class
)
class WebClientConfig {

  @Bean
  fun gitLabWebClient(gitLabProperties: GitLabProperties): WebClient {
    val httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, gitLabProperties.api.timeout.toMillis().toInt())
      .doOnConnected { conn ->
        conn.addHandlerLast(ReadTimeoutHandler(gitLabProperties.api.timeout.seconds, TimeUnit.SECONDS))
        conn.addHandlerLast(WriteTimeoutHandler(gitLabProperties.api.timeout.seconds, TimeUnit.SECONDS))
      }
      .responseTimeout(gitLabProperties.api.timeout)

    // diff 버퍼 사이즈 대비
    val exchangeStrategies = ExchangeStrategies.builder()
      .codecs { configurer ->
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16MB
      }
      .build()

    return WebClient.builder()
      .baseUrl(gitLabProperties.url)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .exchangeStrategies(exchangeStrategies)
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .defaultHeader("PRIVATE-TOKEN", gitLabProperties.token)
      .build()
  }

  @Bean
  fun googleChatWebClient(googleChatProperties: GoogleChatProperties): WebClient {
    val httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, googleChatProperties.timeout.toMillis().toInt())
      .doOnConnected { conn ->
        conn.addHandlerLast(ReadTimeoutHandler(googleChatProperties.timeout.seconds, TimeUnit.SECONDS))
        conn.addHandlerLast(WriteTimeoutHandler(googleChatProperties.timeout.seconds, TimeUnit.SECONDS))
      }
      .responseTimeout(googleChatProperties.timeout)

    return WebClient.builder()
      .baseUrl(googleChatProperties.webhookUrl)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .build()
  }
}
