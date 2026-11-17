package quest.saigon.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import quest.saigon.Language
import quest.saigon.shared.model.*
import quest.saigon.shared.platform.TokenStorage
import quest.saigon.shared.platform.createHttpClientEngine
import quest.saigon.shared.platform.createPlatformTokenStorage
import quest.saigon.shared.platform.defaultServerOrigin
import quest.saigon.shared.platform.includeCookieHeaderOnRequests
import kotlin.coroutines.cancellation.CancellationException

data class UploadPart(
    val fieldName: String,
    val fileName: String,
    val contentType: String = "image/jpeg",
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UploadPart) return false
        return fieldName == other.fieldName && fileName == other.fileName && contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

class TownApiClient(
    engine: HttpClientEngine = createHttpClientEngine(),
    val tokenStorage: TokenStorage = createPlatformTokenStorage(),
    private val serverOriginProvider: () -> String = { defaultServerOrigin() },
    private val languageProvider: () -> Language = { Language.EN },
    private val onUnauthorized: suspend () -> Unit = {},
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val httpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    val sse = SseClient(
        httpClient = httpClient,
        tokenStorage = tokenStorage,
        resolveUrl = ::resolveUrl,
        onUnauthorized = {
            tokenStorage.writeSessionToken(null)
            onUnauthorized()
        },
    )

    fun resolveUrl(path: String): String {
        if (path.isBlank()) return path
        if (
            path.startsWith("http://") ||
            path.startsWith("https://") ||
            path.startsWith("data:") ||
            path.startsWith("blob:")
        ) {
            return path
        }
        val origin = serverOriginProvider().trim().trimEnd('/')
        if (origin.isEmpty() || !path.startsWith("/")) return path
        return origin + path
    }

    private fun normalizePostUrls(post: PostCardDto): PostCardDto = post.copy(
        authorPhotoUrl = post.authorPhotoUrl?.let(::resolveUrl),
        photoUrl = resolveUrl(post.photoUrl),
        photoUrls = post.photoUrls.map(::resolveUrl),
        avatarUrl = post.avatarUrl?.let(::resolveUrl),
        musicUrl = post.musicUrl?.let(::resolveUrl),
        comments = post.comments.map(::normalizeCommentUrls),
        attendees = post.attendees.map(::normalizePersonUrls),
        pollOptions = post.pollOptions.map { option ->
            option.copy(voters = option.voters.map(::normalizePersonUrls))
        },
    )

    private fun normalizeCommentUrls(comment: CommentCardDto): CommentCardDto =
        comment.copy(authorPhotoUrl = comment.authorPhotoUrl?.let(::resolveUrl))

    private fun normalizePersonUrls(person: EventPersonDto): EventPersonDto = person.copy(
        photoUrl = person.photoUrl?.let(::resolveUrl),
        latestPostPhotoUrl = person.latestPostPhotoUrl?.let(::resolveUrl),
    )

    private fun normalizeMeUrls(me: MeDto): MeDto = me.copy(
        photoUrl = me.photoUrl?.let(::resolveUrl),
    )

    // --- Auth & Session ---

    suspend fun getConfig(): ApiResult<ConfigDto> =
        get("/api/config", ConfigDto.serializer(), notifyOnUnauthorized = false)

    suspend fun recordVisit(): ApiResult<OkResponse> =
        postJson("/api/stats/visit", OkResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)

    suspend fun getMe(): ApiResult<MeDto> =
        get("/api/me", MeDto.serializer(), notifyOnUnauthorized = false).map(::normalizeMeUrls)

    suspend fun register(name: String, phone: String, password: String): ApiResult<MeDto> {
        var cookieToken: String? = null
        val result = postJson(
            "/api/auth/register",
            MeDto.serializer(),
            RegisterRequest(name, phone, password),
            RegisterRequest.serializer(),
            notifyOnUnauthorized = false,
            onResponse = { response -> cookieToken = extractSessionCookie(response) },
        )
        if (result is ApiResult.Success) {
            val resolvedToken = result.value.token?.takeIf { it.isNotBlank() } ?: cookieToken
            resolvedToken?.let { tokenStorage.writeSessionToken(it) }
        }
        return result.map(::normalizeMeUrls)
    }

    suspend fun login(phone: String, password: String): ApiResult<MeDto> {
        var cookieToken: String? = null
        val result = postJson(
            "/api/auth/login",
            MeDto.serializer(),
            LoginRequest(phone, password),
            LoginRequest.serializer(),
            notifyOnUnauthorized = false,
            onResponse = { response -> cookieToken = extractSessionCookie(response) },
        )
        if (result is ApiResult.Success) {
            val resolvedToken = result.value.token?.takeIf { it.isNotBlank() } ?: cookieToken
            resolvedToken?.let { tokenStorage.writeSessionToken(it) }
        }
        return result.map(::normalizeMeUrls)
    }

    suspend fun logout(): ApiResult<OkResponse> {
        val result = postJson("/api/auth/logout", OkResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)
        tokenStorage.writeSessionToken(null)
        return result
    }

    suspend fun patchMe(request: UpdateMeRequest): ApiResult<MeDto> =
        patchJson("/api/me", MeDto.serializer(), request, UpdateMeRequest.serializer()).map(::normalizeMeUrls)

    suspend fun uploadProfilePhoto(photoBytes: ByteArray): ApiResult<MeDto> =
        postMultipart(
            "/api/me/photo",
            MeDto.serializer(),
            parts = listOf(UploadPart("photo", "profile.jpg", "image/jpeg", photoBytes)),
        ).map(::normalizeMeUrls)

    suspend fun getUserProfile(userId: String): ApiResult<UserProfileDto> =
        get("/api/users/$userId", UserProfileDto.serializer()).map {
            it.copy(photoUrl = it.photoUrl?.let(::resolveUrl))
        }

    suspend fun getUserPosts(userId: String, cursor: String? = null, limit: Int = 24): ApiResult<FeedPageDto> =
        get(
            buildQuery("/api/users/$userId/posts", mapOf("cursor" to cursor, "limit" to limit.toString())),
            FeedPageDto.serializer(),
        ).map { page -> page.copy(posts = page.posts.map(::normalizePostUrls)) }

    // --- Feed & Posts ---

    suspend fun getFeed(cursor: String? = null, limit: Int? = null): ApiResult<FeedPageDto> =
        get(
            buildQuery("/api/posts", mapOf("cursor" to cursor, "limit" to limit?.toString())),
            FeedPageDto.serializer(),
        ).map { page -> page.copy(posts = page.posts.map(::normalizePostUrls)) }

    suspend fun searchPosts(query: String, cursor: String? = null, limit: Int = 24): ApiResult<FeedPageDto> =
        get(
            buildQuery("/api/posts/search", mapOf("q" to query, "cursor" to cursor, "limit" to limit.toString())),
            FeedPageDto.serializer(),
        ).map { page -> page.copy(posts = page.posts.map(::normalizePostUrls)) }

    suspend fun getPost(postId: String): ApiResult<PostCardDto> =
        get("/api/posts/$postId", PostCardDto.serializer()).map(::normalizePostUrls)

    suspend fun createPost(
        fields: Map<String, String>,
        photos: List<ByteArray>,
    ): ApiResult<PostCardDto> = postMultipart(
        "/api/posts",
        PostCardDto.serializer(),
        fields = fields,
        parts = photos.mapIndexed { index, bytes ->
            UploadPart("photo", "photo_$index.jpg", "image/jpeg", bytes)
        },
    ).map(::normalizePostUrls)

    suspend fun patchPost(postId: String, message: String): ApiResult<PatchPostResponse> =
        patchJson("/api/posts/$postId", PatchPostResponse.serializer(), PatchPostRequest(message), PatchPostRequest.serializer())

    suspend fun deletePost(postId: String): ApiResult<OkResponse> =
        delete("/api/posts/$postId", OkResponse.serializer())

    suspend fun recordPostView(postId: String): ApiResult<OkResponse> =
        postJson("/api/posts/$postId/view", OkResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)

    suspend fun recordPostShare(postId: String): ApiResult<OkResponse> =
        postJson("/api/posts/$postId/share", OkResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)

    suspend fun contactPostAuthor(postId: String): ApiResult<ContactZaloResponse> =
        postJson("/api/posts/$postId/contact", ContactZaloResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)

    suspend fun joinEvent(postId: String): ApiResult<PostCardDto> =
        postJson("/api/posts/$postId/join", PostCardDto.serializer(), Unit, EmptySerializer).map(::normalizePostUrls)

    suspend fun leaveEvent(postId: String): ApiResult<PostCardDto> =
        postJson("/api/posts/$postId/leave", PostCardDto.serializer(), Unit, EmptySerializer).map(::normalizePostUrls)

    suspend fun joinOpenChat(postId: String): ApiResult<ChatDto> =
        postJson("/api/posts/$postId/open-chat", ChatDto.serializer(), Unit, EmptySerializer).map(::normalizeChatUrls)

    suspend fun getEventAttendees(postId: String, cursor: String? = null, limit: Int = 30): ApiResult<AttendeesPageDto> =
        get(
            buildQuery("/api/posts/$postId/attendees", mapOf("cursor" to cursor, "limit" to limit.toString())),
            AttendeesPageDto.serializer(),
        ).map { page -> page.copy(attendees = page.attendees.map(::normalizePersonUrls)) }

    suspend fun votePoll(postId: String, optionIndex: Int): ApiResult<PostCardDto> =
        postJson("/api/posts/$postId/poll/$optionIndex", PostCardDto.serializer(), Unit, EmptySerializer).map(::normalizePostUrls)

    suspend fun getPollVoters(postId: String): ApiResult<PollVotersResponse> =
        get("/api/posts/$postId/voters", PollVotersResponse.serializer()).map { res ->
            res.copy(options = res.options.map { it.copy(voters = it.voters.map(::normalizePersonUrls)) })
        }

    suspend fun answerQuestion(postId: String, text: String): ApiResult<PostCardDto> =
        postJson(
            "/api/posts/$postId/answers",
            PostCardDto.serializer(),
            QuestionAnswerRequest(text),
            QuestionAnswerRequest.serializer(),
        ).map(::normalizePostUrls)

    suspend fun getQuestionAnswers(postId: String): ApiResult<QuestionAnswersResponse> =
        get("/api/posts/$postId/answers", QuestionAnswersResponse.serializer()).map { res ->
            res.copy(answers = res.answers.map { it.copy(photoUrl = it.photoUrl?.let(::resolveUrl)) })
        }

    suspend fun getCheckIns(): ApiResult<CheckInsResponse> =
        get("/api/checkins", CheckInsResponse.serializer()).map { res ->
            res.copy(checkIns = res.checkIns.map { it.copy(authorPhotoUrl = it.authorPhotoUrl?.let(::resolveUrl)) })
        }

    suspend fun updateCheckIn(postId: String, minutes: Int): ApiResult<CheckInUpdateResponse> =
        patchJson(
            "/api/posts/$postId/checkin",
            CheckInUpdateResponse.serializer(),
            CheckInUpdateRequest(minutes),
            CheckInUpdateRequest.serializer(),
        )

    suspend fun deleteCheckIn(postId: String): ApiResult<OkResponse> =
        delete("/api/posts/$postId/checkin", OkResponse.serializer())

    // --- Comments ---

    suspend fun getComments(postId: String, cursor: String? = null, limit: Int? = null): ApiResult<CommentsPageDto> =
        get(
            buildQuery("/api/posts/$postId/comments", mapOf("cursor" to cursor, "limit" to limit?.toString())),
            CommentsPageDto.serializer(),
        ).map { page -> page.copy(comments = page.comments.map(::normalizeCommentUrls)) }

    suspend fun addComment(postId: String, text: String, replyToId: String? = null): ApiResult<CommentCardDto> =
        postJson(
            "/api/posts/$postId/comments",
            CommentCardDto.serializer(),
            CreateCommentRequest(text, replyToId),
            CreateCommentRequest.serializer(),
        ).map(::normalizeCommentUrls)

    suspend fun deleteComment(postId: String, commentId: String): ApiResult<OkResponse> =
        delete("/api/posts/$postId/comments/$commentId", OkResponse.serializer())

    // --- Notifications & Push ---

    suspend fun getNotifications(cursor: String? = null, limit: Int = 50): ApiResult<NotificationsPageDto> =
        get(
            buildQuery("/api/notifications", mapOf("cursor" to cursor, "limit" to limit.toString())),
            NotificationsPageDto.serializer(),
        )

    suspend fun markNotificationRead(id: String): ApiResult<NotificationDto> =
        postJson("/api/notifications/$id/read", NotificationDto.serializer(), Unit, EmptySerializer)

    suspend fun markAllNotificationsRead(): ApiResult<ReadAllResponse> =
        postJson("/api/notifications/read-all", ReadAllResponse.serializer(), Unit, EmptySerializer)

    suspend fun registerFcmToken(token: String, language: String = "system"): ApiResult<OkResponse> =
        postJson(
            "/api/push/fcm",
            OkResponse.serializer(),
            FcmTokenRequest(token, language),
            FcmTokenRequest.serializer(),
        )

    suspend fun deleteFcmToken(token: String): ApiResult<OkResponse> =
        deleteWithBody(
            "/api/push/fcm",
            OkResponse.serializer(),
            FcmTokenRequest(token),
            FcmTokenRequest.serializer(),
        )

    // --- Avatars & Music ---

    suspend fun getAvatars(): ApiResult<AvatarsResponse> =
        get("/api/avatars", AvatarsResponse.serializer()).map { res ->
            res.copy(avatars = res.avatars.map { it.copy(imageUrl = it.imageUrl?.let(::resolveUrl)) })
        }

    suspend fun getAvatar(id: String): ApiResult<AvatarDto> =
        get("/api/avatars/$id", AvatarDto.serializer()).map { it.copy(imageUrl = it.imageUrl?.let(::resolveUrl)) }

    suspend fun createAvatar(prompt: String): ApiResult<AvatarDto> =
        postJson("/api/avatars", AvatarDto.serializer(), CreateAvatarRequest(prompt), CreateAvatarRequest.serializer())
            .map { it.copy(imageUrl = it.imageUrl?.let(::resolveUrl)) }

    suspend fun retryAvatar(id: String): ApiResult<AvatarDto> =
        postJson("/api/avatars/$id/retry", AvatarDto.serializer(), Unit, EmptySerializer)
            .map { it.copy(imageUrl = it.imageUrl?.let(::resolveUrl)) }

    suspend fun deleteAvatar(id: String): ApiResult<OkResponse> =
        delete("/api/avatars/$id", OkResponse.serializer())

    suspend fun searchMusic(query: String = "", cursor: String? = null, limit: Int = 20): ApiResult<MusicPageDto> =
        get(
            buildQuery("/api/music", mapOf("q" to query, "cursor" to cursor, "limit" to limit.toString())),
            MusicPageDto.serializer(),
        ).map { page -> page.copy(music = page.music.map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) }) }

    suspend fun getMyMusic(): ApiResult<MusicPageDto> =
        get("/api/me/music", MusicPageDto.serializer()).map { page ->
            page.copy(music = page.music.map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) })
        }

    suspend fun getMusic(id: String): ApiResult<MusicDto> =
        get("/api/music/$id", MusicDto.serializer()).map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) }

    suspend fun createMusic(prompt: String, gender: String? = null, instrumental: Boolean = false): ApiResult<MusicDto> =
        postJson(
            "/api/music",
            MusicDto.serializer(),
            CreateMusicRequest(prompt, gender, instrumental),
            CreateMusicRequest.serializer(),
        ).map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) }

    suspend fun retryMusic(id: String): ApiResult<MusicDto> =
        postJson("/api/music/$id/retry", MusicDto.serializer(), Unit, EmptySerializer)
            .map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) }

    suspend fun renameMusic(id: String, name: String): ApiResult<MusicDto> =
        patchJson("/api/music/$id", MusicDto.serializer(), RenameMusicRequest(name), RenameMusicRequest.serializer())
            .map { it.copy(audioUrl = it.audioUrl?.let(::resolveUrl)) }

    suspend fun deleteMusic(id: String): ApiResult<OkResponse> =
        delete("/api/music/$id", OkResponse.serializer())

    // --- Discover, Places, Events, Communities ---

    suspend fun getEventsSummary(): ApiResult<EventsSummaryDto> =
        get("/api/events/summary", EventsSummaryDto.serializer())

    suspend fun getDiscovery(cursor: String? = null): ApiResult<EventsDiscoveryResponse> =
        get(buildQuery("/api/events", mapOf("cursor" to cursor)), EventsDiscoveryResponse.serializer()).map { res ->
            res.copy(
                events = res.events.map(::normalizePostUrls),
                locations = res.locations.map(::normalizePlaceUrls),
                people = res.people.map(::normalizeCommunityMemberUrls),
            )
        }

    suspend fun searchPlaces(query: String, communityId: String? = null): ApiResult<PlacesSearchResponse> =
        get(
            buildQuery("/api/places", mapOf("q" to query, "communityId" to communityId)),
            PlacesSearchResponse.serializer(),
        ).map { res ->
            res.copy(
                recent = res.recent.map(::normalizePlaceUrls),
                places = res.places.map(::normalizePlaceUrls),
            )
        }

    suspend fun createPlace(name: String, lat: Double, lng: Double): ApiResult<PlaceSuggestionDto> =
        postJson("/api/places", PlaceSuggestionDto.serializer(), CreatePlaceRequest(name, lat, lng), CreatePlaceRequest.serializer())
            .map(::normalizePlaceUrls)

    suspend fun getPlace(idOrToken: String): ApiResult<PlaceSuggestionDto> =
        get("/api/places/$idOrToken", PlaceSuggestionDto.serializer()).map(::normalizePlaceUrls)

    suspend fun getPlaceDetails(placeId: String): ApiResult<PlaceSuggestionDto> =
        get(buildQuery("/api/places/details", mapOf("id" to placeId)), PlaceSuggestionDto.serializer()).map(::normalizePlaceUrls)

    suspend fun getPlacePosts(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
        cursor: String? = null,
        limit: Int = 24,
    ): ApiResult<FeedPageDto> = get(
        buildQuery(
            "/api/places/posts",
            mapOf(
                "name" to name,
                "placeId" to placeId,
                "locationId" to locationId,
                "cursor" to cursor,
                "limit" to limit.toString(),
            ),
        ),
        FeedPageDto.serializer(),
    ).map { page -> page.copy(posts = page.posts.map(::normalizePostUrls)) }

    suspend fun getPlaceDescription(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
    ): ApiResult<LocationDescriptionResponse> = get(
        buildQuery("/api/places/description", mapOf("name" to name, "placeId" to placeId, "locationId" to locationId)),
        LocationDescriptionResponse.serializer(),
    )

    suspend fun updatePlaceDescription(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
        description: String,
    ): ApiResult<LocationDescriptionResponse> = patchJson(
        "/api/places/description",
        LocationDescriptionResponse.serializer(),
        UpdateLocationDescriptionRequest(name, placeId, locationId, description),
        UpdateLocationDescriptionRequest.serializer(),
    )

    suspend fun getPlacePhotos(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
        limit: Int = 20,
    ): ApiResult<UserPhotosResponse> = get(
        buildQuery(
            "/api/places/photos",
            mapOf("name" to name, "placeId" to placeId, "locationId" to locationId, "limit" to limit.toString()),
        ),
        UserPhotosResponse.serializer(),
    ).map { res -> res.copy(photos = res.photos.map { it.copy(url = resolveUrl(it.url)) }) }

    suspend fun uploadPlacePhotos(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
        photos: List<ByteArray>,
    ): ApiResult<UserPhotosResponse> = postMultipart(
        "/api/places/photos",
        UserPhotosResponse.serializer(),
        fields = buildMap {
            put("name", name)
            if (!placeId.isNullOrBlank()) put("placeId", placeId)
            if (!locationId.isNullOrBlank()) put("locationId", locationId)
        },
        parts = photos.mapIndexed { idx, bytes -> UploadPart("photos", "place_$idx.jpg", "image/jpeg", bytes) },
    ).map { res -> res.copy(photos = res.photos.map { it.copy(url = resolveUrl(it.url)) }) }

    suspend fun deletePlacePhoto(photoId: String): ApiResult<OkResponse> =
        delete("/api/places/photos/$photoId", OkResponse.serializer())

    suspend fun getCommunities(query: String = "", limit: Int = 30): ApiResult<CommunitiesResponse> =
        get(
            buildQuery("/api/communities", mapOf("q" to query.takeIf { it.isNotBlank() }, "limit" to limit.toString())),
            CommunitiesResponse.serializer(),
        ).map { res -> res.copy(communities = res.communities.map(::normalizeCommunityUrls)) }

    suspend fun suggestCommunity(text: String): ApiResult<CommunitySuggestResponse> =
        postJson("/api/communities/suggest", CommunitySuggestResponse.serializer(), SuggestCommunityRequest(text), SuggestCommunityRequest.serializer())

    suspend fun suggestCommunityEmoji(name: String): ApiResult<EmojiResponse> =
        postJson("/api/communities/emoji", EmojiResponse.serializer(), EmojiForNameRequest(name), EmojiForNameRequest.serializer())

    suspend fun createCommunity(name: String, emoji: String = ""): ApiResult<CommunityDto> =
        postJson("/api/communities", CommunityDto.serializer(), CreateCommunityRequest(name, emoji), CreateCommunityRequest.serializer())
            .map(::normalizeCommunityUrls)

    suspend fun getCommunity(id: String): ApiResult<CommunityDto> =
        get("/api/communities/$id", CommunityDto.serializer()).map(::normalizeCommunityUrls)

    suspend fun updateCommunityDescription(id: String, description: String): ApiResult<CommunityDto> =
        patchJson(
            "/api/communities/$id",
            CommunityDto.serializer(),
            UpdateCommunityDescriptionRequest(description),
            UpdateCommunityDescriptionRequest.serializer(),
        ).map(::normalizeCommunityUrls)

    suspend fun joinCommunity(id: String): ApiResult<CommunityDto> =
        postJson("/api/communities/$id/join", CommunityDto.serializer(), Unit, EmptySerializer).map(::normalizeCommunityUrls)

    suspend fun leaveCommunity(id: String): ApiResult<CommunityDto> =
        postJson("/api/communities/$id/leave", CommunityDto.serializer(), Unit, EmptySerializer).map(::normalizeCommunityUrls)

    suspend fun getCommunityMembers(id: String, cursor: String? = null, limit: Int = 30): ApiResult<CommunityMembersResponse> =
        get(
            buildQuery("/api/communities/$id/members", mapOf("cursor" to cursor, "limit" to limit.toString())),
            CommunityMembersResponse.serializer(),
        ).map { res -> res.copy(members = res.members.map(::normalizeCommunityMemberUrls)) }

    suspend fun getCommunityPosts(id: String, cursor: String? = null, limit: Int? = null): ApiResult<FeedPageDto> =
        get(
            buildQuery("/api/communities/$id/posts", mapOf("cursor" to cursor, "limit" to limit?.toString())),
            FeedPageDto.serializer(),
        ).map { page -> page.copy(posts = page.posts.map(::normalizePostUrls)) }

    suspend fun getMyCommunities(): ApiResult<CommunitiesResponse> =
        get("/api/me/communities", CommunitiesResponse.serializer()).map { res ->
            res.copy(communities = res.communities.map(::normalizeCommunityUrls))
        }

    suspend fun getUserCommunities(userId: String): ApiResult<CommunitiesResponse> =
        get("/api/users/$userId/communities", CommunitiesResponse.serializer()).map { res ->
            res.copy(communities = res.communities.map(::normalizeCommunityUrls))
        }

    // --- Shop & Products ---

    suspend fun searchProducts(query: String = "", cursor: String? = null, limit: Int = 50): ApiResult<ProductsPageDto> =
        get(
            buildQuery("/api/products/search", mapOf("q" to query.takeIf { it.isNotBlank() }, "cursor" to cursor, "limit" to limit.toString())),
            ProductsPageDto.serializer(),
        ).map { page -> page.copy(products = page.products.map(::normalizeProductUrls)) }

    suspend fun getMyProducts(cursor: String? = null, limit: Int = 20): ApiResult<ProductsPageDto> =
        get(
            buildQuery("/api/me/products", mapOf("cursor" to cursor, "limit" to limit.toString())),
            ProductsPageDto.serializer(),
        ).map { page -> page.copy(products = page.products.map(::normalizeProductUrls)) }

    suspend fun getUserProducts(userId: String, cursor: String? = null, limit: Int = 20): ApiResult<ProductsPageDto> =
        get(
            buildQuery("/api/users/$userId/products", mapOf("cursor" to cursor, "limit" to limit.toString())),
            ProductsPageDto.serializer(),
        ).map { page -> page.copy(products = page.products.map(::normalizeProductUrls)) }

    suspend fun getProduct(productId: String): ApiResult<ProductDto> =
        get("/api/products/$productId", ProductDto.serializer()).map(::normalizeProductUrls)

    suspend fun createProduct(
        name: String,
        price: String,
        category: String,
        description: String,
        photos: List<ByteArray>,
    ): ApiResult<ProductDto> = postMultipart(
        "/api/products",
        ProductDto.serializer(),
        fields = mapOf(
            "name" to name,
            "price" to price,
            "category" to category,
            "description" to description,
        ),
        parts = photos.mapIndexed { idx, bytes -> UploadPart("photos", "product_$idx.jpg", "image/jpeg", bytes) },
    ).map(::normalizeProductUrls)

    suspend fun updateProduct(
        productId: String,
        name: String,
        price: String,
        category: String,
        description: String,
        photoOrderJson: String?,
        newPhotos: List<ByteArray>,
    ): ApiResult<ProductDto> = patchMultipart(
        "/api/products/$productId",
        ProductDto.serializer(),
        fields = buildMap {
            put("name", name)
            put("price", price)
            put("category", category)
            put("description", description)
            if (photoOrderJson != null) put("photoOrder", photoOrderJson)
        },
        parts = newPhotos.mapIndexed { idx, bytes -> UploadPart("photos", "product_$idx.jpg", "image/jpeg", bytes) },
    ).map(::normalizeProductUrls)

    suspend fun deleteProduct(productId: String): ApiResult<OkResponse> =
        delete("/api/products/$productId", OkResponse.serializer())

    suspend fun orderProduct(productId: String, contactMethod: String, message: String): ApiResult<ProductOrderDto> =
        postJson(
            "/api/products/$productId/orders",
            ProductOrderDto.serializer(),
            CreateProductOrderRequest(contactMethod, message),
            CreateProductOrderRequest.serializer(),
        )

    suspend fun getMyOrders(cursor: String? = null, limit: Int = 20): ApiResult<OrdersPageDto> =
        get(
            buildQuery("/api/me/orders", mapOf("cursor" to cursor, "limit" to limit.toString())),
            OrdersPageDto.serializer(),
        ).map { page -> page.copy(orders = page.orders.map { it.copy(buyerPhotoUrl = it.buyerPhotoUrl?.let(::resolveUrl)) }) }

    suspend fun resolveOrder(orderId: String, resolved: Boolean): ApiResult<ProductOrderDto> =
        patchJson("/api/orders/$orderId", ProductOrderDto.serializer(), ResolveOrderRequest(resolved), ResolveOrderRequest.serializer())
            .map { it.copy(buyerPhotoUrl = it.buyerPhotoUrl?.let(::resolveUrl)) }

    // --- Activities ---

    suspend fun getActivities(
        name: String? = null,
        placeId: String? = null,
        locationId: String? = null,
        cursor: String? = null,
        limit: Int = 30,
    ): ApiResult<ActivitiesPageDto> = get(
        buildQuery(
            "/api/activities",
            mapOf("name" to name, "placeId" to placeId, "locationId" to locationId, "cursor" to cursor, "limit" to limit.toString()),
        ),
        ActivitiesPageDto.serializer(),
    ).map { page -> page.copy(activities = page.activities.map(::normalizeActivityUrls)) }

    suspend fun getMyActivities(cursor: String? = null, limit: Int = 20): ApiResult<ActivitiesPageDto> =
        get(
            buildQuery("/api/me/activities", mapOf("cursor" to cursor, "limit" to limit.toString())),
            ActivitiesPageDto.serializer(),
        ).map { page -> page.copy(activities = page.activities.map(::normalizeActivityUrls)) }

    suspend fun getUserActivities(userId: String, cursor: String? = null, limit: Int = 20): ApiResult<ActivitiesPageDto> =
        get(
            buildQuery("/api/users/$userId/activities", mapOf("cursor" to cursor, "limit" to limit.toString())),
            ActivitiesPageDto.serializer(),
        ).map { page -> page.copy(activities = page.activities.map(::normalizeActivityUrls)) }

    suspend fun getActivity(id: String): ApiResult<ActivityDto> =
        get("/api/activities/$id", ActivityDto.serializer()).map(::normalizeActivityUrls)

    suspend fun createActivity(request: CreateActivityRequest): ApiResult<ActivityDto> =
        postJson("/api/activities", ActivityDto.serializer(), request, CreateActivityRequest.serializer()).map(::normalizeActivityUrls)

    suspend fun updateActivity(id: String, request: UpdateActivityRequest): ApiResult<ActivityDto> =
        patchJson("/api/activities/$id", ActivityDto.serializer(), request, UpdateActivityRequest.serializer()).map(::normalizeActivityUrls)

    suspend fun deleteActivity(id: String): ApiResult<OkResponse> =
        delete("/api/activities/$id", OkResponse.serializer())

    suspend fun joinActivity(id: String): ApiResult<ActivityDto> =
        postJson("/api/activities/$id/join", ActivityDto.serializer(), Unit, EmptySerializer).map(::normalizeActivityUrls)

    suspend fun leaveActivity(id: String): ApiResult<ActivityDto> =
        postJson("/api/activities/$id/leave", ActivityDto.serializer(), Unit, EmptySerializer).map(::normalizeActivityUrls)

    suspend fun startActivityChat(id: String, userIds: List<String>): ApiResult<ChatDto> =
        postJson("/api/activities/$id/chat", ChatDto.serializer(), ActivityStartChatRequest(userIds), ActivityStartChatRequest.serializer())
            .map(::normalizeChatUrls)

    suspend fun suggestActivityEmoji(name: String): ApiResult<EmojiResponse> =
        postJson("/api/activities/emoji", EmojiResponse.serializer(), EmojiForNameRequest(name), EmojiForNameRequest.serializer())

    suspend fun getActivityPhotos(id: String, limit: Int = 20): ApiResult<UserPhotosResponse> =
        get(buildQuery("/api/activities/$id/photos", mapOf("limit" to limit.toString())), UserPhotosResponse.serializer())
            .map { res -> res.copy(photos = res.photos.map { it.copy(url = resolveUrl(it.url)) }) }

    suspend fun uploadActivityPhotos(id: String, photos: List<ByteArray>): ApiResult<UserPhotosResponse> =
        postMultipart(
            "/api/activities/$id/photos",
            UserPhotosResponse.serializer(),
            parts = photos.mapIndexed { idx, bytes -> UploadPart("photos", "activity_$idx.jpg", "image/jpeg", bytes) },
        ).map { res -> res.copy(photos = res.photos.map { it.copy(url = resolveUrl(it.url)) }) }

    suspend fun deleteActivityPhoto(activityId: String, photoId: String): ApiResult<OkResponse> =
        delete("/api/activities/$activityId/photos/$photoId", OkResponse.serializer())

    // --- Chats & People ---

    suspend fun getChats(cursor: String? = null, limit: Int = 50, type: String? = null): ApiResult<ChatsPageDto> =
        get(
            buildQuery("/api/chats", mapOf("cursor" to cursor, "limit" to limit.toString(), "type" to type)),
            ChatsPageDto.serializer(),
        ).map { page -> page.copy(chats = page.chats.map(::normalizeChatUrls)) }

    suspend fun getDirectChat(userId: String): ApiResult<ChatDto> =
        get("/api/chats/direct/$userId", ChatDto.serializer()).map(::normalizeChatUrls)

    suspend fun createDirectChat(userId: String, initialText: String = ""): ApiResult<ChatDto> =
        postJson("/api/chats", ChatDto.serializer(), CreateChatRequest(userId = userId, text = initialText), CreateChatRequest.serializer())
            .map(::normalizeChatUrls)

    suspend fun createGroupChat(name: String = ""): ApiResult<ChatDto> =
        postJson("/api/chats", ChatDto.serializer(), CreateChatRequest(name = name), CreateChatRequest.serializer())
            .map(::normalizeChatUrls)

    suspend fun getChat(chatId: String): ApiResult<ChatDto> =
        get("/api/chats/$chatId", ChatDto.serializer()).map(::normalizeChatUrls)

    suspend fun markChatRead(chatId: String): ApiResult<UnreadCountResponse> =
        postJson("/api/chats/$chatId/read", UnreadCountResponse.serializer(), Unit, EmptySerializer)

    suspend fun markChatNoticesRead(): ApiResult<UnreadCountResponse> =
        postJson("/api/chats/notices/read", UnreadCountResponse.serializer(), Unit, EmptySerializer)

    suspend fun leaveChat(chatId: String): ApiResult<OkResponse> =
        postJson("/api/chats/$chatId/leave", OkResponse.serializer(), Unit, EmptySerializer)

    suspend fun deleteChat(chatId: String): ApiResult<OkResponse> =
        delete("/api/chats/$chatId", OkResponse.serializer())

    suspend fun renameChat(chatId: String, name: String): ApiResult<ChatDto> =
        patchJson("/api/chats/$chatId", ChatDto.serializer(), RenameChatRequest(name), RenameChatRequest.serializer())
            .map(::normalizeChatUrls)

    suspend fun getChatMembers(chatId: String): ApiResult<ChatMembersResponse> =
        get("/api/chats/$chatId/members", ChatMembersResponse.serializer()).map { res ->
            res.copy(members = res.members.map { it.copy(photoUrl = it.photoUrl?.let(::resolveUrl)) })
        }

    suspend fun addChatMember(chatId: String, userId: String): ApiResult<ChatDto> =
        postJson("/api/chats/$chatId/members", ChatDto.serializer(), AddChatMemberRequest(userId), AddChatMemberRequest.serializer())
            .map(::normalizeChatUrls)

    suspend fun removeChatMember(chatId: String, userId: String): ApiResult<ChatDto> =
        delete("/api/chats/$chatId/members/$userId", ChatDto.serializer()).map(::normalizeChatUrls)

    suspend fun makeChatOwner(chatId: String, userId: String): ApiResult<ChatDto> =
        postJson("/api/chats/$chatId/members/$userId/owner", ChatDto.serializer(), Unit, EmptySerializer).map(::normalizeChatUrls)

    suspend fun getChatMessages(chatId: String, cursor: String? = null, limit: Int = 50): ApiResult<ChatMessagesPageDto> =
        get(
            buildQuery("/api/chats/$chatId/messages", mapOf("cursor" to cursor, "limit" to limit.toString())),
            ChatMessagesPageDto.serializer(),
        ).map { page -> page.copy(messages = page.messages.map(::normalizeChatMessageUrls)) }

    suspend fun sendChatMessage(chatId: String, text: String, photos: List<ByteArray> = emptyList()): ApiResult<ChatMessageDto> =
        if (photos.isEmpty()) {
            postJson(
                "/api/chats/$chatId/messages",
                ChatMessageDto.serializer(),
                SendChatMessageRequest(text),
                SendChatMessageRequest.serializer(),
            ).map(::normalizeChatMessageUrls)
        } else {
            postMultipart(
                "/api/chats/$chatId/messages",
                ChatMessageDto.serializer(),
                fields = mapOf("text" to text),
                parts = photos.mapIndexed { idx, bytes -> UploadPart("photo", "chat_$idx.jpg", "image/jpeg", bytes) },
            ).map(::normalizeChatMessageUrls)
        }

    suspend fun deleteChatMessage(chatId: String, messageId: String): ApiResult<OkResponse> =
        delete("/api/chats/$chatId/messages/$messageId", OkResponse.serializer())

    suspend fun getMessageReactionDetails(chatId: String, messageId: String): ApiResult<ReactionDetailsResponse> =
        get("/api/chats/$chatId/messages/$messageId/reactions", ReactionDetailsResponse.serializer()).map { res ->
            res.copy(reactions = res.reactions.map { it.copy(photoUrl = it.photoUrl?.let(::resolveUrl)) })
        }

    suspend fun addMessageReaction(chatId: String, messageId: String, emoji: String): ApiResult<ReactionsResponse> =
        postJson(
            "/api/chats/$chatId/messages/$messageId/reactions",
            ReactionsResponse.serializer(),
            ReactMessageRequest(emoji),
            ReactMessageRequest.serializer(),
        )

    suspend fun removeMessageReaction(chatId: String, messageId: String, emoji: String): ApiResult<ReactionsResponse> =
        deleteWithBody(
            "/api/chats/$chatId/messages/$messageId/reactions",
            ReactionsResponse.serializer(),
            ReactMessageRequest(emoji),
            ReactMessageRequest.serializer(),
        )

    suspend fun sendChatTyping(chatId: String): ApiResult<OkResponse> =
        postJson("/api/chats/$chatId/typing", OkResponse.serializer(), Unit, EmptySerializer, notifyOnUnauthorized = false)

    suspend fun searchPeople(query: String = ""): ApiResult<PeopleResponse> =
        get(buildQuery("/api/people", mapOf("q" to query)), PeopleResponse.serializer()).map { res ->
            res.copy(people = res.people.map(::normalizePersonUrls))
        }

    suspend fun searchChatPeopleToAdd(chatId: String, query: String = ""): ApiResult<PeopleResponse> =
        get(buildQuery("/api/chats/$chatId/people", mapOf("q" to query)), PeopleResponse.serializer()).map { res ->
            res.copy(people = res.people.map(::normalizePersonUrls))
        }

    // --- Profile extras, Features, Stats, Admin ---

    suspend fun getMyPosts(cursor: String? = null, limit: Int = 20): ApiResult<OwnedPostsPageDto> =
        get(buildQuery("/api/me/posts", mapOf("cursor" to cursor, "limit" to limit.toString())), OwnedPostsPageDto.serializer())
            .map { page ->
                page.copy(
                    posts = page.posts.map {
                        it.copy(photoUrl = resolveUrl(it.photoUrl), avatarUrl = it.avatarUrl?.let(::resolveUrl))
                    },
                )
            }

    suspend fun getFeatures(limit: Int = 200, openOnly: Boolean = false): ApiResult<FeaturesResponse> =
        get(
            buildQuery("/api/features", mapOf("limit" to limit.toString(), "open" to if (openOnly) "1" else null)),
            FeaturesResponse.serializer(),
        )

    suspend fun createFeature(text: String): ApiResult<FeatureSuggestionDto> =
        postJson("/api/features", FeatureSuggestionDto.serializer(), CreateFeatureRequest(text), CreateFeatureRequest.serializer())

    suspend fun voteFeature(id: String): ApiResult<FeatureSuggestionDto> =
        postJson("/api/features/$id/vote", FeatureSuggestionDto.serializer(), Unit, EmptySerializer)

    suspend fun resolveFeature(id: String): ApiResult<FeatureSuggestionDto> =
        postJson("/api/features/$id/resolve", FeatureSuggestionDto.serializer(), Unit, EmptySerializer)

    suspend fun deleteFeature(id: String): ApiResult<OkResponse> =
        delete("/api/features/$id", OkResponse.serializer())

    suspend fun getStats(): ApiResult<StatsResponse> =
        get("/api/stats", StatsResponse.serializer())

    suspend fun claimOrCheckAdmin(): ApiResult<AdminMeResponse> =
        get("/api/admin/me", AdminMeResponse.serializer())

    suspend fun getAdminUsers(cursor: String? = null, limit: Int = 20): ApiResult<AdminUsersPageDto> =
        get(buildQuery("/api/admin/users", mapOf("cursor" to cursor, "limit" to limit.toString())), AdminUsersPageDto.serializer())
            .map { page -> page.copy(users = page.users.map { it.copy(photoUrl = it.photoUrl?.let(::resolveUrl)) }) }

    suspend fun pauseUser(userId: String): ApiResult<PauseUserResponse> =
        postJson("/api/admin/users/$userId/pause", PauseUserResponse.serializer(), Unit, EmptySerializer)

    suspend fun unpauseUser(userId: String): ApiResult<PauseUserResponse> =
        postJson("/api/admin/users/$userId/unpause", PauseUserResponse.serializer(), Unit, EmptySerializer)

    suspend fun getAdminPosts(cursor: String? = null, limit: Int = 20): ApiResult<AdminPostsPageDto> =
        get(buildQuery("/api/admin/posts", mapOf("cursor" to cursor, "limit" to limit.toString())), AdminPostsPageDto.serializer())
            .map { page ->
                page.copy(
                    posts = page.posts.map {
                        it.copy(
                            authorPhotoUrl = it.authorPhotoUrl?.let(::resolveUrl),
                            photoUrl = resolveUrl(it.photoUrl),
                            avatarUrl = it.avatarUrl?.let(::resolveUrl),
                        )
                    },
                )
            }

    suspend fun deleteAdminPost(postId: String): ApiResult<OkResponse> =
        delete("/api/admin/posts/$postId", OkResponse.serializer())

    suspend fun getAdminComments(cursor: String? = null, limit: Int = 50): ApiResult<AdminCommentsPageDto> =
        get(buildQuery("/api/admin/comments", mapOf("cursor" to cursor, "limit" to limit.toString())), AdminCommentsPageDto.serializer())
            .map { page -> page.copy(comments = page.comments.map(::normalizeCommentUrls)) }

    suspend fun deleteAdminComment(commentId: String): ApiResult<OkResponse> =
        delete("/api/admin/comments/$commentId", OkResponse.serializer())

    // --- URL normalization helpers ---

    private fun normalizePlaceUrls(place: PlaceSuggestionDto): PlaceSuggestionDto = place.copy(
        photoUrl = place.photoUrl?.let(::resolveUrl),
        googlePhotoUrl = place.googlePhotoUrl?.let(::resolveUrl),
    )

    private fun normalizeCommunityUrls(community: CommunityDto): CommunityDto = community.copy(
        latestPosts = community.latestPosts.map { it.copy(photoUrl = resolveUrl(it.photoUrl)) },
    )

    private fun normalizeCommunityMemberUrls(member: CommunityMemberDto): CommunityMemberDto = member.copy(
        photoUrl = member.photoUrl?.let(::resolveUrl),
        latestPostPhotoUrl = member.latestPostPhotoUrl?.let(::resolveUrl),
    )

    private fun normalizeProductUrls(product: ProductDto): ProductDto = product.copy(
        photoUrls = product.photoUrls.map(::resolveUrl),
        sellerPhotoUrl = product.sellerPhotoUrl?.let(::resolveUrl),
    )

    private fun normalizeActivityUrls(activity: ActivityDto): ActivityDto = activity.copy(
        host = activity.host?.let(::normalizePersonUrls),
        interestedPeople = activity.interestedPeople.map(::normalizePersonUrls),
        photoUrls = activity.photoUrls.map(::resolveUrl),
    )

    private fun normalizeChatUrls(chat: ChatDto): ChatDto = chat.copy(
        photos = chat.photos.map { it.copy(photoUrl = it.photoUrl?.let(::resolveUrl)) },
    )

    private fun normalizeChatMessageUrls(msg: ChatMessageDto): ChatMessageDto = msg.copy(
        authorPhotoUrl = msg.authorPhotoUrl?.let(::resolveUrl),
        photoUrls = msg.photoUrls.map(::resolveUrl),
        attachment = msg.attachment?.copy(photoUrl = msg.attachment.photoUrl?.let(::resolveUrl)),
    )

    // --- Low-level HTTP helpers ---

    private suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
        notifyOnUnauthorized: Boolean = true,
    ): ApiResult<T> = execute(deserializer, notifyOnUnauthorized) {
        httpClient.get(resolveUrl(path)) {
            attachAuth()
        }
    }

    private suspend fun <Req, Res> postJson(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        body: Req,
        serializer: SerializationStrategy<Req>,
        notifyOnUnauthorized: Boolean = true,
        onResponse: (HttpResponse) -> Unit = {},
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized, onResponse) {
        httpClient.post(resolveUrl(path)) {
            attachAuth()
            contentType(ContentType.Application.Json)
            if (serializer === EmptySerializer) {
                setBody("{}")
            } else {
                setBody(json.encodeToString(serializer, body))
            }
        }
    }

    private suspend fun <Req, Res> patchJson(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        body: Req,
        serializer: SerializationStrategy<Req>,
        notifyOnUnauthorized: Boolean = true,
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized) {
        httpClient.patch(resolveUrl(path)) {
            attachAuth()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(serializer, body))
        }
    }

    private suspend fun <Res> delete(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        notifyOnUnauthorized: Boolean = true,
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized) {
        httpClient.delete(resolveUrl(path)) {
            attachAuth()
        }
    }

    private suspend fun <Req, Res> deleteWithBody(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        body: Req,
        serializer: SerializationStrategy<Req>,
        notifyOnUnauthorized: Boolean = true,
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized) {
        httpClient.delete(resolveUrl(path)) {
            attachAuth()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(serializer, body))
        }
    }

    private suspend fun <Res> postMultipart(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        fields: Map<String, String> = emptyMap(),
        parts: List<UploadPart> = emptyList(),
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized = true) {
        httpClient.post(resolveUrl(path)) {
            attachAuth()
            setBody(multipartBody(fields, parts))
        }
    }

    private suspend fun <Res> patchMultipart(
        path: String,
        deserializer: DeserializationStrategy<Res>,
        fields: Map<String, String> = emptyMap(),
        parts: List<UploadPart> = emptyList(),
    ): ApiResult<Res> = execute(deserializer, notifyOnUnauthorized = true) {
        httpClient.patch(resolveUrl(path)) {
            attachAuth()
            setBody(multipartBody(fields, parts))
        }
    }

    private fun multipartBody(fields: Map<String, String>, parts: List<UploadPart>): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                fields.forEach { (key, value) ->
                    append(key, value)
                }
                parts.forEach { part ->
                    append(
                        part.fieldName,
                        part.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, part.contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName}\"")
                        },
                    )
                }
            },
        )

    private suspend fun HttpRequestBuilder.attachAuth() {
        val token = tokenStorage.readSessionToken()
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
            if (includeCookieHeaderOnRequests) {
                header(HttpHeaders.Cookie, "sq_session=$token")
            }
        }
    }

    private fun extractSessionCookie(response: HttpResponse): String? {
        val cookies = response.headers.getAll(HttpHeaders.SetCookie) ?: return null
        for (raw in cookies) {
            val match = SESSION_COOKIE_REGEX.find(raw)
            val value = match?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private suspend fun <Res> execute(
        deserializer: DeserializationStrategy<Res>,
        notifyOnUnauthorized: Boolean,
        onResponse: (HttpResponse) -> Unit = {},
        call: suspend () -> HttpResponse,
    ): ApiResult<Res> {
        val response = try {
            call()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return ApiErrorMapper.toFailure(0, "", languageProvider())
        }
        onResponse(response)
        val text = try {
            response.bodyAsText()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            ""
        }
        if (response.status == HttpStatusCode.Unauthorized && notifyOnUnauthorized) {
            tokenStorage.writeSessionToken(null)
            onUnauthorized()
        }
        if (!response.status.isSuccess()) {
            return ApiErrorMapper.toFailure(response.status.value, text, languageProvider())
        }
        return try {
            ApiResult.Success(json.decodeFromString(deserializer, text))
        } catch (_: Throwable) {
            ApiErrorMapper.toFailure(response.status.value, text, languageProvider())
        }
    }

    private fun buildQuery(path: String, params: Map<String, String?>): String {
        val pairs = params.entries
            .filter { !it.value.isNullOrBlank() }
            .map { "${it.key}=${it.value!!.encodeURLParameter()}" }
        return if (pairs.isEmpty()) path else "$path?${pairs.joinToString("&")}"
    }

    private object EmptySerializer : SerializationStrategy<Unit> {
        override val descriptor = OkResponse.serializer().descriptor
        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Unit) {}
    }

    private companion object {
        val SESSION_COOKIE_REGEX = Regex("\\bsq_session=([^;\\s]+)")
    }
}
