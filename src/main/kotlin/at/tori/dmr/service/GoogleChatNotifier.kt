package at.tori.dmr.service

import at.tori.dmr.client.GoogleChatClient
import at.tori.dmr.domain.*
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GoogleChatNotifier(
  private val googleChatClient: GoogleChatClient
) {
  suspend fun notifyLineByLineReview(
    context: ReviewContext,
    lineCommentCount: Int,
    mrUrl: String
  ) {
    if (!googleChatClient.isConfigured()) {
      logger.debug { "Google Chat이 설정되지 않아 알림을 건너뜁니다" }
      return
    }

    logger.info { "라인별 리뷰 알림을 Google Chat으로 전송 중: ${context.mrTitle}" }

    try {
      val message = createLineByLineReviewMessage(context, lineCommentCount, mrUrl)
      val success = googleChatClient.sendMessage(message)

      if (success) {
        logger.info { "라인별 리뷰 알림을 Google Chat으로 전송 성공" }
      } else {
        logger.warn { "라인별 리뷰 알림을 Google Chat으로 전송 실패" }
      }
    } catch (e: Exception) {
      logger.error(e) { "라인별 리뷰 알림을 Google Chat으로 전송하는 중 오류 발생" }
    }
  }

  private fun createLineByLineReviewMessage(
    context: ReviewContext,
    lineCommentCount: Int,
    mrUrl: String
  ): GoogleChatMessage {
    val header = Header(
      title = "DMR 코드 리뷰 완료",
      subtitle = context.projectName
    )

    val sections = mutableListOf<Section>()

    sections.add(
      Section(
        header = "MR 상세",
        widgets = listOf(
          KeyValue(
            KeyValueContent(
              topLabel = "제목",
              content = context.mrTitle
            )
          ),
          KeyValue(
            KeyValueContent(
              topLabel = "요청자",
              content = context.author
            )
          ),
          KeyValue(
            KeyValueContent(
              topLabel = "브랜치",
              content = "${context.sourceBranch} → ${context.targetBranch}"
            )
          )
        )
      )
    )

    sections.add(
      Section(
        header = "리뷰 결과",
        widgets = listOf(
          KeyValue(
            KeyValueContent(
              topLabel = "라인별 댓글",
              content = "${lineCommentCount}개",
              icon = "DESCRIPTION"
            )
          ),
          KeyValue(
            KeyValueContent(
              topLabel = "변경된 파일",
              content = "${context.totalFiles}개",
              icon = "BOOKMARK"
            )
          ),
          KeyValue(
            KeyValueContent(
              topLabel = "코드 변경량",
              content = "+${context.totalAdditions} -${context.totalDeletions}",
              icon = "STAR"
            )
          )
        )
      )
    )

    sections.add(
      Section(
        widgets = listOf(
          TextParagraph(
            TextContent("각 파일의 변경된 라인에 구체적인 리뷰 댓글이 작성되었습니다. GitLab MR 페이지에서 확인해주세요.")
          )
        )
      )
    )

    sections.add(
      Section(
        widgets = listOf(
          Buttons(
            buttons = listOf(
              Button(
                textButton = TextButton(
                  text = "새 창으로 MR 보기",
                  onClick = OnClick(OpenLink(mrUrl))
                )
              )
            )
          )
        )
      )
    )

    return GoogleChatMessage(
      cards = listOf(
        Card(
          header = header,
          sections = sections
        )
      )
    )
  }

  suspend fun notifyError(
    projectName: String,
    mrTitle: String,
    mrUrl: String,
    error: String
  ) {
    if (!googleChatClient.isConfigured()) {
      return
    }

    logger.info { "오류 알림을 Google Chat으로 전송 중" }

    try {
      val message = buildString {
        appendLine("*Code Review Failed*")
        appendLine()
        appendLine("*Project:* $projectName")
        appendLine("*MR:* $mrTitle")
        appendLine("*Error:* $error")
        appendLine()
        appendLine("View MR: $mrUrl")
      }

      googleChatClient.sendText(message)
    } catch (e: Exception) {
      logger.error(e) { "오류 알림을 Google Chat으로 전송하는 중 오류 발생" }
    }
  }
}
