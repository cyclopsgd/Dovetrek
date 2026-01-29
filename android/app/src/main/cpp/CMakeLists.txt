cmake_minimum_required(VERSION 3.22.1)
project("routesolver")

add_library(routesolver SHARED solver.cpp)

find_library(log-lib log)
target_link_libraries(routesolver ${log-lib})
