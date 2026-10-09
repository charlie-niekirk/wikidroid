package dev.cniekirk.wikidroid.core.seedmap

/**
 * A Java Edition version the seed map can generate, listed in cubiomes' `MCVersion` order. [nativeId] is
 * that enum's value (1 for Beta 1.7, counting up) and [label] is what its `mc2str` prints; an instrumented
 * test checks both against the pinned submodule, so a cubiomes update that renumbers or adds versions
 * fails loudly. Keep new entries in the same order as the native enum.
 *
 * Only the latest patch of a minor release is listed, as cubiomes does (1.16 stands for 1.16.5).
 */
enum class McVersion(
    val label: String,
) {
    B1_7("Beta 1.7"),
    B1_8("Beta 1.8"),
    V1_0("1.0"),
    V1_1("1.1"),
    V1_2("1.2"),
    V1_3("1.3"),
    V1_4("1.4"),
    V1_5("1.5"),
    V1_6("1.6"),
    V1_7("1.7"),
    V1_8("1.8"),
    V1_9("1.9"),
    V1_10("1.10"),
    V1_11("1.11"),
    V1_12("1.12"),
    V1_13("1.13"),
    V1_14("1.14"),
    V1_15("1.15"),
    V1_16_1("1.16.1"),
    V1_16("1.16"),
    V1_17("1.17"),
    V1_18("1.18"),
    V1_19_2("1.19.2"),
    V1_19("1.19"),
    V1_20("1.20"),
    V1_21_1("1.21.1"),
    V1_21_3("1.21.3"),
    V1_21_4("1.21.4"),
    V1_21_5("1.21.5"),
    V1_21_6("1.21.6"),
    V1_21_9("1.21.9"),
    V1_21_11("1.21.11"),
    V26_1("26.1"),
    V26_2("26.2"),
    V26_3("26.3"),
    ;

    internal val nativeId: Int get() = ordinal + 1

    /**
     * Whether the generator can build [dimension] in this version: cubiomes models the Nether from 1.16.1
     * and the End from 1.9; before that those dimensions had no biome noise to generate.
     */
    fun supports(dimension: Dimension): Boolean =
        when (dimension) {
            Dimension.OVERWORLD -> true
            Dimension.NETHER -> this >= V1_16_1
            Dimension.END -> this >= V1_9
        }

    companion object {
        val newest: McVersion = entries.last()

        /** The version called [label], or null if this build does not know it (a seed saved by a newer app). */
        fun fromLabel(label: String): McVersion? = entries.firstOrNull { it.label == label }
    }
}
