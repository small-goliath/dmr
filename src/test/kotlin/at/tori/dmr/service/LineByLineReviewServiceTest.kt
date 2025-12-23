package at.tori.dmr.service

import at.tori.dmr.analyzer.CrossFileAnalysisResult
import at.tori.dmr.analyzer.CrossFileImpactAnalyzer
import at.tori.dmr.analyzer.DependencyAnalyzer
import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.domain.*
import at.tori.dmr.parser.JsonResponseParser
import at.tori.dmr.parser.LineComment
import at.tori.dmr.prompt.PromptTemplateService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import kotlin.test.assertEquals

@DisplayName("라인별 리뷰 서비스 테스트")
class LineByLineReviewServiceTest {

  private lateinit var gitLabApiClient: GitLabApiClient
  private lateinit var dependencyAnalyzer: DependencyAnalyzer
  private lateinit var crossFileImpactAnalyzer: CrossFileImpactAnalyzer
  private lateinit var chatClient: ChatClient
  private lateinit var lineReviewChatOptions: OpenAiChatOptions
  private lateinit var promptTemplateService: PromptTemplateService
  private lateinit var jsonResponseParser: JsonResponseParser
  private lateinit var chunkedReviewService: ChunkedReviewService
  private lateinit var lineByLineReviewService: LineByLineReviewService

  @BeforeEach
  fun setUp() {
    gitLabApiClient = mockk()
    dependencyAnalyzer = mockk()
    crossFileImpactAnalyzer = mockk()
    chatClient = mockk(relaxed = true)
    lineReviewChatOptions = mockk()
    promptTemplateService = mockk()
    jsonResponseParser = mockk()
    chunkedReviewService = mockk()
    lineByLineReviewService = LineByLineReviewService(
      gitLabApiClient,
      dependencyAnalyzer,
      crossFileImpactAnalyzer,
      chatClient,
      lineReviewChatOptions,
      promptTemplateService,
      jsonResponseParser,
      chunkedReviewService
    )
  }

  private fun createReviewContext(fileCount: Int = 2): ReviewContext {
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
  @DisplayName("라인별 리뷰 수행")
  inner class PerformLineByLineReviewTest {

    @Test
    @DisplayName("청킹 리뷰가 성공하면 결과를 반환함")
    fun `should return result from chunked review when successful`() = runTest {
      val context = createReviewContext()

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 15

      val result = lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      assertEquals(15, result)
      coVerify(exactly = 0) { dependencyAnalyzer.analyze(any(), any(), any()) }
    }

    @Test
    @DisplayName("diffRefs가 없으면 0을 반환함")
    fun `should return 0 when diffRefs is null`() = runTest {
      val context = createReviewContext().copy(diffRefs = null)

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 0

      val result = lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      assertEquals(0, result)
      coVerify(exactly = 0) { dependencyAnalyzer.analyze(any(), any(), any()) }
    }

    @Test
    @DisplayName("의존성 분석을 순차적으로 수행함")
    fun `should perform dependency analysis in sequence`() = runTest {
      val context = createReviewContext()

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 0
      coEvery { dependencyAnalyzer.analyze(100L, context.files, "main") } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(100L, context.files, "main") } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(context.files, emptyList()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "test prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns emptyList()

      lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      coVerifyOrder {
        dependencyAnalyzer.analyze(100L, context.files, "main")
        dependencyAnalyzer.analyzeUsedDependencies(100L, context.files, "main")
        crossFileImpactAnalyzer.analyze(context.files, any())
      }
    }
  }

  @Nested
  @DisplayName("댓글 작성")
  inner class PostLineCommentsTest {

    @Test
    @DisplayName("각 댓글에 심각도 아이콘을 추가함")
    fun `should add severity icon to each comment`() = runTest {
      val context = createReviewContext()
      val lineComments = listOf(
        LineComment("src/File1.kt", 10, CommentSeverity.CRITICAL, "Critical"),
        LineComment("src/File1.kt", 20, CommentSeverity.WARNING, "Warning"),
        LineComment("src/File1.kt", 30, CommentSeverity.SUGGESTION, "Suggestion"),
        LineComment("src/File1.kt", 40, CommentSeverity.INFO, "Info")
      )

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 0
      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns lineComments
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      coVerify { gitLabApiClient.createDiscussion(100L, 10L, match { it.startsWith("🔴") }, any()) }
      coVerify { gitLabApiClient.createDiscussion(100L, 10L, match { it.startsWith("🟡") }, any()) }
      coVerify { gitLabApiClient.createDiscussion(100L, 10L, match { it.startsWith("💡") }, any()) }
      coVerify { gitLabApiClient.createDiscussion(100L, 10L, match { it.startsWith("ℹ️") }, any()) }
    }

    @Test
    @DisplayName("새 파일인 경우 oldPath를 null로 설정함")
    fun `should set oldPath to null for new files`() = runTest {
      val context = createReviewContext(1).copy(
        files = listOf(
          FileChange(
            filePath = "src/NewFile.kt",
            oldPath = "src/NewFile.kt",
            newFile = true,
            deletedFile = false,
            renamedFile = false,
            diff = "@@ -0,0 +1,10 @@\n+new line",
            extension = "kt",
            fileSize = 100L
          )
        )
      )
      val lineComments = listOf(
        LineComment("src/NewFile.kt", 5, CommentSeverity.INFO, "Comment on new file")
      )

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 0
      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns lineComments
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      coVerify {
        gitLabApiClient.createDiscussion(100L, 10L, any(), match {
          it.oldPath == null && it.newPath == "src/NewFile.kt"
        })
      }
    }
  }

  @Nested
  @DisplayName("오류 처리")
  inner class ErrorHandlingTest {

    @Test
    @DisplayName("리뷰 중 오류 발생 시 0을 반환함")
    fun `should return 0 when error occurs during review`() = runTest {
      val context = createReviewContext()

      coEvery { chunkedReviewService.performChunkedReview(100L, 10L, context) } returns 0
      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } throws RuntimeException("Analysis failed")

      val result = lineByLineReviewService.performLineByLineReview(100L, 10L, context)

      assertEquals(0, result)
    }
  }
}
