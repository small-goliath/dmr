package at.tori.dmr.service

import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.config.CodeReviewProperties
import at.tori.dmr.config.ChunkingProperties
import at.tori.dmr.domain.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("컨텍스트 빌더 서비스 테스트")
class ContextBuilderServiceTest {

  private lateinit var gitLabApiClient: GitLabApiClient
  private lateinit var codeReviewProperties: CodeReviewProperties
  private lateinit var contextBuilderService: ContextBuilderService

  @BeforeEach
  fun setUp() {
    gitLabApiClient = mockk()
    codeReviewProperties = CodeReviewProperties(
      maxFiles = 50,
      maxFileSize = 1024 * 100,
      excludedExtensions = listOf("json", "md", "txt"),
      excludedPaths = listOf("build/", "gradle/", ".git/"),
      chunking = ChunkingProperties(
        enabled = false,
        filesPerChunk = 10
      )
    )
    contextBuilderService = ContextBuilderService(gitLabApiClient, codeReviewProperties)
  }

  private fun createMergeRequest(): MergeRequest {
    return MergeRequest(
      id = 100L,
      iid = 10L,
      title = "Test MR",
      description = "Test description",
      sourceBranch = "feature",
      targetBranch = "main",
      state = "opened",
      mergeStatus = "can_be_merged",
      webUrl = "https://gitlab.com/group/project/-/merge_requests/10",
      createdAt = "2024-01-01T10:00:00Z",
      updatedAt = "2024-01-01T11:00:00Z",
      author = User(1L, "Test User", "testuser", "test@example.com"),
      draft = false,
      workInProgress = false,
      diffRefs = DiffRefs("base123", "start123", "head123")
    )
  }

  private fun createMergeRequestChange(
    oldPath: String = "src/File.kt",
    newPath: String = "src/File.kt",
    newFile: Boolean = false,
    deletedFile: Boolean = false,
    renamedFile: Boolean = false,
    diff: String = "@@ -1,1 +1,2 @@\n line1\n+line2\n-line3"
  ): MergeRequestChange {
    return MergeRequestChange(
      oldPath = oldPath,
      newPath = newPath,
      aMode = "100644",
      bMode = "100644",
      newFile = newFile,
      deletedFile = deletedFile,
      renamedFile = renamedFile,
      diff = diff
    )
  }

  @Nested
  @DisplayName("컨텍스트 빌드")
  inner class BuildContextTest {

    @Test
    @DisplayName("정상적인 MR 컨텍스트를 빌드함")
    fun `should build review context for normal MR`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/File1.kt", "src/File1.kt"),
        createMergeRequestChange("src/File2.kt", "src/File2.kt")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals("gitlab.com/group/project/-", context.projectName)
      assertEquals("Test MR", context.mrTitle)
      assertEquals("Test description", context.mrDescription)
      assertEquals("feature", context.sourceBranch)
      assertEquals("main", context.targetBranch)
      assertEquals("testuser", context.author)
      assertEquals(2, context.files.size)
      assertEquals(2, context.totalFiles)
      assertTrue(context.totalAdditions > 0)
      assertTrue(context.totalDeletions > 0)
    }

