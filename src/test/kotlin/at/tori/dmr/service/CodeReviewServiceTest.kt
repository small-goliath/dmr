package at.tori.dmr.service

import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.domain.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("코드 리뷰 서비스 테스트")
class CodeReviewServiceTest {

  private lateinit var gitLabApiClient: GitLabApiClient
  private lateinit var contextBuilderService: ContextBuilderService
  private lateinit var googleChatNotifier: GoogleChatNotifier
  private lateinit var lineByLineReviewService: LineByLineReviewService
  private lateinit var codeReviewService: CodeReviewService

  @BeforeEach
  fun setUp() {
    gitLabApiClient = mockk()
    contextBuilderService = mockk()
    googleChatNotifier = mockk()
    lineByLineReviewService = mockk()
    codeReviewService = CodeReviewService(
      gitLabApiClient,
      contextBuilderService,
      googleChatNotifier,
      lineByLineReviewService
    )
  }

  private fun createMergeRequestEvent(action: String = "open"): MergeRequestEvent {
    return MergeRequestEvent(
      objectKind = "merge_request",
      user = User(
        id = 1L,
        name = "Test User",
        username = "testuser",
        email = "test@example.com"
      ),
      project = Project(
        id = 100L,
        name = "test-project",
        webUrl = "https://gitlab.com/group/test-project",
        pathWithNamespace = "group/test-project"
      ),
      objectAttributes = MergeRequestAttributes(
        id = 200L,
        iid = 10L,
        title = "Test MR",
        sourceBranch = "feature",
        targetBranch = "main",
        sourceProjectId = 100L,
        targetProjectId = 100L,
        state = "opened",
        action = action
      )
    )
  }

  private fun createMergeRequest(draft: Boolean = false, wip: Boolean = false): MergeRequest {
    return MergeRequest(
      id = 200L,
      iid = 10L,
      title = "Test MR",
      description = "Test description",
      sourceBranch = "feature",
      targetBranch = "main",
      state = "opened",
      mergeStatus = "can_be_merged",
      webUrl = "https://gitlab.com/group/test-project/-/merge_requests/10",
      createdAt = "2024-01-01T10:00:00Z",
      updatedAt = "2024-01-01T11:00:00Z",
      author = User(1L, "Test User", "testuser", "test@example.com"),
      draft = draft,
      workInProgress = wip
    )
  }

  private fun createReviewContext(fileCount: Int = 3): ReviewContext {
    return ReviewContext(
      projectName = "test-project",
      mrTitle = "Test MR",
      mrDescription = "Test description",
      sourceBranch = "feature",
      targetBranch = "main",
      author = "testuser",
      files = (1..fileCount).map {
        FileChange(
          filePath = "src/File$it.kt",
          oldPath = "src/File$it.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "@@ -1,1 +1,2 @@\n line1\n+line2",
          extension = "kt",
          fileSize = 100L
        )
      },
      totalFiles = fileCount,
      totalAdditions = 10,
      totalDeletions = 5,
      diffRefs = DiffRefs("base123", "start123", "head123")
    )
  }

