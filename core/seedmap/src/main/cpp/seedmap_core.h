/*
 * The logic behind the JNI functions, free of any JNI types so a plain host program can run it.
 *
 * Every function validates its arguments and reports a bad one through its return value. cubiomes itself
 * calls exit() for a structure type it does not implement, which would kill the app, so structure types
 * are checked against a whitelist here before they reach it.
 */
#ifndef SEEDMAP_CORE_H_
#define SEEDMAP_CORE_H_

#include <stdint.h>

#include "generator.h"

/* Most cells one biome request may cover (a 1024 x 1024 area), and the widest side. */
#define SM_MAX_BIOME_CELLS (1 << 20)
#define SM_MAX_BIOME_SIDE 1024

/* Widest area, in blocks, one structure query may cover, and the most regions it may walk. */
#define SM_MAX_STRUCTURE_SPAN 16384
#define SM_MAX_STRUCTURE_REGIONS (1 << 19)

/* The most strongholds a world has from 1.9 on; earlier versions have 3. */
#define SM_MAX_STRONGHOLDS 128

/* Whether the generator can be put into `dim` for this version (Nether from 1.16.1, End from 1.9). */
int sm_dimension_supported(int mc, int dim);

/* Whether `type` is a structure the map knows, exists in `mc`, and generates in `dim`. */
int sm_structure_supported(int type, int mc, int dim);

/*
 * Biome ids for an area of `w` x `h` cells at `scale` blocks per cell (1, 4, 16, 64 or 256), whose
 * north-west cell is (x, z) in units of that scale. `y` is in blocks when scale is 1 and in 4-block units
 * otherwise. Writes w * h ids row by row into `out`. Returns 0 on success.
 */
int sm_gen_biomes(const Generator *g, int scale, int x, int z, int w, int h, int y, int *out);

/*
 * Positions of one structure type with x0 <= x < x1 and z0 <= z < z1 (blocks), checked for biome
 * viability. Returns a malloc'd array of `*count` (x, z) pairs, to be released with free(), or NULL with
 * `*count` set to 0 when the arguments are invalid or nothing was found. `*count` is -1 on a bad argument.
 */
int *sm_structures(Generator *g, int type, int x0, int z0, int x1, int z1, int *count);

/*
 * Writes up to `count` stronghold positions as (x, z) pairs into `out` (2 * count ints), nearest ring
 * first. The generator must be on the overworld. Returns how many were written, or -1 on a bad argument.
 */
int sm_strongholds(const Generator *g, int count, int *out);

#endif
