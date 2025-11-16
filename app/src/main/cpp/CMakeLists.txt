# Sets the minimum version of CMake required.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project(flam)

# --- START OF OPENCV CONFIGURATION ---

# 1. SET THE PATH TO YOUR OPENCV SDK
#    Yeh aapka path hai jo aapne diya tha.
set(OpenCV_DIR "C:/Users/aryan/AndroidStudioProjects/Opencv/sdk/native/jni")

# 2. FIND THE OPENCV PACKAGE
#    Yeh command upar wale path se OpenCV ko dhoondta hai.
find_package(OpenCV REQUIRED)

# 3. INCLUDE THE OPENCV HEADERS
#    Iss se hum C++ code mein OpenCV ki files include kar paayenge.
include_directories(${OpenCV_INCLUDE_DIRS})

# --- END OF OPENCV CONFIGURATION ---


# Humari native library "flam" banata hai.
add_library(flam
        SHARED
        native-lib.cpp)

# Android ki logging library dhoondta hai.
find_library(log-lib
        log)

# Humari "flam" library ko OpenCV aur logging libraries se link karta hai.
target_link_libraries(flam
        ${OpenCV_LIBS}
        ${log-lib})