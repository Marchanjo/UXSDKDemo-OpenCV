package com.dji.uxsdkdemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.Utils;
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

import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC4;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Size;

import static org.opencv.imgproc.Imgproc.cvtColor;

public class CaptureFrame {
    private static final String TAG = MainActivity.class.getName();
    private DJICodecManager mCodecManager;//Marcelo
    //private VideoFeeder.VideoFeed standardVideoFeeder;//Marcelo
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;//Marcelo
    private Camera mDroneCamera;//Marcelo
    private TextureView videostreamPreviewTtView;//Marcelo
    private int videoViewWidth;//Marcelo
    private int videoViewHeight;//Marcelo
    private ImageButton screenShot;//Marcelo
    private int  count;//Marcelo
    private Activity appActivity;
    private ImageView imageView;
    private Detection detector;

    private Mat mOut;
    private boolean capturing=false;


    public CaptureFrame(Activity appActivity, TextureView videostreamPreviewTtView) {
        this.appActivity =  appActivity;
        this.videostreamPreviewTtView = videostreamPreviewTtView;
        videostreamPreviewTtView.setVisibility(View.VISIBLE);
        openCVStart();
    }


    public CaptureFrame(Activity appActivity, ImageButton screenShot, TextureView videostreamPreviewTtView, ImageView imageView) {
        detector = new Detection();
        this.screenShot = screenShot;
        screenShot.setSelected(false);
        screenShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleYUVClick();//Captura 1 frame a cada 30
                //handleYUVClickSingleFrame();//Captura somente um frame
            }
        });

        this.imageView = imageView;

        this.appActivity =  appActivity;
        this.videostreamPreviewTtView = videostreamPreviewTtView;
        videostreamPreviewTtView.setVisibility(View.VISIBLE);
        openCVStart();
        capturing=false;
    }

    public void openCVStart() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, appActivity,mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(appActivity) {
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
        if (mDroneCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
            }
            //if (standardVideoFeeder != null) {
             //   standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
            //}
        }
        capturing=false;
    }

    public void onDestroy() {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        capturing=false;
    }

    public void onResume() {
        initSurfaceOrTextureView();
        notifyStatusChange();
        capturing=false;
    }


    private void showToast(String s) {
        Toast.makeText(videostreamPreviewTtView.getContext(), s, Toast.LENGTH_SHORT).show();
    }

    private long lastupdate;

    private void notifyStatusChange() {
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
            mDroneCamera = null;
            showToast("Disconnected");
        } else {
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mDroneCamera = product.getCamera();
                mDroneCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
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

    public void captureFrameFromSurface() {
        //Marcelo OpenCV

        /*AsyncTask.execute(new Runnable() {//async para não segurar a thread
            @Override
            public void run() {
                appActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Mat mOut = new Mat();
                        Utils.bitmapToMat(videostreamPreviewTtView.getBitmap(), mOut);
                        Imgproc.cvtColor(mOut, mOut, Imgproc.COLOR_RGB2BGR);
                        final String path = Environment.getExternalStorageDirectory() + "/DJI_ScreenShot" + "/ScreenShot_" + System.currentTimeMillis()+ "_OpenCV3.jpg";;
                        showImg(mOut);
                        Imgcodecs.imwrite(path, mOut);

                    }
                });

            }
        });*/
        capturing=true;
        callRunnable();
    }

    private void callRunnable (){
        if(capturing) handler.postDelayed(refresh,50);
    }

    Handler handler = new Handler(Looper.getMainLooper());

    private Runnable refresh = new Runnable() {//async para não segurar a thread
        @Override
        public void run() {
                    Mat inputFrame = new Mat();
                    Utils.bitmapToMat(videostreamPreviewTtView.getBitmap(), inputFrame);
                    Imgproc.cvtColor(inputFrame, inputFrame, Imgproc.COLOR_RGB2BGR);

//                    Mat mIntermediate;
//                    mIntermediate = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC4);
//                    Mat mOutGray = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC1);
//                    mOut = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC4);
//                    Imgproc.blur(inputFrame, mIntermediate, new Size(3, 3));
//                    Imgproc.Canny(mIntermediate, mOutGray, 80, 100);
//                    Imgproc.cvtColor(mOutGray, mOut, Imgproc.COLOR_GRAY2BGR);



                    mOut=detector.preProcessing(inputFrame);
                    showImg(mOut);
                    //final String path = Environment.getExternalStorageDirectory() + "/DJI_ScreenShot" + "/ScreenShot_" + System.currentTimeMillis()+ "_OpenCV3.jpg";;
                    //Imgcodecs.imwrite(path, mOut);
                    callRunnable();
                }
    };

    public void handleYUVClickSingleFrame() {
        //showToast("Start Frame Capture");
        mCodecManager.enabledYuvData(true);
        final int[] countFrame = {0};//na verdade é uma simples variável int mas tem que ser final para usar na callback, e sendo final não pode ser alterada, logo tem que ser array e usar a posição [0]
        mCodecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
            @Override
            public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
                //In this demo, we test the YUV data by saving it into JPG files.
                //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
                if (countFrame[0]++ == 1 && yuvFrame != null) {
                    final byte[] bytes = new byte[dataSize];
                    yuvFrame.get(bytes);


                   AsyncTask.execute(new Runnable() {//async para não segurar a thread
                        @Override
                        public void run() {
                            appActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mCodecManager.enabledYuvData(false);//tem que executar na thread da UI
                                    mCodecManager.setYuvDataCallback(null);//tem que executar na thread da UI
                                }
                            });

                        }
                    });

                    Log.i(TAG, "SavedFrame: " + countFrame[0]);
                    AsyncTask.execute(new Runnable() {//async para não segurar a thread
                        @Override
                        public void run() {
                            saveYuvDataToJPEG(bytes, width, height);
                        }
                    });
                }
            }
        });
    showToast("Frame Capture");
    }





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

        mOut = new Mat(picBGR.height(),picBGR.width(), CvType.CV_8UC4);

        final String path = Environment.getExternalStorageDirectory() + "/DJI_ScreenShot" + "/ScreenShot_" + System.currentTimeMillis();
        final String path1 = path +"_OpenCV1.jpg";
        final String path2 = path +"_OpenCV2.jpg";
        Log.i(TAG, "OpenCV path: " + path2);
        /*
        Mat mIntermediate = new Mat(picBGR.height(),picBGR.width(), CvType.CV_8UC4);
        Imgproc.blur(picBGR, mIntermediate, new Size(3, 3));
        Imgproc.Canny(mIntermediate, mOut, 80, 100);*/
        Log.i("capture", "ClassDetect01");
        mOut=detector.preProcessing(picBGR);
        Log.i("capture", "ClassDetect05");

        showImg(mOut);
        Log.i("capture", "ClassDetect08");
        Imgcodecs.imwrite(path1, mOut);
        Imgcodecs.imwrite(path2, picBGR);


/*
        //showImg(mOut);
        //fim Meu OpenCV

        Log.i(TAG, "SaveFrame 04a");
        screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
        Log.i(TAG, "SaveFrame 04b");

       // Imgcodecs.imwrite(path, mOut);//fim Meu OpenCV
        Imgcodecs.imwrite(path, picBGR);//fim Meu OpenCV*/
    }

    private void showImg(Mat in){
        final Mat img = new Mat(in.height(),in.width(), CvType.CV_8UC4);
        cvtColor(in, img, Imgproc.COLOR_BGR2RGB);
        Log.i("capture", "ClassDetect06");
        AsyncTask.execute(new Runnable() {//async para não segurar a thread
            @Override
            public void run() {
                appActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bmImg = Bitmap.createBitmap(img.cols(), img.rows(),Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(img, bmImg);
                        imageView.setImageBitmap(bmImg);
                        Log.i("capture", "ClassDetect07");
                    }
                });

            }
        });
    }


   /* private void showImg(Mat img) {
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
