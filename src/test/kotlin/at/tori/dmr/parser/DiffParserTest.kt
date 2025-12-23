package at.tori.dmr.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("gitlab diff parser 테스트")
class DiffParserTest {

  private lateinit var parser: DiffParser

  @BeforeEach
  fun setUp() {
    parser = DiffParser()
  }

  @Nested
  @DisplayName("기본 diff 파싱")
  inner class BasicDiffParsingTest {

    @Test
    @DisplayName("단일 청크의 diff를 파싱")
    fun `should parse single chunk diff`() {
      val diff = """
        @@ -10,3 +12,4 @@
         context line 1
        -removed line
         context line 2
        +added line
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(10, chunk.oldStart)
      assertEquals(3, chunk.oldCount)
      assertEquals(12, chunk.newStart)
      assertEquals(4, chunk.newCount)
      assertEquals(4, chunk.lines.size)
    }

    @Test
    @DisplayName("여러 청크의 diff를 파싱")
    fun `should parse multiple chunk diff`() {
      val diff = """
        @@ -10,2 +10,3 @@
         line 1
        +added line 1
         line 2
        @@ -20,2 +21,2 @@
        -removed line 2
         line 3
        +added line 2
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(2, chunks.size)

      assertEquals(10, chunks[0].oldStart)
      assertEquals(10, chunks[0].newStart)

      assertEquals(20, chunks[1].oldStart)
      assertEquals(21, chunks[1].newStart)
    }

    @Test
    @DisplayName("추가만 있는 diff를 파싱")
    fun `should parse diff with only additions`() {
      val diff = """
        @@ -0,0 +1,3 @@
        +new line 1
        +new line 2
        +new line 3
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(3, chunk.lines.size)
      assertTrue(chunk.lines.all { it.type == LineType.ADDITION })
      assertEquals(null, chunk.lines[0].oldLine)
      assertEquals(1, chunk.lines[0].newLine)
      assertEquals("new line 1", chunk.lines[0].content)
    }

    @Test
    @DisplayName("삭제만 있는 diff를 파싱")
    fun `should parse diff with only deletions`() {
      val diff = """
        @@ -1,3 +0,0 @@
        -deleted line 1
        -deleted line 2
        -deleted line 3
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(3, chunk.lines.size)
      assertTrue(chunk.lines.all { it.type == LineType.DELETION })
      assertEquals(1, chunk.lines[0].oldLine)
      assertEquals(-1, chunk.lines[0].newLine)
      assertEquals("deleted line 1", chunk.lines[0].content)
    }
  }

  @Nested
  @DisplayName("라인 타입별 파싱")
  inner class LineTypeParsingTest {

    @Test
    @DisplayName("ADDITION 라인을 올바르게 파싱")
    fun `should correctly parse ADDITION lines`() {
      val diff = """
        @@ -1,1 +1,2 @@
         context
        +added line
      """.trimIndent()

      val chunks = parser.parse(diff)

      val addedLine = chunks[0].lines.find { it.type == LineType.ADDITION }
      assertNotNull(addedLine)
      assertEquals("added line", addedLine?.content)
      assertEquals(null, addedLine?.oldLine)
      assertEquals(2, addedLine?.newLine)
    }

    @Test
    @DisplayName("DELETION 라인을 올바르게 파싱")
    fun `should correctly parse DELETION lines`() {
      val diff = """
        @@ -1,2 +1,1 @@
         context
        -deleted line
      """.trimIndent()

      val chunks = parser.parse(diff)

      val deletedLine = chunks[0].lines.find { it.type == LineType.DELETION }
      assertNotNull(deletedLine)
      assertEquals("deleted line", deletedLine?.content)
      assertEquals(2, deletedLine?.oldLine)
      assertEquals(-1, deletedLine?.newLine)
    }

    @Test
    @DisplayName("CONTEXT 라인을 올바르게 파싱")
    fun `should correctly parse CONTEXT lines`() {
      val diff = """
        @@ -1,3 +1,3 @@
         context line 1
        -old
        +new
         context line 2
      """.trimIndent()

      val chunks = parser.parse(diff)

      val contextLines = chunks[0].lines.filter { it.type == LineType.CONTEXT }
      assertEquals(2, contextLines.size)
      assertEquals("context line 1", contextLines[0].content)
      assertEquals(1, contextLines[0].oldLine)
      assertEquals(1, contextLines[0].newLine)
    }

    @Test
    @DisplayName("빈 라인을 CONTEXT로 파싱")
    fun `should parse empty lines as CONTEXT`() {
      val diff = """
        @@ -1,3 +1,3 @@
         line 1

         line 2
      """.trimIndent()

      val chunks = parser.parse(diff)

      val emptyLine = chunks[0].lines[1]
      assertEquals(LineType.CONTEXT, emptyLine.type)
      assertEquals("", emptyLine.content)
    }
  }

