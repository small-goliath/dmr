package at.tori.dmr.service

import at.tori.dmr.client.GoogleChatClient
import at.tori.dmr.domain.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Google Chat 알림 서비스 테스트")
class GoogleChatNotifierTest {

  private lateinit var googleChatClient: GoogleChatClient
  private lateinit var googleChatNotifier: GoogleChatNotifier

  @BeforeEach
  fun setUp() {
    googleChatClient = mockk()
    googleChatNotifier = GoogleChatNotifier(googleChatClient)
  }

  private fun createReviewContext(): ReviewContext {
    return ReviewContext(
      projectName = "test-project",
      mrTitle = "Add new feature",
      mrDescription = "Test description",
      sourceBranch = "feature",
      targetBranch = "main",
      author = "testuser",
      files = listOf(
        FileChange(
          filePath = "src/File1.kt",
          oldPath = "src/File1.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "@@ -1,1 +1,2 @@\n line1\n+line2",
          extension = "kt",
          fileSize = 100L
        )
      ),
      totalFiles = 3,
      totalAdditions = 50,
      totalDeletions = 20,
      diffRefs = DiffRefs("base123", "start123", "head123")
    )
  }

  @Nested
  @DisplayName("라인별 리뷰 알림")
  inner class LineByLineReviewNotificationTest {

    @Test
    @DisplayName("정상적인 리뷰 완료 알림을 전송함")
    fun `should send line-by-line review notification`() = runTest {
      val context = createReviewContext()
      val lineCommentCount = 5
      val mrUrl = "https://gitlab.com/group/project/-/merge_requests/10"

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, lineCommentCount, mrUrl)

      coVerify {
        googleChatClient.sendMessage(match {
          it.cards != null && it.cards.isNotEmpty() &&
              it.cards!![0].header?.title == "DMR 코드 리뷰 완료" &&
              it.cards!![0].header?.subtitle == "test-project"
        })
      }
    }

