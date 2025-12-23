package at.tori.dmr.service

import at.tori.dmr.analyzer.CrossFileAnalysisResult
import at.tori.dmr.analyzer.CrossFileImpactAnalyzer
import at.tori.dmr.analyzer.DependencyAnalyzer
import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.config.ChunkingProperties
import at.tori.dmr.config.CodeReviewProperties
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

@DisplayName("청킹 리뷰 서비스 테스트")
class ChunkedReviewServiceTest {

  private lateinit var gitLabApiClient: GitLabApiClient
  private lateinit var dependencyAnalyzer: DependencyAnalyzer
  private lateinit var crossFileImpactAnalyzer: CrossFileImpactAnalyzer
  private lateinit var chatClient: ChatClient
  private lateinit var lineReviewChatOptions: OpenAiChatOptions
  private lateinit var promptTemplateService: PromptTemplateService
  private lateinit var jsonResponseParser: JsonResponseParser
  private lateinit var codeReviewProperties: CodeReviewProperties
  private lateinit var chunkedReviewService: ChunkedReviewService

  @BeforeEach
  fun setUp() {
    gitLabApiClient = mockk()
    dependencyAnalyzer = mockk()
    crossFileImpactAnalyzer = mockk()
    chatClient = mockk(relaxed = true)
    lineReviewChatOptions = mockk()
    promptTemplateService = mockk()
    jsonResponseParser = mockk()
    codeReviewProperties = CodeReviewProperties(
      maxFiles = 50,
      maxFileSize = 1024 * 100,
      excludedExtensions = emptyList(),
      excludedPaths = emptyList(),
      chunking = ChunkingProperties(
        enabled = true,
        filesPerChunk = 5
      )
    )
    chunkedReviewService = ChunkedReviewService(
      gitLabApiClient,
      dependencyAnalyzer,
      crossFileImpactAnalyzer,
      chatClient,
      lineReviewChatOptions,
      promptTemplateService,
      jsonResponseParser,
      codeReviewProperties
    )
  }

  private fun createReviewContext(fileCount: Int = 15): ReviewContext {
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
      totalAdditions = 10 * fileCount,
      totalDeletions = 5 * fileCount,
      diffRefs = DiffRefs("base123", "start123", "head123")
    )
  }

  @Nested
  @DisplayName("청킹 활성화 조건")
  inner class ChunkingActivationTest {

    @Test
    @DisplayName("청킹이 비활성화되면 0을 반환함")
    fun `should return 0 when chunking is disabled`() = runTest {
      codeReviewProperties = codeReviewProperties.copy(
        chunking = ChunkingProperties(enabled = false, filesPerChunk = 5)
      )
      chunkedReviewService = ChunkedReviewService(
        gitLabApiClient,
        dependencyAnalyzer,
        crossFileImpactAnalyzer,
        chatClient,
        lineReviewChatOptions,
        promptTemplateService,
        jsonResponseParser,
        codeReviewProperties
      )

      val context = createReviewContext(15)

      val result = chunkedReviewService.performChunkedReview(100L, 10L, context)

      assertEquals(0, result)
      coVerify(exactly = 0) { dependencyAnalyzer.analyze(any(), any(), any()) }
    }

    @Test
    @DisplayName("파일 수가 청크 크기 이하면 0을 반환함")
    fun `should return 0 when file count is less than or equal to chunk size`() = runTest {
      val context = createReviewContext(5)

      val result = chunkedReviewService.performChunkedReview(100L, 10L, context)

      assertEquals(0, result)
      coVerify(exactly = 0) { dependencyAnalyzer.analyze(any(), any(), any()) }
    }

    @Test
    @DisplayName("파일 수가 청크 크기보다 크면 청킹 리뷰를 수행함")
    fun `should perform chunking review when file count exceeds chunk size`() = runTest {
      val context = createReviewContext(15)

      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "test prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns emptyList()
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      val result = chunkedReviewService.performChunkedReview(100L, 10L, context)

      coVerify { dependencyAnalyzer.analyze(100L, context.files, "main") }
      coVerify { dependencyAnalyzer.analyzeUsedDependencies(100L, context.files, "main") }
      coVerify { crossFileImpactAnalyzer.analyze(context.files, any()) }
    }

    @Test
    @DisplayName("diffRefs가 없으면 0을 반환함")
    fun `should return 0 when diffRefs is null`() = runTest {
      val context = createReviewContext(15).copy(diffRefs = null)

      val result = chunkedReviewService.performChunkedReview(100L, 10L, context)

      assertEquals(0, result)
      coVerify(exactly = 0) { dependencyAnalyzer.analyze(any(), any(), any()) }
    }
  }

  @Nested
  @DisplayName("의존성 분석")
  inner class DependencyAnalysisTest {

    @Test
    @DisplayName("전체 파일에 대한 Forward 의존성을 분석함")
    fun `should analyze forward dependencies for all files`() = runTest {
      val context = createReviewContext(15)

      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "test prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns emptyList()
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      chunkedReviewService.performChunkedReview(100L, 10L, context)

      coVerify(exactly = 1) { dependencyAnalyzer.analyze(100L, context.files, "main") }
    }

    @Test
    @DisplayName("전체 파일에 대한 Backward 의존성을 분석함")
    fun `should analyze backward dependencies for all files`() = runTest {
      val context = createReviewContext(15)

      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "test prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns emptyList()
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      chunkedReviewService.performChunkedReview(100L, 10L, context)

      coVerify(exactly = 1) { dependencyAnalyzer.analyzeUsedDependencies(100L, context.files, "main") }
    }

    @Test
    @DisplayName("전체 파일에 대한 파일간 영향도를 분석함")
    fun `should analyze cross-file impact for all files`() = runTest {
      val context = createReviewContext(15)

      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } returns emptyList()
      coEvery { dependencyAnalyzer.analyzeUsedDependencies(any(), any(), any()) } returns emptyList()
      coEvery { crossFileImpactAnalyzer.analyze(any(), any()) } returns CrossFileAnalysisResult(emptyList(), emptySet(), false, false, "")
      coEvery { promptTemplateService.buildLineByLineReviewPrompt(any(), any(), any(), any()) } returns "test prompt"
      coEvery { jsonResponseParser.parseLineComments(any()) } returns emptyList()
      coEvery { gitLabApiClient.createDiscussion(any(), any(), any(), any()) } returns mockk()

      chunkedReviewService.performChunkedReview(100L, 10L, context)

      coVerify(exactly = 1) { crossFileImpactAnalyzer.analyze(context.files, any()) }
    }
  }

  @Nested
  @DisplayName("오류 처리")
  inner class ErrorHandlingTest {

    @Test
    @DisplayName("의존성 분석 실패 시 0을 반환함")
    fun `should return 0 when dependency analysis fails`() = runTest {
      val context = createReviewContext(15)

      coEvery { dependencyAnalyzer.analyze(any(), any(), any()) } throws RuntimeException("Analysis failed")

      val result = chunkedReviewService.performChunkedReview(100L, 10L, context)

      assertEquals(0, result)
    }
  }
}
