package at.tori.dmr.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MergeRequestEvent(
  @JsonProperty("object_kind") val objectKind: String,
  val user: User,
  val project: Project,
  @JsonProperty("object_attributes") val objectAttributes: MergeRequestAttributes,
  val repository: Repository? = null,
  val changes: Changes? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
  val id: Long,
  val name: String,
  val username: String,
  val email: String? = null,
  @JsonProperty("avatar_url") val avatarUrl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Project(
  val id: Long,
  val name: String,
  val description: String? = null,
  @JsonProperty("web_url") val webUrl: String,
  @JsonProperty("path_with_namespace") val pathWithNamespace: String,
  @JsonProperty("default_branch") val defaultBranch: String? = null,
  @JsonProperty("http_url") val httpUrl: String? = null,
  @JsonProperty("ssh_url") val sshUrl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MergeRequestAttributes(
  val id: Long,
  val iid: Long,
  val title: String,
  val description: String? = null,
  @JsonProperty("source_branch") val sourceBranch: String,
  @JsonProperty("target_branch") val targetBranch: String,
  @JsonProperty("source_project_id") val sourceProjectId: Long,
  @JsonProperty("target_project_id") val targetProjectId: Long,
  val state: String,
  @JsonProperty("merge_status") val mergeStatus: String? = null,
  @JsonProperty("created_at") val createdAt: String? = null,
  @JsonProperty("updated_at") val updatedAt: String? = null,
  @JsonProperty("last_commit") val lastCommit: Commit? = null,
  @JsonProperty("work_in_progress") val workInProgress: Boolean = false,
  val draft: Boolean = false,
  val action: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repository(
  val name: String,
  val url: String,
  val description: String? = null,
  val homepage: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Changes(
  @JsonProperty("updated_at") val updatedAt: UpdateChange? = null,
  val title: TitleChange? = null,
  val description: DescriptionChange? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateChange(
  val previous: String? = null,
  val current: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TitleChange(
  val previous: String? = null,
  val current: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DescriptionChange(
  val previous: String? = null,
  val current: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Commit(
  val id: String,
  val message: String,
  val title: String? = null,
  val timestamp: String? = null,
  val url: String? = null,
  val author: CommitAuthor? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommitAuthor(
  val name: String,
  val email: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MergeRequest(
  val id: Long,
  val iid: Long,
  val title: String,
  val description: String? = null,
  @JsonProperty("source_branch") val sourceBranch: String,
  @JsonProperty("target_branch") val targetBranch: String,
  val state: String,
  @JsonProperty("merge_status") val mergeStatus: String,
  @JsonProperty("web_url") val webUrl: String,
  @JsonProperty("created_at") val createdAt: String,
  @JsonProperty("updated_at") val updatedAt: String,
  val author: User,
  val assignees: List<User>? = null,
  val reviewers: List<User>? = null,
  val draft: Boolean = false,
  @JsonProperty("work_in_progress") val workInProgress: Boolean = false,
  @JsonProperty("diff_refs") val diffRefs: DiffRefs? = null,
  val sha: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiffRefs(
  @JsonProperty("base_sha") val baseSha: String,
  @JsonProperty("head_sha") val headSha: String,
  @JsonProperty("start_sha") val startSha: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MergeRequestChange(
  @JsonProperty("old_path") val oldPath: String,
  @JsonProperty("new_path") val newPath: String,
  @JsonProperty("a_mode") val aMode: String? = null,
  @JsonProperty("b_mode") val bMode: String? = null,
  @JsonProperty("new_file") val newFile: Boolean = false,
  @JsonProperty("renamed_file") val renamedFile: Boolean = false,
  @JsonProperty("deleted_file") val deletedFile: Boolean = false,
  val diff: String
)

data class CreateNoteRequest(
  val body: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Note(
  val id: Long,
  val body: String,
  val author: User,
  @JsonProperty("created_at") val createdAt: String,
  @JsonProperty("updated_at") val updatedAt: String,
  val system: Boolean = false,
  @JsonProperty("noteable_id") val noteableId: Long,
  @JsonProperty("noteable_type") val noteableType: String
)

sealed class MergeRequestAction {
  data class Open(val mr: MergeRequest) : MergeRequestAction()
  data class Update(val mr: MergeRequest) : MergeRequestAction()
  data class Reopen(val mr: MergeRequest) : MergeRequestAction()
  data class Approved(val mr: MergeRequest) : MergeRequestAction()
  data class Unapproved(val mr: MergeRequest) : MergeRequestAction()
  data class Merge(val mr: MergeRequest) : MergeRequestAction()
  data class Close(val mr: MergeRequest) : MergeRequestAction()
  data object Ignore : MergeRequestAction()
}

data class FileChange(
  val filePath: String,
  val oldPath: String,
  val newFile: Boolean,
  val deletedFile: Boolean,
  val renamedFile: Boolean,
  val diff: String,
  val extension: String,
  val fileSize: Long = 0
)

data class ReviewContext(
  val projectName: String,
  val mrTitle: String,
  val mrDescription: String?,
  val sourceBranch: String,
  val targetBranch: String,
  val author: String,
  val files: List<FileChange>,
  val totalFiles: Int,
  val totalAdditions: Int,
  val totalDeletions: Int,
  val diffRefs: DiffRefs? = null
) {
  val summary: String
    get() = """
            |Project: $projectName
            |MR: $mrTitle
            |Author: $author
            |Branch: $sourceBranch -> $targetBranch
            |Files changed: $totalFiles (showing ${files.size})
            |Lines: +$totalAdditions -$totalDeletions
        """.trimMargin()
}

data class DiscussionPosition(
  @JsonProperty("base_sha") val baseSha: String,
  @JsonProperty("start_sha") val startSha: String,
  @JsonProperty("head_sha") val headSha: String,
  @JsonProperty("position_type") val positionType: String = "text",
  @JsonProperty("old_path") val oldPath: String?,
  @JsonProperty("new_path") val newPath: String,
  @JsonProperty("old_line") val oldLine: Int?,
  @JsonProperty("new_line") val newLine: Int?
)

data class CreateDiscussionRequest(
  val body: String,
  val position: DiscussionPosition? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Discussion(
  val id: String,
  val notes: List<Note>,
  @JsonProperty("individual_note") val individualNote: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResult(
  @JsonProperty("basename") val basename: String,
  @JsonProperty("data") val data: String,
  @JsonProperty("path") val path: String,
  @JsonProperty("filename") val filename: String,
  @JsonProperty("ref") val ref: String,
  @JsonProperty("startline") val startLine: Int,
  @JsonProperty("project_id") val projectId: Long
)

enum class CommentSeverity {
  CRITICAL,
  WARNING,
  SUGGESTION,
  INFO
}