  @Nested
  @DisplayName("웹훅 이벤트 처리")
  inner class WebhookEventProcessingTest {

    @Test
    @DisplayName("open 액션은 코드 리뷰를 트리거함")
    fun `should trigger review for open action`() = runTest {
      val event = createMergeRequestEvent("open")
      val mr = createMergeRequest()
      val context = createReviewContext()

      coEvery { gitLabApiClient.getMergeRequest(any(), any()) } returns mr
      coEvery { contextBuilderService.buildContext(any(), any()) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(any(), any(), any()) } returns 5
      coEvery { gitLabApiClient.postMergeRequestNote(any(), any(), any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(any(), any(), any()) } returns Unit

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(timeout = 1000) { gitLabApiClient.getMergeRequest(100L, 10L) }
    }

    @Test
    @DisplayName("update 액션은 변경사항이 있을 때 리뷰를 트리거함")
    fun `should trigger review for update action with changes`() = runTest {
      val event = createMergeRequestEvent("update").copy(
        changes = Changes(
          title = TitleChange("Old Title", "New Title")
        )
      )

      val mr = createMergeRequest()
      val context = createReviewContext()

      coEvery { gitLabApiClient.getMergeRequest(any(), any()) } returns mr
      coEvery { contextBuilderService.buildContext(any(), any()) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(any(), any(), any()) } returns 5
      coEvery { gitLabApiClient.postMergeRequestNote(any(), any(), any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(any(), any(), any()) } returns Unit

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(timeout = 1000) { gitLabApiClient.getMergeRequest(100L, 10L) }
    }

    @Test
    @DisplayName("update 액션이지만 변경사항이 없으면 리뷰를 스킵함")
    fun `should skip review for update action without changes`() = runTest {
      val event = createMergeRequestEvent("update").copy(changes = null)

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }

    @Test
    @DisplayName("reopen 액션은 코드 리뷰를 트리거함")
    fun `should trigger review for reopen action`() = runTest {
      val event = createMergeRequestEvent("reopen")
      val mr = createMergeRequest()
      val context = createReviewContext()

      coEvery { gitLabApiClient.getMergeRequest(any(), any()) } returns mr
      coEvery { contextBuilderService.buildContext(any(), any()) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(any(), any(), any()) } returns 5
      coEvery { gitLabApiClient.postMergeRequestNote(any(), any(), any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(any(), any(), any()) } returns Unit

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(timeout = 1000) { gitLabApiClient.getMergeRequest(100L, 10L) }
    }

    @Test
    @DisplayName("approved 액션은 리뷰를 트리거하지 않음")
    fun `should not trigger review for approved action`() = runTest {
      val event = createMergeRequestEvent("approved")

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }

    @Test
    @DisplayName("merge 액션은 리뷰를 트리거하지 않음")
    fun `should not trigger review for merge action`() = runTest {
      val event = createMergeRequestEvent("merge")

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }

    @Test
    @DisplayName("close 액션은 리뷰를 트리거하지 않음")
    fun `should not trigger review for close action`() = runTest {
      val event = createMergeRequestEvent("close")

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }
  }

  @Nested
  @DisplayName("코드 리뷰 수행")
  inner class PerformCodeReviewTest {

    @Test
    @DisplayName("정상적인 코드 리뷰 수행")
    fun `should perform complete code review`() = runTest {
      val mr = createMergeRequest()
      val context = createReviewContext()

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr
      coEvery { contextBuilderService.buildContext(100L, mr) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(100L, 10L, context) } returns 5
      coEvery { gitLabApiClient.postMergeRequestNote(100L, 10L, any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(context, 5, any()) } returns Unit

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify { gitLabApiClient.getMergeRequest(100L, 10L) }
      coVerify { contextBuilderService.buildContext(100L, mr) }
      coVerify { lineByLineReviewService.performLineByLineReview(100L, 10L, context) }
      coVerify { gitLabApiClient.postMergeRequestNote(100L, 10L, match { it.contains("DMR 코드 리뷰 완료") }) }
      coVerify { googleChatNotifier.notifyLineByLineReview(context, 5, any()) }
    }

    @Test
    @DisplayName("드래프트 MR은 스킵함")
    fun `should skip draft MR`() = runTest {
      val mr = createMergeRequest(draft = true)

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify { gitLabApiClient.getMergeRequest(100L, 10L) }
      coVerify(exactly = 0) { contextBuilderService.buildContext(any(), any()) }
    }

    @Test
    @DisplayName("WIP MR은 스킵함")
    fun `should skip WIP MR`() = runTest {
      val mr = createMergeRequest(wip = true)

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify { gitLabApiClient.getMergeRequest(100L, 10L) }
      coVerify(exactly = 0) { contextBuilderService.buildContext(any(), any()) }
    }

    @Test
    @DisplayName("리뷰할 파일이 없으면 스킵함")
    fun `should skip when no files to review`() = runTest {
      val mr = createMergeRequest()
      val context = createReviewContext(fileCount = 0)

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr
      coEvery { contextBuilderService.buildContext(100L, mr) } returns context

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify { gitLabApiClient.getMergeRequest(100L, 10L) }
      coVerify { contextBuilderService.buildContext(100L, mr) }
      coVerify(exactly = 0) { lineByLineReviewService.performLineByLineReview(any(), any(), any()) }
    }

    @Test
    @DisplayName("요약 댓글에 정확한 통계를 포함함")
    fun `should include accurate statistics in summary comment`() = runTest {
      val mr = createMergeRequest()
      val context = createReviewContext(fileCount = 5)
      val lineCommentCount = 8

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr
      coEvery { contextBuilderService.buildContext(100L, mr) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(100L, 10L, context) } returns lineCommentCount
      coEvery { gitLabApiClient.postMergeRequestNote(100L, 10L, any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(any(), any(), any()) } returns Unit

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify {
        gitLabApiClient.postMergeRequestNote(100L, 10L, match {
          it.contains("총 8개의 리뷰 댓글") &&
              it.contains("변경된 파일: 5개") &&
              it.contains("추가: +10") &&
              it.contains("삭제: -5")
        })
      }
    }
  }

  @Nested
  @DisplayName("오류 처리")
  inner class ErrorHandlingTest {

    @Test
    @DisplayName("리뷰 중 오류 발생 시 Google Chat 알림 전송")
    fun `should send error notification when review fails`() = runTest {
      val mr = createMergeRequest()

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr
      coEvery { contextBuilderService.buildContext(100L, mr) } throws RuntimeException("Test error")
      coEvery { googleChatNotifier.notifyError(any(), any(), any(), any()) } returns Unit

      try {
        codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")
      } catch (e: Exception) {
        // do nothing
      }

      coVerify { googleChatNotifier.notifyError(any(), "Test MR", any(), "Test error") }
    }

    @Test
    @DisplayName("Google Chat 알림 실패는 리뷰 전체를 실패시키지 않음")
    fun `should not fail entire review when Google Chat notification fails`() = runTest {
      val mr = createMergeRequest()
      val context = createReviewContext()

      coEvery { gitLabApiClient.getMergeRequest(100L, 10L) } returns mr
      coEvery { contextBuilderService.buildContext(100L, mr) } returns context
      coEvery { lineByLineReviewService.performLineByLineReview(100L, 10L, context) } returns 5
      coEvery { gitLabApiClient.postMergeRequestNote(100L, 10L, any()) } returns mockk()
      coEvery { googleChatNotifier.notifyLineByLineReview(any(), any(), any()) } throws RuntimeException("Chat error")

      codeReviewService.performCodeReview(100L, 10L, "https://gitlab.com/test/mr/10")

      coVerify { gitLabApiClient.postMergeRequestNote(100L, 10L, any()) }
    }
  }

  @Nested
  @DisplayName("MR 액션 결정")
  inner class ActionDeterminationTest {

    @Test
    @DisplayName("알 수 없는 액션은 Ignore로 처리")
    fun `should treat unknown action as Ignore`() = runTest {
      val event = createMergeRequestEvent("unknown-action")

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }

    @Test
    @DisplayName("액션이 null인 경우 unknown으로 처리")
    fun `should treat null action as unknown`() = runTest {
      val event = createMergeRequestEvent().copy(
        objectAttributes = createMergeRequestEvent().objectAttributes.copy(action = null)
      )

      codeReviewService.processWebhookEvent(event)

      kotlinx.coroutines.delay(100)

      coVerify(exactly = 0) { gitLabApiClient.getMergeRequest(any(), any()) }
    }
  }
}
