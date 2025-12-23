package at.tori.dmr.analyzer

import at.tori.dmr.domain.FileChange
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

enum class ImpactLevel {
  CRITICAL,
  HIGH,
  MEDIUM,
  LOW
}

data class CrossFileImpact(
  val changedFile: String,
  val affectedFiles: Set<String>,
  val dependencies: List<at.tori.dmr.analyzer.DependencyInfo>,
  val impactLevel: at.tori.dmr.analyzer.ImpactLevel,
  val breakingChanges: List<String>,
  val description: String
)

data class CrossFileAnalysisResult(
  val impacts: List<at.tori.dmr.analyzer.CrossFileImpact>,
  val totalAffectedFiles: Set<String>,
  val hasCriticalImpact: Boolean,
  val hasBreakingChanges: Boolean,
  val summary: String
)

@Service
class CrossFileImpactAnalyzer {

  /**
   * 변경된 각 파일별로 의존성을 가지고있는 모든 파일에 대해서 분석
   *
   * @param changedFiles 변경된 파일 목록
   * @param dependencies 의존성 분석 결과
   * @return 파일 간 분석 결과
   */
  fun analyze(
    changedFiles: List<at.tori.dmr.domain.FileChange>,
    dependencies: List<at.tori.dmr.analyzer.DependencyInfo>
  ): at.tori.dmr.analyzer.CrossFileAnalysisResult {
    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "파일 간 영향도 분석 중: ${changedFiles.size}개 파일" }

    val impacts = mutableListOf<at.tori.dmr.analyzer.CrossFileImpact>()
    val allAffectedFiles = mutableSetOf<String>()

    // 파일별로 의존성 그룹핑
    val dependenciesByFile = dependencies.groupBy { it.symbol.filePath }

    for (file in changedFiles) {
      if (file.deletedFile) {
        // 삭제된 파일은 의존성이 있는 경우 최대 영향도
        val fileDeps = dependenciesByFile[file.filePath] ?: emptyList()
        if (fileDeps.isNotEmpty()) {
          val affectedFiles = fileDeps.flatMap { it.affectedFiles }.toSet()
          impacts.add(
            _root_ide_package_.at.tori.dmr.analyzer.CrossFileImpact(
              changedFile = file.filePath,
              affectedFiles = affectedFiles,
              dependencies = fileDeps,
              impactLevel = _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.CRITICAL,
              breakingChanges = listOf("File deleted with ${fileDeps.size} dependent symbols"),
              description = "파일이 삭제되었으나 ${affectedFiles.size}개의 다른 파일에서 사용 중입니다."
            )
          )
          allAffectedFiles.addAll(affectedFiles)
        }
        continue
      }

      val fileDeps = dependenciesByFile[file.filePath] ?: emptyList()

      if (fileDeps.isEmpty()) {
        impacts.add(
          _root_ide_package_.at.tori.dmr.analyzer.CrossFileImpact(
            changedFile = file.filePath,
            affectedFiles = emptySet(),
            dependencies = emptyList(),
            impactLevel = _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.LOW,
            breakingChanges = emptyList(),
            description = "다른 파일에 영향을 주지 않는 독립적인 변경입니다."
          )
        )
        continue
      }

      val affectedFiles = fileDeps.flatMap { it.affectedFiles }.toSet()
      val totalUsages = fileDeps.sumOf { it.usageCount }
      val publicSymbols = fileDeps.filter { it.symbol.isPublic }

      val impactLevel = determineImpactLevel(
        affectedFileCount = affectedFiles.size,
        usageCount = totalUsages,
        hasPublicSymbols = publicSymbols.isNotEmpty()
      )

      val breakingChanges = mutableListOf<String>()
      for (dep in fileDeps) {
        if (dep.hasExternalUsages && dep.symbol.isPublic) {
          val changeType = analyzeChangeType(file.diff, dep.symbol.name)
          if (changeType == _root_ide_package_.at.tori.dmr.analyzer.ChangeType.SIGNATURE_MODIFIED || changeType == _root_ide_package_.at.tori.dmr.analyzer.ChangeType.DELETED) {
            breakingChanges.add(
              "${dep.symbol.type} '${dep.symbol.name}' ${changeType.description}"
            )
          }
        }
      }

      val description = buildImpactDescription(
        affectedFiles.size,
        totalUsages,
        publicSymbols.size,
        breakingChanges.isNotEmpty()
      )

      impacts.add(
        _root_ide_package_.at.tori.dmr.analyzer.CrossFileImpact(
          changedFile = file.filePath,
          affectedFiles = affectedFiles,
          dependencies = fileDeps,
          impactLevel = impactLevel,
          breakingChanges = breakingChanges,
          description = description
        )
      )

      allAffectedFiles.addAll(affectedFiles)
    }