  @Nested
  @DisplayName("라인 번호 추적")
  inner class LineNumberTrackingTest {

    @Test
    @DisplayName("추가/삭제 시 라인 번호를 정확하게 추적")
    fun `should track line numbers correctly with additions and deletions`() {
      val diff = """
        @@ -10,5 +10,6 @@
         context 10
        -deleted 11
         context 12
        +added 11
        +added 12
         context 13
      """.trimIndent()

      val chunks = parser.parse(diff)

      val lines = chunks[0].lines

      assertEquals(10, lines[0].oldLine)
      assertEquals(10, lines[0].newLine)

      assertEquals(11, lines[1].oldLine)
      assertEquals(-1, lines[1].newLine)

      assertEquals(12, lines[2].oldLine)
      assertEquals(11, lines[2].newLine)

      assertEquals(null, lines[3].oldLine)
      assertEquals(12, lines[3].newLine)

      assertEquals(null, lines[4].oldLine)
      assertEquals(13, lines[4].newLine)

      assertEquals(13, lines[5].oldLine)
      assertEquals(14, lines[5].newLine)
    }

    @Test
    @DisplayName("oldCount와 newCount가 1인 경우 올바르게 처리")
    fun `should handle single line changes correctly`() {
      val diff = """
        @@ -5 +5 @@
        -old line
        +new line
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      assertEquals(5, chunks[0].oldStart)
      assertEquals(1, chunks[0].oldCount)
      assertEquals(5, chunks[0].newStart)
      assertEquals(1, chunks[0].newCount)
    }
  }

  @Nested
  @DisplayName("특수 케이스 처리")
  inner class SpecialCaseTest {

    @Test
    @DisplayName("'No newline at end of file' 마커를 무시")
    fun `should ignore no newline at end of file marker`() {
      val diff = """
        @@ -1,2 +1,2 @@
         line 1
        -old line 2
        +new line 2
        \ No newline at end of file
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]
      assertEquals(3, chunk.lines.size)
      assertFalse(chunk.lines.any { it.content.contains("No newline") })
    }

    @Test
    @DisplayName("헤더에 정보가 없는 라인을 CONTEXT로 처리")
    fun `should treat lines without markers as CONTEXT`() {
      val diff = """
        @@ -1,3 +1,3 @@
         line 1
        line without marker
         line 3
      """.trimIndent()

      val chunks = parser.parse(diff)

      val unmarkedLine = chunks[0].lines[1]
      assertEquals(LineType.CONTEXT, unmarkedLine.type)
      assertEquals("line without marker", unmarkedLine.content)
    }

    @Test
    @DisplayName("빈 diff는 빈 리스트 반환")
    fun `should return empty list for empty diff`() {
      val diff = ""

      val chunks = parser.parse(diff)

      assertTrue(chunks.isEmpty())
    }

    @Test
    @DisplayName("헤더만 있고 내용이 없는 diff 처리")
    fun `should handle diff with header but no content`() {
      val diff = "@@ -1,0 +1,0 @@"

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      assertTrue(chunks[0].lines.isEmpty())
    }

