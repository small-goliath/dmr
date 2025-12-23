package at.tori.dmr.client

import at.tori.dmr.domain.*
import at.tori.dmr.exception.GitLabApiException
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@DisplayName("gitlab api 테스트")
class GitLabApiClientTest {

  private lateinit var webClient: WebClient
  private lateinit var requestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>
  private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec
  private lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>
  private lateinit var requestBodySpec: WebClient.RequestBodySpec
  private lateinit var responseSpec: WebClient.ResponseSpec
  private lateinit var client: GitLabApiClient

  @BeforeEach
  fun setUp() {
    webClient = mockk()
    requestHeadersUriSpec = mockk()
    requestBodyUriSpec = mockk()
    requestHeadersSpec = mockk()
    requestBodySpec = mockk()
    responseSpec = mockk()
    client = GitLabApiClient(webClient)
  }

  @AfterEach
  fun tearDown() {
    clearAllMocks()
  }

  @Nested
  @DisplayName("getMergeRequest 메서드 테스트")
  inner class GetMergeRequestTest {

    @Test
    @DisplayName("정상적으로 MR 정보를 조회")
    fun `should successfully get merge request`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val expectedMr = MergeRequest(
        id = 1L,
        iid = mrIid,
        title = "Test MR",
        description = "Test Description",
        sourceBranch = "feature/test",
        targetBranch = "main",
        state = "opened",
        mergeStatus = "can_be_merged",
        webUrl = "https://gitlab.com/test",
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
        author = User(
          id = 1L,
          name = "Test User",
          username = "testuser",
          email = "test@example.com"
        )
      )

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}", projectId, mrIid)
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<MergeRequest>() } returns Mono.just(expectedMr)

      val result = client.getMergeRequest(projectId, mrIid)

      assertEquals(expectedMr.id, result.id)
      assertEquals(expectedMr.title, result.title)
      assertEquals(expectedMr.sourceBranch, result.sourceBranch)
      assertEquals(expectedMr.targetBranch, result.targetBranch)

      verify { webClient.get() }
      verify { requestHeadersUriSpec.uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}", projectId, mrIid) }
      verify { requestHeadersSpec.retrieve() }
      verify { responseSpec.bodyToMono<MergeRequest>() }
    }

    @Test
    @DisplayName("API 호출 실패 시 GitLabApiException 발생")
    fun `should throw GitLabApiException when API call fails`() = runTest {
      val projectId = 123L
      val mrIid = 456L

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}", projectId, mrIid)
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<MergeRequest>() } returns Mono.error(
        RuntimeException("Not Found")
      )

      assertThrows(GitLabApiException::class.java) {
        runBlocking {
          client.getMergeRequest(projectId, mrIid)
        }
      }
    }
  }

  @Nested
  @DisplayName("getMergeRequestChanges 메서드 테스트")
  inner class GetMergeRequestChangesTest {

    @Test
    @DisplayName("정상적으로 MR 변경사항을 조회")
    fun `should successfully get merge request changes`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val responseMap = mapOf(
        "changes" to listOf(
          mapOf(
            "old_path" to "old/file.kt",
            "new_path" to "new/file.kt",
            "a_mode" to "100644",
            "b_mode" to "100644",
            "new_file" to false,
            "renamed_file" to true,
            "deleted_file" to false,
            "diff" to "+new line\n-old line"
          )
        )
      )

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/merge_requests/{mrIid}/changes",
          projectId,
          mrIid
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val result = client.getMergeRequestChanges(projectId, mrIid)

      assertEquals(1, result.size)
      assertEquals("old/file.kt", result[0].oldPath)
      assertEquals("new/file.kt", result[0].newPath)
      assertTrue(result[0].renamedFile)
      assertFalse(result[0].newFile)
      assertFalse(result[0].deletedFile)
    }

    @Test
    @DisplayName("변경사항이 없는 경우 빈 리스트 반환")
    fun `should return empty list when no changes`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val responseMap = mapOf<String, Any>("changes" to emptyList<Map<String, Any>>())

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/merge_requests/{mrIid}/changes",
          projectId,
          mrIid
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.just(responseMap)

      val result = client.getMergeRequestChanges(projectId, mrIid)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("API 호출 실패 시 GitLabApiException 발생")
    fun `should throw GitLabApiException when API call fails`() = runTest {
      val projectId = 123L
      val mrIid = 456L

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/merge_requests/{mrIid}/changes",
          projectId,
          mrIid
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Map<String, Any>>() } returns Mono.error(RuntimeException("API Error"))

      assertThrows(GitLabApiException::class.java) {
        runBlocking {
          client.getMergeRequestChanges(projectId, mrIid)
        }
      }
    }
  }

  @Nested
  @DisplayName("postMergeRequestNote 메서드 테스트")
  inner class PostMergeRequestNoteTest {

    @Test
    @DisplayName("정상적으로 MR Note를 작성")
    fun `should successfully post merge request note`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val body = "Test note body"
      val expectedNote = Note(
        id = 1L,
        body = body,
        author = User(
          id = 1L,
          name = "Test User",
          username = "testuser",
          email = "test@example.com"
        ),
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
        noteableId = mrIid,
        noteableType = "MergeRequest"
      )

      every { webClient.post() } returns requestBodyUriSpec
      every {
        requestBodyUriSpec.uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/notes", projectId, mrIid)
      } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<CreateNoteRequest>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Note>() } returns Mono.just(expectedNote)

      val result = client.postMergeRequestNote(projectId, mrIid, body)

      assertEquals(expectedNote.id, result.id)
      assertEquals(expectedNote.body, result.body)

      verify { webClient.post() }
      verify { requestBodySpec.bodyValue(match<CreateNoteRequest> { it.body == body }) }
    }

    @Test
    @DisplayName("API 호출 실패 시 GitLabApiException 발생")
    fun `should throw GitLabApiException when API call fails`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val body = "Test note"

      every { webClient.post() } returns requestBodyUriSpec
      every {
        requestBodyUriSpec.uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/notes", projectId, mrIid)
      } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<CreateNoteRequest>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Note>() } returns Mono.error(RuntimeException("API Error"))

      assertThrows(GitLabApiException::class.java) {
        runBlocking {
          client.postMergeRequestNote(projectId, mrIid, body)
        }
      }
    }
  }

  @Nested
  @DisplayName("createDiscussion 메서드 테스트")
  inner class CreateDiscussionTest {

    @Test
    @DisplayName("정상적으로 MR Discussion을 생성")
    fun `should successfully create discussion`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val body = "Test discussion"
      val position = DiscussionPosition(
        baseSha = "abc123",
        startSha = "def456",
        headSha = "ghi789",
        oldPath = "old.kt",
        newPath = "new.kt",
        oldLine = null,
        newLine = 10
      )
      val expectedDiscussion = Discussion(
        id = "discussion-1",
        notes = listOf(
          Note(
            id = 1L,
            body = body,
            author = User(
              id = 1L,
              name = "Test User",
              username = "testuser",
              email = "test@example.com"
            ),
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            noteableId = mrIid,
            noteableType = "MergeRequest"
          )
        )
      )

      every { webClient.post() } returns requestBodyUriSpec
      every {
        requestBodyUriSpec.uri(
          "/api/v4/projects/{projectId}/merge_requests/{mrIid}/discussions",
          projectId,
          mrIid
        )
      } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<CreateDiscussionRequest>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Discussion>() } returns Mono.just(expectedDiscussion)

      val result = client.createDiscussion(projectId, mrIid, body, position)

      assertEquals(expectedDiscussion.id, result.id)
      assertEquals(1, result.notes.size)
      assertEquals(body, result.notes[0].body)
    }

    @Test
    @DisplayName("API 호출 실패 시 GitLabApiException 발생")
    fun `should throw GitLabApiException when API call fails`() = runTest {
      val projectId = 123L
      val mrIid = 456L
      val body = "Test discussion"
      val position = DiscussionPosition(
        baseSha = "abc123",
        startSha = "def456",
        headSha = "ghi789",
        oldPath = "old.kt",
        newPath = "new.kt",
        oldLine = null,
        newLine = 10
      )

      every { webClient.post() } returns requestBodyUriSpec
      every {
        requestBodyUriSpec.uri(
          "/api/v4/projects/{projectId}/merge_requests/{mrIid}/discussions",
          projectId,
          mrIid
        )
      } returns requestBodySpec
      every { requestBodySpec.bodyValue(any<CreateDiscussionRequest>()) } returns requestBodySpec
      every { requestBodySpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<Discussion>() } returns Mono.error(RuntimeException("API Error"))

      assertThrows(GitLabApiException::class.java) {
        runBlocking {
          client.createDiscussion(projectId, mrIid, body, position)
        }
      }
    }
  }

  @Nested
  @DisplayName("searchCode 메서드 테스트")
  inner class SearchCodeTest {

    @Test
    @DisplayName("정상적으로 코드를 검색 (ref 파라미터 포함)")
    fun `should successfully search code with ref parameter`() = runTest {
      val projectId = 123L
      val query = "test function"
      val ref = "main"
      val expectedResults = listOf(
        SearchResult(
          basename = "Test.kt",
          data = "fun test() {}",
          path = "src/test/kotlin/Test.kt",
          filename = "Test.kt",
          ref = ref,
          startLine = 10,
          projectId = projectId
        )
      )

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/search?scope=blobs&search={query}&ref={ref}",
          projectId,
          query,
          ref
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<List<SearchResult>>() } returns Mono.just(
        expectedResults
      )

      val result = client.searchCode(projectId, query, ref)

      assertEquals(1, result.size)
      assertEquals("Test.kt", result[0].filename)
      assertEquals("src/test/kotlin/Test.kt", result[0].path)
    }

    @Test
    @DisplayName("정상적으로 코드를 검색 (ref 파라미터 없음)")
    fun `should successfully search code without ref parameter`() = runTest {
      val projectId = 123L
      val query = "test function"
      val expectedResults = listOf(
        SearchResult(
          basename = "Test.kt",
          data = "fun test() {}",
          path = "src/test/kotlin/Test.kt",
          filename = "Test.kt",
          ref = "main",
          startLine = 10,
          projectId = projectId
        )
      )

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/search?scope=blobs&search={query}",
          projectId,
          query
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<List<SearchResult>>() } returns Mono.just(
        expectedResults
      )

      val result = client.searchCode(projectId, query, null)

      assertEquals(1, result.size)
      assertEquals("Test.kt", result[0].filename)
    }

    @Test
    @DisplayName("검색 결과가 없는 경우 빈 리스트 반환")
    fun `should return empty list when no search results`() = runTest {
      val projectId = 123L
      val query = "nonexistent"
      val ref = "main"

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/search?scope=blobs&search={query}&ref={ref}",
          projectId,
          query,
          ref
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<List<SearchResult>>() } returns Mono.just(
        emptyList()
      )

      val result = client.searchCode(projectId, query, ref)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("API 호출 실패 시 빈 리스트 반환 (예외를 던지지 않음)")
    fun `should return empty list when API call fails`() = runTest {
      val projectId = 123L
      val query = "test"
      val ref = "main"

      every { webClient.get() } returns requestHeadersUriSpec
      every {
        requestHeadersUriSpec.uri(
          "/api/v4/projects/{projectId}/search?scope=blobs&search={query}&ref={ref}",
          projectId,
          query,
          ref
        )
      } returns requestHeadersSpec
      every { requestHeadersSpec.retrieve() } returns responseSpec
      every { responseSpec.bodyToMono<List<SearchResult>>() } returns Mono.error(
        RuntimeException("API Error")
      )

      val result = client.searchCode(projectId, query, ref)

      assertTrue(result.isEmpty())
    }
  }
}
