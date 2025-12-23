package at.tori.dmr.analyzer

import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.domain.FileChange
import at.tori.dmr.domain.SearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("의존성 분석기 테스트")
class DependencyAnalyzerTest {

  private val gitLabApiClient = mockk<GitLabApiClient>()
  private val analyzer = DependencyAnalyzer(gitLabApiClient)

  @Nested
  @DisplayName("analyze 메서드 테스트")
  inner class AnalyzeTest {

    @Test
    @DisplayName("Kotlin 함수 정의를 추출하고 의존성 분석")
    fun `should extract kotlin function and analyze dependencies`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Service.kt",
          oldPath = "src/main/kotlin/Service.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun processData(data: String): Int {",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Controller.kt",
          data = "val result = processData(input)",
          path = "src/main/kotlin/Controller.kt",
          filename = "Controller.kt",
          ref = "main",
          startLine = 15,
          projectId = 1L
        ),
        SearchResult(
          basename = "Service.kt",
          data = "fun processData(data: String): Int {",
          path = "src/main/kotlin/Service.kt",
          filename = "Service.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "processData", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("processData", result[0].symbol.name)
      assertEquals(SymbolType.FUNCTION, result[0].symbol.type)
      assertEquals(1, result[0].usageCount)
      assertEquals(setOf("src/main/kotlin/Controller.kt"), result[0].affectedFiles)
      assertTrue(result[0].hasExternalUsages)

      coVerify { gitLabApiClient.searchCode(projectId, "processData", targetBranch) }
    }

