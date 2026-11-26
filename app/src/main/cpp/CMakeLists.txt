cmake_minimum_required(VERSION 3.22.1)
project(notecraft_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(
    notecraft_audio
    SHARED
    AudioEngine.cpp
    JniBridge.cpp
)

target_include_directories(
    notecraft_audio
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    notecraft_audio
    oboe::oboe
    android
    log
    amidi
)
