package com.pau.camerax;
import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.common.util.concurrent.ListenableFuture;
import com.pau.camerax.LumaListener;
import com.pau.camerax.databinding.ActivityMainBinding;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements LumaListener {
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

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            Toast.makeText(this, "Permisos no otorgados por el usuario.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener(v -> takePhoto());
        viewBinding.videoCaptureButton.setOnClickListener(v -> captureVideo());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        ImageCapture imageCapture = this.imageCapture;
        if (imageCapture == null) {
            return;
        }

        // Create time stamped name and MediaStore entry.
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
        }

        // Create output options object which contains file + metadata
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        // Set up image capture listener, which is triggered after the photo has been taken
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        String msg = "Photo capture succeeded: " + output.getSavedUri();
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                    }
                }
        );
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private void captureVideo() {
        VideoCapture videoCapture = this.videoCapture;
        if (videoCapture == null) {
            return;
        }

        viewBinding.videoCaptureButton.setEnabled(false);

        Recording curRecording = recording;
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop();
            recording = null;
            return;
        }

        // create and start a new recording session
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video");
        }

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions
                .Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        videoCapture.startRecording(mediaStoreOutputOptions, ContextCompat.getMainExecutor(this), new Consumer<VideoRecordEvent>() {
            @Override
            public void accept(VideoRecordEvent recordEvent) {
                if (recordEvent instanceof VideoRecordEvent.Start) {
                    viewBinding.videoCaptureButton.setText(getString(R.string.stop_capture));
                    viewBinding.videoCaptureButton.setEnabled(true);
                } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                    if (!finalizeEvent.hasError()) {
                        String msg = "Video capture succeeded: " +
                                finalizeEvent.getOutputResults().getOutputUri();
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT)
                                .show();
                        Log.d(TAG, msg);
                    } else {
                        Log.e(TAG, "Video capture ends with error: " +
                                finalizeEvent.getError());
                    }
                    viewBinding.videoCaptureButton.setText(getString(R.string.start_capture));
                    viewBinding.videoCaptureButton.setEnabled(true);
                }
            }
        });
    }





    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewBinding.viewFinder.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                imageCapture = new ImageCapture.Builder().build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    private ByteBuffer toByteBuffer(ImageProxy.PlaneProxy plane) {
                        ByteBuffer buffer = plane.getBuffer();
                        buffer.rewind();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        return buffer;
                    }

                    @Override
                    public void analyze(ImageProxy image) {
                        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
                        ByteBuffer buffer = toByteBuffer(plane);
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        int[] pixels = new int[data.length];
                        for (int i = 0; i < data.length; ++i) {
                            pixels[i] = data[i] & 0xFF;
                        }
                        double luma = calculateAverageLuminosity(pixels);

                        Log.d(TAG, "Average luminosity: " + luma);

                        image.close();
                    }
                });
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll();

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

                } catch (Exception exc) {
                    Log.e(TAG, "Use case binding failed", exc);
                }

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private double calculateAverageLuminosity(int[] pixels) {
        int sum = 0;
        for (int pixel : pixels) {
            sum += pixel;
        }
        return sum / (double) pixels.length;
    }



    private ImageAnalysis createImageAnalysis() {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new android.util.Size(viewBinding.viewFinder.getWidth(), viewBinding.viewFinder.getHeight()))
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new LuminosityAnalyzer(this));

        return imageAnalysis;
    }


    @Override
    public void onLumaCalculated(double luma) {
        // Handle the calculated luma here
        Log.d(TAG, "Average Luma: " + luma);
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
