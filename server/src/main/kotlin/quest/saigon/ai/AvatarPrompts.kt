package quest.saigon.ai

object AvatarPrompts {
    const val EXPAND_SYSTEM = """
        You write one short image-edit note for a visual-novel character.
        The note describes things such as expression, pose, apparel, and mood.
        Do not mention backgrounds, cameras, or text.
        Output the note only, with no quotes or markdown.
    """

    fun editInstruction(userPrompt: String, expanded: String?): String {
        val mood = expanded?.trim()?.takeIf { it.isNotEmpty() } ?: userPrompt.trim()
        return """
            Edit the person in the input photo into a visual-novel character.
            Keep them recognizable: same face, hair, age, and features.
            Illustrated style, clean line art, soft cel shading.
            No outline, no white border, no sticker edge, no halo, and no cutout stroke.
            Solid flat gray background #808080 only. No scenery, floor, shadow blob, text, logo, or props.
            Waist-up, centered, facing the camera.
            Mood and expression: $mood
        """.trimIndent()
    }

    const val MASK_INSTRUCTION = """
        Convert this image into a perfect binary mask.
        Every character pixel, including highlights, hair, clothes, and shadows, must be solid pure white #FFFFFF.
        The background must be solid pure black #000000.
        No gray, shading, texture, outline, text, or shadow.
    """
}
