package at.tori.dmr

import at.tori.dmr.config.CodeReviewProperties
import at.tori.dmr.config.GitLabProperties
import at.tori.dmr.config.GoogleChatProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
  _root_ide_package_.at.tori.dmr.config.GitLabProperties::class,
  _root_ide_package_.at.tori.dmr.config.GoogleChatProperties::class,
  _root_ide_package_.at.tori.dmr.config.CodeReviewProperties::class
)
class DmrApplication

fun main(args: Array<String>) {
  runApplication<at.tori.dmr.DmrApplication>(*args)
}
