package at.tori.dmr.client

import at.tori.dmr.domain.*
import at.tori.dmr.exception.GitLabApiException
import kotlinx.coroutines.reactor.awaitSingle
import mu.KotlinLogging
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger {}

@Component
class GitLabApiClient(
  private val gitLabWebClient: WebClient
) {
  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 1000, multiplier = 2.0)
  )
  suspend fun getMergeRequest(projectId: Long, mrIid: Long): MergeRequest {
    logger.debug { "머지 리퀘스트 조회 중: project=$projectId, iid=$mrIid" }

    return try {
      gitLabWebClient.get()
        .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}", projectId, mrIid)
        .retrieve()
        .bodyToMono<MergeRequest>()
        .awaitSingle()
        .also { logger.debug { "MR 조회 성공: ${it.title}" } }
    } catch (e: Exception) {
      logger.error(e) { "머지 리퀘스트 조회 실패: project=$projectId, iid=$mrIid" }
      throw GitLabApiException("Failed to fetch merge request", e)
    }
  }

  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 1000, multiplier = 2.0)
  )
  suspend fun getMergeRequestChanges(projectId: Long, mrIid: Long): List<MergeRequestChange> {
    logger.debug { "MR 변경사항 조회 중: project=$projectId, iid=$mrIid" }

    return try {
      val response = gitLabWebClient.get()
        .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/changes", projectId, mrIid)
        .retrieve()
        .bodyToMono<Map<String, Any>>()
        .awaitSingle()

      @Suppress("UNCHECKED_CAST")
      val changes = response["changes"] as? List<Map<String, Any>> ?: emptyList()

      changes.map { change ->
        MergeRequestChange(
          oldPath = change["old_path"] as? String ?: "",
          newPath = change["new_path"] as? String ?: "",
          aMode = change["a_mode"] as? String,
          bMode = change["b_mode"] as? String,
          newFile = change["new_file"] as? Boolean == true,
          renamedFile = change["renamed_file"] as? Boolean == true,
          deletedFile = change["deleted_file"] as? Boolean == true,
          diff = change["diff"] as? String ?: ""
        )
      }.also { logger.debug { "파일 변경사항 조회 성공: ${it.size}개" } }
    } catch (e: Exception) {
      logger.error(e) { "MR 변경사항 조회 실패: project=$projectId, iid=$mrIid" }
      throw GitLabApiException("Failed to fetch MR changes", e)
    }
  }

  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 1000, multiplier = 2.0)
  )
  suspend fun postMergeRequestNote(projectId: Long, mrIid: Long, body: String): Note {
    return try {
      gitLabWebClient.post()
        .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/notes", projectId, mrIid)
        .bodyValue(CreateNoteRequest(body))
        .retrieve()
        .bodyToMono<Note>()
        .awaitSingle()
        .also { logger.info { "MR Note 작성 성공: project=$projectId, iid=$mrIid" } }
    } catch (e: Exception) {
      logger.error(e) { "MR Note 작성 실패: project=$projectId, iid=$mrIid" }
      throw GitLabApiException("Failed to post note to merge request", e)
    }
  }

  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 3,
    backoff = Backoff(delay = 1000, multiplier = 2.0)
  )
  suspend fun createDiscussion(
    projectId: Long,
    mrIid: Long,
    body: String,
    position: DiscussionPosition
  ): Discussion {
    return try {
      gitLabWebClient.post()
        .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/discussions", projectId, mrIid)
        .bodyValue(CreateDiscussionRequest(body, position))
        .retrieve()
        .bodyToMono<Discussion>()
        .awaitSingle()
        .also {
          logger.info {
            "MR Discussion 생성 성공: project=$projectId, iid=$mrIid, file=${position.newPath}"
          }
        }
    } catch (e: Exception) {
      logger.error(e) {
        "MR Discussion 생성 실패: project=$projectId, iid=$mrIid, file=${position.newPath}, line=${position.newLine}"
      }
      throw GitLabApiException("Failed to create discussion on merge request", e)
    }
  }

  @Retryable(
    retryFor = [Exception::class],
    maxAttempts = 2,
    backoff = Backoff(delay = 500)
  )
  suspend fun searchCode(projectId: Long, query: String, ref: String? = null): List<SearchResult> {
    logger.debug { "코드 검색 중: project=$projectId, query=$query" }

    return try {
      val uri = if (ref != null) {
        "/api/v4/projects/{projectId}/search?scope=blobs&search={query}&ref={ref}"
      } else {
        "/api/v4/projects/{projectId}/search?scope=blobs&search={query}"
      }

      val result = if (ref != null) {
        gitLabWebClient.get()
          .uri(uri, projectId, query, ref)
          .retrieve()
          .bodyToMono<List<SearchResult>>()
          .awaitSingle()
      } else {
        gitLabWebClient.get()
          .uri(uri, projectId, query)
          .retrieve()
          .bodyToMono<List<SearchResult>>()
          .awaitSingle()
      }

      result.also { logger.debug { "검색 결과 발견: ${it.size}개 (query: $query)" } }
    } catch (e: Exception) {
      logger.warn { "코드 검색 실패: project=$projectId, query=$query - ${e.message}" }
      emptyList()
    }
  }
}
