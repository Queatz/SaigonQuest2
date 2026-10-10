package quest.saigon.town

import quest.saigon.ai.CommunityPrompts
import quest.saigon.ai.CommunitySuggester
import quest.saigon.auth.RateLimiter

data class OpenedCommunity(val community: CommunityRecord, val created: Boolean)

class CommunityService(
    private val repo: TownRepository,
    private val clock: () -> Long,
    private val suggester: CommunitySuggester = CommunitySuggester { message, existing ->
        CommunityPrompts.fallback(message, existing)
    },
    private val suggestLimiter: RateLimiter = RateLimiter(20, 60_000, clock),
) {
    fun open(user: UserRecord, rawName: String, rawEmoji: String): OpenedCommunity {
        val name = CommunityNames.clean(rawName) ?: throw TownException("invalid_community")
        val emoji = CommunityNames.cleanEmoji(rawEmoji)
        val slug = CommunityNames.slug(name)
        val existing = repo.findCommunityBySlug(slug)
        if (existing != null) {
            repo.joinCommunity(existing.id, user.id, clock())
            return OpenedCommunity(repo.findCommunity(existing.id) ?: existing, created = false)
        }
        val created = try {
            repo.insertCommunity(
                CommunityRecord(
                    id = "",
                    name = name,
                    emoji = emoji,
                    slug = slug,
                    createdBy = user.id,
                    createdAt = clock(),
                ),
            )
        } catch (error: TownException) {
            if (error.code != "community_taken") throw error
            val raced = repo.findCommunityBySlug(slug) ?: throw error
            repo.joinCommunity(raced.id, user.id, clock())
            return OpenedCommunity(repo.findCommunity(raced.id) ?: raced, created = false)
        }
        repo.joinCommunity(created.id, user.id, clock())
        return OpenedCommunity(repo.findCommunity(created.id) ?: created, created = true)
    }

    fun suggest(userId: String, text: String): quest.saigon.ai.CommunitySuggestion {
        if (!suggestLimiter.allow(userId)) throw TownException("rate_limited")
        val existing = repo.searchCommunities("", null, 40).map { it.community }
        return suggester.suggest(text, existing)
    }
}
