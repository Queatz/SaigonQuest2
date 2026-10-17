package quest.saigon

fun previewPostMessage(text: String): String = text.take(Limits.MESSAGE_PREVIEW)

fun postMessageNeedsMore(text: String): Boolean = text.length > Limits.MESSAGE_PREVIEW
