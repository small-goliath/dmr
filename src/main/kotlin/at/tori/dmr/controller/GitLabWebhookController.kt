package at.tori.dmr.controller

import at.tori.dmr.config.GitLabProperties
import at.tori.dmr.domain.MergeRequestEvent
import at.tori.dmr.exception.InvalidWebhookException
import at.tori.dmr.service.CodeReviewService
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

data class WebhookResponse(
  val status: String,
  val message: String
)

@RestController
@RequestMapping("/api/webhook")
class GitLabWebhookController(
  private val codeReviewService: CodeReviewService,
  private val gitLabProperties: GitLabProperties
) {
  @PostMapping("/gitlab")
  fun handleGitLabWebhook(
    @Valid @RequestBody event: MergeRequestEvent,
    @RequestHeader("X-Gitlab-Event", required = false) gitlabEvent: String?,
    @RequestHeader("X-Gitlab-Token", required = false) gitlabToken: String?
  ): ResponseEntity<WebhookResponse> {
    logger.info { "${event.user.name}(${event.user.username})의 $gitlabEvent 이벤트 수신: project: ${event.project.pathWithNamespace}, mr.action=${event.objectAttributes.action}" }

    validateWebhookToken(gitlabToken)
    validateEventType(gitlabEvent, event.objectKind)
    codeReviewService.processWebhookEvent(event)

    return ResponseEntity.ok(
      WebhookResponse(
        status = "accepted",
        message = "Webhook received and processing started"
      )
    )
  }

  private fun validateWebhookToken(gitlabToken: String?) {
    if (gitLabProperties.webhook.secretToken.isNotBlank()) {
      if (gitlabToken != gitLabProperties.webhook.secretToken) {
        logger.warn { "유효하지 않은 웹훅 토큰 수신" }
        throw InvalidWebhookException("Invalid webhook token")
      }
    }
  }

  private fun validateEventType(gitlabEvent: String?, objectKind: String) {
    if (gitlabEvent != MERGE_REQUEST_HOOK) {
      logger.debug { "MR이 아닌 웹훅 이벤트 스킵: $gitlabEvent" }
      throw InvalidWebhookException("Not a merge request event")
    }

    if (objectKind != MERGE_REQUEST_KIND) {
      logger.debug { "MR이 아닌 object kind 스킵: $objectKind" }
      throw InvalidWebhookException("Not a merge request event")
    }
  }

  companion object {
    private const val MERGE_REQUEST_HOOK = "Merge Request Hook"
    private const val MERGE_REQUEST_KIND = "merge_request"
  }
}
