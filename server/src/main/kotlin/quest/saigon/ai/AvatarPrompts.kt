package quest.saigon.ai

object AvatarPrompts {
    const val EXPAND_SYSTEM = """
        You write one short image-edit note for an illustrated character.
        The note describes things such as expression, pose, apparel, and mood.
        Do not mention backgrounds, cameras, or text.
        Output the note only, with no quotes or markdown.
    """

    fun editInstruction(userPrompt: String, expanded: String?): String {
        val mood = expanded?.trim()?.takeIf { it.isNotEmpty() } ?: userPrompt.trim()
        return """
            Edit the person in the input photo into an illustrated character.
            Keep them recognizable: same face, hair, age, and features.
            Strong luminosity contrast, smooth cell shading, no strokes, no line art, no outlines, bright studio lighting.
            Make skin tones smoothly and beautifully pop with strong lighting and overall brightness.
            Keep the subject in frame.
            No white border, no sticker edge, no halo, and no cutout stroke.
            Solid flat light gray background #bababa only. The area behind the person must be that light gray, never black, gray, or white.
            No scenery, floor, shadows, text, logos, or props.
            Waist-up, centered, facing the camera.
            Mood and expression and other details: $mood
        """.trimIndent()
    }

    const val MASK_INSTRUCTION = """
        Generate a perfect binary segmentation mask of the person in this image.
        White (#FFFFFF) = every pixel belonging to the person (including all internal details, lines, shadows, textures, crown).
        Black (#000000) = everything else (background only).
        No grays. No transparency. No missing internal elements. 
        The mask must be completely solid white inside the person's outline - no lines or gaps should appear transparent/black within the figure.
        Preserve exact original dimensions and positioning.
    """
}
