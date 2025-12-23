package at.tori.dmr.controller

import at.tori.dmr.config.GitLabProperties
import at.tori.dmr.config.WebhookProperties
import at.tori.dmr.domain.*
import at.tori.dmr.service.CodeReviewService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(GitLabWebhookController::class)
@DisplayName("gitlab webhooks 테스트")
class GitLabWebhookControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @MockkBean
  private lateinit var codeReviewService: CodeReviewService

  @MockkBean
  private lateinit var gitLabProperties: GitLabProperties

  private val validSecretToken = "valid-secret-token-123"
  private val invalidSecretToken = "invalid-token"

  @BeforeEach
  fun setUp() {
    clearAllMocks()
  }

  private fun createValidMergeRequestEvent(action: String = "open"): MergeRequestEvent {
    return MergeRequestEvent(
      objectKind = "merge_request",
      user = User(
        id = 1L,
        name = "Test User",
        username = "testuser",
        email = "test@example.com"
      ),
      project = Project(
        id = 123L,
        name = "Test Project",
        webUrl = "https://gitlab.com/test/project",
        pathWithNamespace = "test/project"
      ),
      objectAttributes = MergeRequestAttributes(
        id = 456L,
        iid = 1L,
        title = "Test MR",
        sourceBranch = "feature-branch",
        targetBranch = "main",
        sourceProjectId = 123L,
        targetProjectId = 123L,
        state = "opened",
        action = action
      )
    )
  }

  @Nested
  @DisplayName("정상적인 웹훅 요청 테스트")
  inner class ValidWebhookTest {

    @Test
    @DisplayName("유효한 토큰과 이벤트로 웹훅 요청이 성공적으로 처리됨")
    fun `should successfully process webhook with valid token and event`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()

      every { webhookProperties.secretToken } returns validSecretToken
      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } just Runs

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("accepted"))
        .andExpect(jsonPath("$.message").value("Webhook received and processing started"))

      verify { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("시크릿 토큰이 빈 문자열로 설정된 경우 토큰 검증을 건너뜀")
    fun `should skip token validation when secret token is blank`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns ""

      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } just Runs

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.status").value("accepted"))

      verify { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("다양한 액션 타입의 MR 이벤트를 처리")
    fun `should process different MR action types`() {
      val actions = listOf("open", "update", "reopen", "approved", "merge", "close")
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } just Runs

      actions.forEach { action ->
        val event = createValidMergeRequestEvent(action)

        mockMvc.perform(
          post("/api/webhook/gitlab")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Gitlab-Event", "Merge Request Hook")
            .header("X-Gitlab-Token", validSecretToken)
            .content(objectMapper.writeValueAsString(event))
        )
          .andExpect(status().isOk)
      }

      verify(exactly = actions.size) { codeReviewService.processWebhookEvent(any()) }
    }
  }

  @Nested
  @DisplayName("웹훅 토큰 검증 테스트")
  inner class TokenValidationTest {

    @Test
    @DisplayName("잘못된 토큰으로 요청 시 InvalidWebhookException 발생")
    fun `should throw InvalidWebhookException when token is invalid`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .header("X-Gitlab-Token", invalidSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.errorType").value("InvalidWebhook"))

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("토큰 헤더가 누락된 경우 InvalidWebhookException 발생")
    fun `should throw InvalidWebhookException when token header is missing`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.errorType").value("InvalidWebhook"))

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("시크릿 토큰이 공백 문자열인 경우 토큰 없이도 요청 허용")
    fun `should allow request without token when secret token is whitespace`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns "   "

      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } just Runs

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isOk)

      verify { codeReviewService.processWebhookEvent(any()) }
    }
  }

  @Nested
  @DisplayName("이벤트 타입 검증 테스트")
  inner class EventTypeValidationTest {

    @Test
    @DisplayName("MR이 아닌 이벤트 타입은 거부됨 - Push Hook")
    fun `should reject non-MR event type - Push Hook`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Push Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("MR이 아닌 이벤트 타입은 거부됨 - Issue Hook")
    fun `should reject non-MR event type - Issue Hook`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Issue Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("X-Gitlab-Event 헤더가 누락된 경우 거부됨")
    fun `should reject when X-Gitlab-Event header is missing`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("objectKind가 merge_request가 아닌 경우 거부됨")
    fun `should reject when objectKind is not merge_request`() {
      val event = createValidMergeRequestEvent().copy(objectKind = "issue")
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }
  }

  @Nested
  @DisplayName("서비스 예외 처리 테스트")
  inner class ServiceExceptionTest {

    @Test
    @DisplayName("CodeReviewService에서 예외 발생 시 전파됨")
    fun `should propagate exception from CodeReviewService`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } throws RuntimeException("Service error")

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isInternalServerError)
        .andExpect(jsonPath("$.errorType").value("UnexpectedError"))

      verify { codeReviewService.processWebhookEvent(any()) }
    }
  }


  @Nested
  @DisplayName("통합 시나리오 테스트")
  inner class IntegrationScenarioTest {

    @Test
    @DisplayName("실제 GitLab 웹훅과 유사한 완전한 페이로드 처리")
    fun `should process complete payload similar to real GitLab webhook`() {
      val event = MergeRequestEvent(
        objectKind = "merge_request",
        user = User(
          id = 100L,
          name = "John Doe",
          username = "johndoe",
          email = "john.doe@example.com",
          avatarUrl = "https://gitlab.com/uploads/user/avatar/100/avatar.png"
        ),
        project = Project(
          id = 200L,
          name = "awesome-project",
          description = "An awesome project",
          webUrl = "https://gitlab.com/group/awesome-project",
          pathWithNamespace = "group/awesome-project",
          defaultBranch = "main",
          httpUrl = "https://gitlab.com/group/awesome-project.git",
          sshUrl = "git@gitlab.com:group/awesome-project.git"
        ),
        objectAttributes = MergeRequestAttributes(
          id = 300L,
          iid = 42L,
          title = "Add new feature",
          description = "This MR adds a new awesome feature",
          sourceBranch = "feature/new-feature",
          targetBranch = "main",
          sourceProjectId = 200L,
          targetProjectId = 200L,
          state = "opened",
          mergeStatus = "can_be_merged",
          createdAt = "2023-01-01T10:00:00Z",
          updatedAt = "2023-01-01T11:00:00Z",
          workInProgress = false,
          draft = false,
          action = "open"
        ),
        repository = Repository(
          name = "awesome-project",
          url = "git@gitlab.com:group/awesome-project.git",
          description = "An awesome project",
          homepage = "https://gitlab.com/group/awesome-project"
        )
      )

      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties
      every { codeReviewService.processWebhookEvent(any()) } just Runs

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Merge Request Hook")
          .header("X-Gitlab-Token", validSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.status").value("accepted"))
        .andExpect(jsonPath("$.message").value("Webhook received and processing started"))

      verify { codeReviewService.processWebhookEvent(any()) }
    }

    @Test
    @DisplayName("토큰 검증과 이벤트 타입 검증이 순차적으로 실행됨")
    fun `should validate token before event type`() {
      val event = createValidMergeRequestEvent()
      val webhookProperties = mockk<WebhookProperties>()
      every { webhookProperties.secretToken } returns validSecretToken

      every { gitLabProperties.webhook } returns webhookProperties

      mockMvc.perform(
        post("/api/webhook/gitlab")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Gitlab-Event", "Push Hook")
          .header("X-Gitlab-Token", invalidSecretToken)
          .content(objectMapper.writeValueAsString(event))
      )
        .andExpect(status().isBadRequest)

      verify(exactly = 0) { codeReviewService.processWebhookEvent(any()) }
    }
  }
}
