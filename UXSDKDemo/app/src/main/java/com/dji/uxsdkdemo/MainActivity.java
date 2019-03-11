package com.dji.uxsdkdemo;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import org.opencv.android.CameraBridgeViewBase;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    private CaptureFrame frameAccess;
    private Button test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        frameAccess = new CaptureFrame(this,(ImageButton) findViewById(R.id.activity_main_screen_shot), (TextureView) findViewById(R.id.livestream_preview), (ImageView) findViewById(R.id.openCV_preview));
        //frameAccess = new CaptureFrame(this, (TextureView) findViewById(R.id.livestream_preview));


        test=findViewById(R.id.activity_test);
        test.setSelected(false);
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //handleYUVClick();//Captura 1 frame a cada 30
                frameAccess.handleYUVClickSingleFrame();//Captura somente um frame
            }
        });
    }

    @Override
    protected void onPause() {//Marcelo
        frameAccess.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() { //Marcelo
        frameAccess.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {//Marcelo
        super.onResume();
        frameAccess.onResume();//depois do super.onResume
    }
}

