cmake_minimum_required(VERSION 3.22.1)
project(nodejsrunner)

include_directories(${CMAKE_SOURCE_DIR}/node-include)

add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so")

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(native-lib libnode ${log-lib})
