package quest.saigon

data class FeedMotion(
    val photoOffset: Double,
    val cardOffset: Double,
)

fun feedMotion(dragPx: Double): FeedMotion =
    FeedMotion(
        photoOffset = dragPx,
        cardOffset = dragPx * Limits.CARD_SPEED,
    )
