package at.tori.dmr.domain

import com.fasterxml.jackson.annotation.JsonProperty

data class GoogleChatMessage(
  val text: String? = null,
  val cards: List<Card>? = null
)

data class Card(
  val header: Header? = null,
  val sections: List<Section>
)

data class Header(
  val title: String,
  val subtitle: String? = null,
  val imageUrl: String? = null,
  val imageStyle: String? = null
)

data class Section(
  val header: String? = null,
  val widgets: List<Widget>
)

sealed class Widget

data class TextParagraph(
  @JsonProperty("textParagraph") val textParagraph: TextContent
) : Widget()

data class TextContent(
  val text: String
)

data class KeyValue(
  @JsonProperty("keyValue") val keyValue: KeyValueContent
) : Widget()

data class KeyValueContent(
  val topLabel: String? = null,
  val content: String,
  val contentMultiline: Boolean = false,
  val bottomLabel: String? = null,
  val icon: String? = null,
  val button: Button? = null
)

data class Buttons(
  val buttons: List<Button>
) : Widget()

data class Button(
  val textButton: TextButton? = null,
  val imageButton: ImageButton? = null
)

data class TextButton(
  val text: String,
  val onClick: OnClick
)

data class ImageButton(
  val icon: String,
  val onClick: OnClick,
  val name: String? = null
)

data class OnClick(
  val openLink: OpenLink
)

data class OpenLink(
  val url: String
)
