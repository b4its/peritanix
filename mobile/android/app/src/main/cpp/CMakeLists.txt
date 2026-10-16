cmake_minimum_required(VERSION 3.22.1)
project("dape_native")

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
