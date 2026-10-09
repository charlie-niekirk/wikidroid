/*
 * JNI bridge to cubiomes. This is the only C the app owns.
 *
 * Rules that keep the native side simple and safe:
 *  - Results cross the boundary as primitives and primitive arrays only. No Kotlin object is built
 *    here, so there are no class or field lookups by name for R8 to break.
 *  - A generator is an opaque pointer held as a jlong. Kotlin owns its lifetime (create/destroy) and
 *    never uses one from two threads at once: cubiomes generators are mutated by every query.
 *  - Every argument is checked again here. A bad value would crash the whole process, not throw.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "finders.h"
#include "generator.h"
#include "util.h"

#define JNI_FN(ret, name) JNIEXPORT ret JNICALL Java_dev_cniekirk_wikidroid_core_seedmap_NativeSeedMap_##name

/* Biome id returned when a query cannot be answered; cubiomes uses -1 (`none`) for the same thing. */
#define NO_BIOME (-1)

static const uint32_t KNOWN_FLAGS = LARGE_BIOMES | NO_BETA_OCEAN | FORCE_OCEAN_VARIANTS;

static Generator *generator_from(jlong handle) {
    return (Generator *)(intptr_t)handle;
}

static int dimension_supported(int mc, int dim) {
    switch (dim) {
        case DIM_OVERWORLD: return 1;
        case DIM_NETHER: return mc >= MC_1_16_1;
        case DIM_END: return mc >= MC_1_9;
        default: return 0;
    }
}

JNI_FN(jlong, nativeCreate)(JNIEnv *env, jclass clazz, jint version, jint flags) {
    (void)env;
    (void)clazz;
    if (version < MC_B1_7 || version > MC_NEWEST) return 0;
    if (((uint32_t)flags & ~KNOWN_FLAGS) != 0) return 0;

    Generator *g = (Generator *)calloc(1, sizeof(Generator));
    if (g == NULL) return 0;
    setupGenerator(g, version, (uint32_t)flags);
    return (jlong)(intptr_t)g;
}

JNI_FN(void, nativeDestroy)(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    free(generator_from(handle));
}

/*
 * Points the generator at a seed and dimension. The layer stack, noise and End/Nether state share
 * storage inside Generator, so the generator is set up again each time rather than switched.
 * Returns JNI_FALSE, leaving the generator untouched, if the dimension does not exist in that version.
 */
JNI_FN(jboolean, nativeApplySeed)(JNIEnv *env, jclass clazz, jlong handle, jint dim, jlong seed) {
    (void)env;
    (void)clazz;
    Generator *g = generator_from(handle);
    if (g == NULL || !dimension_supported(g->mc, dim)) return JNI_FALSE;

    setupGenerator(g, g->mc, g->flags);
    applySeed(g, dim, (uint64_t)seed);
    return JNI_TRUE;
}

/*
 * Biome id at one position, or NO_BIOME. `scale` is 1 (block coordinates) or 4 (biome coordinates, where
 * x, y and z are all in units of 4 blocks); cubiomes documents no other value for single-point queries.
 */
JNI_FN(jint, nativeGetBiomeAt)(JNIEnv *env, jclass clazz, jlong handle, jint scale, jint x, jint y, jint z) {
    (void)env;
    (void)clazz;
    Generator *g = generator_from(handle);
    if (g == NULL || g->dim == DIM_UNDEF) return NO_BIOME;
    if (scale != 1 && scale != 4) return NO_BIOME;
    return getBiomeAt(g, scale, x, y, z);
}

/* World spawn as [x, z], or null for a generator that is not on the overworld. */
JNI_FN(jintArray, nativeGetSpawn)(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    Generator *g = generator_from(handle);
    if (g == NULL || g->dim != DIM_OVERWORLD) return NULL;

    Pos spawn = getSpawn(g);
    jint out[2] = {spawn.x, spawn.z};
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) return NULL; /* OutOfMemoryError is already pending */
    (*env)->SetIntArrayRegion(env, result, 0, 2, out);
    return result;
}

/* cubiomes' own display name for a version int (for example "1.21.4"), or null if it has none. */
JNI_FN(jstring, nativeVersionName)(JNIEnv *env, jclass clazz, jint version) {
    (void)clazz;
    if (version < MC_B1_7 || version > MC_NEWEST) return NULL;
    const char *name = mc2str(version);
    return name == NULL ? NULL : (*env)->NewStringUTF(env, name);
}

JNI_FN(jint, nativeNewestVersion)(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return MC_NEWEST;
}
