package com.pau.camerax;
import android.media.Image;
import android.util.Log;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class LuminosityAnalyzer implements ImageAnalysis.Analyzer {
    private LumaListener listener;

    public LuminosityAnalyzer(LumaListener listener) {
        this.listener = listener;
    }

    private byte[] imageToByteArray(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind(); // Rewind the buffer to zero
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data); // Copy the buffer into a byte array
        return data; // Return the byte array
    }

    @Override
    public void analyze(ImageProxy image) {
        byte[] data = imageToByteArray(image);
        int[] pixels = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            pixels[i] = data[i] & 0xFF;
        }
        double luma = calculateAverageLuminosity(pixels);

        if (listener != null) {
            listener.onLumaCalculated(luma);
        }

        image.close();
    }

    private double calculateAverageLuminosity(int[] pixels) {
        int sum = 0;
        for (int pixel : pixels) {
            sum += pixel;
        }
        return sum / (double) pixels.length;
    }
}

