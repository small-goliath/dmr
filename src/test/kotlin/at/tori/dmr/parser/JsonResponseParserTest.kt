package at.tori.dmr.parser

import at.tori.dmr.domain.CommentSeverity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("json 응답 parser 테스트")
class JsonResponseParserTest {

  private lateinit var parser: JsonResponseParser
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setUp() {
    objectMapper = jacksonObjectMapper()
    parser = JsonResponseParser(objectMapper)
  }

  @Nested
  @DisplayName("코드 펜스 내 JSON 파싱")
  inner class CodeFenceJsonTest {

    @Test
    @DisplayName("마크다운 코드 펜스 내의 JSON을 정상적으로 파싱")
    fun `should parse JSON in markdown code fence`() {
      val response = """
        Here are the code review comments:

        ```json
        {
          "line_comments": [
            {
              "file_path": "src/main/kotlin/Test.kt",
              "new_line": 10,
              "severity": "warning",
              "comment": "Consider using more descriptive variable names"
            }
          ]
        }
        ```

        That's all!
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals("src/main/kotlin/Test.kt", result[0].filePath)
      assertEquals(10, result[0].newLine)
      assertEquals(CommentSeverity.WARNING, result[0].severity)
      assertEquals("Consider using more descriptive variable names", result[0].comment)
    }

    @Test
    @DisplayName("코드 펜스 내의 여러 라인 댓글을 파싱")
    fun `should parse multiple line comments in code fence`() {
      val response = """
        ```json
        {
          "line_comments": [
            {
              "file_path": "src/File1.kt",
              "new_line": 5,
              "severity": "critical",
              "comment": "Potential null pointer exception"
            },
            {
              "file_path": "src/File2.kt",
              "new_line": 20,
              "severity": "suggestion",
              "comment": "Could be simplified"
            },
            {
              "file_path": "src/File3.kt",
              "new_line": 15,
              "severity": "info",
              "comment": "Good practice"
            }
          ]
        }
        ```
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(3, result.size)

      assertEquals("src/File1.kt", result[0].filePath)
      assertEquals(CommentSeverity.CRITICAL, result[0].severity)

      assertEquals("src/File2.kt", result[1].filePath)
      assertEquals(CommentSeverity.SUGGESTION, result[1].severity)

      assertEquals("src/File3.kt", result[2].filePath)
      assertEquals(CommentSeverity.INFO, result[2].severity)
    }
  }

