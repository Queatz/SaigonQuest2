package quest.saigon.ai

object AvatarPrompts {
    const val EXPAND_SYSTEM = """
        You write one short image-edit note for storybook character.
        The note describes things such as expression, pose, apparel, and mood.
        Do not mention backgrounds, cameras, or text.
        Output the note only, with no quotes or markdown.
    """

    fun editInstruction(userPrompt: String, expanded: String?): String {
        val mood = expanded?.trim()?.takeIf { it.isNotEmpty() } ?: userPrompt.trim()
        return """
            Edit the person in the input photo into a storybook character.
            Keep them recognizable: same face, hair, age, and features.
            Illustrated storybook style with studio high contrast ghibli cell shading.
            No outline, no white border, no sticker edge, no halo, and no cutout stroke.
            Solid flat gray background #a8a8a8 only. No scenery, floor, shadows, text, logos, or props.
            Waist-up, centered, facing the camera.
            Mood and expression and other details: $mood
        """.trimIndent()
    }

    const val MASK_INSTRUCTION = """
        Convert this image into a perfect black/white binary mask.
        The foreground subject, including highlights, hair, clothes, etc., must be solid pure white #FFFFFF.
        The background must be solid pure black #000000.
        No gray, shading, texture, outline, text, or shadow.
    """
}
