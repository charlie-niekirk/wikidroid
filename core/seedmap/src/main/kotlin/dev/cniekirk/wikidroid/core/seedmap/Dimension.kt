package dev.cniekirk.wikidroid.core.seedmap

enum class Dimension(
    internal val nativeId: Int,
) {
    OVERWORLD(0),
    NETHER(-1),
    END(1),
}
