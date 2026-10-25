package quest.saigon.http

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay
import quest.saigon.AppConfig
import quest.saigon.CallToAction
import quest.saigon.Limits
import quest.saigon.MusicNames
import quest.saigon.PhoneNumbers
import quest.saigon.Language
import quest.saigon.languagePreference
import quest.saigon.resolveLanguage
import quest.saigon.ai.AvatarPrompts
import quest.saigon.media.ImageOps
import quest.saigon.town.AdminPost
import quest.saigon.town.AdminUser
import quest.saigon.town.AvatarRecord
import quest.saigon.town.AvatarStatus
import quest.saigon.town.ChatMemberView
import quest.saigon.town.ChatService
import quest.saigon.town.CommentItem
import quest.saigon.town.CommentService
import quest.saigon.town.FeatureSuggestionView
import quest.saigon.town.FeedItem
import quest.saigon.town.MusicRecord
import quest.saigon.town.MusicStatus
import quest.saigon.town.OwnedPost
import quest.saigon.town.PostRecord
import quest.saigon.town.PushSubscriptionRecord
import quest.saigon.town.TownException
import quest.saigon.town.SaigonDays
import quest.saigon.town.TownServices
import quest.saigon.town.UserRecord
import java.io.ByteArrayOutputStream
import java.io.File

private const val SESSION_COOKIE = "sq_session"
private const val SESSION_SECONDS = 30 * 24 * 60 * 60
private const val MAX_UPLOAD = 12_000_000

