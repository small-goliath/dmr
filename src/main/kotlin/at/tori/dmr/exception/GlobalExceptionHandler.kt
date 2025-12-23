package at.tori.dmr.exception

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

private val logger = KotlinLogging.logger {}

data class ErrorResponse(
  val status: String,
  val message: String,
  val errorType: String,
  val timestamp: Long = Instant.now().toEpochMilli()
)

@RestControllerAdvice
class GlobalExceptionHandler {

  @ExceptionHandler(InvalidWebhookException::class)
  fun handleInvalidWebhook(ex: InvalidWebhookException): ResponseEntity<ErrorResponse> {
    logger.warn { "유효하지 않은 웹훅: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = "error",
          message = ex.message ?: "Invalid webhook",
          errorType = "InvalidWebhook"
        )
      )
  }

  @ExceptionHandler(MergeRequestNotFoundException::class)
  fun handleMergeRequestNotFound(ex: MergeRequestNotFoundException): ResponseEntity<ErrorResponse> {
    logger.warn { "머지 리퀘스트를 찾을 수 없음: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = "error",
          message = ex.message ?: "Merge request not found",
          errorType = "MergeRequestNotFound"
        )
      )
  }

  @ExceptionHandler(GitLabApiException::class)
  fun handleGitLabApiException(ex: GitLabApiException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "GitLab API 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.BAD_GATEWAY)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to communicate with GitLab",
          errorType = "GitLabApiError"
        )
      )
  }

  @ExceptionHandler(GoogleChatApiException::class)
  fun handleGoogleChatApiException(ex: GoogleChatApiException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "Google Chat API 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.BAD_GATEWAY)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to send notification to Google Chat",
          errorType = "GoogleChatApiError"
        )
      )
  }

  @ExceptionHandler(AiServiceException::class)
  fun handleAiServiceException(ex: AiServiceException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "AI 서비스 오류: ${ex.message}" }

    val (status, message) = when (ex) {
      is AiRateLimitException -> HttpStatus.TOO_MANY_REQUESTS to "AI service rate limit exceeded"
      is AiModelUnavailableException -> HttpStatus.SERVICE_UNAVAILABLE to "AI service is currently unavailable"
      is AiTimeoutException -> HttpStatus.GATEWAY_TIMEOUT to "AI service request timed out"
      is AiTokenLimitException -> HttpStatus.PAYLOAD_TOO_LARGE to "Request exceeds AI service token limit"
      is AiInvalidResponseException -> HttpStatus.INTERNAL_SERVER_ERROR to "AI service returned invalid response"
    }

    return ResponseEntity
      .status(status)
      .body(
        ErrorResponse(
          status = "error",
          message = message,
          errorType = ex::class.simpleName ?: "AiServiceError"
        )
      )
  }

  @ExceptionHandler(CodeReviewException::class)
  fun handleCodeReviewException(ex: CodeReviewException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "코드 리뷰 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to perform code review",
          errorType = "CodeReviewError"
        )
      )
  }

  @ExceptionHandler(DependencyAnalysisException::class)
  fun handleDependencyAnalysisException(ex: DependencyAnalysisException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "의존성 분석 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to analyze dependencies",
          errorType = "DependencyAnalysisError"
        )
      )
  }

  @ExceptionHandler(InvalidConfigurationException::class)
  fun handleInvalidConfiguration(ex: InvalidConfigurationException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "설정 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = "error",
          message = ex.message ?: "Invalid configuration",
          errorType = "ConfigurationError"
        )
      )
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    val errors = ex.bindingResult.fieldErrors
      .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

    logger.warn { "유효성 검증 오류: $errors" }
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = "error",
          message = "Validation failed: $errors",
          errorType = "ValidationError"
        )
      )
  }

  @ExceptionHandler(DiffParsingException::class)
  fun handleDiffParsingException(ex: DiffParsingException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "Diff 파싱 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.UNPROCESSABLE_ENTITY)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to parse diff content",
          errorType = "DiffParsingError"
        )
      )
  }

  @ExceptionHandler(JsonParsingException::class)
  fun handleJsonParsingException(ex: JsonParsingException): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "JSON 파싱 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.UNPROCESSABLE_ENTITY)
      .body(
        ErrorResponse(
          status = "error",
          message = "Failed to parse JSON response",
          errorType = "JsonParsingError"
        )
      )
  }

  @ExceptionHandler(Exception::class)
  fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
    logger.error(ex) { "예상치 못한 오류: ${ex.message}" }
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = "error",
          message = "An unexpected error occurred",
          errorType = "UnexpectedError"
        )
      )
  }
}
