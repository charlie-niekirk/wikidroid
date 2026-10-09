#include "seedmap_core.h"

#include <stdlib.h>

#include "finders.h"

static int floor_div(int a, int b) {
    int q = a / b;
    return (a % b != 0 && ((a < 0) != (b < 0))) ? q - 1 : q;
}

int sm_dimension_supported(int mc, int dim) {
    switch (dim) {
        case DIM_OVERWORLD: return 1;
        case DIM_NETHER: return mc >= MC_1_16_1;
        case DIM_END: return mc >= MC_1_9;
        default: return 0;
    }
}

/* The types the map shows. Anything else is rejected before it can reach cubiomes' exit() paths. */
static int structure_listed(int type) {
    switch (type) {
        case Desert_Pyramid:
        case Jungle_Temple:
        case Swamp_Hut:
        case Igloo:
        case Village:
        case Ocean_Ruin:
        case Shipwreck:
        case Monument:
        case Mansion:
        case Outpost:
        case Ruined_Portal:
        case Ruined_Portal_N:
        case Ancient_City:
        case Treasure:
        case Trail_Ruins:
        case Trial_Chambers:
        case Abandoned_Camp:
        case Fortress:
        case Bastion:
        case Nether_Fossil:
        case End_City:
        case End_Gateway:
            return 1;
        default:
            return 0;
    }
}

int sm_structure_supported(int type, int mc, int dim) {
    if (mc < MC_B1_7 || mc > MC_NEWEST || !structure_listed(type)) return 0;
    if (!sm_dimension_supported(mc, dim)) return 0;
    StructureConfig config;
    if (!getStructureConfig(type, mc, &config)) return 0;
    return config.dim == dim;
}

static int scale_valid(int scale) {
    return scale == 1 || scale == 4 || scale == 16 || scale == 64 || scale == 256;
}

int sm_gen_biomes(const Generator *g, int scale, int x, int z, int w, int h, int y, int *out) {
    if (g == NULL || out == NULL || g->dim == DIM_UNDEF || !scale_valid(scale)) return -1;
    if (w < 1 || h < 1 || w > SM_MAX_BIOME_SIDE || h > SM_MAX_BIOME_SIDE) return -1;
    if ((int64_t)w * h > SM_MAX_BIOME_CELLS) return -1;
    /* Keep the area within about twice the world border so coordinate maths cannot overflow. */
    const int64_t limit = 60000000;
    if (llabs((int64_t)x) * scale > limit || llabs((int64_t)z) * scale > limit) return -1;
    if (llabs((int64_t)x + w) * scale > limit || llabs((int64_t)z + h) * scale > limit) return -1;
    if (y < -512 || y > 512) return -1;

    Range range = {scale, x, z, w, h, y, 1};
    int *cache = allocCache(g, range);
    if (cache == NULL) return -1;
    int status = genBiomes(g, cache, range);
    if (status == 0) {
        for (int64_t i = 0, n = (int64_t)w * h; i < n; i++) out[i] = cache[i];
    }
    free(cache);
    return status;
}

int *sm_structures(Generator *g, int type, int x0, int z0, int x1, int z1, int *count) {
    *count = 0;
    if (g == NULL || g->dim == DIM_UNDEF) goto bad;
    if (x1 <= x0 || z1 <= z0) goto bad;
    if ((int64_t)x1 - x0 > SM_MAX_STRUCTURE_SPAN || (int64_t)z1 - z0 > SM_MAX_STRUCTURE_SPAN) goto bad;
    const int64_t limit = 60000000;
    if (llabs((int64_t)x0) > limit || llabs((int64_t)z0) > limit) goto bad;
    if (llabs((int64_t)x1) > limit || llabs((int64_t)z1) > limit) goto bad;
    if (!sm_structure_supported(type, g->mc, g->dim)) goto bad;

    StructureConfig config;
    if (!getStructureConfig(type, g->mc, &config)) goto bad;
    const int regionBlocks = config.regionSize * 16;
    if (regionBlocks < 1) goto bad;

    const int rx0 = floor_div(x0, regionBlocks);
    const int rx1 = floor_div(x1 - 1, regionBlocks);
    const int rz0 = floor_div(z0, regionBlocks);
    const int rz1 = floor_div(z1 - 1, regionBlocks);
    if ((int64_t)(rx1 - rx0 + 1) * (rz1 - rz0 + 1) > SM_MAX_STRUCTURE_REGIONS) goto bad;

    int capacity = 64;
    int used = 0;
    int *found = (int *)malloc(sizeof(int) * 2 * capacity);
    if (found == NULL) goto bad;

    for (int rz = rz0; rz <= rz1; rz++) {
        for (int rx = rx0; rx <= rx1; rx++) {
            Pos pos;
            if (!getStructurePos(type, g->mc, g->seed, rx, rz, &pos)) continue;
            if (pos.x < x0 || pos.x >= x1 || pos.z < z0 || pos.z >= z1) continue;
            if (!isViableStructurePos(type, g, pos.x, pos.z, 0)) continue;
            if (used == capacity) {
                capacity *= 2;
                int *grown = (int *)realloc(found, sizeof(int) * 2 * capacity);
                if (grown == NULL) {
                    free(found);
                    goto bad;
                }
                found = grown;
            }
            found[2 * used] = pos.x;
            found[2 * used + 1] = pos.z;
            used++;
        }
    }
    if (used == 0) {
        free(found);
        return NULL;
    }
    *count = used;
    return found;

bad:
    *count = -1;
    return NULL;
}

int sm_strongholds(const Generator *g, int count, int *out) {
    if (g == NULL || out == NULL || g->dim != DIM_OVERWORLD) return -1;
    if (count < 1 || count > SM_MAX_STRONGHOLDS) return -1;
    if (g->mc < MC_B1_8) return 0; /* Beta 1.7 has no strongholds */

    const int most = g->mc >= MC_1_9 ? SM_MAX_STRONGHOLDS : 3;
    const int wanted = count < most ? count : most;
    StrongholdIter iter;
    initFirstStronghold(&iter, g->mc, g->seed);
    for (int i = 0; i < wanted; i++) {
        nextStronghold(&iter, g);
        out[2 * i] = iter.pos.x;
        out[2 * i + 1] = iter.pos.z;
    }
    return wanted;
}
