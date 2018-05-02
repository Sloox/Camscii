package wrightstuff.camerautils.ascii;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


import static org.opencv.core.Core.FONT_HERSHEY_PLAIN;

/**
 * Created by michaelwright on 28/11/2017.
 */

public class Ascii {


    private static final String TAG = Ascii.class.getSimpleName();
    private static final double SCALEFACTOR = 0.5;

    public static Bitmap convertNV21toBitmap(final Context context, byte[] data, int width, int height) {
        RenderScript rs;
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
        Type.Builder yuvType = null, rgbaType;
        Allocation in = null, out = null;

        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        if (yuvType == null) {
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(data.length);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

            rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(data);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);

        Bitmap bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);
        return bmpout;
    }


    public static Bitmap ascifyWithoutScallingSlowAndDrawsTextPerPixel(final Bitmap image, int width, int height, int size) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);

        Canvas canvas = new Canvas(bitmap);
        // new antialised Paint
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        //color
        paint.setColor(Color.rgb(255, 255, 255));
        // size
        paint.setTextSize((size));

        Rect bounds = new Rect();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                double gValue = (double) Color.red(pixel) * 0.2989 + (double) Color.blue(pixel) * 0.5870 + (double) Color.green(pixel) * 0.1140;
                String s = getAsciiFromGreyScale(gValue, false);
                paint.getTextBounds(s, 0, 1, bounds);
                canvas.drawText(s, x, y, paint);
            }
        }
        return bitmap;
    }

    public static Bitmap ascifyWithBitmaps(final Bitmap image, int width, int height, int size, boolean blackWhite) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);

        Canvas canvas = new Canvas(bitmap);
        // new antialised Paint
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        //color
        paint.setColor(Color.rgb(255, 255, 255));
        // size
        paint.setTextSize((size));

        int boundsHeight = image.getHeight() / size;
        int boundsWidth = image.getWidth() / size;


        for (int y = 0; y < boundsHeight; y++) {
            for (int x = 0; x < boundsWidth; x++) {
                int xMod = x * size;
                int yMod = y * size;
                int pixel = image.getPixel(xMod, yMod);
                double gValue = (double) Color.red(pixel) * 0.2989 + (double) Color.blue(pixel) * 0.5870 + (double) Color.green(pixel) * 0.1140;
                String s = getAsciiFromGreyScale(gValue, blackWhite);
                canvas.drawText(s, xMod, yMod, paint);
            }
        }
        return bitmap;
    }


    public static Mat ascifyAsMAT(final Mat image, int size, double scalefactor, boolean blackWhite) {
        Mat asciMat = new Mat(image.size(), image.type(), Scalar.all(0)); //black background
        int width = (int) image.rows();
        int height = (int) image.cols();
        double[] pixel;
        double gValue;
        Scalar white = Scalar.all(255);
        for (int y = 0; y < height; y += size) {
            for (int x = 0; x < width; x += size) {
                pixel = image.get(x, y);
                gValue = pixel[0] * 0.2989 + pixel[2] * 0.5870 + pixel[1] * 0.1140;
                Imgproc.putText(asciMat, getAsciiFromGreyScale(gValue, blackWhite), new Point(y, x), FONT_HERSHEY_PLAIN, scalefactor, white);
            }
        }
        return asciMat;
    }


    /*Parllel Ascii Mat*/
    public static Mat ascifyAsMATParallel(final Mat image, int size, int threads, double scalefactor, boolean blackWhite) {
        Mat asciMat = new Mat(image.size(), image.type(), Scalar.all(0)); //black background
        int width = (int) image.rows();

        int numCores = /*getNumberOfCores()*/threads;
        if (numCores <= 0) {
            numCores = 1;
        }

        int regionStart = 0;
        int regionSector = (width / numCores);
        final ExecutorService executor = Executors.newFixedThreadPool(numCores);
        final List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numCores; ++i) {

            int finalRegionStart = regionStart;
            Future<?> future = executor.submit(() -> {
                ascifyMatParts(image, size, finalRegionStart, regionSector + finalRegionStart, asciMat, scalefactor, blackWhite);
            });
            regionStart += regionSector;
            futures.add(future);
        }
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            Log.d(TAG, "Failed to execute future:" + e);
        }
        return asciMat;
    }

    public static void ascifyMatParts(Mat image, int size, int regionStart, int regionEnd, Mat asciMat, double scalefactor, boolean blackWhite) {
        int height = (int) image.cols();
        double[] pixel;
        double gValue;
        Scalar white = Scalar.all(255);
        int start = regionStart + size / 2;
        for (int y = 0; y < height; y += size) {
            for (int x = (start); x < regionEnd; x += size) {
                pixel = image.get(x, y);
                gValue = pixel[0] * 0.2989 + pixel[2] * 0.5870 + pixel[1] * 0.1140;
                Imgproc.putText(asciMat, getAsciiFromGreyScale(gValue, blackWhite), new Point(y, x), FONT_HERSHEY_PLAIN, scalefactor, white);
            }
        }


    }


    private static int getNumberOfCores() {
        try {
            return Runtime.getRuntime().availableProcessors();
        } catch (Exception e) {
            //failed to get process
        }
        return 1;
    }


    private static String getAscifromGrayScaleUsingString(double g, String valuestoUse)//takes the grayscale value as parameter
    {
        int length = valuestoUse.length() - 1;
        int valueInterval = 255 / valuestoUse.length() - 1;
        int kilop = (int) (g / (double) valueInterval) % length;
        return String.valueOf(valuestoUse.charAt(kilop)); // return the character

    }

    /**
     * Create a new string and assign to it a string based on the grayscale value.
     * If the grayscale value is very high, the pixel is very bright and assign characters
     * such as . and , that do not appear very dark. If the grayscale value is very lowm the pixel is very dark,
     * assign characters such as # and @ which appear very dark.
     *
     * @param g          grayscale
     * @param blackWhite
     * @return char
     */
    private static String getAsciiFromGreyScale(double g, boolean blackWhite)//takes the grayscale value as parameter
    {
        if (blackWhite) {
            return returnStr(g);
        }
        return returnStrNeg(g);
    }


    private static String returnStr(double g) {
        if (g >= 230.0) {
            return " ";
        } else if (g >= 200.0) {
            return ".";
        } else if (g >= 180.0) {
            return ",";
        } else if (g >= 160.0) {
            return ":";
        } else if (g >= 130.0) {
            return ";";
        } else if (g >= 100.0) {
            return "o";
        } else if (g >= 70.0) {
            return "&";
        } else if (g >= 50.0) {
            return "8";
        } else if (g >= 20.0) {
            return "%";
        } else if (g > 5.0) {
            return "#";
        } else {
            return "@";
        }
    }

    /**
     * Same method as above, except it reverses the darkness of the pixel. A dark pixel is given a light character and vice versa.
     *
     * @param g grayscale
     * @return char
     */
    private static String returnStrNeg(double g) {
        if (g >= 230.0) {
            return "@";
        } else if (g >= 200.0) {
            return "#";
        } else if (g >= 180.0) {
            return " %";
        } else if (g >= 160.0) {
            return "8";
        } else if (g >= 130.0) {
            return "&";
        } else if (g >= 100.0) {
            return "o";
        } else if (g >= 70.0) {
            return ";";
        } else if (g >= 50.0) {
            return " :";
        } else if (g >= 20.0) {
            return ",";
        } else if (g > 5.0) {
            return ".";
        } else {
            return " ";
        }
    }
}
