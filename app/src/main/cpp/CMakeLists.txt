cmake_minimum_required(VERSION 3.22.1)
project("truetune_audio")

# Oboe is pulled in as a prefab AAR from the app's Gradle config. This lets
# CMake `find_package` locate the headers + shared library.
find_package(oboe REQUIRED CONFIG)

add_library(truetune_audio SHARED
    native-lib.cpp
    AudioInput.cpp
    PitchDetector.cpp
)

target_include_directories(truetune_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# -Wall / -Wextra flag anything obvious. -fvisibility=hidden shrinks the .so
# and keeps only our JNI entry points exported.
target_compile_options(truetune_audio PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
)

target_link_libraries(truetune_audio
    android
    log
    oboe::oboe
)
