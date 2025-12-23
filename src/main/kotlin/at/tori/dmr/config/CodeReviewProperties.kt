package at.tori.dmr.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "code-review")
@Validated
data class CodeReviewProperties(
  @field:Min(1, message = "최대 파일 수는 1 이상이어야 합니다")
  val maxFiles: Int = 50,

  @field:Min(1, message = "최대 파일 크기는 1 이상이어야 합니다")
  val maxFileSize: Long = 1048576, // 1MB

  val lineByLineEnabled: Boolean = true,

  @field:Valid
  val chunking: ChunkingProperties = ChunkingProperties(),

  @field:Valid
  val ai: AiProperties = AiProperties(),

  val excludedExtensions: List<String> = emptyList(),
  val excludedPaths: List<String> = emptyList(),

  @field:Valid
  val analysis: AnalysisProperties = AnalysisProperties()
)

data class ChunkingProperties(
  val enabled: Boolean = false,

  @field:Min(1, message = "청크당 파일 수는 1 이상이어야 합니다")
  val filesPerChunk: Int = 5,

  val strategy: ChunkStrategy = ChunkStrategy.FILE
)

enum class ChunkStrategy {
  FILE,  // 파일 단위로 청킹
}

data class AiProperties(
  val temperature: Double = 0.3,

  @field:Min(100, message = "max-tokens는 100 이상이어야 합니다")
  val maxTokens: Int = 12000,

  val topP: Double = 0.95
)

data class AnalysisProperties(
  @field:Valid
  val sideEffects: SideEffectsProperties = SideEffectsProperties()
)

data class SideEffectsProperties(
  val severityThreshold: String = "medium",
  val patterns: Map<String, List<String>> = emptyMap()
)
