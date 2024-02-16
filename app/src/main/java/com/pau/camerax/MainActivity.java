package com.pau.camerax;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pau.camerax.databinding.ActivityMainBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding viewBinding;

    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;

    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener(v -> takePhoto());
        viewBinding.videoCaptureButton.setOnClickListener(v -> captureVideo());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void takePhoto() {
        // Implementar la lógica para tomar una foto
    }

    private void captureVideo() {
        // Implementar la lógica para capturar un video
    }

    private void startCamera() {
        // Implementar la lógica para iniciar la cámara
    }

    private boolean allPermissionsGranted() {
        return REQUIRED_PERMISSIONS.length > 0 && ContextCompat.checkSelfPermission(
                this, REQUIRED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private static final String TAG = "CameraXApp";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ?
                    Manifest.permission.WRITE_EXTERNAL_STORAGE : ""
    };
}
