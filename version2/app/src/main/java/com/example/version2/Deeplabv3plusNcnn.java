package com.example.version2;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class Deeplabv3plusNcnn {

    public native boolean Init(AssetManager mgr);
    public native float Detect(Bitmap bitmap, Bitmap output, boolean use_gpu );


    static {
        System.loadLibrary("deeplabv3plusncnn");
    }
}
