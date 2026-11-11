cmake_minimum_required(VERSION 3.22.1)
project(gambitly_engine CXX)

# Builds Stockfish as a stand-alone UCI executable. It is named lib*.so and
# emitted into the library output directory so the Android Gradle plugin packs
# it into the APK's native-lib folder, from where the app launches it as a process.

set(SF_DIR ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/src)

# The NNUE network is embedded in the binary. Its file name carries the first
# 12 hex digits of its SHA-256, which is how the download is verified.
set(SF_NET nn-5af11540bbfe.nnue)
set(SF_NET_DIR ${CMAKE_CURRENT_SOURCE_DIR}/nnue)
set(SF_NET_PATH ${SF_NET_DIR}/${SF_NET})
if(NOT EXISTS ${SF_NET_PATH})
    message(STATUS "Downloading ${SF_NET}")
    file(DOWNLOAD https://tests.stockfishchess.org/api/nn/${SF_NET} ${SF_NET_PATH}.part STATUS dl_status)
    list(GET dl_status 0 dl_code)
    if(NOT dl_code EQUAL 0)
        file(REMOVE ${SF_NET_PATH}.part)
        message(FATAL_ERROR "Could not download ${SF_NET}: ${dl_status}")
    endif()
    file(RENAME ${SF_NET_PATH}.part ${SF_NET_PATH})
endif()
file(SHA256 ${SF_NET_PATH} net_hash)
string(SUBSTRING ${net_hash} 0 12 net_hash_prefix)
if(NOT SF_NET STREQUAL "nn-${net_hash_prefix}.nnue")
    message(FATAL_ERROR "${SF_NET_PATH} is corrupt (sha256 ${net_hash}); delete it and rebuild")
endif()

add_executable(stockfish
    ${SF_DIR}/benchmark.cpp
    ${SF_DIR}/bitbase.cpp
    ${SF_DIR}/bitboard.cpp
    ${SF_DIR}/endgame.cpp
    ${SF_DIR}/evaluate.cpp
    ${SF_DIR}/main.cpp
    ${SF_DIR}/material.cpp
    ${SF_DIR}/misc.cpp
    ${SF_DIR}/movegen.cpp
    ${SF_DIR}/movepick.cpp
    ${SF_DIR}/pawns.cpp
    ${SF_DIR}/position.cpp
    ${SF_DIR}/psqt.cpp
    ${SF_DIR}/search.cpp
    ${SF_DIR}/thread.cpp
    ${SF_DIR}/timeman.cpp
    ${SF_DIR}/tt.cpp
    ${SF_DIR}/uci.cpp
    ${SF_DIR}/ucioption.cpp
    ${SF_DIR}/tune.cpp
    ${SF_DIR}/syzygy/tbprobe.cpp
    ${SF_DIR}/nnue/evaluate_nnue.cpp
    ${SF_DIR}/nnue/features/half_ka_v2_hm.cpp
)

set_target_properties(stockfish PROPERTIES
    OUTPUT_NAME "libstockfish.so"
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# Mirrors the upstream Makefile's ARCH=armv8 release settings.
target_compile_definitions(stockfish PRIVATE NDEBUG IS_64BIT USE_POPCNT USE_PTHREADS USE_NEON=8)
target_compile_options(stockfish PRIVATE -O3 -fno-exceptions -Wno-deprecated-declarations)
# incbin resolves the network file through the assembler's include path.
target_include_directories(stockfish PRIVATE ${SF_NET_DIR})