    @Test
    @DisplayName("잘못된 헤더 형식은 무시")
    fun `should ignore invalid header format`() {
      val diff = """
        @@ invalid header @@
         some line
        @@ -1,1 +1,1 @@
         valid line
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      assertEquals(1, chunks[0].lines.size)
      assertEquals("valid line", chunks[0].lines[0].content)
    }
  }

  @Nested
  @DisplayName("getChangedLinesWithContext 메서드")
  inner class ChangedLinesWithContextTest {

    @Test
    @DisplayName("변경된 라인과 주변 컨텍스트를 반환")
    fun `should return changed lines with surrounding context`() {
      val diff = """
        @@ -1,10 +1,10 @@
         line 1
         line 2
         line 3
         line 4
        -old line 5
        +new line 5
         line 6
         line 7
         line 8
         line 9
      """.trimIndent()

      val result = parser.getChangedLinesWithContext(diff, contextLines = 2)

      assertTrue(result.size >= 5)
      assertTrue(result.any { it.type == LineType.DELETION })
      assertTrue(result.any { it.type == LineType.ADDITION })
      assertTrue(result.any { it.type == LineType.CONTEXT })
    }

    @Test
    @DisplayName("컨텍스트 라인 수를 조정할 수 있음")
    fun `should respect context lines parameter`() {
      val diff = """
        @@ -1,7 +1,7 @@
         line 1
         line 2
         line 3
        -old line 4
        +new line 4
         line 5
         line 6
         line 7
      """.trimIndent()

      val result1 = parser.getChangedLinesWithContext(diff, contextLines = 1)
      val result2 = parser.getChangedLinesWithContext(diff, contextLines = 3)

      assertTrue(result2.size >= result1.size)
    }

    @Test
    @DisplayName("청크 경계를 초과하지 않음")
    fun `should not exceed chunk boundaries`() {
      val diff = """
        @@ -1,3 +1,3 @@
        -old line 1
        +new line 1
         line 2
         line 3
      """.trimIndent()

      val result = parser.getChangedLinesWithContext(diff, contextLines = 10)

      assertTrue(result.size <= 4)
      assertTrue(result.any { it.type == LineType.DELETION || it.type == LineType.ADDITION })
    }

    @Test
    @DisplayName("변경사항이 없으면 빈 리스트 반환")
    fun `should return empty list when no changes`() {
      val diff = """
        @@ -1,3 +1,3 @@
         line 1
         line 2
         line 3
      """.trimIndent()

      val result = parser.getChangedLinesWithContext(diff, contextLines = 3)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("여러 변경사항의 컨텍스트를 병합")
    fun `should merge context for multiple changes`() {
      val diff = """
        @@ -1,10 +1,10 @@
         line 1
         line 2
        -old line 3
        +new line 3
         line 4
         line 5
        -old line 6
        +new line 6
         line 7
         line 8
      """.trimIndent()

      val result = parser.getChangedLinesWithContext(diff, contextLines = 2)

      assertTrue(result.any { it.type == LineType.DELETION })
      assertTrue(result.any { it.type == LineType.ADDITION })
      val uniqueContents = result.map { it.content }.distinct()
      assertEquals(result.size, uniqueContents.size)
    }

    @Test
    @DisplayName("기본 컨텍스트 라인 수는 3")
    fun `should use default context lines of 3`() {
      val diff = """
        @@ -1,10 +1,10 @@
         line 1
         line 2
         line 3
         line 4
        -old line 5
        +new line 5
         line 6
         line 7
         line 8
         line 9
         line 10
      """.trimIndent()

      val result = parser.getChangedLinesWithContext(diff)

      assertTrue(result.size >= 7)
    }
  }

  @Nested
  @DisplayName("실제 Git diff 시나리오")
  inner class RealGitDiffScenarioTest {

    @Test
    @DisplayName("실제 Kotlin 파일의 diff를 파싱")
    fun `should parse real Kotlin file diff`() {
      val diff = """
        @@ -15,7 +15,8 @@ class UserService(
             private val userRepository: UserRepository
         ) {

        -    fun findUser(id: Long): User? {
        -        return userRepository.findById(id)
        +    fun findUser(id: Long): User {
        +        return userRepository.findById(id)
        +            ?: throw UserNotFoundException("User not found: ${'$'}id")
             }

      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(15, chunk.oldStart)
      assertEquals(7, chunk.oldCount)
      assertEquals(15, chunk.newStart)
      assertEquals(8, chunk.newCount)

      val deletions = chunk.lines.filter { it.type == LineType.DELETION }
      val additions = chunk.lines.filter { it.type == LineType.ADDITION }

      assertTrue(deletions.size >= 2)
      assertTrue(additions.size >= 3)
    }

    @Test
    @DisplayName("파일 생성 diff를 파싱")
    fun `should parse file creation diff`() {
      val diff = """
        @@ -0,0 +1,5 @@
        +package com.example
        +
        +class NewClass {
        +    fun newMethod() = "Hello"
        +}
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(0, chunk.oldStart)
      assertEquals(0, chunk.oldCount)
      assertEquals(1, chunk.newStart)
      assertEquals(5, chunk.newCount)

      assertTrue(chunk.lines.all { it.type == LineType.ADDITION })
      assertEquals(5, chunk.lines.size)
    }

    @Test
    @DisplayName("파일 삭제 diff를 파싱")
    fun `should parse file deletion diff`() {
      val diff = """
        @@ -1,5 +0,0 @@
        -package com.example
        -
        -class OldClass {
        -    fun oldMethod() = "Goodbye"
        -}
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(1, chunks.size)
      val chunk = chunks[0]

      assertEquals(1, chunk.oldStart)
      assertEquals(5, chunk.oldCount)
      assertEquals(0, chunk.newStart)
      assertEquals(0, chunk.newCount)

      assertTrue(chunk.lines.all { it.type == LineType.DELETION })
      assertEquals(5, chunk.lines.size)
    }

    @Test
    @DisplayName("여러 청크를 가진 큰 diff를 파싱")
    fun `should parse large diff with multiple chunks`() {
      val diff = """
        @@ -10,3 +10,4 @@ class Service {
         fun method1() {
        -    oldCode()
        +    newCode()
        +    additionalCode()
         }
        @@ -50,2 +51,2 @@ class Service {
         fun method2() {
        -    return false
        +    return true
         }
        @@ -100,3 +101,2 @@ class Service {
         fun method3() {
        -    deprecatedCall()
             modernCall()
         }
      """.trimIndent()

      val chunks = parser.parse(diff)

      assertEquals(3, chunks.size)

      // 첫 번째 청크
      assertEquals(10, chunks[0].oldStart)
      assertTrue(chunks[0].lines.any { it.type == LineType.ADDITION })

      // 두 번째 청크
      assertEquals(50, chunks[1].oldStart)
      assertTrue(chunks[1].lines.any { it.type == LineType.DELETION })

      // 세 번째 청크
      assertEquals(100, chunks[2].oldStart)
    }
  }
}
