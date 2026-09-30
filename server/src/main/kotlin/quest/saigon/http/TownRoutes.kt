package quest.saigon.http

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import quest.saigon.AppConfig
import quest.saigon.Limits
import quest.saigon.MusicNames
import quest.saigon.PhoneNumbers
import quest.saigon.languagePreference
import quest.saigon.media.ImageOps
import quest.saigon.town.AvatarRecord
import quest.saigon.town.AvatarStatus
import quest.saigon.town.FeedItem
import quest.saigon.town.MusicRecord
import quest.saigon.town.MusicStatus
import quest.saigon.town.OwnedPost
import quest.saigon.town.PostRecord
import quest.saigon.town.TownException
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
        call.respondJson(mePayload(user))
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
        call.respondJson(mePayload(user))
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
        call.respondJson(mePayload(user))
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
            next = next.copy(location = location)
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
        call.respondJson(mePayload(services.repo.findUser(next.id) ?: next))
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
        call.respondJson(mePayload(updated))
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

    get("/posts") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: Limits.FEED_PAGE_SIZE
        val page = services.repo.feed(call.request.queryParameters["cursor"], limit)
        call.respondJson(
            mapOf(
                "posts" to page.items.map(::postPayload),
                "nextCursor" to page.nextCursor,
            ),
        )
    }

    post("/posts") {
        val user = call.user(services) ?: return@post call.fail("unauthorized")
        val form = call.formParts()
        val message = form.text("message").trim()
        if (message.length !in 1..Limits.MESSAGE_MAX) return@post call.fail("invalid_message")
        val avatar = services.repo.findAvatar(form.text("avatarId"))
        if (avatar == null || avatar.userId != user.id || avatar.status != AvatarStatus.READY || avatar.imagePath == null) {
            return@post call.fail("avatar_required")
        }
        val location = form.text("location").trim().ifBlank { user.location.trim() }
        if (location.isEmpty() || location.length > Limits.LOCATION_MAX) return@post call.fail("invalid_location")
        val musicId = form.text("musicId").trim().ifBlank { null }
        val music = musicId?.let { services.repo.findMusic(it) }
        if (musicId != null && (music == null || music.status != MusicStatus.READY || music.audioPath == null)) {
            return@post call.fail("invalid_music")
        }
        val photo = form.file("photo") ?: return@post call.fail("photo_required")
        val jpeg = try {
            services.images.preparePostPhoto(photo)
        } catch (_: Exception) {
            return@post call.fail("invalid_photo")
        }
        val path = services.uploads.writePost(jpeg)
        val post = services.repo.insertPost(
            PostRecord(
                id = "",
                userId = user.id,
                avatarId = avatar.id,
                photoPath = path,
                message = message,
                location = location,
                createdAt = services.clock(),
                musicId = music?.id,
            ),
        )
        call.respondJson(
            postPayload(
                FeedItem(
                    id = post.id,
                    authorName = user.name,
                    authorPhotoPath = user.profilePhotoPath,
                    message = post.message,
                    location = post.location,
                    photoPath = post.photoPath,
                    avatarPath = avatar.imagePath,
                    authorLocation = user.location,
                    authorIntro = user.intro,
                    createdAt = post.createdAt,
                    musicName = music?.name,
                    musicPath = music?.audioPath,
                ),
            ),
            HttpStatusCode.Created,
        )
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

    delete("/posts/{id}") {
        val user = call.user(services) ?: return@delete call.fail("unauthorized")
        val post = services.repo.findPost(call.parameters["id"].orEmpty()) ?: return@delete call.fail("not_found")
        if (post.userId != user.id) return@delete call.fail("not_found")
        services.repo.deletePost(post.id)
        services.uploads.delete(post.photoPath)
        call.respondJson(mapOf("ok" to true))
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
    "not_found" -> HttpStatusCode.NotFound
    "unavailable", "qwen_not_configured" -> HttpStatusCode.ServiceUnavailable
    "rate_limited" -> HttpStatusCode.TooManyRequests
    "phone_taken", "avatar_busy", "avatar_not_failed", "music_busy", "music_not_failed" -> HttpStatusCode.Conflict
    else -> HttpStatusCode.BadRequest
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

private fun mePayload(user: UserRecord) = mapOf(
    "id" to user.id,
    "name" to user.name,
    "phone" to PhoneNumbers.display(user.phone),
    "location" to user.location,
    "intro" to user.intro,
    "language" to user.language,
    "hasProfilePhoto" to (user.profilePhotoPath != null),
    "photoUrl" to if (user.profilePhotoPath != null) "/api/me/photo" else null,
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
    "authorName" to item.authorName,
    "authorPhotoUrl" to if (item.authorPhotoPath != null) "/api/posts/${item.id}/author-photo" else null,
    "authorLocation" to item.authorLocation,
    "authorIntro" to item.authorIntro,
    "message" to item.message,
    "location" to item.location,
    "photoUrl" to "/media/${item.photoPath}",
    "avatarUrl" to item.avatarPath?.let { "/media/$it" },
    "createdAt" to item.createdAt,
    "musicName" to item.musicName,
    "musicUrl" to item.musicPath?.let { "/media/$it" },
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
)

private fun cleanName(raw: String): String? {
    val name = raw.trim().replace(Regex("\\s+"), " ")
    if (name.length !in 1..Limits.NAME_MAX) return null
    if (name.any { it.isISOControl() }) return null
    return name
}

private fun cleanIntro(raw: String): String? {
    val intro = raw.replace("\r\n", "\n").trim()
    if (intro.length > Limits.INTRO_MAX) return null
    if (intro.any { it.isISOControl() && it != '\n' }) return null
    return intro
}

private fun clientIp(call: ApplicationCall, trustProxy: Boolean): String {
    if (trustProxy) {
        val forwarded = call.request.headers["X-Forwarded-For"]?.split(',')?.firstOrNull()?.trim()
        if (!forwarded.isNullOrBlank()) return forwarded
    }
    return call.request.local.remoteHost
}

private suspend fun ApplicationCall.fileBytes(field: String): ByteArray? = formParts().file(field)

private suspend fun ApplicationCall.formParts(): FormParts {
    val text = mutableMapOf<String, String>()
    val files = mutableMapOf<String, ByteArray>()
    val multipart = receiveMultipart(formFieldLimit = 1024 * 1024)
    while (true) {
        val part = multipart.readPart() ?: break
        when (part) {
            is PartData.FormItem -> text[part.name.orEmpty()] = part.value
            is PartData.FileItem -> files[part.name.orEmpty()] = readLimited(part)
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
    private val files: Map<String, ByteArray>,
) {
    fun text(name: String): String = text[name].orEmpty()
    fun file(name: String): ByteArray? = files[name]
}
