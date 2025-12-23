package at.tori.dmr.prompt

import at.tori.dmr.analyzer.CrossFileAnalysisResult
import at.tori.dmr.analyzer.DependencyInfo
import at.tori.dmr.analyzer.UsedDependencyInfo
import at.tori.dmr.analyzer.UsedSymbolType
import at.tori.dmr.domain.ReviewContext
import at.tori.dmr.parser.DiffParser
import at.tori.dmr.parser.LineType
import org.springframework.stereotype.Service

@Service
class PromptTemplateService(
  private val diffParser: DiffParser = DiffParser()
) {
  fun buildLineByLineReviewPrompt(
    context: ReviewContext,
    dependencies: List<DependencyInfo>,
    usedDependencies: List<UsedDependencyInfo>,
    crossFileAnalysis: CrossFileAnalysisResult
  ): String = buildString {
    appendHeader()
    appendMergeRequestInfo(context)
    appendDependencyAnalysis(dependencies)
    appendUsedDependencyAnalysis(usedDependencies)
    appendCrossFileAnalysis(crossFileAnalysis)
    appendChangedFiles(context)
    appendResponseFormat()
    appendReviewGuidelines()
  }

  private fun StringBuilder.appendHeader() {
    appendLine("# 코드 리뷰 요청")
    appendLine()
    appendLine("**CRITICAL: 당신은 JSON만 반환해야 합니다. 설명, 주석, 마크다운 등 다른 텍스트를 포함하지 마세요!**")
    appendLine()
    appendLine("당신은 전문 코드 리뷰어입니다. 다음 머지 리퀘스트를 상세히 리뷰해주세요.")
    appendLine()
    appendLine("**중요: 모든 댓글 내용을 한국어로 작성해주세요.**")
    appendLine()
  }

  private fun StringBuilder.appendMergeRequestInfo(context: ReviewContext) {
    appendLine("## 머지 리퀘스트 정보")
    appendLine(context.summary)
    appendLine()

    context.mrDescription?.takeIf { it.isNotBlank() }?.let { description ->
      appendLine("## MR 설명")
      appendLine(description)
      appendLine()
    }
  }

  private fun StringBuilder.appendDependencyAnalysis(dependencies: List<DependencyInfo>) {
    if (dependencies.isEmpty()) return

    appendLine("## 의존성 분석 결과 (실제 코드 포함)")
    appendLine()

    dependencies.take(MAX_DEPENDENCIES_TO_SHOW).forEach { dep ->
      appendLine("### ${dep.symbol.type} `${dep.symbol.name}` (변경됨: ${dep.symbol.filePath})")
      appendLine()
      appendLine("**영향받는 파일들:**")
      appendLine()

      val usagesByFile = dep.usages.groupBy { it.path }

      usagesByFile.entries.take(MAX_AFFECTED_FILES_PER_DEPENDENCY).forEach { (file, usages) ->
        appendLine("#### $file")
        usages.take(MAX_USAGES_PER_FILE).forEach { usage ->
          appendLine("**라인 ${usage.startLine}:**")
          appendLine("```kotlin")
          appendLine(usage.data.trim())
          appendLine("```")
          appendLine()
        }
        if (usages.size > MAX_USAGES_PER_FILE) {
          appendLine("... (${usages.size - MAX_USAGES_PER_FILE}개 더)")
          appendLine()
        }
      }

      if (usagesByFile.size > MAX_AFFECTED_FILES_PER_DEPENDENCY) {
        appendLine("... 그 외 ${usagesByFile.size - MAX_AFFECTED_FILES_PER_DEPENDENCY}개 파일")
      }

      appendLine("---")
      appendLine()
    }

    appendLine("**AI에게:** 위 코드를 분석하여, 변경된 심볼이 각 사용처에서 올바르게 업데이트되었는지 확인하고, 문제가 있는 구체적인 라인을 지적하세요.")
    appendLine()
  }

  private fun StringBuilder.appendUsedDependencyAnalysis(usedDependencies: List<UsedDependencyInfo>) {
    if (usedDependencies.isEmpty()) return

    appendLine("## 역방향 의존성 분석 (변경된 파일이 사용하는 외부 심볼)")
    appendLine()
    appendLine("변경된 코드가 어떤 외부 심볼(함수, 클래스 등)을 사용하는지 분석했습니다.")
    appendLine()

    usedDependencies.take(MAX_USED_DEPENDENCIES_TO_SHOW).forEach { usedDep ->
      appendLine("### ${usedDep.sourceFile}")
      appendLine()

      if (usedDep.externalFiles.isNotEmpty()) {
        appendLine("**의존하는 외부 파일:**")
        usedDep.externalFiles.take(5).forEach { file ->
          appendLine("- $file")
        }
        if (usedDep.externalFiles.size > 5) {
          appendLine("- ... 그 외 ${usedDep.externalFiles.size - 5}개 파일")
        }
        appendLine()
      }

      val symbolsByType = usedDep.usedSymbols.groupBy { it.type }

      symbolsByType.forEach { (type, symbols) ->
        val typeLabel = when (type) {
          UsedSymbolType.FUNCTION_CALL -> "함수 호출"
          UsedSymbolType.CLASS_USAGE -> "클래스 사용"
          UsedSymbolType.PROPERTY_ACCESS -> "프로퍼티 접근"
          UsedSymbolType.IMPORT -> "Import"
        }

        appendLine("**$typeLabel:**")
        symbols.take(10).forEach { symbol ->
          val definitionInfo = symbol.definitionFile?.let { " (정의: $it)" } ?: ""
          appendLine("- `${symbol.name}`$definitionInfo")
        }
        if (symbols.size > 10) {
          appendLine("- ... 그 외 ${symbols.size - 10}개")
        }
        appendLine()
      }

      appendLine("---")
      appendLine()
    }

    appendLine("변경된 코드가 사용하는 외부 심볼이 올바르게 사용되고 있는지, 호환성 문제나 사이드 이펙트가 없는지 확인하세요.")
    appendLine()
  }

  private fun StringBuilder.appendCrossFileAnalysis(crossFileAnalysis: CrossFileAnalysisResult) {
    appendLine("## 파일 간 영향 분석")
    appendLine(crossFileAnalysis.summary)
    appendLine()

    if (crossFileAnalysis.hasBreakingChanges) {
      appendLine("**Breaking Changes 감지됨!**")
      appendLine()
    }
  }

  private fun StringBuilder.appendChangedFiles(context: ReviewContext) {
    appendLine("## 변경된 파일들")
    appendLine()

    context.files.take(MAX_FILES_TO_SHOW).forEach { file ->
      appendLine("### 파일: ${file.filePath}")
      if (file.newFile) appendLine("*새 파일*")
      if (file.renamedFile) appendLine("*이름 변경: ${file.oldPath}*")
      if (file.deletedFile) appendLine("*삭제된 파일*")
      appendLine()

      val changedLines = diffParser.getChangedLinesWithContext(file.diff, contextLines = 2)
      if (changedLines.isNotEmpty()) {
        appendLine("변경 사항 (- 삭제, + 추가):")
        appendLine("```diff")

        var lineCount = 0
        changedLines.forEach { line ->
          if (lineCount >= MAX_LINES_PER_FILE) return@forEach

          val lineContent = line.content
          when (line.type) {
            LineType.DELETION -> {
              appendLine("-${line.oldLine}: $lineContent")
              lineCount++
            }

            LineType.ADDITION -> {
              appendLine("+${line.newLine}: $lineContent")
              lineCount++
            }

            LineType.CONTEXT -> {
              appendLine(" ${line.newLine}: $lineContent")
            }
          }
        }

        if (changedLines.size > MAX_LINES_PER_FILE) {
          appendLine("... (더 많은 변경 사항 있음)")
        }
        appendLine("```")
      }
      appendLine()
    }
  }

  private fun StringBuilder.appendResponseFormat() {
    appendLine("## 응답 형식 (필수!)")
    appendLine()
    appendLine("**다음 JSON 형식만 반환하세요. 다른 텍스트는 절대 포함하지 마세요:**")
    appendLine()
    appendLine(RESPONSE_FORMAT_EXAMPLE)
    appendLine()
    appendLine("**severity는 다음 중 하나:** critical, warning, suggestion, info")
    appendLine()
    appendLine("**중요:** ```json이나 다른 마크다운 포맷을 사용하지 마세요. 순수 JSON 객체만 반환하세요!")
    appendLine()
    appendLine("**주의사항:**")
    appendLine("- file_path는 정확히 위에 나온 파일명과 일치해야 합니다")
    appendLine("- new_line은 변경/추가된 라인 번호여야 합니다 (+ 표시된 라인 번호)")
    appendLine("- diff에서 - 표시는 삭제된 라인, + 표시는 추가된 라인, 공백은 컨텍스트입니다")
    appendLine()
  }

  private fun StringBuilder.appendReviewGuidelines() {
    appendLine("**리뷰 방식:**")
    appendLine("1. **변경 전/후 비교**: diff에서 - (삭제)와 + (추가)를 비교하여 무엇이 어떻게 변경되었는지 파악")
    appendLine("   - 예: 함수 시그니처 변경, 로직 수정, 리팩토링 등")
    appendLine("2. **변경된 코드 자체 리뷰**: 변경된 코드의 버그, 성능, 보안 문제 지적")
    appendLine("3. **의존성 분석 결과 활용**: 위에 제공된 실제 코드 스니펫을 보고 영향 범위 확인")
    appendLine("   - 예: \"UserService.kt:45 에서 `getUser(id)` 호출 시 새로운 includeDeleted 파라미터가 전달되지 않았습니다.\"")
    appendLine("   - 예: \"OrderService.kt:128 에서 여전히 이전 시그니처로 호출하고 있어 컴파일 오류가 발생할 수 있습니다.\"")
    appendLine("4. **구체적인 파일명과 라인 번호 언급**: \"확인하세요\"가 아니라 \"XXX.kt:123에서 YYY 문제\"")
    appendLine()
    appendLine("- 모든 내용을 한국어로 작성하세요")
    appendLine("- JSON 형식을 정확히 따르고 완성된 JSON을 반환하세요")
  }

  companion object {
    private const val MAX_DEPENDENCIES_TO_SHOW = 10
    private const val MAX_USED_DEPENDENCIES_TO_SHOW = 10
    private const val MAX_AFFECTED_FILES_PER_DEPENDENCY = 5
    private const val MAX_USAGES_PER_FILE = 3
    private const val MAX_FILES_TO_SHOW = 15
    private const val MAX_LINES_PER_FILE = 50

    private val RESPONSE_FORMAT_EXAMPLE = """
{
  "line_comments": [
    {
      "file_path": "src/main/kotlin/Example.kt",
      "new_line": 42,
      "severity": "warning",
      "comment": "한국어로 작성된 구체적인 리뷰 내용"
    }
  ],
  "summary": "전체 요약"
}
        """.trimIndent()
  }
}
