package quest.saigon

object Limits {
    const val NAME_MAX = 40
    const val MESSAGE_MAX = 5000
    const val MESSAGE_PREVIEW = 80
    const val PROMPT_MAX = 200
    const val MUSIC_PROMPT_MAX = 400
    const val MUSIC_NAME_MAX = 40
    const val MUSIC_LYRICS_MAX = 4000
    const val LOCATION_MAX = 80
    const val PLACE_ID_MAX = 256
    const val EVENT_PRICE_MAX = 100_000_000
    const val PRODUCT_NAME_MAX = 120
    const val PRODUCT_CATEGORY_MAX = 80
    const val PRODUCT_DESCRIPTION_MAX = 5000
    const val ACTIVITY_NAME_MAX = 80
    const val ACTIVITY_DESCRIPTION_MAX = 500
    const val POLL_OPTIONS_MIN = 2
    const val POLL_OPTIONS_MAX = 5
    const val POLL_OPTION_MAX = 40
    const val QUESTION_TITLE_MAX = 80
    const val QUESTION_ANSWER_MAX = 280
    const val CTA_TITLE_MAX = 80
    const val CTA_TEXT_MAX = 40
    const val CTA_URL_MAX = 2048
    const val OPEN_CHAT_HOURS_MIN = 1
    const val OPEN_CHAT_HOURS_MAX = 24
    const val OPEN_CHAT_HOURS_DEFAULT = 24
    const val OPEN_CHAT_REMAINING_MIN = 1
    const val OPEN_CHAT_REMAINING_MAX = 10
    const val INTRO_MAX = 280
    const val FEATURE_MAX = 280
    const val COMMUNITY_DESCRIPTION_MAX = 280
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 72
    const val PHOTO_MAX_EDGE = 1200
    const val AVATAR_MAX_EDGE = 512
    const val JPEG_QUALITY = 0.85
    const val TYPEWRITER_MILLIS_PER_CHAR = 16
    const val AVATAR_ENTER_PX = 50
    const val CARD_SPEED = 1.1
    const val FEED_PAGE_SIZE = 8
    const val MINI_PROFILE_COMMUNITIES = 5
    const val MINI_PROFILE_ACTIVITIES = 8
    const val ACTIVITY_CARD_INTERESTED_LIMIT = 5
    const val ACTIVITY_CARD_PHOTO_LIMIT = 10
    const val COMMENT_MAX = 280
    const val COMMENT_PAGE_SIZE = 50
    const val COMMENT_PREVIEW = 12
    const val CHAT_NAME_MAX = 40
    const val CHAT_MESSAGE_MAX = 2000
    const val CHAT_MESSAGE_PAGE_SIZE = 50
    const val CHAT_REACTION_MAX = 8
    const val COMMENT_CYCLE_MILLIS = 2000
    const val QUICK_REPLY_COUNT = 5
    const val PHOTOS_MAX = 20
    const val PHOTO_PEEK_MILLIS = 750
    const val PHOTO_PEEK_TAP_MILLIS = 2000
    const val PUSH_ENDPOINT_MAX = 2048
}

object ProductContactMethod {
    const val ZALO = "zalo"
    const val MESSAGES = "messages"
}
