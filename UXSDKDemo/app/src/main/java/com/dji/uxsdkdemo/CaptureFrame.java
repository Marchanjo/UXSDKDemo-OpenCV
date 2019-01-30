package com.dji.uxsdkdemo;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.thirdparty.afinal.core.AsyncTask;

import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC4;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.core.CvType.CV_8UC4;
import static org.opencv.imgproc.Imgproc.COLOR_YUV2RGBA_NV21;
import static org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_IYUV;
import static org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21;
import static org.opencv.imgproc.Imgproc.COLOR_YUV420sp2BGRA;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class CaptureFrame {
    private static final String TAG = MainActivity.class.getName();
    private DJICodecManager mCodecManager;//Marcelo
    private VideoFeeder.VideoFeed standardVideoFeeder;//Marcelo
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;//Marcelo
    private Camera mCamera;//Marcelo
    private TextureView videostreamPreviewTtView;//Marcelo
    private int videoViewWidth;//Marcelo
    private int videoViewHeight;//Marcelo
    private ImageButton screenShot;//Marcelo
    private int  count;//Marcelo
    private Context appContextReceived;


    public CaptureFrame(Context appContext,ImageButton screenShot, TextureView videostreamPreviewTtView) {
        appContextReceived=appContext;
        this.screenShot = screenShot;
        screenShot.setSelected(false);
        screenShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleYUVClick();//Captura 1 frame a cada 30
                //handleYUVClickSingleFrame();//Captura somente um frame
            }
        });

        this.videostreamPreviewTtView = videostreamPreviewTtView;
        videostreamPreviewTtView.setVisibility(View.VISIBLE);
        openCVStart();
    }

    public void openCVStart() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,appContextReceived,mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(appContextReceived) {testar
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //mOpenCvCameraView.enableView();
                    //mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
            }
            if (standardVideoFeeder != null) {
                standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
            }
        }
    }

    public void onDestroy() {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
    }

    public void onResume() {
        initSurfaceOrTextureView();
        notifyStatusChange();
    }


    private void showToast(String s) {//Marcelo
        Toast.makeText(videostreamPreviewTtView.getContext(), s, Toast.LENGTH_SHORT).show();
    }

    private long lastupdate;//Marcelo

    private void notifyStatusChange() {//Marcelo
        final BaseProduct product = VideoDecodingApplication.getProductInstance();
        Log.d(TAG, "notifyStatusChange: " + (product == null ? "Disconnect" : (product.getModel() == null ? "null model" : product.getModel().name())));

        if (product != null && product.isConnected() && product.getModel() != null) {
            showToast(product.getModel().name() + " Connected ");
        } else {
            showToast("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (System.currentTimeMillis() - lastupdate > 1000) {
                    Log.d(TAG, "camera recv video data size: " + size);
                    lastupdate = System.currentTimeMillis();
                }

                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);

                }

            }
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast("can't change mode of camera, error:" + djiError.getDescription());
                        }
                    }
                });


                if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
                }

            }
        }
    }

    private void initSurfaceOrTextureView() {//Marcelo
        initPreviewerTextureView();
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewerTextureView() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight);
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(videostreamPreviewTtView.getContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable2: width " + videoViewWidth + " height " + videoViewHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

  /*  public void onClick(View v) {
        if (v.getId() == R.id.activity_main_screen_shot) {
            handleYUVClick();
        }
    }*/

  /*  private void handleYUVClick() {
        if (screenShot.isSelected()) {
            screenShot.setText("Screen Shot");
            screenShot.setSelected(false);
            mCodecManager.enabledYuvData(false);
            mCodecManager.setYuvDataCallback(null);
        } else {//Começa a capturar frames
            screenShot.setText("Live Stream");
            screenShot.setSelected(true);
            mCodecManager.enabledYuvData(true);
            mCodecManager.setYuvDataCallback(this);
        }
    }*/

 //Captura 1 frame a cada 30 frames - funciona OK
    private void handleYUVClick() {
        if (screenShot.isSelected()) {
            showToast("Stop Capturing Frames ");
            screenShot.setImageResource(R.drawable.ic_burst_mode);
//            screenShot.setText("Screen Shot");
            screenShot.setSelected(false);
            mCodecManager.enabledYuvData(false);
            mCodecManager.setYuvDataCallback(null);
        } else {//Começa a capturar frames
            showToast("Capturing Frames ");
            screenShot.setImageResource(R.drawable.ic_action_playback_stop);
//            screenShot.setText("Live Stream");
            screenShot.setSelected(true);
            mCodecManager.enabledYuvData(true);
            mCodecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
                @Override
                public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
                    //In this demo, we test the YUV data by saving it into JPG files.
                    //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
                    if (count++ % 30 == 0 && yuvFrame != null) {
                        final byte[] bytes = new byte[dataSize];
                        yuvFrame.get(bytes);
                        Log.i(TAG, "SaveFrame: " + count);
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                saveYuvDataToJPEG(bytes, width, height);
                            }
                        });
                    }
                }
            });
        }
    }

