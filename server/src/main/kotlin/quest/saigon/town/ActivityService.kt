package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.media.UploadStore

class ActivityService(
    private val repo: TownRepository,
    private val chats: ChatService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val translations: TranslationService = TranslationService(),
    private val uploads: UploadStore,
) {
    fun create(
        actor: UserRecord,
        emoji: String,
        name: String,
        description: String,
        locationName: String,
        locationId: String?,
        placeId: String?,
        lat: Double?,
        lng: Double?,
        hostUserId: String?,
        schedule: List<ActivityScheduleSlot>,
        interested: Boolean,
    ): ActivityView {
        ensureAvailable(actor)
        val cleaned = clean(emoji, name, description, locationName, locationId, placeId, lat, lng, hostUserId, schedule)
        val stored = repo.insertActivity(
            ActivityRecord(
                id = "",
                createdBy = actor.id,
                emoji = cleaned.emoji,
                name = cleaned.name,
                description = cleaned.description,
                locationName = cleaned.locationName,
                locationId = cleaned.locationId,
                placeId = cleaned.placeId,
                lat = cleaned.lat,
                lng = cleaned.lng,
                hostUserId = cleaned.hostUserId,
                schedule = cleaned.schedule,
                createdAt = clock(),
            ),
        )
        if (interested) repo.addActivityInterest(stored.id, actor.id, clock())
        return view(ensureTranslations(repo.findActivity(stored.id) ?: stored), actor.id, includePeople = true)
    }

    fun update(
        actor: UserRecord,
        id: String,
        emoji: String,
        name: String,
        description: String,
        hostUserId: String?,
        schedule: List<ActivityScheduleSlot>,
        translationOverrides: Map<String, Map<String, String>>? = null,
    ): ActivityView {
        ensureAvailable(actor)
        val current = owned(actor, id)
        val cleaned = clean(
            emoji,
            name,
            description,
            current.locationName,
            current.locationId,
            current.placeId,
            current.lat,
            current.lng,
            hostUserId,
            schedule,
        )
        val sourceChanged = cleaned.name != current.name || cleaned.description != current.description
        val sourceFields = mapOf("name" to cleaned.name, "description" to cleaned.description)
        val next = current.copy(
            emoji = cleaned.emoji,
            name = cleaned.name,
            description = cleaned.description,
            hostUserId = cleaned.hostUserId,
            schedule = cleaned.schedule,
            translations = when {
                sourceChanged -> null
                translationOverrides != null -> translations.override(
                    sourceFields,
                    cleanTranslationOverrides(translationOverrides),
                )
                else -> current.translations
            },
        )
        val updated = repo.updateActivity(next) ?: throw TownException("not_found")
        return view(ensureTranslations(updated), actor.id, includePeople = true)
    }

    fun delete(actor: UserRecord, id: String) {
        ensureAvailable(actor)
        owned(actor, id)
        repo.deleteActivity(id) ?: throw TownException("not_found")
        repo.deleteActivityPhotos(id).forEach { uploads.delete(it.photoPath) }
    }

    fun get(id: String, viewerId: String?): ActivityView {
        val activity = repo.findActivity(id) ?: throw TownException("not_found")
        return view(ensureTranslations(activity), viewerId, includePeople = true)
    }

    fun list(
        viewerId: String?,
        locationName: String?,
        placeId: String?,
        locationId: String?,
        cursor: String?,
        limit: Int,
    ): Pair<List<ActivityView>, String?> {
        val pageLimit = limit.coerceIn(1, 50)
        val page = if (!locationName.isNullOrBlank() || !placeId.isNullOrBlank() || !locationId.isNullOrBlank()) {
            repo.activitiesForLocation(locationName.orEmpty(), placeId, locationId, cursor, pageLimit)
        } else {
            repo.searchActivities(cursor, pageLimit)
        }
        return page.items.map { view(ensureTranslations(it), viewerId, includePeople = false) } to page.nextCursor
    }

    fun mine(actor: UserRecord, cursor: String?, limit: Int): Pair<List<ActivityView>, String?> {
        val page = repo.activitiesByUser(actor.id, cursor, limit.coerceIn(1, 50))
        return page.items.map { view(ensureTranslations(it), actor.id, includePeople = false) } to page.nextCursor
    }

    fun interestedByUser(userId: String, viewerId: String?, cursor: String?, limit: Int): Pair<List<ActivityView>, String?> {
        val page = repo.activitiesInterestedByUser(userId, cursor, limit.coerceIn(1, 50))
        return page.items.map { view(ensureTranslations(it), viewerId, includePeople = false) } to page.nextCursor
    }

    fun interest(actor: UserRecord, id: String): ActivityView {
        ensureAvailable(actor)
        val activity = repo.findActivity(id) ?: throw TownException("not_found")
        repo.addActivityInterest(activity.id, actor.id, clock())
        return view(ensureTranslations(repo.findActivity(activity.id) ?: activity), actor.id, includePeople = true)
    }

    fun uninterest(actor: UserRecord, id: String): ActivityView {
        ensureAvailable(actor)
        val activity = repo.findActivity(id) ?: throw TownException("not_found")
        repo.removeActivityInterest(activity.id, actor.id)
        return view(ensureTranslations(repo.findActivity(activity.id) ?: activity), actor.id, includePeople = true)
    }

    fun startChat(actor: UserRecord, id: String, userIds: List<String>): ChatSummary {
        ensureAvailable(actor)
        val activity = repo.findActivity(id) ?: throw TownException("not_found")
        val hostId = activity.hostUserId?.takeIf { it.isNotBlank() }
        val allowed = repo.activityInterestedIds(activity.id).toSet() + setOfNotNull(hostId)
        val selected = (userIds + listOfNotNull(hostId))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it in allowed && it != actor.id }
        if (selected.isEmpty()) throw TownException("no_chat_members")
        val attachment = ChatAttachment(
            type = "activity",
            activityId = activity.id,
            activityName = activity.name,
            activityEmoji = activity.emoji,
            activityDescription = activity.description,
        )
        return if (selected.size == 1) {
            chats.createDirect(actor, selected.first(), "", attachment)
        } else {
            val groupName = ChatService.cleanName(activity.name.take(Limits.CHAT_NAME_MAX))
                ?: ChatService.cleanName(activity.emoji)
                ?: activity.name.take(Limits.CHAT_NAME_MAX)
            val group = chats.createGroup(actor, groupName)
            selected.forEach { memberId ->
                chats.addMember(actor, group.id, memberId)
            }
            chats.send(actor, group.id, "", emptyList(), attachment)
            chats.get(actor, group.id)
        }
    }

    private fun view(activity: ActivityRecord, viewerId: String?, includePeople: Boolean): ActivityView {
        val interested = viewerId != null && repo.isActivityInterested(activity.id, viewerId)
        val host = activity.hostUserId?.let(::attendeeFor)
        val interestedPeople = repo.activityInterested(
            activity.id,
            null,
            if (includePeople) 50 else Limits.ACTIVITY_CARD_INTERESTED_LIMIT,
        ).items
        val people = if (includePeople) {
            if (host != null && interestedPeople.none { it.id == host.id }) listOf(host) + interestedPeople else interestedPeople
        } else {
            interestedPeople
        }
        val photos = repo.userPhotosForActivity(activity.id, Limits.ACTIVITY_CARD_PHOTO_LIMIT).map { it.photoPath }
        return ActivityView(activity, interested, host, people, photos)
    }

    private fun ensureTranslations(activity: ActivityRecord): ActivityRecord {
        val fields = translationFields(activity)
        val blob = translations.ensure(
            id = "activity:${activity.id}",
            fields = fields,
            current = activity.translations,
            persist = { next ->
                val live = repo.findActivity(activity.id) ?: return@ensure
                if (TranslationService.sourceHash(translationFields(live)) != next.sourceHash) return@ensure
                if (live.translations?.let { it.sourceHash == next.sourceHash && it.status == TranslationService.STATUS_READY } == true) {
                    return@ensure
                }
                repo.updateActivity(live.copy(translations = next))
            },
            maxLengths = mapOf(
                "name" to Limits.ACTIVITY_NAME_MAX,
                "description" to Limits.ACTIVITY_DESCRIPTION_MAX,
            ),
        )
        return if (blob == null || blob == activity.translations) activity else activity.copy(translations = blob)
    }

    private fun translationFields(activity: ActivityRecord): Map<String, String> =
        mapOf("name" to activity.name, "description" to activity.description)

    private fun cleanTranslationOverrides(
        overrides: Map<String, Map<String, String>>,
    ): Map<String, Map<String, String>> {
        if (overrides.keys != TranslationService.LOCALES.toSet()) throw TownException("invalid_activity_translations")
        return TranslationService.LOCALES.associateWith { locale ->
            val fields = overrides[locale] ?: throw TownException("invalid_activity_translations")
            val name = fields["name"]?.trim()?.replace(Regex("\\s+"), " ")
                ?: throw TownException("invalid_activity_translations")
            val description = fields["description"]?.replace("\r\n", "\n")?.trim()
                ?: throw TownException("invalid_activity_translations")
            if (name.length > Limits.ACTIVITY_NAME_MAX || name.any(Char::isISOControl)) {
                throw TownException("invalid_activity_translations")
            }
            if (description.length > Limits.ACTIVITY_DESCRIPTION_MAX || description.any { it.isISOControl() && it != '\n' }) {
                throw TownException("invalid_activity_translations")
            }
            mapOf("name" to name, "description" to description)
        }
    }

    private fun attendeeFor(userId: String): EventAttendee? {
        val user = repo.findUser(userId) ?: return null
        return EventAttendee(
            id = user.id,
            name = user.name,
            profilePhotoPath = user.profilePhotoPath,
            location = user.location,
            intro = user.intro,
            phone = user.phone,
            joinedAt = 0,
        )
    }

    private fun owned(actor: UserRecord, id: String): ActivityRecord {
        val activity = repo.findActivity(id) ?: throw TownException("not_found")
        if (activity.createdBy != actor.id) throw TownException("not_found")
        return activity
    }

    private fun ensureAvailable(actor: UserRecord) {
        if (actor.paused) throw TownException("account_paused")
    }

    private fun clean(
        emoji: String,
        name: String,
        description: String,
        locationName: String,
        locationId: String?,
        placeId: String?,
        lat: Double?,
        lng: Double?,
        hostUserId: String?,
        schedule: List<ActivityScheduleSlot>,
    ): CleanedActivity {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isEmpty() || cleanName.length > Limits.ACTIVITY_NAME_MAX || cleanName.any(Char::isISOControl)) {
            throw TownException("invalid_activity_name")
        }
        val cleanDescription = description.replace("\r\n", "\n").trim()
        if (cleanDescription.length > Limits.ACTIVITY_DESCRIPTION_MAX || cleanDescription.any { it.isISOControl() && it != '\n' }) {
            throw TownException("invalid_activity_description")
        }
        val cleanLocation = locationName.trim().replace(Regex("\\s+"), " ")
        if (cleanLocation.isEmpty() || cleanLocation.length > Limits.LOCATION_MAX || cleanLocation.any(Char::isISOControl)) {
            throw TownException("invalid_location")
        }
        val cleanPlaceId = placeId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanPlaceId != null && (cleanPlaceId.length > Limits.PLACE_ID_MAX || cleanPlaceId.any { !it.isLetterOrDigit() && it != '_' && it != '-' })) {
            throw TownException("invalid_location")
        }
        val cleanLocationId = locationId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanLocationId != null && (cleanLocationId.length > 128 || cleanLocationId.any(Char::isISOControl))) {
            throw TownException("invalid_location")
        }
        if (lat != null && (!lat.isFinite() || lat !in -90.0..90.0)) throw TownException("invalid_location")
        if (lng != null && (!lng.isFinite() || lng !in -180.0..180.0)) throw TownException("invalid_location")
        val host = hostUserId?.trim()?.takeIf { it.isNotBlank() }
        if (host != null && repo.findUser(host) == null) throw TownException("invalid_activity_host")
        val cleanedSchedule = cleanSchedule(schedule)
        return CleanedActivity(
            emoji = CommunityNames.cleanEmoji(emoji),
            name = cleanName,
            description = cleanDescription,
            locationName = cleanLocation,
            locationId = cleanLocationId,
            placeId = cleanPlaceId,
            lat = lat,
            lng = lng,
            hostUserId = host,
            schedule = cleanedSchedule,
        )
    }

    private fun cleanSchedule(schedule: List<ActivityScheduleSlot>): List<ActivityScheduleSlot> {
        if (schedule.isEmpty()) return emptyList()
        val cleaned = schedule.map { slot ->
            if (slot.dayOfWeek !in 1..7) throw TownException("invalid_activity_schedule")
            if (slot.startMinutes !in 0..1439 || slot.endMinutes !in 1..1440 || slot.endMinutes <= slot.startMinutes) {
                throw TownException("invalid_activity_schedule")
            }
            slot
        }.distinctBy { it.dayOfWeek }.sortedBy { it.dayOfWeek }
        if (cleaned.size != schedule.map { it.dayOfWeek }.distinct().size) throw TownException("invalid_activity_schedule")
        if (cleaned.size != schedule.size) throw TownException("invalid_activity_schedule")
        return cleaned
    }

    private data class CleanedActivity(
        val emoji: String,
        val name: String,
        val description: String,
        val locationName: String,
        val locationId: String?,
        val placeId: String?,
        val lat: Double?,
        val lng: Double?,
        val hostUserId: String?,
        val schedule: List<ActivityScheduleSlot>,
    )
}