    @Test
    @DisplayName("삭제된 파일은 제외함")
    fun `should exclude deleted files`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/File1.kt", "src/File1.kt", deletedFile = false),
        createMergeRequestChange("src/File2.kt", "src/File2.kt", deletedFile = true)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.files.size)
      assertEquals("src/File1.kt", context.files[0].filePath)
    }

    @Test
    @DisplayName("diff가 빈 파일은 제외함")
    fun `should exclude files with empty diff`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/File1.kt", "src/File1.kt", diff = "@@ -1,1 +1,2 @@\n line1\n+line2"),
        createMergeRequestChange("src/File2.kt", "src/File2.kt", diff = "")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.files.size)
      assertEquals("src/File1.kt", context.files[0].filePath)
    }

    @Test
    @DisplayName("제외된 확장자는 필터링함")
    fun `should filter excluded extensions`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/File.kt", "src/File.kt"),
        createMergeRequestChange("README.md", "README.md"),
        createMergeRequestChange("config.json", "config.json"),
        createMergeRequestChange("notes.txt", "notes.txt")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.files.size)
      assertEquals("src/File.kt", context.files[0].filePath)
    }

    @Test
    @DisplayName("제외된 경로는 필터링함")
    fun `should filter excluded paths`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/main/File.kt", "src/main/File.kt"),
        createMergeRequestChange("build/generated/File.kt", "build/generated/File.kt"),
        createMergeRequestChange("gradle/wrapper/gradle.properties", "gradle/wrapper/gradle.properties"),
        createMergeRequestChange(".git/config", ".git/config")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.files.size)
      assertEquals("src/main/File.kt", context.files[0].filePath)
    }

    @Test
    @DisplayName("최대 파일 크기를 초과하는 파일은 제외함")
    fun `should exclude files exceeding max size`() = runTest {
      val mr = createMergeRequest()
      val smallDiff = "@@ -1,1 +1,2 @@\n line1\n+line2"
      val largeDiff = "x".repeat((codeReviewProperties.maxFileSize + 1000).toInt())

      val changes = listOf(
        createMergeRequestChange("src/Small.kt", "src/Small.kt", diff = smallDiff),
        createMergeRequestChange("src/Large.kt", "src/Large.kt", diff = largeDiff)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.files.size)
      assertEquals("src/Small.kt", context.files[0].filePath)
    }

    @Test
    @DisplayName("최대 파일 개수를 초과하면 제한함")
    fun `should limit files to max count`() = runTest {
      codeReviewProperties = codeReviewProperties.copy(maxFiles = 3)
      contextBuilderService = ContextBuilderService(gitLabApiClient, codeReviewProperties)

      val mr = createMergeRequest()
      val changes = (1..10).map {
        createMergeRequestChange("src/File$it.kt", "src/File$it.kt")
      }

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(3, context.files.size)
    }
  }

  @Nested
  @DisplayName("통계 계산")
  inner class StatisticsCalculationTest {

    @Test
    @DisplayName("추가 라인과 삭제 라인을 정확히 계산함")
    fun `should calculate additions and deletions correctly`() = runTest {
      val mr = createMergeRequest()
      val diff = """
        @@ -1,5 +1,6 @@
         context line 1
        -removed line 1
        -removed line 2
         context line 2
        +added line 1
        +added line 2
        +added line 3
         context line 3
      """.trimIndent()

      val changes = listOf(createMergeRequestChange(diff = diff))

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(3, context.totalAdditions)
      assertEquals(2, context.totalDeletions)
    }

    @Test
    @DisplayName("diff 헤더는 계산에서 제외함")
    fun `should exclude diff headers from count`() = runTest {
      val mr = createMergeRequest()
      val diff = """
        --- a/File.kt
        +++ b/File.kt
        @@ -1,2 +1,3 @@
         line1
        +line2
        -line3
      """.trimIndent()

      val changes = listOf(createMergeRequestChange(diff = diff))

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1, context.totalAdditions)
      assertEquals(1, context.totalDeletions)
    }

    @Test
    @DisplayName("여러 파일의 통계를 합산함")
    fun `should aggregate statistics across multiple files`() = runTest {
      val mr = createMergeRequest()
      val diff1 = "@@ -1,1 +1,2 @@\n line\n+added1\n+added2"
      val diff2 = "@@ -1,1 +1,2 @@\n line\n-removed1\n+added3"

      val changes = listOf(
        createMergeRequestChange("File1.kt", "File1.kt", diff = diff1),
        createMergeRequestChange("File2.kt", "File2.kt", diff = diff2)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(3, context.totalAdditions)
      assertEquals(1, context.totalDeletions)
    }
  }

  @Nested
  @DisplayName("파일 변환")
  inner class FileConversionTest {

    @Test
    @DisplayName("새 파일은 newFile 플래그가 true임")
    fun `should set newFile flag for new files`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/NewFile.kt", "src/NewFile.kt", newFile = true)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertTrue(context.files[0].newFile)
    }

    @Test
    @DisplayName("이름이 변경된 파일은 oldPath와 newPath가 다름")
    fun `should preserve oldPath and newPath for renamed files`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/OldName.kt", "src/NewName.kt", renamedFile = true)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals("src/OldName.kt", context.files[0].oldPath)
      assertEquals("src/NewName.kt", context.files[0].filePath)
      assertTrue(context.files[0].renamedFile)
    }

    @Test
    @DisplayName("확장자를 올바르게 추출함")
    fun `should extract file extension correctly`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("src/File.kt", "src/File.kt"),
        createMergeRequestChange("README.md", "README.md"),
        createMergeRequestChange("config", "config")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertTrue(context.files.any { it.extension == "kt" })
    }

    @Test
    @DisplayName("파일 크기를 diff 길이로 설정함")
    fun `should set file size based on diff length`() = runTest {
      val mr = createMergeRequest()
      val diff = "x".repeat(1000)
      val changes = listOf(
        createMergeRequestChange(diff = diff)
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(1000L, context.files[0].fileSize)
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  inner class EdgeCaseTest {

    @Test
    @DisplayName("변경사항이 없으면 빈 컨텍스트를 반환함")
    fun `should return empty context when no changes`() = runTest {
      val mr = createMergeRequest()

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns emptyList()

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(0, context.files.size)
      assertEquals(0, context.totalFiles)
      assertEquals(0, context.totalAdditions)
      assertEquals(0, context.totalDeletions)
    }

    @Test
    @DisplayName("모든 파일이 필터링되면 빈 파일 리스트를 반환함")
    fun `should return empty files list when all filtered out`() = runTest {
      val mr = createMergeRequest()
      val changes = listOf(
        createMergeRequestChange("README.md", "README.md"),
        createMergeRequestChange("config.json", "config.json")
      )

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(0, context.files.size)
      assertEquals(2, context.totalFiles)
    }

    @Test
    @DisplayName("diffRefs가 null이면 컨텍스트에도 null로 설정됨")
    fun `should handle null diffRefs`() = runTest {
      val mr = createMergeRequest().copy(diffRefs = null)
      val changes = listOf(createMergeRequestChange())

      coEvery { gitLabApiClient.getMergeRequestChanges(200L, 10L) } returns changes

      val context = contextBuilderService.buildContext(200L, mr)

      assertEquals(null, context.diffRefs)
    }
  }
}
