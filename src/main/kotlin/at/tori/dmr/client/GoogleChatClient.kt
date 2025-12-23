package at.tori.dmr.client

import at.tori.dmr.config.GoogleChatProperties
import at.tori.dmr.domain.GoogleChatMessage
import kotlinx.coroutines.reactor.awaitSingleOrNull
import mu.KotlinLogging
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger {}

@Component
class GoogleChatClient(
  private val googleChatWebClient: WebClient,
  private val googleChatProperties: GoogleChatProperties
) {
  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 1000, multiplier = 2.0)
  )
  suspend fun sendMessage(message: GoogleChatMessage): Boolean {
    if (!googleChatProperties.enabled) {
      logger.debug { "Google Chat 알림이 비활성화되어 있습니다" }
      return false
    }

    return try {
      logger.info { "Google Chat 웹훅으로 메시지 전송 중: ${googleChatProperties.webhookUrl.take(50)}..." }

      val response = googleChatWebClient.post()
        .uri("")
        .bodyValue(message)
        .retrieve()
        .bodyToMono<Map<String, Any>>()
        .awaitSingleOrNull()

      logger.info { "Google Chat 메시지 전송 성공: $response" }
      true
    } catch (e: Exception) {
      logger.error(e) { "Google Chat 메시지 전송 실패. 메시지: ${e.message}" }
      false
    }
  }

  suspend fun sendText(text: String): Boolean {
    return sendMessage(GoogleChatMessage(text = text))
  }

  fun isConfigured(): Boolean {
    return googleChatProperties.enabled && googleChatProperties.webhookUrl.isNotBlank()
  }
}
