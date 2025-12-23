package at.tori.dmr.service

import at.tori.dmr.client.GitLabApiClient
import at.tori.dmr.config.CodeReviewProperties
import at.tori.dmr.domain.FileChange
import at.tori.dmr.domain.MergeRequest
import at.tori.dmr.domain.MergeRequestChange
import at.tori.dmr.domain.ReviewContext
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ContextBuilderService(
  private val gitLabApiClient: GitLabApiClient,
  private val codeReviewProperties: CodeReviewProperties
) {
  suspend fun buildContext(projectId: Long, mr: MergeRequest): ReviewContext {
    logger.debug { "리뷰 컨텍스트 구성 중: ${mr.title}" }

    val changes = gitLabApiClient.getMergeRequestChanges(projectId, mr.iid)
    logger.debug { "파일 변경사항 조회: ${changes.size}개" }

    val fileChanges = changes
      .map { it.toFileChange() }
      .filter { shouldIncludeFile(it) }
      .take(codeReviewProperties.maxFiles)

    logger.debug { "관련 파일 필터링 완료: ${fileChanges.size}개" }

    val stats = calculateStats(fileChanges)

    return ReviewContext(
      projectName = mr.webUrl.substringAfter("://").substringBefore("/merge_requests"),
      mrTitle = mr.title,
      mrDescription = mr.description,
      sourceBranch = mr.sourceBranch,
      targetBranch = mr.targetBranch,
      author = mr.author.username,
      files = fileChanges,
      totalFiles = changes.size,
      totalAdditions = stats.first,
      totalDeletions = stats.second,
      diffRefs = mr.diffRefs
    )
  }

  private fun shouldIncludeFile(file: FileChange): Boolean {
    if (file.deletedFile) {
      logger.debug { "삭제된 파일 스킵: ${file.filePath}" }
      return false
    }

    if (file.diff.isBlank()) {
      logger.debug { "diff가 없는 파일 스킵: ${file.filePath}" }
      return false
    }

    if (file.extension in codeReviewProperties.excludedExtensions) {
      logger.debug { "제외된 확장자 스킵: ${file.filePath} (.${file.extension})" }
      return false
    }

    if (codeReviewProperties.excludedPaths.any { file.filePath.contains(it) }) {
      logger.debug { "제외된 경로 스킵: ${file.filePath}" }
      return false
    }

    if (file.fileSize > codeReviewProperties.maxFileSize) {
      logger.debug { "큰 파일 스킵: ${file.filePath} (${file.fileSize} bytes)" }
      return false
    }

    return true
  }

  private fun calculateStats(files: List<FileChange>): Pair<Int, Int> {
    var additions = 0
    var deletions = 0

    files.forEach { file ->
      file.diff.lines().forEach { line ->
        when {
          line.startsWith("+") && !line.startsWith("+++") -> additions++
          line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
      }
    }

    return additions to deletions
  }
}

private fun MergeRequestChange.toFileChange(): FileChange {
  val path = if (newFile || !deletedFile) newPath else oldPath
  val extension = path.substringAfterLast('.', "")

  return FileChange(
    filePath = newPath,
    oldPath = oldPath,
    newFile = newFile,
    deletedFile = deletedFile,
    renamedFile = renamedFile,
    diff = diff,
    extension = extension,
    fileSize = diff.length.toLong()
  )
}