    val hasCritical = impacts.any { it.impactLevel == _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.CRITICAL }
    val hasBreaking = impacts.any { it.breakingChanges.isNotEmpty() }

    val summary = buildSummary(impacts, allAffectedFiles.size)

    _root_ide_package_.at.tori.dmr.analyzer.logger.info { "파일 간 영향도 분석 완료: ${impacts.size}개 영향, ${allAffectedFiles.size}개 파일 영향받음" }

    return _root_ide_package_.at.tori.dmr.analyzer.CrossFileAnalysisResult(
      impacts = impacts,
      totalAffectedFiles = allAffectedFiles,
      hasCriticalImpact = hasCritical,
      hasBreakingChanges = hasBreaking,
      summary = summary
    )
  }

  private fun determineImpactLevel(
    affectedFileCount: Int,
    usageCount: Int,
    hasPublicSymbols: Boolean
  ): at.tori.dmr.analyzer.ImpactLevel {
    return when {
      affectedFileCount >= 10 || usageCount >= 20 -> _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.CRITICAL
      affectedFileCount >= 5 || (usageCount >= 10 && hasPublicSymbols) -> _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.HIGH
      affectedFileCount >= 2 || usageCount >= 5 -> _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.MEDIUM
      else -> _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.LOW
    }
  }

  private fun analyzeChangeType(diff: String, symbolName: String): at.tori.dmr.analyzer.ChangeType {
    val lines = diff.lines()

    var hasAddition = false
    var hasDeletion = false

    for (line in lines) {
      if (line.contains(symbolName)) {
        when {
          line.startsWith("+") -> hasAddition = true
          line.startsWith("-") -> hasDeletion = true
        }
      }
    }

    return when {
      hasDeletion && !hasAddition -> _root_ide_package_.at.tori.dmr.analyzer.ChangeType.DELETED
      hasDeletion && hasAddition -> _root_ide_package_.at.tori.dmr.analyzer.ChangeType.SIGNATURE_MODIFIED
      hasAddition && !hasDeletion -> _root_ide_package_.at.tori.dmr.analyzer.ChangeType.ADDED
      else -> _root_ide_package_.at.tori.dmr.analyzer.ChangeType.UNCHANGED
    }
  }

  private fun buildImpactDescription(
    affectedFileCount: Int,
    usageCount: Int,
    publicSymbolCount: Int,
    hasBreakingChanges: Boolean
  ): String {
    return buildString {
      if (affectedFileCount > 0) {
        append("이 변경은 ${affectedFileCount}개의 다른 파일")
        if (usageCount > affectedFileCount) {
          append("(총 ${usageCount}회 사용)")
        }
        append("에 영향을 줍니다. ")
      }

      if (publicSymbolCount > 0) {
        append("${publicSymbolCount}개의 public 심볼이 변경되었습니다. ")
      }

      if (hasBreakingChanges) {
        append("Breaking change가 감지되었습니다!")
      }
    }.trim()
  }

  private fun buildSummary(impacts: List<at.tori.dmr.analyzer.CrossFileImpact>, totalAffectedFiles: Int): String {
    val criticalCount = impacts.count { it.impactLevel == _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.CRITICAL }
    val highCount = impacts.count { it.impactLevel == _root_ide_package_.at.tori.dmr.analyzer.ImpactLevel.HIGH }
    val breakingChanges = impacts.flatMap { it.breakingChanges }

    return buildString {
      appendLine("## 파일 간 영향 분석")
      appendLine()
      appendLine("총 ${impacts.size}개 파일 변경, ${totalAffectedFiles}개 파일에 영향")
      appendLine()

      if (criticalCount > 0) {
        appendLine("- Critical 영향: ${criticalCount}건")
      }
      if (highCount > 0) {
        appendLine("- High 영향: ${highCount}건")
      }

      if (breakingChanges.isNotEmpty()) {
        appendLine()
        appendLine("### Breaking Changes 감지:")
        breakingChanges.forEach {
          appendLine("- $it")
        }
      }
    }
  }
}

enum class ChangeType(val description: String) {
  ADDED("추가됨"),
  DELETED("삭제됨"),
  SIGNATURE_MODIFIED("시그니처 변경됨"),
  UNCHANGED("변경 없음")
}