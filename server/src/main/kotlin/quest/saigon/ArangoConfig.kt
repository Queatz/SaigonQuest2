package quest.saigon

data class ArangoConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val user: String = DEFAULT_USER,
    val password: String = DEFAULT_PASSWORD,
    val database: String = DEFAULT_DATABASE,
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8529
        const val DEFAULT_USER = "root"
        const val DEFAULT_PASSWORD = "saigonquest"
        const val DEFAULT_DATABASE = "saigonquest"

        fun fromEnv(getenv: (String) -> String? = System::getenv): ArangoConfig =
            ArangoConfig(
                host = getenv("ARANGO_HOST")?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port = getenv("ARANGO_PORT")?.toIntOrNull() ?: DEFAULT_PORT,
                user = getenv("ARANGO_USER")?.takeIf { it.isNotBlank() } ?: DEFAULT_USER,
                password = getenv("ARANGO_PASSWORD") ?: DEFAULT_PASSWORD,
                database = getenv("ARANGO_DATABASE")?.takeIf { it.isNotBlank() } ?: DEFAULT_DATABASE,
            )
    }
}
