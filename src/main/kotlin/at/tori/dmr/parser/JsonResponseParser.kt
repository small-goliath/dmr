package at.tori.dmr.parser

import at.tori.dmr.domain.CommentSeverity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

sealed interface JsonParseResult<out T> {
  data class Success<T>(val data: T) : JsonParseResult<T>
  data class Failure(val error: String, val cause: Throwable? = null) : JsonParseResult<Nothing>
}

data class LineComment(
  val filePath: String,
  val newLine: Int,
  val severity: CommentSeverity,
  val comment: String
)

@Component
class JsonResponseParser(
  private val objectMapper: ObjectMapper
) {
  fun parseLineComments(response: String): List<LineComment> {
    logger.info { "AI 응답 파싱 중: ${response.length}자" }

    return when (val result = extractAndParseJson(response)) {
      is JsonParseResult.Success -> {
        extractLineCommentsFromMap(result.data)
      }

      is JsonParseResult.Failure -> {
        logger.error { "JSON 파싱 실패: ${result.error}" }
        result.cause?.let { logger.error(it) { "원인:" } }
        emptyList()
      }
    }
  }

  private fun extractAndParseJson(response: String): JsonParseResult<Map<String, Any>> {
    return extractJsonString(response)
      .flatMap { parseJsonToMap(it) }
  }

  private fun extractJsonString(response: String): JsonParseResult<String> {
    val jsonInCodeFence = """```json\s*\n(.*?)\n```""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val codeFenceMatch = jsonInCodeFence.find(response)

    return if (codeFenceMatch != null) {
      logger.info { "코드 펜스에서 JSON 발견" }
      JsonParseResult.Success(codeFenceMatch.groupValues[1].trim())
    } else {
      extractJsonFromBraces(response)
    }
  }

  private fun extractJsonFromBraces(response: String): JsonParseResult<String> {
    val jsonStart = response.indexOf("{")
    if (jsonStart < 0) {
      return JsonParseResult.Failure("AI 응답에서 여는 중괄호를 찾을 수 없습니다")
    }

    val jsonEnd = findMatchingClosingBrace(response, jsonStart)
    if (jsonEnd <= jsonStart) {
      return JsonParseResult.Failure("일치하는 닫는 중괄호를 찾을 수 없습니다")
    }

    val jsonStr = response.substring(jsonStart, jsonEnd)

    return JsonParseResult.Success(jsonStr)
  }

  private fun findMatchingClosingBrace(text: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escape = false

    for (i in start until text.length) {
      val char = text[i]

      when {
        escape -> escape = false
        char == '\\' -> escape = true
        char == '"' -> inString = !inString
        !inString && char == '{' -> depth++
        !inString && char == '}' -> {
          depth--
          if (depth == 0) {
            return i + 1
          }
        }
      }
    }

    return -1
  }

  private fun parseJsonToMap(jsonStr: String): JsonParseResult<Map<String, Any>> {
    return try {
      val parsed = objectMapper.readValue<Map<String, Any>>(jsonStr)
      JsonParseResult.Success(parsed)
    } catch (e: com.fasterxml.jackson.core.io.JsonEOFException) {
      logger.warn { "JSON이 잘렸습니다. 수정 시도 중..." }
      attemptJsonFix(jsonStr, e)
    } catch (e: Exception) {
      JsonParseResult.Failure("JSON 파싱 실패", e)
    }
  }

  private fun attemptJsonFix(
    jsonStr: String,
    originalError: Throwable
  ): JsonParseResult<Map<String, Any>> {
    val fixed = fixIncompleteJson(jsonStr)
    return try {
      val parsed = objectMapper.readValue<Map<String, Any>>(fixed)
      JsonParseResult.Success(parsed)
    } catch (e: Exception) {
      JsonParseResult.Failure("수정 후에도 JSON 파싱 실패", originalError)
    }
  }

  private fun fixIncompleteJson(json: String): String {
    var fixed = json.trimEnd()

    val openBraces = fixed.count { it == '{' }
    val closeBraces = fixed.count { it == '}' }
    val openBrackets = fixed.count { it == '[' }
    val closeBrackets = fixed.count { it == ']' }

    logger.debug {
      "JSON 구조: { 여는괄호=$openBraces 닫는괄호=$closeBraces [ 여는대괄호=$openBrackets 닫는대괄호=$closeBrackets"
    }

    if (fixed.endsWith(",")) {
      fixed = fixed.dropLast(1)
    }

    fixed = fixUnterminatedString(fixed)

    repeat(openBrackets - closeBrackets) {
      fixed += "]"
    }

    repeat(openBraces - closeBraces) {
      fixed += "}"
    }

    logger.info { "수정된 JSON (처음 500자): ${fixed.take(500)}" }

    return fixed
  }

  private fun fixUnterminatedString(json: String): String {
    val lastQuote = json.lastIndexOf('"')
    if (lastQuote < 0) return json

    val secondLastQuote = json.substring(0, lastQuote).lastIndexOf('"')
    if (secondLastQuote < 0) return json

    val betweenQuotes = json.substring(secondLastQuote + 1, lastQuote)

    if (betweenQuotes.contains(':') || betweenQuotes.contains('{') || betweenQuotes.contains('[')) {
      return json
    }

    val afterLastQuote = json.substring(lastQuote + 1).trim()
    if (afterLastQuote.isEmpty() || afterLastQuote.all { it in ",}]" }) {
      return json
    }

    return json + "\""
  }

  private fun extractLineCommentsFromMap(parsed: Map<String, Any>): List<LineComment> {
    @Suppress("UNCHECKED_CAST")
    val lineCommentsRaw = parsed["line_comments"] as? List<Map<String, Any>>

    if (lineCommentsRaw == null) {
      logger.warn { "'line_comments' 필드를 찾을 수 없습니다. 키: ${parsed.keys}" }
      return emptyList()
    }

    val lineComments = lineCommentsRaw.mapNotNull { parseLineComment(it) }

    logger.info { "라인 댓글 파싱 성공: ${lineComments.size}개" }
    return lineComments
  }

  private fun parseLineComment(commentMap: Map<String, Any>): LineComment? {
    return try {
      val filePath = commentMap["file_path"] as? String
        ?: return null.also { logger.warn { "file_path 누락" } }

      val newLine = (commentMap["new_line"] as? Number)?.toInt()
        ?: return null.also { logger.warn { "new_line 누락 또는 유효하지 않음" } }

      val severityStr = commentMap["severity"] as? String ?: "info"
      val severity = parseSeverity(severityStr)

      val comment = commentMap["comment"] as? String
        ?: return null.also { logger.warn { "comment 누락" } }

      LineComment(
        filePath = filePath,
        newLine = newLine,
        severity = severity,
        comment = comment
      )
    } catch (e: Exception) {
      logger.warn(e) { "라인 댓글 파싱 실패" }
      null
    }
  }

  private fun parseSeverity(severityStr: String): CommentSeverity {
    return when (severityStr.lowercase()) {
      "critical" -> CommentSeverity.CRITICAL
      "warning" -> CommentSeverity.WARNING
      "suggestion" -> CommentSeverity.SUGGESTION
      else -> CommentSeverity.INFO
    }
  }
}

private inline fun <T, R> JsonParseResult<T>.flatMap(
  transform: (T) -> JsonParseResult<R>
): JsonParseResult<R> {
  return when (this) {
    is JsonParseResult.Success -> transform(data)
    is JsonParseResult.Failure -> JsonParseResult.Failure(error, cause)
  }
}
