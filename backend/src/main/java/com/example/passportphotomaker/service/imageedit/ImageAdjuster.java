package com.example.passportphotomaker.service.imageedit;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ImageAdjuster {

    /**
     * Applies brightness, contrast, and saturation adjustments to the image.
     * Preserves alpha if present.
     *
     * @param image      The image to adjust (BGR or BGRA)
     * @param brightness Brightness adjustment (-100 to 100)
     * @param contrast   Contrast multiplier (1.0 = no change)
     * @param saturation Saturation multiplier (1.0 = no change)
     * @return Adjusted image with alpha preserved if originally present
     */
    public static Mat applyAdjustments(Mat image, double brightness, double contrast, double saturation) {
        boolean hasAlpha = image.channels() == 4;

        // 1. Split channels
        List<Mat> channels = new ArrayList<>();
        Core.split(image, channels);

        Mat alpha = null;
        if (hasAlpha) {
            alpha = channels.remove(3); // Extract alpha
        }

        // Merge BGR for processing
        Mat bgr = new Mat();
        Core.merge(channels, bgr);

        // 2. Brightness + contrast
        Mat adjusted = new Mat();
        // For brightness, we want to map -100 to +100 to a reasonable pixel shift
        // For OpenCV, brightness is added to each pixel, so we'll use a more moderate scale
        double brightnessScaled = brightness; // Simple linear mapping
        bgr.convertTo(adjusted, -1, contrast, brightnessScaled);
        bgr.release();

        // 3. Saturation in HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(adjusted, hsv, Imgproc.COLOR_BGR2HSV);
        adjusted.release();

        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsv, hsvChannels);

        Mat saturationMat = hsvChannels.get(1);
        saturationMat.convertTo(saturationMat, CvType.CV_32F);
        Core.multiply(saturationMat, new Scalar(saturation), saturationMat);
        Core.min(saturationMat, new Scalar(255), saturationMat);
        saturationMat.convertTo(saturationMat, CvType.CV_8U);

        hsvChannels.set(1, saturationMat);
        Core.merge(hsvChannels, hsv);
        for (Mat m : hsvChannels) m.release();

        Mat finalBgr = new Mat();
        Imgproc.cvtColor(hsv, finalBgr, Imgproc.COLOR_HSV2BGR);
        hsv.release();

        // 4. Merge alpha back if needed
        if (hasAlpha && alpha != null) {
            List<Mat> finalChannels = new ArrayList<>();
            Core.split(finalBgr, finalChannels);
            finalChannels.add(alpha); // add back alpha
            Mat finalBgra = new Mat();
            Core.merge(finalChannels, finalBgra);
            finalBgr.release();
            return finalBgra;
        } else {
            return finalBgr;
        }
    }
}
