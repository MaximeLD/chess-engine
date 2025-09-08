cmake_minimum_required(VERSION 3.15)
project(syzygyjni C CXX)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)
if(NOT JNI_FOUND)
  message(FATAL_ERROR "JNI not found. Set JAVA_HOME to your JDK (e.g. GraalVM JDK 24).")
endif()

include_directories(
  ${CMAKE_SOURCE_DIR}/fathom
  ${JNI_INCLUDE_DIRS}
)

# Build Fathom core as C++ so it uses <atomic> instead of <stdatomic.h>.
enable_language(CXX)
set(CMAKE_CXX_STANDARD 11)
set_source_files_properties(${CMAKE_SOURCE_DIR}/fathom/tbprobe.c PROPERTIES LANGUAGE CXX)

if(MSVC)
  add_compile_definitions(_CRT_SECURE_NO_WARNINGS)
  add_compile_options("$<$<CONFIG:Release>:/O2>")
endif()

add_library(syzygyjni SHARED
  syzygyjni.c                 # JNI bridge in C
  ${CMAKE_SOURCE_DIR}/fathom/tbprobe.c  # compiled as C++ (see above)
)

# Linker will pull in C++ runtime automatically since one TU is C++.

if(APPLE)
  set_target_properties(syzygyjni PROPERTIES OUTPUT_NAME "syzygyjni")
endif()

if (MSVC)
  foreach(flag_var CMAKE_C_FLAGS_RELEASE CMAKE_CXX_FLAGS_RELEASE)
    if(${flag_var} MATCHES "/MD")
      string(REPLACE "/MD" "/MT" ${flag_var} "${${flag_var}}")
    endif()
  endforeach()
endif()