    @Test
    @DisplayName("Google Chat이 설정되지 않으면 알림을 건너뜀")
    fun `should skip notification when Google Chat not configured`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns false

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify(exactly = 0) { googleChatClient.sendMessage(any()) }
    }

    @Test
    @DisplayName("메시지에 MR 상세 정보를 포함함")
    fun `should include MR details in message`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify {
        googleChatClient.sendMessage(match { message ->
          val sections = message.cards!![0].sections
          sections.any { section ->
            section.widgets.any { widget ->
              widget is KeyValue &&
                  widget.keyValue.topLabel == "제목" &&
                  widget.keyValue.content == "Add new feature"
            }
          } &&
              sections.any { section ->
                section.widgets.any { widget ->
                  widget is KeyValue &&
                      widget.keyValue.topLabel == "요청자" &&
                      widget.keyValue.content == "testuser"
                }
              } &&
              sections.any { section ->
                section.widgets.any { widget ->
                  widget is KeyValue &&
                      widget.keyValue.topLabel == "브랜치" &&
                      widget.keyValue.content == "feature → main"
                }
              }
        })
      }
    }

    @Test
    @DisplayName("메시지에 리뷰 결과 통계를 포함함")
    fun `should include review statistics in message`() = runTest {
      val context = createReviewContext()
      val lineCommentCount = 8

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, lineCommentCount, "https://test.com")

      coVerify {
        googleChatClient.sendMessage(match { message ->
          val sections = message.cards!![0].sections
          sections.any { section ->
            section.widgets.any { widget ->
              widget is KeyValue &&
                  widget.keyValue.topLabel == "라인별 댓글" &&
                  widget.keyValue.content == "8개"
            }
          } &&
              sections.any { section ->
                section.widgets.any { widget ->
                  widget is KeyValue &&
                      widget.keyValue.topLabel == "변경된 파일" &&
                      widget.keyValue.content == "3개"
                }
              } &&
              sections.any { section ->
                section.widgets.any { widget ->
                  widget is KeyValue &&
                      widget.keyValue.topLabel == "코드 변경량" &&
                      widget.keyValue.content == "+50 -20"
                }
              }
        })
      }
    }

    @Test
    @DisplayName("메시지에 MR 링크 버튼을 포함함")
    fun `should include MR link button in message`() = runTest {
      val context = createReviewContext()
      val mrUrl = "https://gitlab.com/group/project/-/merge_requests/10"

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, 5, mrUrl)

      coVerify {
        googleChatClient.sendMessage(match { message ->
          val sections = message.cards!![0].sections
          sections.any { section ->
            section.widgets.any { widget ->
              widget is Buttons &&
                  widget.buttons.any { button ->
                    button.textButton?.text == "새 창으로 MR 보기" &&
                        button.textButton?.onClick?.openLink?.url == mrUrl
                  }
            }
          }
        })
      }
    }

    @Test
    @DisplayName("전송 실패 시 예외를 잡아서 처리함")
    fun `should handle send failure gracefully`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } throws RuntimeException("Send failed")

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify { googleChatClient.sendMessage(any()) }
    }

    @Test
    @DisplayName("전송이 실패를 반환해도 예외를 발생시키지 않음")
    fun `should not throw when send returns false`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns false

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify { googleChatClient.sendMessage(any()) }
    }
  }

  @Nested
  @DisplayName("오류 알림")
  inner class ErrorNotificationTest {

    @Test
    @DisplayName("정상적인 오류 알림을 전송함")
    fun `should send error notification`() = runTest {
      val projectName = "test-project"
      val mrTitle = "Test MR"
      val mrUrl = "https://gitlab.com/test/mr/10"
      val error = "Connection timeout"

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendText(any()) } returns true

      googleChatNotifier.notifyError(projectName, mrTitle, mrUrl, error)

      coVerify {
        googleChatClient.sendText(match {
          it.contains("Code Review Failed") &&
              it.contains("test-project") &&
              it.contains("Test MR") &&
              it.contains("Connection timeout") &&
              it.contains(mrUrl)
        })
      }
    }

    @Test
    @DisplayName("Google Chat이 설정되지 않으면 오류 알림을 건너뜀")
    fun `should skip error notification when Google Chat not configured`() = runTest {
      coEvery { googleChatClient.isConfigured() } returns false

      googleChatNotifier.notifyError("project", "mr", "url", "error")

      coVerify(exactly = 0) { googleChatClient.sendText(any()) }
    }

    @Test
    @DisplayName("오류 알림 전송 실패 시 예외를 잡아서 처리함")
    fun `should handle error notification failure gracefully`() = runTest {
      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendText(any()) } throws RuntimeException("Network error")

      googleChatNotifier.notifyError("project", "mr", "url", "error")

      coVerify { googleChatClient.sendText(any()) }
    }

    @Test
    @DisplayName("오류 메시지에 모든 정보를 포함함")
    fun `should include all information in error message`() = runTest {
      val projectName = "my-project"
      val mrTitle = "Feature: Add login"
      val mrUrl = "https://gitlab.com/group/my-project/-/merge_requests/42"
      val error = "API rate limit exceeded"

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendText(any()) } returns true

      googleChatNotifier.notifyError(projectName, mrTitle, mrUrl, error)

      coVerify {
        googleChatClient.sendText(match {
          it.contains("my-project") &&
              it.contains("Feature: Add login") &&
              it.contains("API rate limit exceeded") &&
              it.contains("https://gitlab.com/group/my-project/-/merge_requests/42")
        })
      }
    }
  }

  @Nested
  @DisplayName("메시지 포맷")
  inner class MessageFormatTest {

    @Test
    @DisplayName("리뷰 메시지는 카드 형식임")
    fun `should format review message as card`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify {
        googleChatClient.sendMessage(match {
          it.cards?.size == 1 &&
              it.cards!![0].header != null &&
              it.cards!![0].sections.isNotEmpty()
        })
      }
    }

    @Test
    @DisplayName("리뷰 메시지는 여러 섹션으로 구성됨")
    fun `should organize review message into sections`() = runTest {
      val context = createReviewContext()

      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendMessage(any()) } returns true

      googleChatNotifier.notifyLineByLineReview(context, 5, "https://test.com")

      coVerify {
        googleChatClient.sendMessage(match {
          val sections = it.cards!![0].sections
          sections.size >= 4
        })
      }
    }

    @Test
    @DisplayName("오류 메시지는 텍스트 형식임")
    fun `should format error message as text`() = runTest {
      coEvery { googleChatClient.isConfigured() } returns true
      coEvery { googleChatClient.sendText(any()) } returns true

      googleChatNotifier.notifyError("project", "mr", "url", "error")

      coVerify { googleChatClient.sendText(any<String>()) }
    }
  }
}
