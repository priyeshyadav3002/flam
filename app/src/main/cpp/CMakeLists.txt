# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of features are available to
# guidance link: https://cmake.org/cmake/help/latest/command/cmake_minimum_required.html
cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.
# guidance link: https://cmake.org/cmake/help/latest/command/project.html
project(flam)

# Creates and names a library, sets it as SHARED
# (a .so file) and provides the relative paths to the source code.
#
# !! THIS IS THE IMPORTANT CHANGE !!
# We name the library "flam" to match your project.
#
# guidance link: https://cmake.org/cmake/help/latest/command/add_library.html
add_library(flam
        SHARED
        native-lib.cpp)

# Specifies a path to search for files.
# guidance link: https://cmake.org/cmake/help/latest/command/find_library.html
find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies the libraries to link a target against.
# guidance link: https://cmake.org/cmake/help/latest/command/target_link_libraries.html
target_link_libraries(flam
        ${log-lib})