package com.pau.camerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.pau.camerax.databinding.ActivityMainBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final String TAG = "CameraApp";
    private static final String KEY_CAMERA_LENS_FACING = "camera_lens_facing";
    private ActivityMainBinding binding;
    private ImageButton videoBinding;

    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageAnalysis imageAnalysis;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private Camera camera;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
        ImageButton imageBinding = binding.imageCaptureButton;
        videoBinding = binding.videoCaptureButton;
        imageBinding.setOnClickListener(v -> takePhoto());
        videoBinding.setOnClickListener(v -> captureVideo());
        ImageButton rotateCameraButton = findViewById(R.id.rotateCameraButton);
        rotateCameraButton.setOnClickListener(this::onRotateCameraClick);
        ImageButton flashButton = binding.flashControlButton;
        flashButton.setOnClickListener(this::onFlashClick);
        ImageView previewGallery = findViewById(R.id.previewGallery);
        previewGallery.setOnClickListener(view -> openGooglePhotos());
        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        if (savedInstanceState != null) {
            int lensFacing = savedInstanceState.getInt(KEY_CAMERA_LENS_FACING);
            currentCameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        }
        bindCameraUseCases();
    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    @SuppressLint("RestrictedApi")
    private void switchCamera() {
        CameraSelector newCameraSelector;
        if (currentCameraSelector.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
            newCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        } else {
            newCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        }
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbindAll();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider()); // Asegúrate de configurar el SurfaceProvider
            camera = cameraProvider.bindToLifecycle(this, newCameraSelector, preview, imageCapture, videoCapture);
            currentCameraSelector = newCameraSelector; // Actualizar la selección de la cámara actual
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void bindCameraUseCases() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(((PreviewView) findViewById(R.id.viewFinder)).getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                imageAnalysis = new ImageAnalysis.Builder().build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, currentCameraSelector, preview, imageAnalysis, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void takePhoto() {
        ImageCapture imageCapture = this.imageCapture;
        if (imageCapture == null) return;
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = createImageContentValues(name);
        ImageCapture.OutputFileOptions outputOptions = createImageOutputOptions(contentValues);
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(MainActivity.this, "Foto realizada", Toast.LENGTH_SHORT).show();
                String msg = "Photo capture succeeded: " + outputFileResults.getSavedUri();
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, msg);
                loadThumbnail();
            }
            @Override
            public void onError(@NonNull ImageCaptureException exc) {
                Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
            }
        });
    }
    private ContentValues createImageContentValues(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraX-Image");
        return contentValues;
    }
    private ImageCapture.OutputFileOptions createImageOutputOptions(ContentValues contentValues) {
        return new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                .build();
    }
    private void captureVideo() {
        VideoCapture<Recorder> videoCapture = this.videoCapture;
        if (videoCapture == null) {
            Log.e(TAG, "VideoCapture is null");
            return;
        }
        videoBinding.setEnabled(false);
        Recording curRecording = recording;
        if (curRecording != null) {
            curRecording.stop();
            recording = null;
            return;
        }
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = createVideoContentValues(name);
        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recording = videoCapture.getOutput()
                .prepareRecording(MainActivity.this, mediaStoreOutputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(MainActivity.this), recordEvent -> {
                    if (recordEvent instanceof VideoRecordEvent.Start) {
                        Toast.makeText(MainActivity.this, "Grabación iniciada", Toast.LENGTH_SHORT).show();
                        videoBinding.setEnabled(true);
                    } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                        if (!finalizeEvent.hasError()) {
                            String msg = "Video capture succeeded: " + finalizeEvent.getOutputResults().getOutputUri();
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            Log.d(TAG, msg);
                        } else {
                            Log.e(TAG, "Video capture ends with error: " + finalizeEvent.getError());
                            if (recording != null) {
                                recording.close();
                                recording = null;
                            }
                        }
                        Toast.makeText(MainActivity.this, "Grabación finalizada", Toast.LENGTH_SHORT).show();
                        videoBinding.setEnabled(true);
                        loadThumbnail();
                    }
                });
    }
    private ContentValues createVideoContentValues(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CameraX-Video");
        return contentValues;
    }
    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            int newFlashMode = imageCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON
                    ? ImageCapture.FLASH_MODE_OFF : ImageCapture.FLASH_MODE_ON;

            imageCapture.setFlashMode(newFlashMode);
            if (newFlashMode == ImageCapture.FLASH_MODE_ON) {
                Toast.makeText(this, "Flash activado", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Flash desactivado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadThumbnail() {
        Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(mediaUri, projection, null, null, sortOrder);
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") long mediaId = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID));
            Uri contentUri = Uri.withAppendedPath(mediaUri, String.valueOf(mediaId));
            binding.previewGallery.setImageURI(contentUri);
        } else {
            Log.e(TAG, "No se encontraron imágenes o videos.");
        }
        if (cursor != null) {
            cursor.close();
        }
    }
    @SuppressLint("RestrictedApi")
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CAMERA_LENS_FACING, currentCameraSelector.getLensFacing());
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    private void openGooglePhotos() {
        Uri uri = Uri.parse("content://media/internal/images/media");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }
    private void onRotateCameraClick(View view) {
        switchCamera();
    }

    private void onFlashClick(View view) {
        toggleFlash();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