//Captura um único frame
    private void handleYUVClickSingleFrame() {
            showToast("Frame Captured");
            mCodecManager.enabledYuvData(true);
        Log.i(TAG, "SaveFrame01");
            mCodecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
                @Override
                public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
                    if (count++ == 30 && yuvFrame != null) {
                        Log.i(TAG, "SaveFrame02");
                        final byte[] bytes = new byte[dataSize];
                        Log.i(TAG, "SaveFrame03");
                        yuvFrame.get(bytes);
                        Log.i(TAG, "SaveFrame04");
                        saveYuvDataToJPEG(bytes, width, height);
                        Log.i(TAG, "SaveFrame05");

                        mCodecManager.enabledYuvData(false);
                        Log.i(TAG, "SaveFrame06");
                        mCodecManager.setYuvDataCallback(null);
                        Log.i(TAG, "SaveFrame07");
                    }


                }
            });

    }





/*
    private void handleYUVClick() {
        //if (!screenShot.isSelected()) {
          //  savedScreenShot=false;
            //screenShot.setText("Live Stream");
          //  screenShot.setSelected(true);
     Log.i(TAG, "SaveFrame1");
     saveOneFrame=true;
     mCodecManager.enabledYuvData(true);
     mCodecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
         @Override
         public void onYuvDataReceived(ByteBuffer byteBuffer, int i, int i1, int i2) {
             teste isso
         }
     });

         //   while(savedScreenShot==false) {
         //       sleep(100);
         //   }
     //savedScreenShot=true;
     //screenShot.setText("Screen Shot");
     //screenShot.setSelected(false);

        veja como fazer com um único click
                talvez usar
        https://developer.android.com/reference/android/os/AsyncTask

     Log.i(TAG, "SaveFrame3");
     mCodecManager.enabledYuvData(false);
        Log.i(TAG, "SaveFrame4");
        mCodecManager.setYuvDataCallback(null);
        Log.i(TAG, "SaveFrame5");

       // }
    }
*/
 /* @Override
    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //In this demo, we test the YUV data by saving it into JPG files.
        //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
      if (count++ % 30 == 0 && yuvFrame != null) {//if (saveOneFrame == true && yuvFrame != null) {
            saveOneFrame=false;
            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            Log.i(TAG, "SaveFrame: " + count);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    saveYuvDataToJPEG(bytes, width, height);
                }
            });
        }
    }*/

    private void saveYuvDataToJPEG(byte[] yuvFrame, int width, int height) {
        if (yuvFrame.length < width * height) {
            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
            return;
        }

        byte[] y = new byte[width * height];
        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];
        byte[] nu = new byte[width * height / 4]; //
        byte[] nv = new byte[width * height / 4];

        System.arraycopy(yuvFrame, 0, y, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            v[i] = yuvFrame[y.length + 2 * i];
            u[i] = yuvFrame[y.length + 2 * i + 1];
        }
        int uvWidth = width / 2;
        int uvHeight = height / 2;
        for (int j = 0; j < uvWidth / 2; j++) {
            for (int i = 0; i < uvHeight / 2; i++) {
                byte uSample1 = u[i * uvWidth + j];
                byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                nu[2 * (i * uvWidth + j)] = uSample1;
                nu[2 * (i * uvWidth + j) + 1] = uSample1;
                nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                nv[2 * (i * uvWidth + j)] = vSample1;
                nv[2 * (i * uvWidth + j) + 1] = vSample1;
                nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
            }
        }
        //nv21test
        byte[] bytes = new byte[yuvFrame.length];
        System.arraycopy(y, 0, bytes, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            bytes[y.length + (i * 2)] = nv[i];
            bytes[y.length + (i * 2) + 1] = nu[i];
        }

        //Marcelo OpenCV
        Mat myuv = new Mat(height + height / 2, width, CV_8UC1);


        myuv.put(0,0,bytes);//carga da matriz

        Mat picBGR = new Mat(height, width, CV_8UC4);

        cvtColor(myuv, picBGR, Imgproc.COLOR_YUV2BGRA_NV21);

        Mat mOut = new Mat(picBGR.height(),picBGR.width(), CvType.CV_8UC4);
        Mat mIntermediate = new Mat(picBGR.height(),picBGR.width(), CvType.CV_8UC4);

        final String path = Environment.getExternalStorageDirectory() + "/DJI_ScreenShot" + "/ScreenShot_" + System.currentTimeMillis() +"_OpenCV.jpg";
        Log.i(TAG, "OpenCV path: " + path);


        Imgproc.blur(picBGR, mIntermediate, new Size(3, 3));
        Imgproc.Canny(mIntermediate, mOut, 80, 100);

        Imgcodecs.imwrite(path, mOut);
        //showImg(mOut);
        //fim Meu OpenCV

        Log.i(TAG, "SaveFrame 04a");
        screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
        Log.i(TAG, "SaveFrame 04b");
    }
