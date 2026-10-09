cmake_minimum_required(VERSION 3.22)
project(seedmap C)

set(CUBIOMES ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes)
if (NOT EXISTS ${CUBIOMES}/generator.c)
    message(FATAL_ERROR "cubiomes is missing. Run: git submodule update --init")
endif()

# Only what biome generation, structure placement and strongholds need. The fork's loot tables,
# carvers and ore finder are left out; they are most of its size and nothing here calls them.
add_library(cubiomes STATIC
    ${CUBIOMES}/biomenoise.c
    ${CUBIOMES}/biomes.c
    ${CUBIOMES}/finders.c
    ${CUBIOMES}/generator.c
    ${CUBIOMES}/layers.c
    ${CUBIOMES}/noise.c
    ${CUBIOMES}/quadbase.c
    ${CUBIOMES}/terrainnoise.c
    ${CUBIOMES}/util.c
    ${CUBIOMES}/xradv.c
    # finders.c pulls these in through getStructurePieces().
    ${CUBIOMES}/features/abandoned_camp.c
    ${CUBIOMES}/features/end_city.c
    ${CUBIOMES}/features/fortress.c
    ${CUBIOMES}/features/stronghold.c
)
target_include_directories(cubiomes PUBLIC ${CUBIOMES})
# cubiomes relies on wrapping signed arithmetic (Java's int/long overflow).
target_compile_options(cubiomes PRIVATE -fwrapv -O2 -ffunction-sections -fdata-sections -w)
set_target_properties(cubiomes PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(seedmap SHARED seedmap_jni.c)
target_compile_options(seedmap PRIVATE -fwrapv -O2 -ffunction-sections -fdata-sections -Wall -Wextra)
target_link_libraries(seedmap PRIVATE cubiomes)
# gc-sections drops the cubiomes code nothing calls; max-page-size keeps segments 16 KB aligned
# (the NDK default since r28, set explicitly so an older NDK cannot silently regress it).
target_link_options(seedmap PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384 -Wl,--build-id=none)
