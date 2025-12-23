package at.tori.dmr.service

import at.tori.dmr.analyzer.CrossFileAnalysisResult
import at.tori.dmr.analyzer.CrossFileImpactAnalyzer
import at.tori.dmr.analyzer.DependencyAnalyzer
import at.tori.dmr.analyzer.DependencyInfo
import at.tori.dmr.analyzer.UsedDependencyInfo
import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.domain.CommentSeverity
import at.tori.dmr.domain.DiffRefs
import at.tori.dmr.domain.DiscussionPosition
import at.tori.dmr.domain.ReviewContext
import at.tori.dmr.parser.JsonResponseParser
import at.tori.dmr.parser.LineComment
import at.tori.dmr.prompt.PromptTemplateService
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class LineByLineReviewService(
  private val gitLabApiClient: GitLabApiClient,
  private val dependencyAnalyzer: DependencyAnalyzer,
  private val crossFileImpactAnalyzer: CrossFileImpactAnalyzer,
  private val chatClient: ChatClient,
  private val lineReviewChatOptions: OpenAiChatOptions,
  private val promptTemplateService: PromptTemplateService,
  private val jsonResponseParser: JsonResponseParser,
  private val chunkedReviewService: ChunkedReviewService
) {
  suspend fun performLineByLineReview(
    projectId: Long,
    mrIid: Long,
    context: ReviewContext
  ): Int {
    logger.info { "라인별 리뷰 시작: ${context.mrTitle}" }

    val chunkedCount = chunkedReviewService.performChunkedReview(projectId, mrIid, context)
    if (chunkedCount > 0) {
      return chunkedCount
    }

    val diffRefs = context.diffRefs
    if (diffRefs == null) {
      logger.warn { "Diff refs를 사용할 수 없어 라인 댓글을 생성할 수 없습니다" }
      return 0
    }

    try {
      logger.info { "Step 1: Forward 의존성 분석 중 (변경된 심볼 → 사용처)..." }
      val dependencies = dependencyAnalyzer.analyze(
        projectId = projectId,
        changedFiles = context.files,
        targetBranch = context.targetBranch
      )
      logger.info { "Forward 의존성 발견: ${dependencies.size}개" }

      logger.info { "Step 2: Backward 의존성 분석 중 (변경된 파일 → 사용하는 외부 심볼)..." }
      val usedDependencies = dependencyAnalyzer.analyzeUsedDependencies(
        projectId = projectId,
        changedFiles = context.files,
        targetBranch = context.targetBranch
      )
      logger.info { "Backward 의존성 발견: ${usedDependencies.size}개 파일" }

      logger.info { "Step 3: 파일간 영향도 분석 중..." }
      val crossFileAnalysis = crossFileImpactAnalyzer.analyze(
        changedFiles = context.files,
        dependencies = dependencies
      )
      logger.info { "파일간 영향도 발견: ${crossFileAnalysis.impacts.size}개" }

      logger.info { "Step 4: 프롬프팅 중..." }
      val prompt = buildEnhancedPrompt(context, dependencies, usedDependencies, crossFileAnalysis)

      logger.info { "Step 5: 리뷰 받는 중..." }
      val aiResponse = callAiForLineReview(prompt)

      logger.info { "Step 6: 리뷰 파싱 중..." }
      val lineComments = parseLineComments(aiResponse)
      logger.info { "AI로부터 라인 댓글 추출: ${lineComments.size}개" }

      logger.info { "Step 7: 깃랩 댓글 및 리뷰 작성 중..." }
      val postedCount = postLineComments(
        projectId = projectId,
        mrIid = mrIid,
        lineComments = lineComments,
        diffRefs = diffRefs,
        context = context
      )

      logger.info { "라인 댓글 작성 성공: ${postedCount}개" }
      return postedCount

    } catch (e: Exception) {
      logger.error(e) { "라인별 리뷰 수행 실패" }
      return 0
    }
  }

  private fun buildEnhancedPrompt(
    context: ReviewContext,
    dependencies: List<DependencyInfo>,
    usedDependencies: List<UsedDependencyInfo>,
    crossFileAnalysis: CrossFileAnalysisResult
  ): String {
    return promptTemplateService.buildLineByLineReviewPrompt(context, dependencies, usedDependencies, crossFileAnalysis)
  }

  private suspend fun callAiForLineReview(prompt: String): String {
    return try {
      chatClient.prompt()
        .system("You are a code review assistant. You MUST respond ONLY with valid JSON. Do not include any explanatory text, comments, or markdown formatting. Just return the raw JSON object.")
        .user(prompt)
        .options(lineReviewChatOptions)
        .call()
        .content() ?: ""
    } catch (e: Exception) {
      logger.error(e) { "라인 리뷰를 위한 AI 호출 실패" }
      ""
    }
  }

  private fun parseLineComments(response: String): List<LineComment> {
    return jsonResponseParser.parseLineComments(response)
  }

  private suspend fun postLineComments(
    projectId: Long,
    mrIid: Long,
    lineComments: List<LineComment>,
    diffRefs: DiffRefs,
    context: ReviewContext
  ): Int = coroutineScope {
    var successCount = 0

    val commentsByFile = lineComments.groupBy { it.filePath }

    logger.info { "${commentsByFile.size}개 파일에 ${lineComments.size}개의 라인 댓글 작성 중" }

    for ((filePath, comments) in commentsByFile) {
      val fileChange = context.files.find { it.filePath == filePath }
      val oldPath = fileChange?.oldPath ?: filePath

      for (comment in comments) {
        try {
          val position = DiscussionPosition(
            baseSha = diffRefs.baseSha,
            startSha = diffRefs.startSha,
            headSha = diffRefs.headSha,
            positionType = "text",
            oldPath = if (fileChange?.newFile == true) null else oldPath,
            newPath = filePath,
            oldLine = null,
            newLine = comment.newLine
          )

          val severityIcon = when (comment.severity) {
            CommentSeverity.CRITICAL -> "🔴"
            CommentSeverity.WARNING -> "🟡"
            CommentSeverity.SUGGESTION -> "💡"
            CommentSeverity.INFO -> "ℹ️"
          }

          val body = "$severityIcon **${comment.severity.name}**: ${comment.comment}"

          gitLabApiClient.createDiscussion(
            projectId = projectId,
            mrIid = mrIid,
            body = body,
            position = position
          )

          successCount++

        } catch (e: Exception) {
          logger.warn(e) {
            "댓글 작성 실패: ${filePath}:${comment.newLine} - ${e.message}"
          }
        }
      }
    }

    logger.info { "${lineComments.size}개 중 ${successCount}개 댓글 작성 성공" }
    successCount
  }
}