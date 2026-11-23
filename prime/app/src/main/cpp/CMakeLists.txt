cmake_minimum_required(VERSION 3.22.1)
project(cortexwhisper)

include(FetchContent)
# whisper.cpp (MIT), pinned release; fetched at build time.
FetchContent_Declare(whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.8.7
    GIT_SHALLOW TRUE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(cortexwhisper SHARED jni.cpp)
target_link_libraries(cortexwhisper whisper log)