fun Route.townApi(services: TownServices) {
    post("/auth/register") {
        val body = Jsons.parse(call.receiveText())
        val name = cleanName(Jsons.text(body, "name")) ?: return@post call.fail("invalid_name")
        val phone = PhoneNumbers.canonicalize(Jsons.text(body, "phone")) ?: return@post call.fail("invalid_phone")
        val password = Jsons.text(body, "password")
        if (password.length !in Limits.PASSWORD_MIN..Limits.PASSWORD_MAX) return@post call.fail("invalid_password")
        val user = try {
            services.repo.createUser(phone, quest.saigon.auth.PasswordHasher.hash(password), name, services.clock())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.session(services, user.id)
        call.respondJson(mePayload(user, services))
    }

    post("/auth/login") {
        val body = Jsons.parse(call.receiveText())
        val phone = PhoneNumbers.canonicalize(Jsons.text(body, "phone")) ?: return@post call.fail("bad_credentials")
        val password = Jsons.text(body, "password")
        val user = services.repo.findUserByPhone(phone)
        if (user == null || !quest.saigon.auth.PasswordHasher.verify(password, user.passwordHash)) {
            return@post call.fail("bad_credentials")
        }
        call.session(services, user.id)
        call.respondJson(mePayload(user, services))
    }

    post("/auth/logout") {
        call.requestCookie()?.let(services.repo::deleteSession)
        call.clearSession(services)
        call.respondJson(mapOf("ok" to true))
    }

    get("/stats") {
        val days = services.repo.statsByDay(services.clock())
        val allTime = services.repo.allTimeStats()
        call.respondJson(
            mapOf(
                "timezone" to "UTC+7",
                "totalUsers" to allTime.totalUsers,
                "totalPosts" to allTime.totalPosts,
                "days" to days.map { row ->
                    mapOf(
                        "day" to row.day,
                        "visitorVisits" to row.visitorVisits,
                        "userVisits" to row.userVisits,
                        "uniqueUserVisits" to row.uniqueUserVisits,
                        "posts" to row.posts,
                        "newUsers" to row.newUsers,
                        "replyClicks" to row.replyClicks,
                        "shareClicks" to row.shareClicks,
                        "postViews" to row.postViews,
                    )
                },
            ),
        )
    }

    post("/stats/visit") {
        val user = call.user(services)
        val ip = clientIp(call, services.config.trustProxy)
        if (services.visitLimiter.allow(user?.id ?: ip)) {
            services.repo.recordVisit(user?.id, services.clock())
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/me") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.respondJson(mePayload(user, services))
    }

    patch("/me") {
        val current = call.user(services) ?: return@patch call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        var next = current
        if (body.has("name")) {
            next = next.copy(name = cleanName(Jsons.text(body, "name")) ?: return@patch call.fail("invalid_name"))
        }
        if (body.has("location")) {
            val location = Jsons.text(body, "location")
            if (location.length > Limits.LOCATION_MAX) return@patch call.fail("invalid_location")
            val placeId = cleanPlaceId(Jsons.text(body, "placeId"))
            val latRaw = Jsons.text(body, "lat")
            val lngRaw = Jsons.text(body, "lng")
            val lat = if (latRaw.isBlank()) null else parseCoord(latRaw) ?: return@patch call.fail("invalid_location")
            val lng = if (lngRaw.isBlank()) null else parseCoord(lngRaw) ?: return@patch call.fail("invalid_location")
            if ((lat == null) != (lng == null)) return@patch call.fail("invalid_location")
            next = next.copy(
                location = location,
                placeId = placeId,
                lat = lat.takeIf { placeId != null },
                lng = lng.takeIf { placeId != null },
            )
        }
        if (body.has("intro")) {
            next = next.copy(intro = cleanIntro(Jsons.text(body, "intro")) ?: return@patch call.fail("invalid_intro"))
        }
        if (body.has("language")) {
            val language = languagePreference(Jsons.text(body, "language")) ?: return@patch call.fail("invalid_language")
            next = next.copy(language = language)
        }
        if (body.has("phone")) {
            val phone = PhoneNumbers.canonicalize(Jsons.text(body, "phone")) ?: return@patch call.fail("invalid_phone")
            next = next.copy(phone = phone)
        }
        try {
            services.repo.updateUser(next)
        } catch (error: TownException) {
            return@patch call.fail(error.code)
        }
        call.respondJson(mePayload(services.repo.findUser(next.id) ?: next, services))
    }

    post("/me/photo") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val bytes = call.fileBytes("photo") ?: return@post call.fail("photo_required")
        val stored = try {
            services.uploads.writeProfile(services.images.prepareProfilePhoto(bytes))
        } catch (_: Exception) {
            return@post call.fail("invalid_photo")
        }
        val updated = user.copy(profilePhotoPath = stored)
        services.repo.updateUser(updated)
        try {
            services.avatars.enqueue(updated, AvatarPrompts.WAVE_HELLO)
        } catch (_: TownException) {
        }
        call.respondJson(mePayload(updated, services))
    }

    get("/me/photo") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val path = user.profilePhotoPath ?: return@get call.fail("not_found")
        val file = services.uploads.resolve(path) ?: return@get call.fail("not_found")
        call.response.headers.append("Cache-Control", "private, no-store")
        call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
    }

    get("/me/posts") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val page = services.repo.postsByUser(user.id, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::ownedPostPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    get("/features") {
        val viewer = call.user(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val openOnly = call.request.queryParameters["open"].orEmpty().let {
            it == "1" || it.equals("true", true)
        }
        call.respondJson(
            mapOf("features" to services.repo.featureSuggestions(viewer?.id, limit, openOnly).map(::featurePayload)),
        )
    }

    post("/features") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        if (user.paused) return@post call.fail("account_paused")
        val text = cleanFeature(Jsons.text(Jsons.parse(call.receiveText()), "text"))
            ?: return@post call.fail("invalid_feature")
        if (text.isEmpty()) return@post call.fail("invalid_feature")
        val stored = services.repo.insertFeatureSuggestion(user.id, text, services.clock())
        val view = services.repo.featureSuggestionView(stored.id, user.id) ?: FeatureSuggestionView(
            id = stored.id,
            text = stored.text,
            voteCount = 0,
            voted = false,
            resolved = false,
            createdAt = stored.createdAt,
        )
        call.respondJson(featurePayload(view), HttpStatusCode.Created)
    }

    post("/features/{id}/vote") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        if (user.paused) return@post call.fail("account_paused")
        val view = services.repo.toggleFeatureVote(call.parameters["id"].orEmpty(), user.id, services.clock())
            ?: return@post call.fail("not_found")
        call.respondJson(featurePayload(view))
    }

    post("/features/{id}/resolve") {
        val admin = call.requireAdmin(services) ?: return@post
        val id = call.parameters["id"].orEmpty()
        services.repo.resolveFeatureSuggestion(id, services.clock()) ?: return@post call.fail("not_found")
        val view = services.repo.featureSuggestionView(id, admin.id) ?: return@post call.fail("not_found")
        call.respondJson(featurePayload(view))
    }

    delete("/features/{id}") {
        call.requireAdmin(services) ?: return@delete
        if (!services.repo.deleteFeatureSuggestion(call.parameters["id"].orEmpty())) {
            return@delete call.fail("not_found")
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/avatars") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.respondJson(mapOf("avatars" to services.repo.avatarsFor(user.id).map(::avatarPayload)))
    }

    get("/avatars/{id}") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val avatar = services.repo.findAvatar(call.parameters["id"].orEmpty())
        if (avatar == null || avatar.userId != user.id) return@get call.fail("not_found")
        call.respondJson(avatarPayload(avatar))
    }

    post("/avatars") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val prompt = Jsons.text(Jsons.parse(call.receiveText()), "prompt")
        if (prompt.length !in 1..Limits.PROMPT_MAX) return@post call.fail("invalid_prompt")
        val avatar = try {
            services.avatars.enqueue(user, prompt)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(avatarPayload(avatar), HttpStatusCode.Accepted)
    }

    post("/avatars/{id}/retry") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val avatar = try {
            services.avatars.retry(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(avatarPayload(avatar), HttpStatusCode.Accepted)
    }

    delete("/avatars/{id}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        try {
            services.avatars.delete(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/music") {
        call.user(services) ?: return@get call.fail("unauthorized")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val page = services.repo.searchMusic(
            call.request.queryParameters["q"].orEmpty(),
            call.request.queryParameters["cursor"],
            limit,
        )
        call.respondJson(
            mapOf(
                "music" to page.items.map(::musicPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    get("/me/music") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.respondJson(mapOf("music" to services.repo.musicsFor(user.id).map(::musicPayload)))
    }

    get("/music/{id}") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val music = services.repo.findMusic(call.parameters["id"].orEmpty())
        if (music == null || music.userId != user.id) return@get call.fail("not_found")
        call.respondJson(musicPayload(music))
    }

    post("/music") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val prompt = Jsons.text(body, "prompt")
        if (prompt.length !in 1..Limits.MUSIC_PROMPT_MAX) return@post call.fail("invalid_music_prompt")
        val gender = Jsons.text(body, "gender").lowercase().ifBlank { null }
        if (gender != null && gender != "male" && gender != "female") return@post call.fail("invalid_gender")
        val instrumental = body.path("instrumental").asBoolean(false)
        val music = try {
            services.music.enqueue(user, prompt, gender, instrumental)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(musicPayload(music), HttpStatusCode.Accepted)
    }

    post("/music/{id}/retry") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val music = try {
            services.music.retry(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(musicPayload(music), HttpStatusCode.Accepted)
    }

    patch("/music/{id}") {
        val user = call.user(services) ?: return@patch call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val name = MusicNames.cleanTitle(Jsons.text(body, "name")) ?: return@patch call.fail("invalid_music_name")
        val music = try {
            services.music.rename(user, call.parameters["id"].orEmpty(), name)
        } catch (error: TownException) {
            return@patch call.fail(error.code)
        }
        call.respondJson(musicPayload(music))
    }

    delete("/music/{id}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        try {
            services.music.delete(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/config") {
        call.respondJson(
            mapOf(
                "mapsKey" to services.config.googleMapsApiKey,
            ),
        )
    }

    get("/posts") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: Limits.FEED_PAGE_SIZE
        val viewer = call.user(services)
        val page = services.repo.feed(call.request.queryParameters["cursor"], limit, viewer?.id)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::postPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    post("/posts") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        if (user.paused) return@post call.fail("account_paused")
        val form = call.formParts()
        val message = form.text("message").trim()
        if (message.length !in 1..Limits.MESSAGE_MAX) return@post call.fail("invalid_message")
        val avatarId = form.text("avatarId").trim().ifBlank { null }
        val avatar = avatarId?.let { services.repo.findAvatar(it) }
        if (avatarId != null && (avatar == null || avatar.userId != user.id || avatar.status != AvatarStatus.READY || avatar.imagePath == null)) {
            return@post call.fail("avatar_required")
        }
        val location = form.text("location").trim().ifBlank { user.location.trim() }
        if (location.isEmpty() || location.length > Limits.LOCATION_MAX) return@post call.fail("invalid_location")
        val firstCommentRaw = form.text("firstComment")
        val firstComment = if (firstCommentRaw.isBlank()) {
            null
        } else {
            CommentService.cleanComment(firstCommentRaw) ?: return@post call.fail("invalid_comment")
        }
        val musicId = form.text("musicId").trim().ifBlank { null }
        val music = musicId?.let { services.repo.findMusic(it) }
        if (musicId != null && (music == null || music.status != MusicStatus.READY || music.audioPath == null)) {
            return@post call.fail("invalid_music")
        }
        val communityId = form.text("communityId").trim().ifBlank { null }
        val communityOnlyRaw = form.text("communityOnly").trim()
        val communityOnly = communityOnlyRaw.equals("true", true) || communityOnlyRaw == "1"
        val community = communityId?.let { services.repo.findCommunity(it) }
        if (communityId != null && community == null) return@post call.fail("invalid_community")
        if (communityOnly && community == null) return@post call.fail("invalid_community")
        val placeId = cleanPlaceId(form.text("placeId"))
        val lat = parseCoord(form.text("lat"))
        val lng = parseCoord(form.text("lng"))
        if ((lat == null) != (lng == null)) return@post call.fail("invalid_location")
        val eventStartAt = parseEpoch(form.text("eventStartAt"))
        val eventEndAt = parseEpoch(form.text("eventEndAt"))
        val eventPrice = try {
            parseVnd(form.text("eventPrice"))
        } catch (_: TownException) {
            return@post call.fail("invalid_event_price")
        }
        if (eventEndAt != null && eventStartAt == null) return@post call.fail("invalid_event")
        if (eventPrice != null && eventStartAt == null) return@post call.fail("invalid_event")
        if (eventStartAt != null && eventEndAt != null && eventEndAt < eventStartAt) return@post call.fail("invalid_event")
        val pollRaw = (0 until Limits.POLL_OPTIONS_MAX).map { form.text("pollOption$it") }
        val pollOptions = if (pollRaw.all { it.isBlank() }) {
            emptyList()
        } else {
            quest.saigon.PollNames.clean(pollRaw) ?: return@post call.fail("invalid_poll")
        }
        val askRaw = form.text("askQuestion").trim()
        val askQuestion = askRaw.equals("true", true) || askRaw == "1"
        val questionTitle = if (!askQuestion) {
            ""
        } else {
            quest.saigon.town.QuestionService.cleanTitle(form.text("questionTitle"))
                ?: return@post call.fail("invalid_question")
        }
        val addCtaRaw = form.text("addCta").trim()
        val addCta = addCtaRaw.equals("true", true) || addCtaRaw == "1"
        val cta = if (!addCta) {
            null
        } else {
            CallToAction.parse(
                form.text("ctaTitle"),
                form.text("ctaText"),
                form.text("ctaDestination"),
                form.text("ctaUrl"),
            ) ?: run {
                val dest = form.text("ctaDestination").trim().lowercase()
                val fail = if (dest == CallToAction.WEBSITE && CallToAction.cleanUrl(form.text("ctaUrl")) == null) {
                    "invalid_cta_url"
                } else {
                    "invalid_cta"
                }
                return@post call.fail(fail)
            }
        }
        val photos = form.files("photo")
        if (photos.isEmpty()) return@post call.fail("photo_required")
        if (photos.size > Limits.PHOTOS_MAX) return@post call.fail("too_many_photos")
        val paths = mutableListOf<String>()
        for (photo in photos) {
            val jpeg = try {
                services.images.preparePostPhoto(photo)
            } catch (_: Exception) {
                return@post call.fail("invalid_photo")
            }
            paths += services.uploads.writePost(jpeg)
        }
        val post = services.repo.insertPost(
            PostRecord(
                id = "",
                userId = user.id,
                avatarId = avatar?.id,
                photoPath = paths.first(),
                message = message,
                location = location,
                createdAt = services.clock(),
                musicId = music?.id,
                extraPhotoPaths = paths.drop(1),
                communityId = community?.id,
                communityOnly = communityOnly,
                placeId = placeId,
                lat = lat,
                lng = lng,
                eventStartAt = eventStartAt,
                eventEndAt = eventEndAt,
                eventPrice = eventPrice,
                pollOptions = pollOptions,
                hasQuestion = askQuestion,
                questionTitle = questionTitle,
                ctaTitle = cta?.title.orEmpty(),
                ctaText = cta?.text.orEmpty(),
                ctaDestination = cta?.destination.orEmpty(),
                ctaUrl = cta?.url.orEmpty(),
            ),
        )
        if (eventStartAt != null) services.repo.joinEvent(post.id, user.id, services.clock())
        if (community != null) services.repo.joinCommunity(community.id, user.id, services.clock())
        if (firstComment != null) services.comments.add(user, post.id, firstComment, null)
        services.replies.enqueue(post.id, message)
        call.respondJson(
            postPayload(
                services.repo.feedItem(post.id, user.id) ?: FeedItem(
                    id = post.id,
                    authorId = user.id,
                    authorName = user.name,
                    authorPhotoPath = user.profilePhotoPath,
                    message = post.message,
                    location = post.location,
                    photoPath = post.photoPath,
                    avatarPath = avatar?.imagePath,
                    authorLocation = user.location,
                    authorIntro = user.intro,
                    createdAt = post.createdAt,
                    musicName = music?.name,
                    musicPath = music?.audioPath,
                    extraPhotoPaths = post.extraPhotoPaths,
                    communityId = community?.id,
                    communityName = community?.name,
                    communityEmoji = community?.emoji,
                    communityOnly = communityOnly,
                    placeId = post.placeId,
                    lat = post.lat,
                    lng = post.lng,
                    eventStartAt = post.eventStartAt,
                    eventEndAt = post.eventEndAt,
                    eventPrice = post.eventPrice,
                    attendeeCount = 1,
                    joined = true,
                    pollOptions = pollOptions.mapIndexed { index, text ->
                        quest.saigon.town.PollOptionView(index, text)
                    },
                    hasQuestion = askQuestion,
                    questionTitle = questionTitle,
                    ctaTitle = cta?.title.orEmpty(),
                    ctaText = cta?.text.orEmpty(),
                    ctaDestination = cta?.destination.orEmpty(),
                    ctaUrl = cta?.url.orEmpty(),
                    authorZaloUrl = if (cta?.destination == CallToAction.ZALO) {
                        PhoneNumbers.zaloUrl(user.phone)
                    } else {
                        null
                    },
                ),
            ),
            HttpStatusCode.Created,
        )
    }

    get("/posts/{id}") {
        val viewer = call.user(services)
        val item = services.repo.feedItem(call.parameters["id"].orEmpty(), viewer?.id) ?: return@get call.fail("not_found")
        call.respondJson(postPayload(item))
    }

    post("/posts/{id}/join") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val item = try {
            services.events.join(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(postPayload(item))
    }

    post("/posts/{id}/leave") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val item = try {
            services.events.leave(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(postPayload(item))
    }

    post("/posts/{id}/poll/{index}") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val index = call.parameters["index"]?.toIntOrNull() ?: return@post call.fail("invalid_poll")
        val item = try {
            services.polls.vote(user, call.parameters["id"].orEmpty(), index)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(postPayload(item))
    }

    get("/posts/{id}/voters") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        if (post.pollOptions.isEmpty()) return@get call.fail("not_found")
        val viewer = call.user(services)
        val options = services.repo.pollOptions(post.id, viewer?.id, 200)
        call.respondJson(mapOf("options" to options.map { pollOptionPayload(it, includePhone = false) }))
    }

    post("/posts/{id}/answers") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val item = try {
            services.questions.answer(user, call.parameters["id"].orEmpty(), Jsons.text(body, "text"))
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(postPayload(item))
    }

    get("/posts/{id}/answers") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val answers = try {
            services.questions.answers(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        call.respondJson(mapOf("answers" to answers.map(::questionAnswerPayload)))
    }

    get("/posts/{id}/attendees") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        if (post.eventStartAt == null) return@get call.fail("not_found")
        val viewer = call.user(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 30
        val page = services.repo.attendees(post.id, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "attendees" to page.items.map { attendeePayload(it, includePhone = viewer != null) },
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    get("/events/summary") {
        val today = SaigonDays.dayKey(services.clock())
        val todayStart = SaigonDays.startMillis(today)
        val tomorrowStart = todayStart + SaigonDays.DAY_MILLIS
        val tomorrowEnd = tomorrowStart + SaigonDays.DAY_MILLIS
        val weekEnd = todayStart + SaigonDays.SUMMARY_DAYS * SaigonDays.DAY_MILLIS
        val counts = services.repo.eventCounts(todayStart, tomorrowStart, tomorrowEnd, weekEnd)
        call.respondJson(mapOf("today" to counts.today, "tomorrow" to counts.tomorrow, "week" to counts.week))
    }

    get("/events") {
        val viewer = call.user(services)
        val todayStart = SaigonDays.startMillis(SaigonDays.dayKey(services.clock()))
        val events = services.repo.upcomingEvents(todayStart, viewer?.id, 100)
        val people = services.repo.people(call.request.queryParameters["cursor"], 40)
        call.respondJson(
            mapOf(
                "events" to events.map(::postPayload),
                "people" to people.items.map(::memberPayload),
                "nextPeopleCursor" to people.nextCursor,
            ),
        )
    }

    get("/places") {
        val viewer = call.user(services) ?: return@get call.fail("unauthorized")
        val query = call.request.queryParameters["q"].orEmpty()
        val communityId = call.request.queryParameters["communityId"]?.trim()?.ifBlank { null }
        val recent = services.repo.recentPlaces(communityId, 8).map { place ->
            place.copy(postCount = services.repo.countPostsAtPlace(place.name, place.placeId, viewer.id))
        }
        val google = if (query.trim().length >= 2) {
            services.places.search(query).map { place ->
                place.copy(postCount = services.repo.countPostsAtPlace(place.name, place.placeId, viewer.id))
            }
        } else {
            emptyList()
        }
        call.respondJson(
            mapOf(
                "recent" to recent.map(::placePayload),
                "places" to google.map(::placePayload),
            ),
        )
    }

    get("/places/details") {
        call.user(services) ?: return@get call.fail("unauthorized")
        val placeId = call.request.queryParameters["id"].orEmpty()
        val details = services.places.details(placeId) ?: return@get call.fail("not_found")
        call.respondJson(placePayload(details))
    }

    get("/places/posts") {
        val name = call.request.queryParameters["name"]?.trim().orEmpty()
        if (name.isEmpty() || name.length > Limits.LOCATION_MAX) return@get call.fail("invalid_location")
        val placeId = cleanPlaceId(call.request.queryParameters["placeId"].orEmpty())
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 48) ?: 24
        val viewer = call.user(services)
        val page = services.repo.postsAtPlace(name, placeId, call.request.queryParameters["cursor"], limit, viewer?.id)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::postPayload),
                "nextCursor" to page.nextCursor,
                "count" to services.repo.countPostsAtPlace(name, placeId, viewer?.id),
            ),
        )
    }

    get("/posts/{id}/comments") {
        val postId = call.parameters["id"].orEmpty()
        services.repo.findPost(postId) ?: return@get call.fail("not_found")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, Limits.COMMENT_PAGE_SIZE)
            ?: Limits.COMMENT_PAGE_SIZE
        val page = services.repo.commentsForPost(postId, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "comments" to page.items.map(::commentPayload),
                "nextCursor" to page.nextCursor,
                "total" to page.total,
            ),
        )
    }

    post("/posts/{id}/comments") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val item = try {
            services.comments.add(
                user,
                call.parameters["id"].orEmpty(),
                Jsons.text(body, "text"),
                Jsons.text(body, "replyToId").ifBlank { null },
            )
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(commentPayload(item), HttpStatusCode.Created)
    }

    delete("/posts/{id}/comments/{commentId}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        try {
            services.comments.remove(user, call.parameters["id"].orEmpty(), call.parameters["commentId"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/users/{id}/photo") {
        val user = services.repo.findUser(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        val path = user.profilePhotoPath ?: return@get call.fail("not_found")
        val file = services.uploads.resolve(path) ?: return@get call.fail("not_found")
        call.response.headers.append("Cache-Control", "public, max-age=300")
        call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
    }

    get("/notifications") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
        val page = services.repo.notificationsFor(user.id, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "notifications" to page.items.map(CommentService::notificationPayload),
                "nextCursor" to page.nextCursor,
                "unreadCount" to page.unreadCount,
            ),
        )
    }

    post("/notifications/read-all") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val unread = services.repo.markAllNotificationsRead(user.id, services.clock())
        call.respondJson(mapOf("ok" to true, "unreadCount" to unread))
    }

    post("/notifications/{id}/read") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val notification = services.repo.findNotification(call.parameters["id"].orEmpty())
            ?: return@post call.fail("not_found")
        if (notification.userId != user.id) return@post call.fail("not_found")
        val updated = services.repo.markNotificationRead(notification.id, services.clock()) ?: notification
        call.respondJson(CommentService.notificationPayload(updated))
    }

    get("/notifications/stream") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.response.cacheControl(CacheControl.NoCache(null))
        call.response.headers.append(HttpHeaders.Connection, "keep-alive")
        call.response.headers.append("X-Accel-Buffering", "no")
        val queue = java.util.concurrent.LinkedBlockingQueue<String>()
        val cancel = services.hub.subscribe(user.id) { json -> queue.offer(json) }
        try {
            call.respondBytesWriter(contentType = ContentType.parse("text/event-stream")) {
                writeStringUtf8("event: ready\ndata: {}\n\n")
                flush()
                var idle = 0
                while (!isClosedForWrite) {
                    val event = queue.poll()
                    if (event != null) {
                        idle = 0
                        writeStringUtf8("data: $event\n\n")
                        flush()
                    } else {
                        delay(400)
                        idle += 400
                        if (idle >= 15_000) {
                            writeStringUtf8(": ping\n\n")
                            flush()
                            idle = 0
                        }
                    }
                }
            }
        } finally {
            cancel()
        }
    }

    get("/push/vapid") {
        call.user(services) ?: return@get call.fail("unauthorized")
        val key = services.config.vapidPublicKey?.takeIf { services.config.webPushConfigured }
            ?: return@get call.fail("unavailable")
        call.respondJson(mapOf("publicKey" to key))
    }

    post("/push/subscribe") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        if (!services.config.webPushConfigured) return@post call.fail("unavailable")
        val body = Jsons.parse(call.receiveText())
        val endpoint = cleanPushEndpoint(Jsons.text(body, "endpoint")) ?: return@post call.fail("invalid_push")
        val p256dh = cleanPushKey(Jsons.text(body.path("keys"), "p256dh").ifBlank { Jsons.text(body, "p256dh") })
            ?: return@post call.fail("invalid_push")
        val auth = cleanPushKey(Jsons.text(body.path("keys"), "auth").ifBlank { Jsons.text(body, "auth") })
            ?: return@post call.fail("invalid_push")
        val language = if (resolveLanguage(Jsons.text(body, "language"), user.language) == Language.VI) "vi" else "en"
        services.repo.upsertPushSubscription(
            PushSubscriptionRecord(
                endpoint = endpoint,
                userId = user.id,
                p256dh = p256dh,
                auth = auth,
                language = language,
                createdAt = services.clock(),
            ),
        )
        call.respondJson(mapOf("ok" to true))
    }

    delete("/push/subscribe") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val endpoint = cleanPushEndpoint(Jsons.text(Jsons.parse(call.receiveText()), "endpoint"))
            ?: return@delete call.fail("invalid_push")
        services.repo.deletePushSubscription(user.id, endpoint)
        call.respondJson(mapOf("ok" to true))
    }

    get("/posts/{id}/author-photo") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        val author = services.repo.findUser(post.userId) ?: return@get call.fail("not_found")
        val path = author.profilePhotoPath ?: return@get call.fail("not_found")
        val file = services.uploads.resolve(path) ?: return@get call.fail("not_found")
        call.response.headers.append("Cache-Control", "public, max-age=300")
        call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
    }

    post("/posts/{id}/view") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@post call.fail("not_found")
        val viewer = call.user(services)
        if (viewer?.id != post.userId) {
            val ip = clientIp(call, services.config.trustProxy)
            if (services.viewLimiter.allow("$ip:${post.id}")) {
                services.repo.recordView(post.id)
                services.repo.recordDailyEvent("postViews", services.clock())
            }
        }
        call.respondJson(mapOf("ok" to true))
    }

    post("/posts/{id}/contact") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@post call.fail("not_found")
        val author = services.repo.findUser(post.userId) ?: return@post call.fail("not_found")
        val actor = call.user(services)
        if (actor?.id != post.userId) {
            val ip = clientIp(call, services.config.trustProxy)
            if (!services.contactLimiter.allow(ip)) return@post call.fail("rate_limited")
            services.repo.recordReplyClick(post.id)
            services.repo.recordDailyEvent("replyClicks", services.clock())
        }
        call.respondJson(mapOf("zaloUrl" to PhoneNumbers.zaloUrl(author.phone)))
    }

    post("/posts/{id}/share") {
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@post call.fail("not_found")
        val actor = call.user(services)
        if (actor?.id != post.userId) {
            services.repo.recordShareClick(post.id)
            services.repo.recordDailyEvent("shareClicks", services.clock())
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/communities") {
        val user = call.user(services)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 30
        val items = services.repo.searchCommunities(call.request.queryParameters["q"].orEmpty(), user?.id, limit)
        call.respondJson(mapOf("communities" to items.map { communityPayload(it.community, it.joined) }))
    }

    post("/communities/suggest") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val text = Jsons.text(Jsons.parse(call.receiveText()), "text").trim()
        if (text.length !in 1..Limits.MESSAGE_MAX) return@post call.fail("invalid_message")
        val suggestion = try {
            services.communities.suggest(user.id, text)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        val match = services.repo.findCommunityBySlug(quest.saigon.town.CommunityNames.slug(suggestion.name))
        call.respondJson(
            mapOf(
                "emoji" to suggestion.emoji,
                "name" to suggestion.name,
                "communityId" to match?.id,
            ),
        )
    }

    post("/communities/emoji") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val name = Jsons.text(Jsons.parse(call.receiveText()), "name").trim()
        if (quest.saigon.town.CommunityNames.clean(name) == null) return@post call.fail("invalid_community")
        val emoji = try {
            services.communities.suggestEmoji(user.id, name)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(mapOf("emoji" to emoji))
    }

    post("/communities") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val opened = try {
            services.communities.open(user, Jsons.text(body, "name"), Jsons.text(body, "emoji"))
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(
            communityPayload(opened.community, true),
            if (opened.created) HttpStatusCode.Created else HttpStatusCode.OK,
        )
    }

    get("/communities/{id}") {
        val community = services.repo.findCommunity(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        val user = call.user(services)
        call.respondJson(communityPayload(community, user != null && services.repo.isMember(community.id, user.id)))
    }

    patch("/communities/{id}") {
        val user = call.user(services) ?: return@patch call.fail("unauthorized")
        val community = services.repo.findCommunity(call.parameters["id"].orEmpty()) ?: return@patch call.fail("not_found")
        if (!services.repo.isMember(community.id, user.id)) return@patch call.fail("forbidden")
        val description = cleanDescription(Jsons.text(Jsons.parse(call.receiveText()), "description"))
            ?: return@patch call.fail("invalid_description")
        val updated = services.repo.updateCommunityDescription(community.id, description) ?: return@patch call.fail("not_found")
        call.respondJson(communityPayload(updated, true))
    }

    post("/communities/{id}/join") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val community = services.repo.findCommunity(call.parameters["id"].orEmpty()) ?: return@post call.fail("not_found")
        services.repo.joinCommunity(community.id, user.id, services.clock())
        val fresh = services.repo.findCommunity(community.id) ?: community
        call.respondJson(communityPayload(fresh, true))
    }

    post("/communities/{id}/leave") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val community = services.repo.findCommunity(call.parameters["id"].orEmpty()) ?: return@post call.fail("not_found")
        services.repo.leaveCommunity(community.id, user.id)
        val fresh = services.repo.findCommunity(community.id) ?: community
        call.respondJson(communityPayload(fresh, false))
    }

    get("/communities/{id}/members") {
        val id = call.parameters["id"].orEmpty()
        services.repo.findCommunity(id) ?: return@get call.fail("not_found")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 30
        val page = services.repo.members(id, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "members" to page.items.map(::memberPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    get("/communities/{id}/posts") {
        val id = call.parameters["id"].orEmpty()
        services.repo.findCommunity(id) ?: return@get call.fail("not_found")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: Limits.FEED_PAGE_SIZE
        val viewer = call.user(services)
        val page = services.repo.communityFeed(id, call.request.queryParameters["cursor"], limit, viewer?.id)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::postPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    get("/me/communities") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.respondJson(
            mapOf(
                "communities" to services.repo.communitiesForUser(user.id).map { communityPayload(it, true) },
            ),
        )
    }

    get("/users/{id}/communities") {
        val user = services.repo.findUser(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        val viewer = call.user(services)
        call.respondJson(
            mapOf(
                "communities" to services.repo.communitiesForUser(user.id).map { community ->
                    communityPayload(community, viewer != null && services.repo.isMember(community.id, viewer.id))
                },
            ),
        )
    }

    get("/users/{id}/posts") {
        val user = services.repo.findUser(call.parameters["id"].orEmpty()) ?: return@get call.fail("not_found")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 48) ?: 24
        val viewer = call.user(services)
        val page = services.repo.postsByAuthor(user.id, call.request.queryParameters["cursor"], limit, viewer?.id)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::postPayload),
                "nextCursor" to page.nextCursor,
                "count" to services.repo.countPostsByAuthor(user.id, viewer?.id),
            ),
        )
    }

    patch("/posts/{id}") {
        val user = call.user(services) ?: return@patch call.fail("unauthorized")
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@patch call.fail("not_found")
        if (post.userId != user.id) return@patch call.fail("not_found")
        val message = Jsons.text(Jsons.parse(call.receiveText()), "message").trim()
        if (message.length !in 1..Limits.MESSAGE_MAX) return@patch call.fail("invalid_message")
        services.repo.updatePostMessage(post.id, message)
        call.respondJson(mapOf("ok" to true, "message" to message))
    }

    delete("/posts/{id}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@delete call.fail("not_found")
        if (post.userId != user.id) return@delete call.fail("not_found")
        services.repo.deletePost(post.id)
        deletePostPhotos(services, post)
        call.respondJson(mapOf("ok" to true))
    }

    get("/admin/me") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        if (services.repo.findAdmin(user.id) != null || services.repo.claimFirstAdmin(user.id, services.clock())) {
            call.respondJson(mapOf("admin" to true))
        } else {
            call.fail("forbidden")
        }
    }

    get("/admin/users") {
        call.requireAdmin(services) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val page = services.repo.usersPage(call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "users" to page.items.map(::adminUserPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    post("/admin/users/{id}/pause") {
        call.requireAdmin(services) ?: return@post
        val user = services.repo.setUserPaused(call.parameters["id"].orEmpty(), true) ?: return@post call.fail("not_found")
        call.respondJson(mapOf("id" to user.id, "paused" to user.paused))
    }

    post("/admin/users/{id}/unpause") {
        call.requireAdmin(services) ?: return@post
        val user = services.repo.setUserPaused(call.parameters["id"].orEmpty(), false) ?: return@post call.fail("not_found")
        call.respondJson(mapOf("id" to user.id, "paused" to user.paused))
    }

    get("/admin/posts") {
        call.requireAdmin(services) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val page = services.repo.adminPosts(call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::adminPostPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    delete("/admin/posts/{id}") {
        call.requireAdmin(services) ?: return@delete
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@delete call.fail("not_found")
        services.repo.deletePost(post.id)
        deletePostPhotos(services, post)
        call.respondJson(mapOf("ok" to true))
    }

    get("/admin/comments") {
        call.requireAdmin(services) ?: return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
        val page = services.repo.adminComments(call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "comments" to page.items.map(::commentPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    delete("/admin/comments/{id}") {
        val user = call.requireAdmin(services) ?: return@delete
        try {
            services.comments.remove(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/chats") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
        val page = services.chats.list(user, call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "chats" to page.items.map(ChatService::chatPayload),
                "nextCursor" to page.nextCursor,
                "unreadCount" to page.unreadCount,
            ),
        )
    }

    get("/chats/stream") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        call.response.cacheControl(CacheControl.NoCache(null))
        call.response.headers.append(HttpHeaders.Connection, "keep-alive")
        call.response.headers.append("X-Accel-Buffering", "no")
        val queue = java.util.concurrent.LinkedBlockingQueue<String>()
        val cancel = services.chatHub.subscribe(user.id) { json -> queue.offer(json) }
        try {
            call.respondBytesWriter(contentType = ContentType.parse("text/event-stream")) {
                writeStringUtf8("event: ready\ndata: {}\n\n")
                flush()
                var idle = 0
                while (!isClosedForWrite) {
                    val event = queue.poll()
                    if (event != null) {
                        idle = 0
                        writeStringUtf8("data: $event\n\n")
                        flush()
                    } else {
                        delay(400)
                        idle += 400
                        if (idle >= 15_000) {
                            writeStringUtf8(": ping\n\n")
                            flush()
                            idle = 0
                        }
                    }
                }
            }
        } finally {
            cancel()
        }
    }

    get("/chats/direct/{userId}") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val chat = services.chats.findDirect(user, call.parameters["userId"].orEmpty())
            ?: return@get call.fail("not_found")
        call.respondJson(ChatService.chatPayload(chat))
    }

    post("/chats") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val item = try {
            val otherId = Jsons.text(body, "userId")
            if (otherId.isNotBlank()) {
                services.chats.createDirect(user, otherId, Jsons.text(body, "text"))
            } else {
                services.chats.createGroup(user, Jsons.text(body, "name"))
            }
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(ChatService.chatPayload(item), HttpStatusCode.Created)
    }

    get("/chats/{id}") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val chat = try {
            services.chats.get(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        val payload = ChatService.chatPayload(chat).toMutableMap()
        payload["defaultReactions"] = services.chats.defaultReactions(chat.id)
        call.respondJson(payload)
    }

    post("/chats/{id}/read") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        try {
            services.chats.read(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true, "unreadCount" to services.repo.unreadChatNotificationCount(user.id)))
    }

    post("/chats/{id}/leave") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val photos = try {
            services.chats.leave(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        photos.forEach { services.uploads.delete(it) }
        call.respondJson(mapOf("ok" to true))
    }

    delete("/chats/{id}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val photos = try {
            services.chats.deleteGroup(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        photos.forEach { services.uploads.delete(it) }
        call.respondJson(mapOf("ok" to true))
    }

    patch("/chats/{id}") {
        val user = call.user(services) ?: return@patch call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val chat = try {
            services.chats.renameGroup(user, call.parameters["id"].orEmpty(), Jsons.text(body, "name"))
        } catch (error: TownException) {
            return@patch call.fail(error.code)
        }
        call.respondJson(ChatService.chatPayload(chat))
    }

    get("/chats/{id}/members") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val members = try {
            services.chats.members(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        call.respondJson(mapOf("members" to members.map(::chatMemberPayload)))
    }

    post("/chats/{id}/members") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val body = Jsons.parse(call.receiveText())
        val chat = try {
            services.chats.addMember(user, call.parameters["id"].orEmpty(), Jsons.text(body, "userId"))
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(ChatService.chatPayload(chat))
    }

    delete("/chats/{id}/members/{userId}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val chat = try {
            services.chats.removeMember(user, call.parameters["id"].orEmpty(), call.parameters["userId"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(ChatService.chatPayload(chat))
    }

    post("/chats/{id}/members/{userId}/owner") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val chat = try {
            services.chats.makeOwner(user, call.parameters["id"].orEmpty(), call.parameters["userId"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(ChatService.chatPayload(chat))
    }

    get("/chats/{id}/messages") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, Limits.CHAT_MESSAGE_PAGE_SIZE)
            ?: Limits.CHAT_MESSAGE_PAGE_SIZE
        val page = try {
            services.chats.messages(user, call.parameters["id"].orEmpty(), call.request.queryParameters["cursor"], limit)
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        call.respondJson(
            mapOf(
                "messages" to page.items.map(ChatService::messagePayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    post("/chats/{id}/messages") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val contentType = call.request.headers[HttpHeaders.ContentType].orEmpty()
        val text: String
        val photos: List<ByteArray>
        if (contentType.contains("multipart", ignoreCase = true)) {
            val form = call.formParts()
            text = form.text("text")
            photos = form.files("photo")
        } else {
            text = Jsons.text(Jsons.parse(call.receiveText()), "text")
            photos = emptyList()
        }
        if (photos.size > Limits.PHOTOS_MAX) return@post call.fail("too_many_photos")
        val paths = mutableListOf<String>()
        for (photo in photos) {
            val jpeg = try {
                services.images.prepareChatPhoto(photo)
            } catch (_: Exception) {
                return@post call.fail("invalid_photo")
            }
            paths += services.uploads.writeChat(jpeg)
        }
        val item = try {
            services.chats.send(user, call.parameters["id"].orEmpty(), text, paths)
        } catch (error: TownException) {
            paths.forEach { services.uploads.delete(it) }
            return@post call.fail(error.code)
        }
        call.respondJson(ChatService.messagePayload(item), HttpStatusCode.Created)
    }

    delete("/chats/{id}/messages/{messageId}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val photos = try {
            services.chats.deleteMessage(user, call.parameters["id"].orEmpty(), call.parameters["messageId"].orEmpty())
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        photos.forEach { services.uploads.delete(it) }
        call.respondJson(mapOf("ok" to true))
    }

    get("/chats/{id}/messages/{messageId}/reactions") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val details = try {
            services.chats.reactionDetails(user, call.parameters["id"].orEmpty(), call.parameters["messageId"].orEmpty())
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        call.respondJson(mapOf("reactions" to details.map(ChatService::reactionDetailPayload)))
    }

    post("/chats/{id}/messages/{messageId}/reactions") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val emoji = Jsons.text(Jsons.parse(call.receiveText()), "emoji")
        val counts = try {
            services.chats.react(user, call.parameters["id"].orEmpty(), call.parameters["messageId"].orEmpty(), emoji)
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(mapOf("reactions" to counts.map(ChatService::reactionPayload)))
    }

    delete("/chats/{id}/messages/{messageId}/reactions") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val emoji = Jsons.text(Jsons.parse(call.receiveText()), "emoji")
        val counts = try {
            services.chats.unreact(user, call.parameters["id"].orEmpty(), call.parameters["messageId"].orEmpty(), emoji)
        } catch (error: TownException) {
            return@delete call.fail(error.code)
        }
        call.respondJson(mapOf("reactions" to counts.map(ChatService::reactionPayload)))
    }

    post("/chats/{id}/typing") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        try {
            services.chats.typing(user, call.parameters["id"].orEmpty())
        } catch (error: TownException) {
            return@post call.fail(error.code)
        }
        call.respondJson(mapOf("ok" to true))
    }

    get("/chats/{id}/media/{name}") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val chatId = call.parameters["id"].orEmpty()
        try {
            services.chats.get(user, chatId)
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        val name = call.parameters["name"].orEmpty()
        if (name.length !in 1..80 || name.any { !it.isLetterOrDigit() && it != '.' && it != '_' && it != '-' } || name.contains("..")) {
            return@get call.fail("not_found")
        }
        val file = services.uploads.resolve("chats/$name") ?: return@get call.fail("not_found")
        call.response.headers.append("Cache-Control", "private, max-age=300")
        call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
    }

    get("/chats/{id}/people") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val people = try {
            services.chats.searchPeopleToAdd(
                user,
                call.parameters["id"].orEmpty(),
                call.request.queryParameters["q"].orEmpty(),
                20,
            )
        } catch (error: TownException) {
            return@get call.fail(error.code)
        }
        call.respondJson(mapOf("people" to people.map { attendeePayload(it, includePhone = false) }))
    }

    get("/people") {
        val user = call.user(services) ?: return@get call.fail("unauthorized")
        val people = services.chats.searchPeople(user, call.request.queryParameters["q"].orEmpty(), emptySet(), 20)
        call.respondJson(mapOf("people" to people.map { attendeePayload(it, includePhone = false) }))
    }

    post("/chats/notices/read") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        services.chats.markListNoticesRead(user)
        call.respondJson(mapOf("ok" to true, "unreadCount" to services.repo.unreadChatNotificationCount(user.id)))
    }
}

fun Route.mediaRoutes(services: TownServices) {
    val images = ImageOps()
    get("/media/{kind}/{name}") {
        val kind = call.parameters["kind"].orEmpty()
        val file = services.uploads.publicFile(kind, call.parameters["name"].orEmpty())
            ?: return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
        if (kind == "avatars" && file.extension.equals("png", true)) {
            call.response.headers.append("Cache-Control", "public, max-age=300")
            call.respondBytes(trimmedAvatar(images, file), ContentType.Image.PNG)
            return@get
        }
        val type = when {
            file.extension.equals("mp3", true) -> ContentType.Audio.MPEG
            file.extension.equals("png", true) -> ContentType.Image.PNG
            else -> ContentType.Image.JPEG
        }
        call.response.headers.append("Cache-Control", "public, max-age=86400")
        call.respondBytes(file.readBytes(), type)
    }
}

private fun trimmedAvatar(images: ImageOps, file: File): ByteArray {
    val source = images.read(file.readBytes())
    val trimmed = images.trimToContent(source)
    val fitted = images.limitEdge(trimmed, Limits.AVATAR_MAX_EDGE)
    if (fitted.width == source.width && fitted.height == source.height) return file.readBytes()
    val bytes = images.writePng(fitted)
    file.writeBytes(bytes)
    return bytes
}

private suspend fun ApplicationCall.fail(code: String) {
    respondText(Jsons.write(mapOf("error" to code)), ContentType.Application.Json, statusFor(code))
}

private suspend fun ApplicationCall.respondJson(value: Any?, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(Jsons.write(value), ContentType.Application.Json, status)
}

private fun statusFor(code: String): HttpStatusCode = when (code) {
    "unauthorized", "bad_credentials" -> HttpStatusCode.Unauthorized
    "forbidden", "account_paused" -> HttpStatusCode.Forbidden
    "not_found" -> HttpStatusCode.NotFound
    "unavailable", "qwen_not_configured" -> HttpStatusCode.ServiceUnavailable
    "rate_limited" -> HttpStatusCode.TooManyRequests
    "phone_taken", "avatar_busy", "avatar_not_failed", "music_busy", "music_not_failed", "already_member" -> HttpStatusCode.Conflict
    else -> HttpStatusCode.BadRequest
}

private suspend fun ApplicationCall.requireAdmin(services: TownServices): UserRecord? {
    val user = user(services) ?: run {
        fail("unauthorized")
        return null
    }
    if (services.repo.findAdmin(user.id) == null) {
        fail("forbidden")
        return null
    }
    return user
}

private fun ApplicationCall.user(services: TownServices): UserRecord? {
    val token = requestCookie() ?: return null
    val session = services.repo.findSession(token) ?: return null
    if (session.expiresAt <= services.clock()) {
        services.repo.deleteSession(token)
        return null
    }
    return services.repo.findUser(session.userId)
}

private fun ApplicationCall.session(services: TownServices, userId: String) {
    val token = services.tokens()
    services.repo.createSession(token, userId, services.clock() + SESSION_SECONDS * 1000L)
    response.cookies.append(sessionCookie(token, services.config, SESSION_SECONDS))
}

private fun ApplicationCall.clearSession(services: TownServices) {
    response.cookies.append(sessionCookie("", services.config, -1))
}

private fun sessionCookie(value: String, config: AppConfig, maxAge: Int) = Cookie(
    name = SESSION_COOKIE,
    value = value,
    encoding = CookieEncoding.RAW,
    maxAge = maxAge,
    path = "/",
    secure = config.cookieSecure,
    httpOnly = true,
    extensions = mapOf("SameSite" to config.cookieSameSite),
)

private fun ApplicationCall.requestCookie(): String? =
    request.cookies[SESSION_COOKIE]?.takeIf { it.isNotBlank() }

private fun mePayload(user: UserRecord, services: TownServices) = mapOf(
    "id" to user.id,
    "name" to user.name,
    "phone" to PhoneNumbers.display(user.phone),
    "location" to user.location,
    "placeId" to user.placeId,
    "lat" to user.lat,
    "lng" to user.lng,
    "intro" to user.intro,
    "language" to user.language,
    "hasProfilePhoto" to (user.profilePhotoPath != null),
    "photoUrl" to if (user.profilePhotoPath != null) "/api/me/photo" else null,
    "paused" to user.paused,
    "admin" to (services.repo.findAdmin(user.id) != null),
)

private fun avatarPayload(avatar: AvatarRecord) = mapOf(
    "id" to avatar.id,
    "prompt" to avatar.prompt,
    "status" to avatar.status,
    "imageUrl" to avatar.imagePath?.let { "/media/$it" },
    "error" to avatar.error,
    "createdAt" to avatar.createdAt,
)

private fun musicPayload(music: MusicRecord) = mapOf(
    "id" to music.id,
    "name" to music.name,
    "prompt" to music.prompt,
    "status" to music.status,
    "audioUrl" to music.audioPath?.let { "/media/$it" },
    "error" to music.error,
    "gender" to music.gender,
    "instrumental" to music.instrumental,
    "lyrics" to music.lyrics,
    "createdAt" to music.createdAt,
)

private fun postPayload(item: FeedItem) = mapOf(
    "id" to item.id,
    "authorId" to item.authorId,
    "authorName" to item.authorName,
    "authorPhotoUrl" to if (item.authorPhotoPath != null) "/api/posts/${item.id}/author-photo" else null,
    "authorLocation" to item.authorLocation,
    "authorIntro" to item.authorIntro,
    "message" to item.message,
    "location" to item.location,
    "photoUrl" to "/media/${item.photoPath}",
    "photoUrls" to item.extraPhotoPaths.map { "/media/$it" },
    "avatarUrl" to item.avatarPath?.let { "/media/$it" },
    "createdAt" to item.createdAt,
    "musicName" to item.musicName,
    "musicUrl" to item.musicPath?.let { "/media/$it" },
    "quickReplies" to item.quickReplies,
    "commentCount" to item.commentCount,
    "comments" to item.previewComments.map(::commentPayload),
    "communityId" to item.communityId,
    "communityName" to item.communityName,
    "communityEmoji" to item.communityEmoji,
    "communityOnly" to item.communityOnly,
    "placeId" to item.placeId,
    "lat" to item.lat,
    "lng" to item.lng,
    "eventStartAt" to item.eventStartAt,
    "eventEndAt" to item.eventEndAt,
    "eventPrice" to item.eventPrice,
    "attendeeCount" to item.attendeeCount,
    "joined" to item.joined,
    "attendees" to item.attendees.map { attendeePayload(it, includePhone = false) },
    "pollOptions" to item.pollOptions.map { pollOptionPayload(it, includePhone = false) },
    "hasQuestion" to item.hasQuestion,
    "questionTitle" to item.questionTitle,
    "answerCount" to item.answerCount,
    "answered" to item.answered,
    "ctaTitle" to item.ctaTitle,
    "ctaText" to item.ctaText,
    "ctaDestination" to item.ctaDestination,
    "ctaUrl" to item.ctaUrl.takeIf { it.isNotBlank() },
    "authorZaloUrl" to item.authorZaloUrl,
)

private fun communityPayload(community: quest.saigon.town.CommunityRecord, joined: Boolean) = mapOf(
    "id" to community.id,
    "name" to community.name,
    "emoji" to community.emoji,
    "description" to community.description,
    "memberCount" to community.memberCount,
    "joined" to joined,
    "createdAt" to community.createdAt,
)

private fun memberPayload(member: quest.saigon.town.CommunityMember) = mapOf(
    "id" to member.id,
    "name" to member.name,
    "photoUrl" to member.profilePhotoPath?.let { "/api/users/${member.id}/photo" },
    "location" to member.location,
    "intro" to member.intro,
    "joinedAt" to member.joinedAt,
)

private fun questionAnswerPayload(item: quest.saigon.town.QuestionAnswerItem) = mapOf(
    "id" to item.id,
    "userId" to item.userId,
    "name" to item.authorName,
    "photoUrl" to item.authorPhotoPath?.let { "/api/users/${item.userId}/photo" },
    "location" to item.authorLocation,
    "intro" to item.authorIntro,
    "text" to item.text,
    "createdAt" to item.createdAt,
)

private fun pollOptionPayload(option: quest.saigon.town.PollOptionView, includePhone: Boolean) = mapOf(
    "index" to option.index,
    "text" to option.text,
    "voteCount" to option.voteCount,
    "voted" to option.voted,
    "voters" to option.voters.map { attendeePayload(it, includePhone) },
)

private fun chatMemberPayload(member: ChatMemberView) = mapOf(
    "id" to member.id,
    "name" to member.name,
    "photoUrl" to member.profilePhotoPath?.let { "/api/users/${member.id}/photo" },
    "location" to member.location,
    "intro" to member.intro,
    "joinedAt" to member.joinedAt,
    "owner" to member.owner,
    "phone" to member.phone?.let { PhoneNumbers.display(it) },
    "zaloUrl" to member.phone?.let { PhoneNumbers.zaloUrl(it) },
)

private fun attendeePayload(member: quest.saigon.town.EventAttendee, includePhone: Boolean) = mapOf(
    "id" to member.id,
    "name" to member.name,
    "photoUrl" to member.profilePhotoPath?.let { "/api/users/${member.id}/photo" },
    "location" to member.location,
    "intro" to member.intro,
    "joinedAt" to member.joinedAt,
    "phone" to if (includePhone) member.phone?.let { PhoneNumbers.display(it) } else null,
    "zaloUrl" to if (includePhone) member.phone?.let { PhoneNumbers.zaloUrl(it) } else null,
)

private fun placePayload(place: quest.saigon.town.PlaceSuggestion) = mapOf(
    "name" to place.name,
    "address" to place.address,
    "placeId" to place.placeId,
    "lat" to place.lat,
    "lng" to place.lng,
    "postCount" to place.postCount,
    "recent" to place.recent,
)

private fun commentPayload(item: CommentItem) = mapOf(
    "id" to item.id,
    "postId" to item.postId,
    "userId" to item.userId,
    "authorName" to item.authorName,
    "authorPhotoUrl" to item.authorPhotoPath?.let { "/api/users/${item.userId}/photo" },
    "authorLocation" to item.authorLocation,
    "authorIntro" to item.authorIntro,
    "text" to item.text,
    "createdAt" to item.createdAt,
    "replyToAuthorName" to item.replyToAuthorName,
    "replyToText" to item.replyToText,
    "postText" to item.postText,
)

private fun adminUserPayload(item: AdminUser) = mapOf(
    "id" to item.id,
    "name" to item.name,
    "intro" to item.intro,
    "photoUrl" to item.profilePhotoPath?.let { "/api/users/${item.id}/photo" },
    "createdAt" to item.createdAt,
    "postCount" to item.postCount,
    "paused" to item.paused,
)

private fun adminPostPayload(item: AdminPost) = mapOf(
    "id" to item.id,
    "authorId" to item.authorId,
    "authorName" to item.authorName,
    "authorPhotoUrl" to item.authorPhotoPath?.let { "/api/users/${item.authorId}/photo" },
    "message" to item.message,
    "location" to item.location,
    "photoUrl" to "/media/${item.photoPath}",
    "avatarUrl" to item.avatarPath?.let { "/media/$it" },
    "createdAt" to item.createdAt,
    "views" to item.views,
    "replyClicks" to item.replyClicks,
    "shareClicks" to item.shareClicks,
    "musicName" to item.musicName,
)

private fun featurePayload(item: FeatureSuggestionView) = mapOf(
    "id" to item.id,
    "text" to item.text,
    "voteCount" to item.voteCount,
    "voted" to item.voted,
    "resolved" to item.resolved,
    "createdAt" to item.createdAt,
)

private fun ownedPostPayload(item: OwnedPost) = mapOf(
    "id" to item.id,
    "message" to item.message,
    "location" to item.location,
    "photoUrl" to "/media/${item.photoPath}",
    "avatarUrl" to item.avatarPath?.let { "/media/$it" },
    "createdAt" to item.createdAt,
    "views" to item.views,
    "replyClicks" to item.replyClicks,
    "shareClicks" to item.shareClicks,
    "musicName" to item.musicName,
    "communityId" to item.communityId,
    "communityName" to item.communityName,
    "communityEmoji" to item.communityEmoji,
    "communityOnly" to item.communityOnly,
)

private fun cleanName(raw: String): String? {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.length !in 1..Limits.NAME_MAX) return null
    if (name.any { it.isISOControl() }) return null
    return name
}

private fun cleanPushEndpoint(raw: String): String? {
    val endpoint = raw.trim()
    if (endpoint.length !in 8..Limits.PUSH_ENDPOINT_MAX) return null
    if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://")) return null
    if (endpoint.any { it.isISOControl() || it.isWhitespace() }) return null
    return endpoint
}

private fun cleanPushKey(raw: String): String? {
    val key = raw.trim()
    if (key.length !in 8..256) return null
    if (key.any { !it.isLetterOrDigit() && it != '-' && it != '_' && it != '+' && it != '/' && it != '=' }) return null
    return key
}

private fun cleanPlaceId(raw: String): String? {
    val id = raw.trim()
    if (id.isEmpty()) return null
    if (id.length > Limits.PLACE_ID_MAX) return null
    if (id.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
    return id
}

private fun parseEpoch(raw: String): Long? =
    raw.trim().toLongOrNull()?.takeIf { it > 0 }

private fun parseCoord(raw: String): Double? =
    raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

private fun parseVnd(raw: String): Long? {
    val cleaned = raw.trim()
        .replace("đ", "", ignoreCase = true)
        .replace("₫", "")
        .replace("vnd", "", ignoreCase = true)
        .replace(Regex("[.,\\s]"), "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toLongOrNull() ?: throw TownException("invalid_event_price")
    if (value < 0 || value > Limits.EVENT_PRICE_MAX) throw TownException("invalid_event_price")
    return value.takeIf { it > 0 }
}

private fun cleanIntro(raw: String): String? = cleanMultiline(raw, Limits.INTRO_MAX)

private fun cleanFeature(raw: String): String? = cleanMultiline(raw, Limits.FEATURE_MAX)

private fun cleanDescription(raw: String): String? = cleanMultiline(raw, Limits.COMMUNITY_DESCRIPTION_MAX)

private fun cleanMultiline(raw: String, max: Int): String? {
    val text = raw.replace("\r\n", "\n").trim()
    if (text.length > max) return null
    if (text.any { it.isISOControl() && it != '\n' }) return null
    return text
}

private fun clientIp(call: ApplicationCall, trustProxy: Boolean): String {
    if (trustProxy) {
        val forwarded = call.request.headers["X-Forwarded-For"]?.split(',')?.firstOrNull()?.trim()
        if (!forwarded.isNullOrBlank()) return forwarded
    }
    return call.request.local.remoteHost
}

private suspend fun ApplicationCall.fileBytes(field: String): ByteArray? = formParts().file(field)

private fun deletePostPhotos(services: TownServices, post: PostRecord) {
    services.uploads.delete(post.photoPath)
    post.extraPhotoPaths.forEach { services.uploads.delete(it) }
}

private suspend fun ApplicationCall.formParts(): FormParts {
    val text = mutableMapOf<String, String>()
    val files = mutableMapOf<String, MutableList<ByteArray>>()
    val multipart = receiveMultipart(formFieldLimit = 1024 * 1024)
    while (true) {
        val part = multipart.readPart() ?: break
        when (part) {
            is PartData.FormItem -> text[part.name.orEmpty()] = part.value
            is PartData.FileItem -> files.getOrPut(part.name.orEmpty()) { mutableListOf() }.add(readLimited(part))
            else -> Unit
        }
        part.release()
    }
    return FormParts(text, files)
}

private fun readLimited(part: PartData.FileItem): ByteArray {
    val output = ByteArrayOutputStream()
    part.provider().toInputStream().use { input ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_UPLOAD) throw TownException("too_large")
            output.write(buffer, 0, read)
        }
    }
    return output.toByteArray()
}

private class FormParts(
    private val text: Map<String, String>,
    private val files: Map<String, List<ByteArray>>,
) {
    fun text(name: String): String = text[name].orEmpty()
    fun file(name: String): ByteArray? = files[name]?.firstOrNull()
    fun files(name: String): List<ByteArray> = files[name].orEmpty()
}