/* não funcionou
    private void showImg(Mat img) {
        Log.i(TAG, "OpenCV show 01: ");
        Bitmap bm = Bitmap.createBitmap(img.cols(), img.rows(),Bitmap.Config.ARGB_8888);
        Log.i(TAG, "OpenCV show 02: ");
        Utils.matToBitmap(img, bm);
        Log.i(TAG, "OpenCV show 03: ");
        //videostreamPreviewSf.setVisibility(View.GONE);quando ativei esta linha começou a dar problema,
        //videostreamPreviewTtView.setVisibility(View.GONE);
        //videostreamPreviewOpenCV.setVisibility(View.GONE);
        //dá problema quando usa imageView, veja se é conflito com textura ou a chamada neste ponto (testar uma das linhas acima e sem imagaView)
        //imageView.setVisibility(View.VISIBLE);quando ativei esta linha começou a dar problema,
        Log.i(TAG, "OpenCV show 04: ");
        //imageView.setImageBitmap(bm);quando ativei esta linha começou a dar problema,
        Log.i(TAG, "OpenCV show 05: ");
    }*/

    /**
     * Save the buffered data into a JPG image file
     */
    private void screenShot(byte[] buf, String shotDir, int width, int height) {
        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
        YuvImage yuvImage = new YuvImage(buf,
                ImageFormat.NV21,
                width,
                height,
                null);
        OutputStream outputFile;
        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";
        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
            return;
        }
        if (outputFile != null) {
            yuvImage.compressToJpeg(new Rect(0,
                    0,
                    width,
                    height), 100, outputFile);
            //Log.e(TAG, "Ori path: " + path);
        }
        try {
            outputFile.close();
        } catch (IOException e) {
            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
            e.printStackTrace();
        }
        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayPath(path);
            }
        });*/
    }


}
