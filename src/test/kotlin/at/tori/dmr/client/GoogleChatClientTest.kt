package at.tori.dmr.client

import at.tori.dmr.config.GoogleChatProperties
import at.tori.dmr.domain.GoogleChatMessage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@DisplayName("google chat notify 테스트")
class GoogleChatClientTest {

  private lateinit var webClient: WebClient
  private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec
  private lateinit var requestBodySpec: WebClient.RequestBodySpec
  private lateinit var responseSpec: WebClient.ResponseSpec
  private lateinit var properties: GoogleChatProperties
  private lateinit var client: GoogleChatClient

  @BeforeEach
  fun setUp() {
    webClient = mockk()
    requestBodyUriSpec = mockk()
    requestBodySpec = mockk()
    responseSpec = mockk()
    properties = mockk()
    client = GoogleChatClient(webClient, properties)
  }

  @AfterEach
  fun tearDown() {
    clearAllMocks()
  }

  @Nested
  @DisplayName("sendMessage 메서드 테스트")
  inner class SendMessageTest {

    @Test
    @DisplayName("Google Chat이 활성화된 경우 정상적으로 메시지 전송")
    fun `should successfully send message when Google Chat is enabled`() = runTest {
      val message = GoogleChatMessage(text = "Test message")
      val responseMap = mapOf("success" to true)

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(message) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val result = client.sendMessage(message)

      assertTrue(result)

      verify { properties.enabled }
      verify { webClient.post() }
      verify { requestBodySpec.bodyValue(message) }
    }

    @Test
    @DisplayName("Google Chat이 비활성화된 경우 메시지 전송하지 않고 false 반환")
    fun `should not send message and return false when Google Chat is disabled`() = runTest {
      val message = GoogleChatMessage(text = "Test message")

      every { properties.enabled } returns false

      val result = client.sendMessage(message)

      assertFalse(result)

      verify { properties.enabled }
      verify(exactly = 0) { webClient.post() }
    }

    @Test
    @DisplayName("메시지 전송 실패 시 false 반환")
    fun `should return false when message sending fails`() = runTest {
      val message = GoogleChatMessage(text = "Test message")

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(message) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.error(RuntimeException("Network error"))

      val result = client.sendMessage(message)

      assertFalse(result)

      verify { webClient.post() }
    }

    @Test
    @DisplayName("응답이 null인 경우에도 true 반환")
    fun `should return true even when response is null`() = runTest {
      val message = GoogleChatMessage(text = "Test message")

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(message) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.empty()

      val result = client.sendMessage(message)

      assertTrue(result)

      verify { webClient.post() }
    }
  }

  @Nested
  @DisplayName("sendText 메서드 테스트")
  inner class SendTextTest {

    @Test
    @DisplayName("텍스트 메시지를 정상적으로 전송")
    fun `should successfully send text message`() = runTest {
      val text = "Simple text message"
      val responseMap = mapOf("success" to true)

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<GoogleChatMessage>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val result = client.sendText(text)

      assertTrue(result)

      verify { requestBodySpec.bodyValue(match<GoogleChatMessage> { it.text == text }) }
    }

    @Test
    @DisplayName("Google Chat이 비활성화된 경우 텍스트 메시지 전송하지 않고 false 반환")
    fun `should not send text message when Google Chat is disabled`() = runTest {
      val text = "Test text"

      every { properties.enabled } returns false

      val result = client.sendText(text)

      assertFalse(result)

      verify(exactly = 0) { webClient.post() }
    }

    @Test
    @DisplayName("빈 문자열도 정상적으로 전송")
    fun `should successfully send empty string`() = runTest {
      val text = ""
      val responseMap = mapOf("success" to true)

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<GoogleChatMessage>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val result = client.sendText(text)

      assertTrue(result)

      verify { requestBodySpec.bodyValue(match<GoogleChatMessage> { it.text == text }) }
    }
  }

  @Nested
  @DisplayName("isConfigured 메서드 테스트")
  inner class IsConfiguredTest {

    @Test
    @DisplayName("Google Chat이 활성화되고 웹훅 URL이 설정된 경우 true 반환")
    fun `should return true when Google Chat is enabled and webhook URL is configured`() {
      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"

      val result = client.isConfigured()

      assertTrue(result)

      verify { properties.enabled }
      verify { properties.webhookUrl }
    }

    @Test
    @DisplayName("Google Chat이 비활성화된 경우 false 반환")
    fun `should return false when Google Chat is disabled`() {
      every { properties.enabled } returns false
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test"

      val result = client.isConfigured()

      assertFalse(result)

      verify { properties.enabled }
    }

    @Test
    @DisplayName("웹훅 URL이 비어있는 경우 false 반환")
    fun `should return false when webhook URL is empty`() {
      every { properties.enabled } returns true
      every { properties.webhookUrl } returns ""

      val result = client.isConfigured()

      assertFalse(result)

      verify { properties.enabled }
      verify { properties.webhookUrl }
    }

    @Test
    @DisplayName("웹훅 URL이 공백인 경우 false 반환")
    fun `should return false when webhook URL is blank`() {
      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "   "

      val result = client.isConfigured()

      assertFalse(result)

      verify { properties.enabled }
      verify { properties.webhookUrl }
    }

    @Test
    @DisplayName("Google Chat이 비활성화되고 웹훅 URL도 비어있는 경우 false 반환")
    fun `should return false when both Google Chat is disabled and webhook URL is empty`() {
      every { properties.enabled } returns false
      every { properties.webhookUrl } returns ""

      val result = client.isConfigured()

      assertFalse(result)

      verify { properties.enabled }
    }
  }

  @Nested
  @DisplayName("통합 시나리오 테스트")
  inner class IntegrationScenarioTest {

    @Test
    @DisplayName("설정이 올바른 경우 메시지 전송 성공")
    fun `should successfully send message when properly configured`() = runTest {
      val text = "Integration test message"
      val responseMap = mapOf("name" to "spaces/xxx/messages/yyy")

      every { properties.enabled } returns true
      every { properties.webhookUrl } returns "https://chat.googleapis.com/webhook/test123"
      every { webClient.post() } returns requestBodyUriSpec
      every { requestBodyUriSpec.uri("") } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<GoogleChatMessage>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val isConfigured = client.isConfigured()
      val sendResult = client.sendText(text)

      assertTrue(isConfigured)
      assertTrue(sendResult)
    }

    @Test
    @DisplayName("설정이 올바르지 않은 경우 메시지 전송 실패")
    fun `should not send message when not properly configured`() = runTest {
      every { properties.enabled } returns false
      every { properties.webhookUrl } returns ""

      val isConfigured = client.isConfigured()
      val sendResult = client.sendText("test")

      assertFalse(isConfigured)
      assertFalse(sendResult)

      verify(exactly = 0) { webClient.post() }
    }
  }
}
