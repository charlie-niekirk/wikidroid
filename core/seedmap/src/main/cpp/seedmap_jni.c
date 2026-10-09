/*
 * JNI bridge to cubiomes. With seedmap_core.c (the logic behind these functions, free of JNI so a host
 * program can run it) this is the only C the app owns.
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

#include "biomes.h"
#include "finders.h"
#include "generator.h"
#include "seedmap_core.h"
#include "util.h"

#define JNI_FN(ret, name) JNIEXPORT ret JNICALL Java_dev_cniekirk_wikidroid_core_seedmap_NativeSeedMap_##name

/* Biome id returned when a query cannot be answered; cubiomes uses -1 (`none`) for the same thing. */
#define NO_BIOME (-1)

static const uint32_t KNOWN_FLAGS = LARGE_BIOMES | NO_BETA_OCEAN | FORCE_OCEAN_VARIANTS;

static Generator *generator_from(jlong handle) {
    return (Generator *)(intptr_t)handle;
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
    if (g == NULL || !sm_dimension_supported(g->mc, dim)) return JNI_FALSE;

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

/* Wraps `count` ints in a new Java array, or returns null (OutOfMemoryError pending) if it cannot. */
static jintArray to_java(JNIEnv *env, const int *values, int count) {
    jintArray result = (*env)->NewIntArray(env, count);
    if (result == NULL) return NULL;
    if (count > 0) (*env)->SetIntArrayRegion(env, result, 0, count, (const jint *)values);
    return result;
}

/*
 * Biome ids for a `w` x `h` area at `scale` (see sm_gen_biomes), row by row, or null if the arguments
 * are invalid or the generator has no seed yet.
 */
JNI_FN(jintArray, nativeGenBiomes)(JNIEnv *env, jclass clazz, jlong handle, jint scale, jint x, jint z, jint w, jint h, jint y) {
    (void)clazz;
    if (w < 1 || h < 1 || w > SM_MAX_BIOME_SIDE || h > SM_MAX_BIOME_SIDE) return NULL;
    if ((int64_t)w * h > SM_MAX_BIOME_CELLS) return NULL;
    int *cells = (int *)malloc(sizeof(int) * (size_t)w * (size_t)h);
    if (cells == NULL) return NULL;

    jintArray result = NULL;
    if (sm_gen_biomes(generator_from(handle), scale, x, z, w, h, y, cells) == 0) {
        result = to_java(env, cells, w * h);
    }
    free(cells);
    return result;
}

/*
 * Positions of one structure type in [x0, x1) x [z0, z1), packed [x, z, x, z, ...]. An empty array means
 * none were found; null means the arguments were invalid or the structure does not exist in this
 * generator's version and dimension.
 */
JNI_FN(jintArray, nativeStructures)(JNIEnv *env, jclass clazz, jlong handle, jint type, jint x0, jint z0, jint x1, jint z1) {
    (void)clazz;
    int count = 0;
    int *found = sm_structures(generator_from(handle), type, x0, z0, x1, z1, &count);
    if (count < 0) return NULL;
    jintArray result = to_java(env, found, count * 2);
    free(found);
    return result;
}

/* Up to `count` stronghold positions, packed [x, z, ...], or null for invalid arguments. */
JNI_FN(jintArray, nativeStrongholds)(JNIEnv *env, jclass clazz, jlong handle, jint count) {
    (void)clazz;
    if (count < 1 || count > SM_MAX_STRONGHOLDS) return NULL;
    int out[2 * SM_MAX_STRONGHOLDS];
    int written = sm_strongholds(generator_from(handle), count, out);
    if (written < 0) return NULL;
    return to_java(env, out, written * 2);
}

/* cubiomes' biome colour table as 256 opaque 0xAARRGGBB ints, indexed by biome id. */
JNI_FN(jintArray, nativeBiomeColors)(JNIEnv *env, jclass clazz) {
    (void)clazz;
    unsigned char colors[256][3];
    int packed[256];
    initBiomeColors(colors);
    for (int i = 0; i < 256; i++) {
        packed[i] = (int)(0xFF000000u | ((uint32_t)colors[i][0] << 16) | ((uint32_t)colors[i][1] << 8) | colors[i][2]);
    }
    return to_java(env, packed, 256);
}

JNI_FN(jboolean, nativeStructureSupported)(JNIEnv *env, jclass clazz, jint type, jint version, jint dim) {
    (void)env;
    (void)clazz;
    return sm_structure_supported(type, version, dim) ? JNI_TRUE : JNI_FALSE;
}

/* cubiomes' name for a structure type (for example "village"), or null if it has none. */
JNI_FN(jstring, nativeStructureName)(JNIEnv *env, jclass clazz, jint type) {
    (void)clazz;
    const char *name = struct2str(type);
    return name == NULL ? NULL : (*env)->NewStringUTF(env, name);
}

/* The biome's resource name in that version, or null when the biome does not exist there. */
JNI_FN(jstring, nativeBiomeName)(JNIEnv *env, jclass clazz, jint version, jint id) {
    (void)clazz;
    if (version < MC_B1_7 || version > MC_NEWEST || id < 0 || id > 255) return NULL;
    if (!biomeExists(version, id)) return NULL;
    const char *name = biome2str(version, id);
    return name == NULL ? NULL : (*env)->NewStringUTF(env, name);
}