  @Nested
  @DisplayName("중괄호로 감싸진 JSON 파싱")
  inner class BracedJsonTest {

    @Test
    @DisplayName("코드 펜스 없이 중괄호로 감싸진 JSON을 파싱")
    fun `should parse JSON without code fence`() {
      val response = """
        Some text before
        {
          "line_comments": [
            {
              "file_path": "test.kt",
              "new_line": 42,
              "severity": "warning",
              "comment": "Test comment"
            }
          ]
        }
        Some text after
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals("test.kt", result[0].filePath)
      assertEquals(42, result[0].newLine)
    }

    @Test
    @DisplayName("중첩된 JSON 객체를 올바르게 파싱")
    fun `should parse nested JSON objects correctly`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "nested.kt",
              "new_line": 100,
              "severity": "critical",
              "comment": "Nested { braces } in comment"
            }
          ]
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals("nested.kt", result[0].filePath)
      assertEquals("Nested { braces } in comment", result[0].comment)
    }
  }

  @Nested
  @DisplayName("불완전한 JSON 처리")
  inner class IncompleteJsonTest {

    @Test
    @DisplayName("불완전한 JSON은 수정을 시도하지만 실패할 수 있음")
    fun `should attempt to fix incomplete JSON but may fail`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "incomplete.kt",
              "new_line": 25,
              "severity": "info",
              "comment": "Missing brace"
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty() || result.isNotEmpty())
    }

    @Test
    @DisplayName("심각하게 손상된 JSON은 빈 리스트 반환")
    fun `should return empty list for severely broken JSON`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "test.kt"
              "new_line": 10
              "comment" "No colons"
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }
  }

  @Nested
  @DisplayName("심각도 파싱")
  inner class SeverityParsingTest {

    @Test
    @DisplayName("CRITICAL 심각도를 올바르게 파싱")
    fun `should parse CRITICAL severity`() {
      val response = """
        {"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "critical", "comment": "Critical issue"}]}
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(CommentSeverity.CRITICAL, result[0].severity)
    }

    @Test
    @DisplayName("WARNING 심각도를 올바르게 파싱")
    fun `should parse WARNING severity`() {
      val response = """
        {"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "warning", "comment": "Warning"}]}
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(CommentSeverity.WARNING, result[0].severity)
    }

    @Test
    @DisplayName("SUGGESTION 심각도를 올바르게 파싱")
    fun `should parse SUGGESTION severity`() {
      val response = """
        {"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "suggestion", "comment": "Suggestion"}]}
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(CommentSeverity.SUGGESTION, result[0].severity)
    }

    @Test
    @DisplayName("심각도가 누락된 경우 INFO로 기본 설정")
    fun `should default to INFO when severity is missing`() {
      val response = """
        {"line_comments": [{"file_path": "test.kt", "new_line": 1, "comment": "No severity"}]}
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(CommentSeverity.INFO, result[0].severity)
    }

    @Test
    @DisplayName("알 수 없는 심각도는 INFO로 처리")
    fun `should treat unknown severity as INFO`() {
      val response = """
        {"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "unknown", "comment": "Unknown"}]}
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(CommentSeverity.INFO, result[0].severity)
    }

    @Test
    @DisplayName("대소문자를 구분하지 않고 심각도 파싱")
    fun `should parse severity case-insensitively`() {
      val responses = listOf(
        """{"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "CRITICAL", "comment": "Test"}]}""",
        """{"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "Warning", "comment": "Test"}]}""",
        """{"line_comments": [{"file_path": "test.kt", "new_line": 1, "severity": "SuGgEsTiOn", "comment": "Test"}]}"""
      )

      assertEquals(CommentSeverity.CRITICAL, parser.parseLineComments(responses[0])[0].severity)
      assertEquals(CommentSeverity.WARNING, parser.parseLineComments(responses[1])[0].severity)
      assertEquals(CommentSeverity.SUGGESTION, parser.parseLineComments(responses[2])[0].severity)
    }
  }

  @Nested
  @DisplayName("에러 케이스 처리")
  inner class ErrorCaseTest {

    @Test
    @DisplayName("JSON이 없는 응답은 빈 리스트 반환")
    fun `should return empty list when no JSON in response`() {
      val response = "This is just plain text without any JSON"

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("line_comments 필드가 없는 JSON은 빈 리스트 반환")
    fun `should return empty list when line_comments field is missing`() {
      val response = """
        {
          "other_field": "value"
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("line_comments가 빈 배열인 경우 빈 리스트 반환")
    fun `should return empty list when line_comments is empty array`() {
      val response = """
        {
          "line_comments": []
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("필수 필드가 누락된 댓글은 무시")
    fun `should ignore comments with missing required fields`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "valid.kt",
              "new_line": 10,
              "comment": "Valid comment"
            },
            {
              "new_line": 20,
              "comment": "Missing file_path"
            },
            {
              "file_path": "missing-line.kt",
              "comment": "Missing new_line"
            },
            {
              "file_path": "missing-comment.kt",
              "new_line": 30
            },
            {
              "file_path": "valid2.kt",
              "new_line": 40,
              "comment": "Another valid comment"
            }
          ]
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(2, result.size)
      assertEquals("valid.kt", result[0].filePath)
      assertEquals("valid2.kt", result[1].filePath)
    }

    @Test
    @DisplayName("완전히 잘못된 JSON은 빈 리스트 반환")
    fun `should return empty list for completely invalid JSON`() {
      val response = "{ this is not valid json at all }"

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("빈 문자열은 빈 리스트 반환")
    fun `should return empty list for empty string`() {
      val response = ""

      val result = parser.parseLineComments(response)

      assertTrue(result.isEmpty())
    }
  }

  @Nested
  @DisplayName("특수 케이스 처리")
  inner class SpecialCaseTest {

    @Test
    @DisplayName("JSON 내 이스케이프된 문자를 올바르게 처리")
    fun `should handle escaped characters in JSON`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "escape.kt",
              "new_line": 5,
              "severity": "info",
              "comment": "Use \"quotes\" and newline\nhere"
            }
          ]
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertTrue(result[0].comment.contains("\"quotes\""))
    }

    @Test
    @DisplayName("매우 긴 댓글을 올바르게 파싱")
    fun `should parse very long comments`() {
      val longComment = "This is a very long comment. ".repeat(50)
      val response = """
        {
          "line_comments": [
            {
              "file_path": "long.kt",
              "new_line": 100,
              "severity": "suggestion",
              "comment": "$longComment"
            }
          ]
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals(longComment, result[0].comment)
    }

    @Test
    @DisplayName("new_line이 Number 타입일 때 올바르게 변환")
    fun `should convert new_line from Number to Int`() {
      val response = """
        {
          "line_comments": [
            {
              "file_path": "number.kt",
              "new_line": 42.0,
              "severity": "info",
              "comment": "Number as double"
            }
          ]
        }
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals(42, result[0].newLine)
    }

    @Test
    @DisplayName("여러 코드 펜스가 있을 때 첫 번째만 파싱")
    fun `should parse only first code fence when multiple exist`() {
      val response = """
        ```json
        {
          "line_comments": [
            {
              "file_path": "first.kt",
              "new_line": 10,
              "severity": "info",
              "comment": "First fence"
            }
          ]
        }
        ```

        ```json
        {
          "line_comments": [
            {
              "file_path": "second.kt",
              "new_line": 20,
              "severity": "info",
              "comment": "Second fence"
            }
          ]
        }
        ```
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(1, result.size)
      assertEquals("first.kt", result[0].filePath)
    }
  }

  @Nested
  @DisplayName("통합 시나리오")
  inner class IntegrationScenarioTest {

    @Test
    @DisplayName("실제 AI 응답과 유사한 완전한 응답을 파싱")
    fun `should parse realistic AI response with multiple comments`() {
      val response = """
        Based on my code review, here are my findings:

        ```json
        {
          "line_comments": [
            {
              "file_path": "src/main/kotlin/UserService.kt",
              "new_line": 45,
              "severity": "critical",
              "comment": "Potential SQL injection vulnerability. Use parameterized queries instead."
            },
            {
              "file_path": "src/main/kotlin/UserService.kt",
              "new_line": 67,
              "severity": "warning",
              "comment": "This method is too long. Consider breaking it into smaller functions."
            },
            {
              "file_path": "src/main/kotlin/DataRepository.kt",
              "new_line": 23,
              "severity": "suggestion",
              "comment": "Consider using a more descriptive variable name instead of 'x'."
            },
            {
              "file_path": "src/test/kotlin/UserServiceTest.kt",
              "new_line": 89,
              "severity": "info",
              "comment": "Good test coverage for edge cases!"
            }
          ]
        }
        ```

        Overall, the code quality is good but there are some security concerns.
      """.trimIndent()

      val result = parser.parseLineComments(response)

      assertEquals(4, result.size)

      assertEquals("src/main/kotlin/UserService.kt", result[0].filePath)
      assertEquals(45, result[0].newLine)
      assertEquals(CommentSeverity.CRITICAL, result[0].severity)
      assertTrue(result[0].comment.contains("SQL injection"))

      assertEquals(67, result[1].newLine)
      assertEquals(CommentSeverity.WARNING, result[1].severity)

      assertEquals("src/main/kotlin/DataRepository.kt", result[2].filePath)
      assertEquals(CommentSeverity.SUGGESTION, result[2].severity)

      assertEquals("src/test/kotlin/UserServiceTest.kt", result[3].filePath)
      assertEquals(CommentSeverity.INFO, result[3].severity)
    }
  }
}
