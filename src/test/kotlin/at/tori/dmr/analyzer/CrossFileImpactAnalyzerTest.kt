package at.tori.dmr.analyzer

import at.tori.dmr.domain.FileChange
import at.tori.dmr.domain.SearchResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("크로스 파일 분석기 테스트")
class CrossFileImpactAnalyzerTest {

  private val analyzer = CrossFileImpactAnalyzer()

  @Nested
  @DisplayName("analyze 메서드 테스트")
  inner class AnalyzeTest {

    @Test
    @DisplayName("변경된 파일이 의존성이 없는 경우 LOW 영향도 반환")
    fun `should return LOW impact when file has no dependencies`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Test.kt",
          oldPath = "src/main/kotlin/Test.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun newFunction() {}",
          extension = "kt"
        )
      )
      val dependencies = emptyList<DependencyInfo>()

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertEquals(ImpactLevel.LOW, result.impacts[0].impactLevel)
      assertEquals(0, result.impacts[0].affectedFiles.size)
      assertFalse(result.hasCriticalImpact)
      assertFalse(result.hasBreakingChanges)
    }

    @Test
    @DisplayName("삭제된 파일이 의존성을 가진 경우 CRITICAL 영향도 반환")
    fun `should return CRITICAL impact when deleted file has dependencies`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/DeletedFile.kt",
          oldPath = "src/main/kotlin/DeletedFile.kt",
          newFile = false,
          deletedFile = true,
          renamedFile = false,
          diff = "-class DeletedFile",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "DeletedClass",
            type = SymbolType.CLASS,
            filePath = "src/main/kotlin/DeletedFile.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "User.kt",
              data = "val obj = DeletedClass()",
              path = "src/main/kotlin/User.kt",
              filename = "User.kt",
              ref = "main",
              startLine = 10,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/User.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertEquals(ImpactLevel.CRITICAL, result.impacts[0].impactLevel)
      assertTrue(result.impacts[0].breakingChanges.isNotEmpty())
      assertTrue(result.hasCriticalImpact)
    }

    @Test
    @DisplayName("10개 이상 파일에 영향을 주는 경우 CRITICAL 영향도 반환")
    fun `should return CRITICAL impact when affecting 10 or more files`() {
      val affectedFiles = (1..10).map { "src/main/kotlin/File$it.kt" }.toSet()
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/CommonUtil.kt",
          oldPath = "src/main/kotlin/CommonUtil.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun utilFunction() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "utilFunction",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/CommonUtil.kt",
            isPublic = true
          ),
          usages = affectedFiles.map { path ->
            SearchResult(
              basename = path.substringAfterLast("/"),
              data = "utilFunction()",
              path = path,
              filename = path.substringAfterLast("/"),
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          },
          affectedFiles = affectedFiles
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertEquals(ImpactLevel.CRITICAL, result.impacts[0].impactLevel)
      assertEquals(10, result.impacts[0].affectedFiles.size)
    }

    @Test
    @DisplayName("5개 이상 파일에 영향을 주는 경우 HIGH 영향도 반환")
    fun `should return HIGH impact when affecting 5 to 9 files`() {
      val affectedFiles = (1..5).map { "src/main/kotlin/File$it.kt" }.toSet()
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Service.kt",
          oldPath = "src/main/kotlin/Service.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun serviceMethod() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "serviceMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Service.kt",
            isPublic = true
          ),
          usages = affectedFiles.map { path ->
            SearchResult(
              basename = path.substringAfterLast("/"),
              data = "serviceMethod()",
              path = path,
              filename = path.substringAfterLast("/"),
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          },
          affectedFiles = affectedFiles
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertEquals(ImpactLevel.HIGH, result.impacts[0].impactLevel)
      assertEquals(5, result.impacts[0].affectedFiles.size)
    }

    @Test
    @DisplayName("2-4개 파일에 영향을 주는 경우 MEDIUM 영향도 반환")
    fun `should return MEDIUM impact when affecting 2 to 4 files`() {
      val affectedFiles = setOf(
        "src/main/kotlin/File1.kt",
        "src/main/kotlin/File2.kt"
      )
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Helper.kt",
          oldPath = "src/main/kotlin/Helper.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun helperMethod() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "helperMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Helper.kt",
            isPublic = true
          ),
          usages = affectedFiles.map { path ->
            SearchResult(
              basename = path.substringAfterLast("/"),
              data = "helperMethod()",
              path = path,
              filename = path.substringAfterLast("/"),
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          },
          affectedFiles = affectedFiles
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertEquals(ImpactLevel.MEDIUM, result.impacts[0].impactLevel)
      assertEquals(2, result.impacts[0].affectedFiles.size)
    }

    @Test
    @DisplayName("Public 심볼의 시그니처 변경 시 Breaking Change 감지")
    fun `should detect breaking changes for public symbol signature modification`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Api.kt",
          oldPath = "src/main/kotlin/Api.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = """
                        -fun apiMethod(param: String)
                        +fun apiMethod(param: String, newParam: Int)
                    """.trimIndent(),
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "apiMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Api.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "Client.kt",
              data = "apiMethod(\"test\")",
              path = "src/main/kotlin/Client.kt",
              filename = "Client.kt",
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/Client.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertTrue(result.impacts[0].breakingChanges.isNotEmpty())
      assertTrue(result.hasBreakingChanges)
      assertTrue(result.impacts[0].breakingChanges[0].contains("시그니처 변경됨"))
    }

    @Test
    @DisplayName("Public 심볼 삭제 시 Breaking Change 감지")
    fun `should detect breaking changes for public symbol deletion`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Api.kt",
          oldPath = "src/main/kotlin/Api.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "-fun deprecatedMethod()",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "deprecatedMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Api.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "OldClient.kt",
              data = "deprecatedMethod()",
              path = "src/main/kotlin/OldClient.kt",
              filename = "OldClient.kt",
              ref = "main",
              startLine = 10,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/OldClient.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertTrue(result.impacts[0].breakingChanges.isNotEmpty())
      assertTrue(result.hasBreakingChanges)
      assertTrue(result.impacts[0].breakingChanges[0].contains("삭제됨"))
    }

    @Test
    @DisplayName("여러 파일 변경 시 모든 영향도 분석")
    fun `should analyze all impacts for multiple file changes`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/File1.kt",
          oldPath = "src/main/kotlin/File1.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun method1() {}",
          extension = "kt"
        ),
        FileChange(
          filePath = "src/main/kotlin/File2.kt",
          oldPath = "src/main/kotlin/File2.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun method2() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "method1",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/File1.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "User1.kt",
              data = "method1()",
              path = "src/main/kotlin/User1.kt",
              filename = "User1.kt",
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/User1.kt")
        ),
        DependencyInfo(
          symbol = Symbol(
            name = "method2",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/File2.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "User2.kt",
              data = "method2()",
              path = "src/main/kotlin/User2.kt",
              filename = "User2.kt",
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/User2.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(2, result.impacts.size)
      assertEquals(2, result.totalAffectedFiles.size)
    }

    @Test
    @DisplayName("빈 변경 파일 목록은 빈 결과 반환")
    fun `should return empty result for empty changed files`() {
      val changedFiles = emptyList<FileChange>()
      val dependencies = emptyList<DependencyInfo>()

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(0, result.impacts.size)
      assertEquals(0, result.totalAffectedFiles.size)
      assertFalse(result.hasCriticalImpact)
      assertFalse(result.hasBreakingChanges)
    }

    @Test
    @DisplayName("Summary 메시지가 올바르게 생성됨")
    fun `should generate correct summary message`() {
      val affectedFiles = (1..10).map { "src/main/kotlin/File$it.kt" }.toSet()
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Critical.kt",
          oldPath = "src/main/kotlin/Critical.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun criticalMethod() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "criticalMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Critical.kt",
            isPublic = true
          ),
          usages = affectedFiles.map { path ->
            SearchResult(
              basename = path.substringAfterLast("/"),
              data = "criticalMethod()",
              path = path,
              filename = path.substringAfterLast("/"),
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          },
          affectedFiles = affectedFiles
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertTrue(result.summary.contains("파일 간 영향 분석"))
      assertTrue(result.summary.contains("10개 파일에 영향"))
      assertTrue(result.summary.contains("Critical 영향"))
    }

    @Test
    @DisplayName("Private 심볼 변경은 Breaking Change로 감지되지 않음")
    fun `should not detect breaking changes for private symbol modifications`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Internal.kt",
          oldPath = "src/main/kotlin/Internal.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = """
                        -private fun privateMethod(param: String)
                        +private fun privateMethod(param: String, newParam: Int)
                    """.trimIndent(),
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "privateMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Internal.kt",
            isPublic = false
          ),
          usages = listOf(
            SearchResult(
              basename = "OtherFile.kt",
              data = "privateMethod(\"test\")",
              path = "src/main/kotlin/OtherFile.kt",
              filename = "OtherFile.kt",
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/OtherFile.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertTrue(result.impacts[0].breakingChanges.isEmpty())
      assertFalse(result.hasBreakingChanges)
    }
  }

  @Nested
  @DisplayName("ChangeType 분석 테스트")
  inner class ChangeTypeAnalysisTest {

    @Test
    @DisplayName("추가된 심볼은 ADDED로 분류")
    fun `should classify added symbol as ADDED`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/New.kt",
          oldPath = "src/main/kotlin/New.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun newMethod() {}",
          extension = "kt"
        )
      )
      val dependencies = emptyList<DependencyInfo>()

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertTrue(result.impacts[0].breakingChanges.isEmpty())
    }

    @Test
    @DisplayName("삭제된 심볼은 DELETED로 분류")
    fun `should classify deleted symbol as DELETED`() {
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Old.kt",
          oldPath = "src/main/kotlin/Old.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "-fun oldMethod() {}",
          extension = "kt"
        )
      )
      val dependencies = listOf(
        DependencyInfo(
          symbol = Symbol(
            name = "oldMethod",
            type = SymbolType.FUNCTION,
            filePath = "src/main/kotlin/Old.kt",
            isPublic = true
          ),
          usages = listOf(
            SearchResult(
              basename = "User.kt",
              data = "oldMethod()",
              path = "src/main/kotlin/User.kt",
              filename = "User.kt",
              ref = "main",
              startLine = 5,
              projectId = 1L
            )
          ),
          affectedFiles = setOf("src/main/kotlin/User.kt")
        )
      )

      val result = analyzer.analyze(changedFiles, dependencies)

      assertEquals(1, result.impacts.size)
      assertTrue(result.impacts[0].breakingChanges.isNotEmpty())
      assertTrue(result.impacts[0].breakingChanges[0].contains("삭제됨"))
    }
  }
}
