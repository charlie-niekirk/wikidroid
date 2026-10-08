package dev.cniekirk.wikidroid.core.testing

/**
 * Reads files from `src/main/resources/fixtures`. The network fixtures are trimmed captures of real
 * minecraft.wiki responses (see `fixtures/network`), so DTO changes are checked against the live shape.
 */
object Fixtures {
    fun read(path: String): String {
        val resource = "fixtures/$path"
        val stream =
            checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream(resource)) {
                "Missing test fixture $resource"
            }
        return stream.use { it.readBytes().decodeToString() }
    }
}
