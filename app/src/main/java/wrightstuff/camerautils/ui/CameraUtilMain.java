package wrightstuff.camerautils.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;

import net.ralphpina.permissionsmanager.PermissionsManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import wrightstuff.camerautils.R;
import wrightstuff.camerautils.ascii.Ascii;

public class CameraUtilMain extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = CameraUtilMain.class.getSimpleName();

    /*opencv camera variables*/
    private Mat mIntermediateMat;
    private CameraBridgeViewBase mOpenCvCameraView;
    private FloatingActionButton fab, fab_threads, fab_scale, fab_size, fab_color, fab_take_picture;
    private int selections = 0;
    private int threads = 8;
    private double scalefactor = 0.8;
    private int size = 10;
    private boolean blackWhite;

    private Mat rgbaPicture;
    private FragmentManager supportFragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PermissionsManager.get().requestCameraPermission().subscribe();


        setContentView(R.layout.activity_camera_util_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        /*Camera*/
        mOpenCvCameraView = findViewById(R.id.image_manipulations_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        //modification for rotation of the camera lies within CameraBridgeViewBase...
        mOpenCvCameraView.setMaxFrameSize(1024, 768);//176x152
        setupButtons();
    }


    /*Opencv stuff*/
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {
        // Explicitly deallocate Mats
        if (mIntermediateMat != null)
            mIntermediateMat.release();

        mIntermediateMat = null;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();

        long startTime = System.currentTimeMillis();
        Mat asciRGBA;
        if (selections == 0) {
            asciRGBA = Ascii.ascifyAsMATParallel(rgba, size, threads, scalefactor, blackWhite);
        } else if (selections == 1) {
            asciRGBA = Ascii.ascifyAsMAT(rgba, size, scalefactor, blackWhite);
        } else if (selections == 2) {
            Size sizeRgba = rgba.size();
            int width = (int) sizeRgba.width;
            int height = (int) sizeRgba.height;
            Bitmap bitrgba = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
            Utils.matToBitmap(rgba, bitrgba);
            Bitmap lameMap = Ascii.ascifyWithBitmaps(bitrgba, width, height, size, blackWhite);
            Utils.bitmapToMat(lameMap, rgba);
            long endTime = System.currentTimeMillis();
            Log.d("Timer", "TimeTaken:" + (endTime - startTime) + "ms");
            return rgba;
        } else {
            return rgba;
        }


        long endTime = System.currentTimeMillis();
        Log.d("Timer", "TimeTaken:" + (endTime - startTime) + "ms");
        rgbaPicture = asciRGBA;
        return asciRGBA;
    }


    /*Lifecycle Classes*/
    @Override
    protected void onPause() {
        super.onPause();
        disableCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disableCamera();
    }

    private void disableCamera() {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }



    /*Button Helper Classes*/

    void setupButtons() {
        fab_scale = findViewById(R.id.fab_scale);
        fab_scale.setOnClickListener(v -> {
            scalefactor -= 0.1;
            if (scalefactor <= 0.05) {
                scalefactor = 1.0;
            }
            Toast.makeText(this, "Scale:" + scalefactor, Toast.LENGTH_SHORT).show();
        });
        fab_threads = findViewById(R.id.fab_threads);
        fab_threads.setOnClickListener(v -> {
            threads = (threads + 1) % 12;
            Toast.makeText(this, "threads:" + threads, Toast.LENGTH_SHORT).show();
        });
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            selections = (selections + 1) % 4;
            Toast.makeText(this, "Selection:" + selections, Toast.LENGTH_SHORT).show();
        });
        fab_size = findViewById(R.id.fab_text);
        fab_size.setOnClickListener(v -> {
            size = (size + 1) % 16;
            size = (size == 0) ? 4 : size;
            Toast.makeText(this, "size:" + size, Toast.LENGTH_SHORT).show();
        });
        fab_color = findViewById(R.id.fab_color);
        fab_color.setOnClickListener(v -> blackWhite = !blackWhite);
        fab_take_picture = findViewById(R.id.fab_picture);
        fab_take_picture.setOnClickListener(v -> {
            Mat rgbaClone = rgbaPicture.clone();
            Core.transpose(rgbaClone, rgbaClone);
            Core.flip(rgbaClone, rgbaClone, 1);
            Core.transpose(rgbaClone, rgbaClone);
            Core.flip(rgbaClone, rgbaClone, 1);
            int width = (int) rgbaClone.size().width;
            int height = (int) rgbaClone.size().height;
            Bitmap bitrgba = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
            Utils.matToBitmap(rgbaClone, bitrgba);
            if (MediaStore.Images.Media.insertImage(getContentResolver(), bitrgba, "AsciCam", "Ascified image") == null) {
                Toast.makeText(this, "File Permissions are required", Toast.LENGTH_SHORT).show();
                PermissionsManager.get().requestStoragePermission().subscribe();
            } else {
                Toast.makeText(this, "Saved Image to Gallery", Toast.LENGTH_SHORT).show();
            }


        });
    }

}
