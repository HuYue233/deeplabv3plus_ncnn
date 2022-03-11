package com.example.version2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.version2.databinding.ActivityMainBinding;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final int SELECT_IMAGE = 1;

    private TextView infoResult;
    private ImageView imageView;
    private ImageView imageView2;
    private Bitmap bitmap = null;
    private Bitmap yourSelectedImage = null;


    private Deeplabv3plusNcnn personSeg = new Deeplabv3plusNcnn();
    float result ;
    Bitmap img_result;

    Handler handler = new Handler(){
        public void handleMessage(android.os.Message msg){
            showObjects(img_result,result);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean ret_init = personSeg.Init(getAssets());
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov5ncnn Init failed");
        }

        infoResult = (TextView) findViewById(R.id.infoResult);
        imageView = (ImageView) findViewById(R.id.imageView);
        imageView2 = (ImageView) findViewById(R.id.imageView2);

        Button buttonImage = (Button) findViewById(R.id.buttonImage);
        buttonImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setType("image/*");
                startActivityForResult(i, SELECT_IMAGE);
            }
        });

        Button buttonDetect = (Button) findViewById(R.id.buttonDetect);
        buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null){
                    return;
                }

                Thread t = new Thread() {
                    @Override
                    public void run() {
                        img_result = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);

                        result = personSeg.Detect(yourSelectedImage ,img_result,false);

                        Message msg = new Message();
                        handler.sendMessage(msg);
                    }
                };
                t.start();

            }
        });

        Button buttonDetectGPU = (Button) findViewById(R.id.buttonDetectGPU);
        buttonDetectGPU.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (yourSelectedImage == null){
                    return;
                }

                Thread t = new Thread(){
                    @Override
                    public void run() {
                        img_result = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);

                        result = personSeg.Detect(yourSelectedImage ,img_result,true);

                        Message msg = new Message();
                        handler.sendMessage(msg);
                    }
                };
                t.start();

            }
        });

    }

    private void showObjects(Bitmap objects ,float result)
    {
        if (objects == null)
        {
            imageView.setImageBitmap(bitmap);
            return;
        }

        // draw objects on bitmap
        Bitmap rgba = objects.copy(Bitmap.Config.ARGB_8888, true);

        infoResult.setText("占比："+Float.toString(result));

        imageView2.setImageBitmap(rgba);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();

            try
            {
                if (requestCode == SELECT_IMAGE) {
                    bitmap = decodeUri(selectedImage);

                    yourSelectedImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    imageView.setImageBitmap(bitmap);
                }
            }
            catch (FileNotFoundException e)
            {
                Log.e("MainActivity", "FileNotFoundException");
                return;
            }
        }
    }

    private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException
    {
        // Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

        // The new size we want to scale to
        final int REQUIRED_SIZE = 640;

        // Find the correct scale value. It should be the power of 2.
        int width_tmp = o.outWidth, height_tmp = o.outHeight;
        int scale = 1;
        while (true) {
            if (width_tmp / 2 < REQUIRED_SIZE
                    || height_tmp / 2 < REQUIRED_SIZE) {
                break;
            }
            width_tmp /= 2;
            height_tmp /= 2;
            scale *= 2;
        }

        // Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o2);

        // Rotate according to EXIF
        int rotate = 0;
        try
        {
            ExifInterface exif = new ExifInterface(getContentResolver().openInputStream(selectedImage));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        }
        catch (IOException e)
        {
            Log.e("MainActivity", "ExifInterface IOException");
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

}