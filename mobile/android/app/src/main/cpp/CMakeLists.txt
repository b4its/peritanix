cmake_minimum_required(VERSION 3.22.1)
project("dape_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(CMAKE_ANDROID_ARCH_ABI MATCHES "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -mfpu=neon")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
endif()

add_library(
    dape_native
    SHARED
    sediment_edge_detector.cpp
    burst_aligner.cpp
    archimedes_float_counter.cpp
)

find_library(log-lib log)

target_link_libraries(
    dape_native
    ${log-lib}
)

