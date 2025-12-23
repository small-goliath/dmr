package at.tori.dmr.parser

data class DiffLineMapping(
  val newLine: Int,
  val oldLine: Int?,
  val content: String,
  val type: LineType
)

enum class LineType {
  ADDITION,
  DELETION,
  CONTEXT
}

data class DiffChunk(
  val oldStart: Int,
  val oldCount: Int,
  val newStart: Int,
  val newCount: Int,
  val lines: List<DiffLineMapping>
)

class DiffParser {

  /**
   * diff 문자열을 파싱하여 라인 매핑 추출
   * format: @@ -old_start,old_count +new_start,new_count @@
   */
  fun parse(diff: String): List<DiffChunk> {
    val chunks = mutableListOf<DiffChunk>()
    val lines = diff.lines()

    var i = 0
    while (i < lines.size) {
      val line = lines[i]

      if (line.startsWith("@@")) {
        val chunk = parseChunk(lines, i)
        if (chunk != null) {
          chunks.add(chunk)
          i += chunk.lines.size + 1
        } else {
          i++
        }
      } else {
        i++
      }
    }

    return chunks
  }

  private fun parseChunk(lines: List<String>, startIndex: Int): DiffChunk? {
    val header = lines[startIndex]

    // 헤더 파싱: @@ -10,5 +12,6 @@
    val headerRegex = """@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""".toRegex()
    val match = headerRegex.find(header) ?: return null

    val oldStart = match.groupValues[1].toInt()
    val oldCount = match.groupValues[2].toIntOrNull() ?: 1
    val newStart = match.groupValues[3].toInt()
    val newCount = match.groupValues[4].toIntOrNull() ?: 1
    val chunkLines = mutableListOf<DiffLineMapping>()
    var oldLine = oldStart
    var newLine = newStart
    var idx = startIndex + 1

    while (idx < lines.size && chunkLines.size < (oldCount + newCount)) {
      val line = lines[idx]

      when {
        line.startsWith("@@") -> break
        line.startsWith("+") -> {
          chunkLines.add(
            DiffLineMapping(
              newLine = newLine,
              oldLine = null,
              content = line.substring(1),
              type = LineType.ADDITION
            )
          )
          newLine++
        }

        line.startsWith("-") -> {
          chunkLines.add(
            DiffLineMapping(
              newLine = -1,
              oldLine = oldLine,
              content = line.substring(1),
              type = LineType.DELETION
            )
          )
          oldLine++
        }

        line.startsWith(" ") || line.isEmpty() -> {
          chunkLines.add(
            DiffLineMapping(
              newLine = newLine,
              oldLine = oldLine,
              content = if (line.isNotEmpty()) line.substring(1) else "",
              type = LineType.CONTEXT
            )
          )
          oldLine++
          newLine++
        }

        line.startsWith("\\") -> {
        }

        else -> {
          chunkLines.add(
            DiffLineMapping(
              newLine = newLine,
              oldLine = oldLine,
              content = line,
              type = LineType.CONTEXT
            )
          )
          oldLine++
          newLine++
        }
      }

      idx++
    }

    return DiffChunk(
      oldStart = oldStart,
      oldCount = oldCount,
      newStart = newStart,
      newCount = newCount,
      lines = chunkLines
    )
  }

  /**
   * diff의 변경 사항을 컨텍스트와 함께 반환 (삭제/추가/컨텍스트 포함)
   * AI가 변경 전/후를 모두 볼 수 있도록 하려면 꼭 필요..
   */
  fun getChangedLinesWithContext(diff: String, contextLines: Int = 3): List<DiffLineMapping> {
    val chunks = parse(diff)
    val result = mutableListOf<DiffLineMapping>()

    chunks.forEach { chunk ->
      val changeIndices = chunk.lines.indices.filter {
        chunk.lines[it].type != LineType.CONTEXT
      }

      if (changeIndices.isEmpty()) return@forEach

      changeIndices.forEach { changeIdx ->
        val startIdx = maxOf(0, changeIdx - contextLines)
        val endIdx = minOf(chunk.lines.size - 1, changeIdx + contextLines)

        for (i in startIdx..endIdx) {
          val line = chunk.lines[i]
          if (!result.any { it == line }) {
            result.add(line)
          }
        }
      }
    }

    return result
  }
}
