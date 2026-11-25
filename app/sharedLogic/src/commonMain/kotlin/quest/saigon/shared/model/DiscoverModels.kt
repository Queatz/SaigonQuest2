package quest.saigon.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceSuggestionDto(
    val id: String? = null,
    val name: String = "",
    val address: String = "",
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val postCount: Long = 0L,
    val recent: Boolean = false,
    val photoUrl: String? = null,
    val googlePhotoUrl: String? = null,
    val category: String? = null,
    val custom: Boolean = false,
    val hasActivities: Boolean = false,
    val activityCount: Long = 0L,
    val activityEmojis: List<String> = emptyList(),
)

@Serializable
data class PlacesSearchResponse(
    val recent: List<PlaceSuggestionDto> = emptyList(),
    val places: List<PlaceSuggestionDto> = emptyList(),
)

@Serializable
data class CreatePlaceRequest(
    val name: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class EventsDiscoveryResponse(
    val events: List<PostCardDto> = emptyList(),
    val locations: List<PlaceSuggestionDto> = emptyList(),
    val people: List<CommunityMemberDto> = emptyList(),
    val nextPeopleCursor: String? = null,
)

@Serializable
data class CommunityPostPhotoDto(
    val id: String,
    val photoUrl: String = "",
)

@Serializable
data class CommunityCardMemberDto(
    val id: String,
    val name: String = "",
    val photoUrl: String? = null,
)

@Serializable
data class CommunityDto(
    val id: String,
    val name: String = "",
    val emoji: String = "✨",
    val description: String = "",
    val memberCount: Long = 0L,
    val joined: Boolean = false,
    val createdAt: Long = 0L,
    val latestPosts: List<CommunityPostPhotoDto> = emptyList(),
    val members: List<CommunityCardMemberDto> = emptyList(),
)

@Serializable
data class CommunitiesResponse(
    val communities: List<CommunityDto> = emptyList(),
)

@Serializable
data class WantMemberDto(
    val id: String,
    val name: String = "",
    val photoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
    val joinedAt: Long = 0L,
)

@Serializable
data class WantDto(
    val id: String,
    val name: String = "",
    val memberCount: Long = 0L,
    val joined: Boolean = false,
    val members: List<WantMemberDto> = emptyList(),
)

@Serializable
data class WantsResponse(
    val wants: List<WantDto> = emptyList(),
)

@Serializable
data class CreateWantRequest(
    val name: String,
)

@Serializable
data class WantStartChatRequest(
    val userIds: List<String>,
)

@Serializable
data class CreateCommunityRequest(
    val name: String,
    val emoji: String = "",
)

@Serializable
data class SuggestCommunityRequest(
    val text: String,
)

@Serializable
data class EmojiForNameRequest(
    val name: String,
)

@Serializable
data class CommunitySuggestResponse(
    val emoji: String = "✨",
    val name: String = "",
    val communityId: String? = null,
)

@Serializable
data class EmojiResponse(
    val emoji: String = "✨",
)

@Serializable
data class UpdateCommunityDescriptionRequest(
    val description: String,
)

@Serializable
data class CommunityMemberDto(
    val id: String,
    val name: String = "",
    val photoUrl: String? = null,
    val location: String = "",
    val intro: String = "",
    val joinedAt: Long = 0L,
    val latestPostId: String? = null,
    val latestPostPhotoUrl: String? = null,
)

@Serializable
data class CommunityMembersResponse(
    val members: List<CommunityMemberDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class PeopleResponse(
    val people: List<EventPersonDto> = emptyList(),
)

@Serializable
data class LocationDescriptionResponse(
    val description: String = "",
)

@Serializable
data class UpdateLocationDescriptionRequest(
    val name: String,
    val placeId: String? = null,
    val locationId: String? = null,
    val description: String,
)

@Serializable
data class UserPhotoDto(
    val id: String,
    val url: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
    val canDelete: Boolean = false,
)

@Serializable
data class UserPhotosResponse(
    val photos: List<UserPhotoDto> = emptyList(),
)
