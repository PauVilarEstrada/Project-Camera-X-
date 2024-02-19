package com.pau.camerax;

import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;

public class Recording {

    private static final String TAG = "Recording";

    private MediaRecorder mediaRecorder;
    private String outputPath;

    public Recording(String outputPath) {
        this.outputPath = outputPath;
    }

    public void start() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outputPath);
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(1280, 720);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                Log.d(TAG, "Recording started");
            } catch (IOException e) {
                Log.e(TAG, "Error preparing or starting MediaRecorder: " + e.getMessage());
                stop();
            }
        }
    }

    public void stop() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                Log.d(TAG, "Recording stopped. Output path: " + outputPath);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping or releasing MediaRecorder: " + e.getMessage());
            } finally {
                mediaRecorder = null;
            }
        }
    }

    public void close() {
        stop();
        // Perform any additional cleanup if needed
    }
}

