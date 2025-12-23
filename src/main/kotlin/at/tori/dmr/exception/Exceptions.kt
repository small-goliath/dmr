package at.tori.dmr.exception

sealed class DmrException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class GitLabApiException(message: String, cause: Throwable? = null) : DmrException(message, cause)

class GoogleChatApiException(message: String, cause: Throwable? = null) : DmrException(message, cause)

sealed class AiServiceException(message: String, cause: Throwable? = null) : DmrException(message, cause)

class AiModelUnavailableException(message: String, cause: Throwable? = null) : AiServiceException(message, cause)

class AiRateLimitException(message: String, cause: Throwable? = null) : AiServiceException(message, cause)

class AiInvalidResponseException(message: String, cause: Throwable? = null) : AiServiceException(message, cause)

class AiTimeoutException(message: String, cause: Throwable? = null) : AiServiceException(message, cause)

class AiTokenLimitException(message: String, cause: Throwable? = null) : AiServiceException(message, cause)

class CodeReviewException(message: String, cause: Throwable? = null) : DmrException(message, cause)

class DependencyAnalysisException(message: String, cause: Throwable? = null) : DmrException(message, cause)

class InvalidWebhookException(message: String) : DmrException(message)

class MergeRequestNotFoundException(projectId: Long, mrIid: Long) :
  DmrException("Merge request not found: project=$projectId, iid=$mrIid")

class InvalidConfigurationException(message: String) : DmrException(message)

class DiffParsingException(message: String, cause: Throwable? = null) : DmrException(message, cause)

class JsonParsingException(message: String, cause: Throwable? = null) : DmrException(message, cause)
