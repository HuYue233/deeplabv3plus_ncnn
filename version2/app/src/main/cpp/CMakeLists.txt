
cmake_minimum_required(VERSION 3.10.2)


project("version2")


set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20220216-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)


add_library(
        deeplabv3plusncnn

             SHARED

             native-lib.cpp )


target_link_libraries(
        deeplabv3plusncnn
                        ncnn

                       ${log-lib} )