    @Test
    @DisplayName("Kotlin 클래스 정의를 추출하고 의존성 분석")
    fun `should extract kotlin class and analyze dependencies`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/models/User.kt",
          oldPath = "src/main/kotlin/models/User.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+class UserService {",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Controller.kt",
          data = "val service = UserService()",
          path = "src/main/kotlin/Controller.kt",
          filename = "Controller.kt",
          ref = "main",
          startLine = 20,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserService", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("UserService", result[0].symbol.name)
      assertEquals(SymbolType.CLASS, result[0].symbol.type)
      assertTrue(result[0].symbol.isPublic)
    }

    @Test
    @DisplayName("Kotlin 데이터 클래스 정의를 추출")
    fun `should extract kotlin data class`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/models/UserDto.kt",
          oldPath = "src/main/kotlin/models/UserDto.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+data class UserDto(",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Service.kt",
          data = "val dto = UserDto(name, email)",
          path = "src/main/kotlin/Service.kt",
          filename = "Service.kt",
          ref = "main",
          startLine = 25,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserDto", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("UserDto", result[0].symbol.name)
      assertEquals(SymbolType.DATA_CLASS, result[0].symbol.type)
    }

    @Test
    @DisplayName("Kotlin 인터페이스 정의를 추출")
    fun `should extract kotlin interface`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Repository.kt",
          oldPath = "src/main/kotlin/Repository.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+interface UserRepository {",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Service.kt",
          data = "class UserService(private val repo: UserRepository)",
          path = "src/main/kotlin/Service.kt",
          filename = "Service.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserRepository", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("UserRepository", result[0].symbol.name)
      assertEquals(SymbolType.INTERFACE, result[0].symbol.type)
    }

    @Test
    @DisplayName("Kotlin object 정의를 추출")
    fun `should extract kotlin object`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Constants.kt",
          oldPath = "src/main/kotlin/Constants.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+object AppConstants {",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Config.kt",
          data = "val timeout = AppConstants.DEFAULT_TIMEOUT",
          path = "src/main/kotlin/Config.kt",
          filename = "Config.kt",
          ref = "main",
          startLine = 5,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "AppConstants", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("AppConstants", result[0].symbol.name)
      assertEquals(SymbolType.OBJECT, result[0].symbol.type)
    }

    @Test
    @DisplayName("Kotlin 프로퍼티 정의를 추출")
    fun `should extract kotlin property`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Config.kt",
          oldPath = "src/main/kotlin/Config.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+val defaultTimeout: Int = 30",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Service.kt",
          data = "val timeout = defaultTimeout",
          path = "src/main/kotlin/Service.kt",
          filename = "Service.kt",
          ref = "main",
          startLine = 12,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "defaultTimeout", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("defaultTimeout", result[0].symbol.name)
      assertEquals(SymbolType.PROPERTY, result[0].symbol.type)
    }

    @Test
    @DisplayName("Kotlin 상수 정의를 추출")
    fun `should extract kotlin constant`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Constants.kt",
          oldPath = "src/main/kotlin/Constants.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+const val MAX_RETRY = 3",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Client.kt",
          data = "repeat(MAX_RETRY) {",
          path = "src/main/kotlin/Client.kt",
          filename = "Client.kt",
          ref = "main",
          startLine = 20,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "MAX_RETRY", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("MAX_RETRY", result[0].symbol.name)
      assertEquals(SymbolType.CONSTANT, result[0].symbol.type)
    }

    @Test
    @DisplayName("Java 클래스 정의를 추출")
    fun `should extract java class`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/java/UserService.java",
          oldPath = "src/main/java/UserService.java",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+public class UserService {",
          extension = "java"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Controller.java",
          data = "UserService service = new UserService();",
          path = "src/main/java/Controller.java",
          filename = "Controller.java",
          ref = "main",
          startLine = 15,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserService", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("UserService", result[0].symbol.name)
      assertEquals(SymbolType.CLASS, result[0].symbol.type)
      assertTrue(result[0].symbol.isPublic)
    }

    @Test
    @DisplayName("Java 인터페이스 정의를 추출")
    fun `should extract java interface`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/java/UserRepository.java",
          oldPath = "src/main/java/UserRepository.java",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+public interface UserRepository {",
          extension = "java"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Service.java",
          data = "implements UserRepository",
          path = "src/main/java/Service.java",
          filename = "Service.java",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserRepository", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("UserRepository", result[0].symbol.name)
      assertEquals(SymbolType.INTERFACE, result[0].symbol.type)
    }

    @Test
    @DisplayName("Java 메서드 정의를 추출")
    fun `should extract java method`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/java/Calculator.java",
          oldPath = "src/main/java/Calculator.java",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+public int calculate(int a, int b) {",
          extension = "java"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Service.java",
          data = "int result = calculator.calculate(10, 20);",
          path = "src/main/java/Service.java",
          filename = "Service.java",
          ref = "main",
          startLine = 25,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "calculate", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("calculate", result[0].symbol.name)
      assertEquals(SymbolType.FUNCTION, result[0].symbol.type)
    }

    @Test
    @DisplayName("삭제된 파일은 심볼 추출을 건너뜀")
    fun `should skip symbol extraction for deleted files`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/OldFile.kt",
          oldPath = "src/main/kotlin/OldFile.kt",
          newFile = false,
          deletedFile = true,
          renamedFile = false,
          diff = "-class OldClass",
          extension = "kt"
        )
      )

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
      coVerify(exactly = 0) { gitLabApiClient.searchCode(any(), any(), any()) }
    }

    @Test
    @DisplayName("외부 사용이 없는 심볼은 결과에 포함되지 않음")
    fun `should not include symbols without external usages`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Internal.kt",
          oldPath = "src/main/kotlin/Internal.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+private fun internalMethod() {}",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Internal.kt",
          data = "private fun internalMethod() {}",
          path = "src/main/kotlin/Internal.kt",
          filename = "Internal.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "internalMethod", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
    }

    @Test
    @DisplayName("Private 가시성을 올바르게 감지")
    fun `should correctly detect private visibility`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Service.kt",
          oldPath = "src/main/kotlin/Service.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+private fun privateMethod() {}",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "Other.kt",
          data = "privateMethod()",
          path = "src/main/kotlin/Other.kt",
          filename = "Other.kt",
          ref = "main",
          startLine = 5,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "privateMethod", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertFalse(result[0].symbol.isPublic)
    }

    @Test
    @DisplayName("검색 실패 시 경고 로그 후 계속 진행")
    fun `should continue after search failure with warning`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Service.kt",
          oldPath = "src/main/kotlin/Service.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun method1() {}\n+fun method2() {}",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "method1", targetBranch) } throws RuntimeException("API Error")
      coEvery { gitLabApiClient.searchCode(projectId, "method2", targetBranch) } returns listOf(
        SearchResult(
          basename = "Client.kt",
          data = "method2()",
          path = "src/main/kotlin/Client.kt",
          filename = "Client.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("method2", result[0].symbol.name)
    }

    @Test
    @DisplayName("여러 심볼을 한 번에 추출")
    fun `should extract multiple symbols at once`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Multiple.kt",
          oldPath = "src/main/kotlin/Multiple.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = """
                        +class MyClass {
                        +fun myMethod() {}
                        +val myProperty: String = ""
                    """.trimIndent(),
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "MyClass", targetBranch) } returns listOf(
        SearchResult(
          basename = "User1.kt",
          data = "val obj = MyClass()",
          path = "src/main/kotlin/User1.kt",
          filename = "User1.kt",
          ref = "main",
          startLine = 5,
          projectId = 1L
        )
      )
      coEvery { gitLabApiClient.searchCode(projectId, "myMethod", targetBranch) } returns listOf(
        SearchResult(
          basename = "User2.kt",
          data = "obj.myMethod()",
          path = "src/main/kotlin/User2.kt",
          filename = "User2.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )
      coEvery { gitLabApiClient.searchCode(projectId, "myProperty", targetBranch) } returns listOf(
        SearchResult(
          basename = "User3.kt",
          data = "val prop = obj.myProperty",
          path = "src/main/kotlin/User3.kt",
          filename = "User3.kt",
          ref = "main",
          startLine = 15,
          projectId = 1L
        )
      )

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(3, result.size)
      assertTrue(result.any { it.symbol.name == "MyClass" })
      assertTrue(result.any { it.symbol.name == "myMethod" })
      assertTrue(result.any { it.symbol.name == "myProperty" })
    }
  }

  @Nested
  @DisplayName("analyzeUsedDependencies 메서드 테스트")
  inner class AnalyzeUsedDependenciesTest {

    @Test
    @DisplayName("변경된 파일이 사용하는 외부 심볼 추출")
    fun `should extract external symbols used by changed file`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Service.kt",
          oldPath = "src/main/kotlin/Service.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+import com.example.Utils.helper\n+val result = helper()",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "helper", targetBranch) } returns listOf(
        SearchResult(
          basename = "Utils.kt",
          data = "fun helper() {}",
          path = "src/main/kotlin/Utils.kt",
          filename = "Utils.kt",
          ref = "main",
          startLine = 5,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("src/main/kotlin/Service.kt", result[0].sourceFile)
      assertTrue(result[0].usedSymbols.any { it.name == "helper" })
      assertTrue(result[0].externalFiles.contains("src/main/kotlin/Utils.kt"))
    }

    @Test
    @DisplayName("Import 문에서 심볼 추출")
    fun `should extract symbols from import statements`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Controller.kt",
          oldPath = "src/main/kotlin/Controller.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+import com.example.service.UserService",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserService", targetBranch) } returns listOf(
        SearchResult(
          basename = "UserService.kt",
          data = "class UserService",
          path = "src/main/kotlin/service/UserService.kt",
          filename = "UserService.kt",
          ref = "main",
          startLine = 3,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertTrue(result[0].usedSymbols.any {
        it.name == "UserService" && it.type == UsedSymbolType.IMPORT
      })
    }

    @Test
    @DisplayName("함수 호출 추출")
    fun `should extract function calls`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Client.kt",
          oldPath = "src/main/kotlin/Client.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+calculateTotal(items)",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "calculateTotal", targetBranch) } returns listOf(
        SearchResult(
          basename = "Calculator.kt",
          data = "fun calculateTotal(items: List<Item>)",
          path = "src/main/kotlin/Calculator.kt",
          filename = "Calculator.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertTrue(result[0].usedSymbols.any {
        it.name == "calculateTotal" && it.type == UsedSymbolType.FUNCTION_CALL
      })
    }

    @Test
    @DisplayName("클래스 인스턴스 생성 추출")
    fun `should extract class instantiation`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Main.kt",
          oldPath = "src/main/kotlin/Main.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+val user = UserDto(name, email)",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "UserDto", targetBranch) } returns listOf(
        SearchResult(
          basename = "UserDto.kt",
          data = "data class UserDto",
          path = "src/main/kotlin/dto/UserDto.kt",
          filename = "UserDto.kt",
          ref = "main",
          startLine = 3,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertTrue(result[0].usedSymbols.any {
        it.name == "UserDto" && it.type == UsedSymbolType.CLASS_USAGE
      })
    }

    @Test
    @DisplayName("프로퍼티 접근 추출")
    fun `should extract property access`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/View.kt",
          oldPath = "src/main/kotlin/View.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+val name = user.getName",
          extension = "kt"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "getName", targetBranch) } returns listOf(
        SearchResult(
          basename = "User.kt",
          data = "val getName: String",
          path = "src/main/kotlin/User.kt",
          filename = "User.kt",
          ref = "main",
          startLine = 5,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertTrue(result[0].usedSymbols.any {
        it.name == "getName" && it.type == UsedSymbolType.PROPERTY_ACCESS
      })
    }

    @Test
    @DisplayName("삭제된 파일은 건너뜀")
    fun `should skip deleted files for used dependencies`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Old.kt",
          oldPath = "src/main/kotlin/Old.kt",
          newFile = false,
          deletedFile = true,
          renamedFile = false,
          diff = "-import com.example.Utils",
          extension = "kt"
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
      coVerify(exactly = 0) { gitLabApiClient.searchCode(any(), any(), any()) }
    }

    @Test
    @DisplayName("외부 심볼을 사용하지 않는 파일은 결과에 포함되지 않음")
    fun `should not include files without external symbol usage`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Simple.kt",
          oldPath = "src/main/kotlin/Simple.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+val x = 10",
          extension = "kt"
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
    }

    @Test
    @DisplayName("Java 클래스 사용 추출 (new 키워드)")
    fun `should extract java class usage with new keyword`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/java/Main.java",
          oldPath = "src/main/java/Main.java",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+User user = new User();",
          extension = "java"
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "User", targetBranch) } returns listOf(
        SearchResult(
          basename = "User.java",
          data = "public class User",
          path = "src/main/java/User.java",
          filename = "User.java",
          ref = "main",
          startLine = 3,
          projectId = 1L
        )
      )

      val result = analyzer.analyzeUsedDependencies(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertTrue(result[0].usedSymbols.any {
        it.name == "User" && it.type == UsedSymbolType.CLASS_USAGE
      })
    }
  }

  @Nested
  @DisplayName("심볼 추출 엣지 케이스")
  inner class SymbolExtractionEdgeCasesTest {

    @Test
    @DisplayName("주석 라인은 무시")
    fun `should ignore comment lines`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Test.kt",
          oldPath = "src/main/kotlin/Test.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+// fun commentedFunction() {}",
          extension = "kt"
        )
      )

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
    }

    @Test
    @DisplayName("빈 라인은 무시")
    fun `should ignore empty lines`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Test.kt",
          oldPath = "src/main/kotlin/Test.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+\n+   \n+\t",
          extension = "kt"
        )
      )

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(0, result.size)
    }

    @Test
    @DisplayName("제네릭이 포함된 함수 정의 처리")
    fun `should handle function definition with generics`() = runTest {
      val projectId = 1L
      val targetBranch = "main"
      val changedFiles = listOf(
        FileChange(
          filePath = "src/main/kotlin/Generic.kt",
          oldPath = "src/main/kotlin/Generic.kt",
          newFile = false,
          deletedFile = false,
          renamedFile = false,
          diff = "+fun <T> transform(item: T): T {",
          extension = "kt"
        )
      )

      val searchResults = listOf(
        SearchResult(
          basename = "User.kt",
          data = "val result = transform(data)",
          path = "src/main/kotlin/User.kt",
          filename = "User.kt",
          ref = "main",
          startLine = 10,
          projectId = 1L
        )
      )

      coEvery { gitLabApiClient.searchCode(projectId, "transform", targetBranch) } returns searchResults

      val result = analyzer.analyze(projectId, changedFiles, targetBranch)

      assertEquals(1, result.size)
      assertEquals("transform", result[0].symbol.name)
      assertEquals(SymbolType.FUNCTION, result[0].symbol.type)
    }
  }
}
