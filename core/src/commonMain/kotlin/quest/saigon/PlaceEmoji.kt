package quest.saigon

object PlaceEmoji {
    fun forPrimaryType(primaryType: String?): String? = when (primaryType?.lowercase()) {
        "airport" -> "✈️"
        "art_gallery" -> "🖼️"
        "atm", "bank" -> "🏦"
        "bakery" -> "🥐"
        "bar", "night_club" -> "🍻"
        "cafe", "coffee_shop" -> "☕"
        "clothing_store", "shopping_mall", "store", "supermarket" -> "🛍️"
        "doctor", "hospital", "pharmacy" -> "🏥"
        "gas_station" -> "⛽"
        "gym", "fitness_center" -> "🏋️"
        "hotel", "lodging" -> "🏨"
        "library" -> "📚"
        "museum" -> "🏛️"
        "park", "national_park" -> "🌳"
        "restaurant", "food", "meal_takeaway", "meal_delivery" -> "🍽️"
        "school", "university" -> "🎓"
        "tourist_attraction" -> "📸"
        "train_station", "subway_station" -> "🚉"
        else -> null
    }
}
