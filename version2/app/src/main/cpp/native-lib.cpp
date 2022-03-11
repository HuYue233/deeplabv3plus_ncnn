// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2020 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <string>
#include <vector>

// ncnn
#include "layer.h"
#include "net.h"
#include "benchmark.h"


static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
static ncnn::PoolAllocator g_workspace_pool_allocator;

static ncnn::Net personSeg;

extern "C" {

float percentage;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "JNI_OnLoad");

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "JNI_OnUnload");

    ncnn::destroy_gpu_instance();
}

// public native boolean Init(AssetManager mgr);
JNIEXPORT jboolean JNICALL Java_com_example_version2_Deeplabv3plusNcnn_Init(JNIEnv* env, jobject thiz, jobject assetManager)
{
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 8;
    opt.blob_allocator = &g_blob_pool_allocator;
    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;


    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    personSeg.opt = opt;

    // init param
    {
        int ret = personSeg.load_param(mgr, "version1.param");

        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "load_param failed");
            return JNI_FALSE;
        }
    }

    // init bin
    {
        int ret = personSeg.load_model(mgr, "version1.bin");
        if (ret != 0)
        {
            __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3Ncnn", "load_model failed");
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

// public native Obj[] Detect(Bitmap bitmap, boolean use_gpu);
JNIEXPORT float JNICALL Java_com_example_version2_Deeplabv3plusNcnn_Detect(JNIEnv* env, jobject thiz, jobject bitmap, jobject out, jboolean use_gpu)
{

    if (use_gpu == JNI_TRUE && ncnn::get_gpu_count() == 0)
    {
        return 0;
        //return env->NewStringUTF("no vulkan capable gpu");
    }

    double start_time = ncnn::get_current_time();

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    const int width = info.width;
    const int height = info.height;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return 0;

    // ncnn from bitmap
    const int target_size = 512;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB, target_size, target_size);
    ncnn::Mat image = in.clone();

    float num = 0;

    // deeplabv3plus
    {
        const float mean_vals[3] = {123.68f, 116.78f, 103.94f};
        const float norm_vals[3] = {1.0/58.40f, 1.0/57.12f, 1.0/57.38f};
        in.substract_mean_normalize(mean_vals, norm_vals);

        ncnn::Extractor ex = personSeg.create_extractor();

        ex.set_vulkan_compute(use_gpu);


        ex.input("input.1", in);
        ncnn::Mat output;
        ex.extract("674", output);


//      output.c == 2 是二分类问题，所以这里只需要两个数组保存结果
        float *pCls0 = output.channel(0);
        float *pCls1 = output.channel(1);
        float *pCls2 = output.channel(3);


//        __android_log_print(ANDROID_LOG_DEBUG, "print", " image.w:%d ",image.w);
//        __android_log_print(ANDROID_LOG_DEBUG, "print", " image.h:%d ",image.h);
//        __android_log_print(ANDROID_LOG_DEBUG, "print", " image.c:%d ",image.c);
        bitmap;


//      图片的三个通道
        float *pImage0 = image.channel(0);
        float *pImage1 = image.channel(1);
        float *pImage2 = image.channel(2);


        for (int i = 0; i < output.w * output.h; i++) {
            if (pCls0[i] < pCls1[i]) {
//                __android_log_print(ANDROID_LOG_DEBUG, "print", " 0:%f ",pCls0[i]);
//                __android_log_print(ANDROID_LOG_DEBUG, "print", " 1:%f ",pCls1[i]);
//                __android_log_print(ANDROID_LOG_DEBUG, "print", " 2:%f ",pCls2[i]);


                pImage0[i] = 255;
//                pImage1[i] = 0;
//                pImage2[i] = 0;
                num++;
            }
        }
        percentage = num / (512 * 512);
//            __android_log_print(ANDROID_LOG_DEBUG, "print", " 荧光占比:%f ",percentage);
    }
    image.to_android_bitmap(env, out, ncnn::Mat::PIXEL_RGB2RGBA);

    double elasped = ncnn::get_current_time() - start_time;
    __android_log_print(ANDROID_LOG_DEBUG, "Deeplabv3plusNcnn", "%.2fms   detect", elasped);

    return percentage;

}

}
