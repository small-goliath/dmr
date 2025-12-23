package at.tori.dmr.service

import at.tori.dmr.analyzer.CrossFileAnalysisResult
import at.tori.dmr.analyzer.CrossFileImpactAnalyzer
import at.tori.dmr.analyzer.DependencyAnalyzer
import at.tori.dmr.analyzer.DependencyInfo
import at.tori.dmr.analyzer.UsedDependencyInfo
import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.config.CodeReviewProperties
import at.tori.dmr.domain.*
import at.tori.dmr.parser.JsonResponseParser
import at.tori.dmr.parser.LineComment
import at.tori.dmr.prompt.PromptTemplateService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Service
import kotlin.collections.filter

private val logger = KotlinLogging.logger {}

@Service
class ChunkedReviewService(
  private val gitLabApiClient: GitLabApiClient,
  private val dependencyAnalyzer: DependencyAnalyzer,
  private val crossFileImpactAnalyzer: CrossFileImpactAnalyzer,
  private val chatClient: ChatClient,
  private val lineReviewChatOptions: OpenAiChatOptions,
  private val promptTemplateService: PromptTemplateService,
  private val jsonResponseParser: JsonResponseParser,
  private val codeReviewProperties: CodeReviewProperties
) {
  suspend fun performChunkedReview(
    projectId: Long,
    mrIid: Long,
    context: ReviewContext
  ): Int {
    val chunking = codeReviewProperties.chunking

    if (!chunking.enabled || context.files.size <= chunking.filesPerChunk) {
      logger.info { "청킹 비활성화 또는 파일 수가 적어 일반 리뷰 수행" }
      return 0
    }
    logger.info { "청킹 리뷰 시작: ${context.files.size}개 파일을 ${chunking.filesPerChunk}개씩 분할" }

    val diffRefs = context.diffRefs
    if (diffRefs == null) {
      logger.warn { "Diff refs를 사용할 수 없어 라인 댓글을 생성할 수 없습니다" }
      return 0
    }

    try {
      // Step 1: Forward 의존성 분석
      logger.info { "Step 1: Forward 의존성 분석 중..." }
      val allDependencies = dependencyAnalyzer.analyze(
        projectId = projectId,
        changedFiles = context.files,
        targetBranch = context.targetBranch
      )
      logger.info { "Forward 의존성 발견: ${allDependencies.size}개" }

      // Step 2: Backward 의존성 분석
      logger.info { "Step 2: Backward 의존성 분석 중..." }
      val allUsedDependencies = dependencyAnalyzer.analyzeUsedDependencies(
        projectId = projectId,
        changedFiles = context.files,
        targetBranch = context.targetBranch
      )
      logger.info { "Backward 의존성 발견: ${allUsedDependencies.size}개 파일" }

      // Step 3: 전체 파일간 영향도 분석
      logger.info { "Step 3: 전체 파일간 영향도 분석 중..." }
      val globalCrossFileAnalysis = crossFileImpactAnalyzer.analyze(
        changedFiles = context.files,
        dependencies = allDependencies
      )
      logger.info { "전체 파일간 영향도: ${globalCrossFileAnalysis.impacts.size}개" }

      // Step 4: 파일을 청크로 분할
      val chunks = chunkFiles(context.files, chunking.filesPerChunk)
      logger.info { "${context.files.size}개 파일을 ${chunks.size}개 청크로 분할" }

      // Step 5: 각 청크별로 병렬 리뷰 수행
      logger.info { "Step 5: ${chunks.size}개 청크 병렬 리뷰 시작..." }
      val allComments = coroutineScope {
        chunks.mapIndexed { index, chunk ->
          async {
            reviewChunk(
              chunkIndex = index + 1,
              totalChunks = chunks.size,
              files = chunk,
              allDependencies = allDependencies,
              allUsedDependencies = allUsedDependencies,
              globalCrossFileAnalysis = globalCrossFileAnalysis,
              context = context
            )
          }
        }.awaitAll().flatten()
      }

      logger.info { "전체 청크 리뷰 완료: 총 ${allComments.size}개 댓글 수집" }

      // Step 6: 댓글 작성
      logger.info { "Step 6: GitLab 댓글 작성 중..." }
      val postedCount = postLineComments(
        projectId = projectId,
        mrIid = mrIid,
        lineComments = allComments,
        diffRefs = diffRefs,
        context = context
      )

      logger.info { "청킹 리뷰 완료: ${postedCount}개 댓글 작성" }
      return postedCount

    } catch (e: Exception) {
      logger.error(e) { "청킹 리뷰 실패" }
      return 0
    }
  }

  private fun chunkFiles(files: List<FileChange>, chunkSize: Int): List<List<FileChange>> {
    return files.chunked(chunkSize)
  }

  private suspend fun reviewChunk(
    chunkIndex: Int,
    totalChunks: Int,
    files: List<FileChange>,
    allDependencies: List<DependencyInfo>,
    allUsedDependencies: List<UsedDependencyInfo>,
    globalCrossFileAnalysis: CrossFileAnalysisResult,
    context: ReviewContext
  ): List<LineComment> {
    logger.info { "청크 [$chunkIndex/$totalChunks] 리뷰 시작: ${files.size}개 파일" }

    try {
      // 이 청크에 관련된 의존성만 필터링
      val chunkFilePaths = files.map { it.filePath }.toSet()
      val relevantDependencies = allDependencies.filter { dep ->
        dep.symbol.filePath in chunkFilePaths ||
            dep.affectedFiles.any { it in chunkFilePaths }
      }

      val relevantUsedDependencies = allUsedDependencies.filter { usedDep ->
        usedDep.sourceFile in chunkFilePaths
      }

      // 청크별 컨텍스트 생성
      val chunkContext = context.copy(files = files)

      // 프롬프트 생성
      val prompt = promptTemplateService.buildLineByLineReviewPrompt(
        context = chunkContext,
        dependencies = relevantDependencies,
        usedDependencies = relevantUsedDependencies,
        crossFileAnalysis = globalCrossFileAnalysis
      )

      // AI 호출
      val aiResponse = callAiForLineReview(prompt)

      // 응답 파싱
      val comments = jsonResponseParser.parseLineComments(aiResponse)
      logger.info { "청크 [$chunkIndex/$totalChunks] 완료: ${comments.size}개 댓글" }

      return comments

    } catch (e: Exception) {
      logger.error(e) { "청크 [$chunkIndex/$totalChunks] 리뷰 실패" }
      return emptyList()
    }
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
      logger.error(e) { "AI 호출 실패" }
      ""
    }
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
