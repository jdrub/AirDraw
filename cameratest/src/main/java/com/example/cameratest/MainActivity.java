package com.example.cameratest;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint;

import org.opencv.features2d.KeyPoint;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.firebase.client.Firebase;
import com.google.gson.Gson;

import static org.opencv.features2d.Features2d.drawKeypoints;

public class MainActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean              mIsJavaCamera = true;
    private MenuItem             mItemSwitchCamera = null;

    private Mat mPrev;
    private List<MatOfPoint2f> points = new ArrayList<MatOfPoint2f>(100);
    private MatOfPoint initial;
    private MatOfByte status;
    private MatOfFloat error;
    private Firebase ref;
    private ArrayList<fbClass> fbData = new ArrayList<fbClass>();
    private int count = 0;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.tutorial1_surface_view);

        if (mIsJavaCamera)
            mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);
        else
            mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_native_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize( 800, 600 );

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemSwitchCamera = menu.add("Toggle Native/Java camera");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String toastMesage = new String();
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item == mItemSwitchCamera) {
            mOpenCvCameraView.setVisibility(SurfaceView.GONE);
            mIsJavaCamera = !mIsJavaCamera;

            if (mIsJavaCamera) {
                mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);
                toastMesage = "Java Camera";
            } else {
                mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_native_surface_view);
                toastMesage = "Native Camera";
            }

            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCvCameraViewListener(this);

            mOpenCvCameraView.enableView();
            Toast toast = Toast.makeText(this, toastMesage, Toast.LENGTH_LONG);
            toast.show();
        }

        return true;
    }

    public void onCameraViewStarted(int width, int height) {
        initial = new MatOfPoint();
        mPrev = new Mat();
        status = new MatOfByte();
        error = new MatOfFloat();

        ref = new Firebase("https://3d-draw.firebaseio.com/data2");
    }

    public void pushFB( Double x, Double y ) {
        fbClass tmp = new fbClass();
        tmp.setX( x );
        tmp.setY(y);
        fbData.add( tmp );
        Gson gson = new Gson();
        if( count % 4 == 0 ) {
            ref.setValue( gson.toJson( fbData ) );
        }
        //Log.d("JSON", gson.toJson( fbData ) );
    }

    public void onCameraViewStopped() {
    }

    public Mat getFlow(Mat m, int maxDetectionCount, double qualityLevel, double minDist, Mat color) {
        count++;
        Mat newM = color;
        //Imgproc.goodFeaturesToTrack(m, initial, maxDetectionCount, qualityLevel, minDist);
        //MatOfPoint2f initial2f = new MatOfPoint2f();
        //initial.convertTo(initial2f, CvType.CV_32FC1);

        FeatureDetector fast = FeatureDetector.create( FeatureDetector.FAST );
        MatOfKeyPoint kp = new MatOfKeyPoint();
        fast.detect( m, kp );

        //
        if( kp.toArray().length < 30 ) {
            return color;
        }
        List<KeyPoint> listOfKeypoints = kp.toList();
        Collections.sort(listOfKeypoints, new Comparator<KeyPoint>() {
            @Override
            public int compare(KeyPoint kp1, KeyPoint kp2) {
                // Sort them in descending order, so the best response KPs will come first
                return (int) (kp2.response - kp1.response);
            }
        });

        List<KeyPoint> listOfBestKeypoints = listOfKeypoints.subList(0, 30);
        //
        //MatOfKeyPoint newP = new MatOfKeyPoint();
        //newP.fromList( listOfBestKeypoints );

        //drawKeypoints(color, newP, color, new Scalar(255, 0, 0), 0);
        //kp.toArray();
        //KeyPoint[] kp_arr = lis;
        Point[] point_arr = new Point[listOfBestKeypoints.size()];
        for( int a = 0; a < listOfBestKeypoints.size(); a++ ) {
            //point_arr[a].x = kp_arr[a].pt.x;
            //point_arr[a].y = kp_arr[a].pt.y;
            point_arr[a] = listOfBestKeypoints.get(a).pt;
        }
        MatOfPoint2f temp = new MatOfPoint2f( point_arr );
        //Log.d("NOTE", "Out: " + point_arr[0].x);
        //points.add(0, initial2f);
        points.add(0, temp);
        if( points.size() == 1 ) {
            points.add(0, temp);
        }
        if( points.get(0).total() < maxDetectionCount / 2 ) {
            //points.get(0).total()
            //Imgproc.goodFeaturesToTrack( m, initial, maxDetectionCount, qualityLevel, minDist);

            //initial2f = new MatOfPoint2f();
            //initial.convertTo(initial2f, CvType.CV_32FC1);
            //points.add(0, initial2f);
        }

        if( mPrev.empty() ) {
            m.copyTo( mPrev );
        }

        if( points.get(0).total() > 0 ) {
            Video.calcOpticalFlowPyrLK( mPrev, m, points.get(0), points.get(1), status, error );

            //Log.d("testlog", "0: (" + points.get(0).toArray()[0] + ") 1: (" + points.get(1).toArray()[0] + ")");
            for( int i = 0; i < points.get(0).toList().size(); i++) {
                Point first = points.get(0).toList().get(i);
                Point second = points.get(1).toList().get(i);
                Core.line(color, first, second, new Scalar(0, 255, 0), 5, 8, 0);
            }
            Point avgBefore = new Point();
            Point avgAfter = new Point();
            double arr[] = new double[2];

            arr[0] = 400;
            arr[1] = 300;
            avgBefore.set( arr );



            double x_mean = arr[0];
            double y_mean = arr[1];
            double dev_arr[] = new double[2];
            dev_arr[0] = 0;
            dev_arr[1] = 0;
            for( int i = 0; i < points.get(0).toList().size(); i++ ) {
                dev_arr[0] += Math.pow((points.get(1).toList().get(i).x - points.get(0).toList().get(i).x) - x_mean, 2);
                dev_arr[1] += Math.pow((points.get(1).toList().get(i).y - points.get(0).toList().get(i).y) - y_mean, 2);
            }
            dev_arr[0] /= points.get(0).toList().size();
            dev_arr[1] /= points.get(0).toList().size();
            dev_arr[0] = Math.sqrt(dev_arr[0]);
            dev_arr[1] = Math.sqrt(dev_arr[1]);

            arr[0] = 0;
            arr[1] = 0;
            double tempX, tempY;
            int outX = 0, outY = 0;
            for( int i = 0; i < points.get(0).toList().size(); i++) {
                tempX = points.get(1).toList().get(i).x - points.get(0).toList().get(i).x;
                tempY = points.get(1).toList().get(i).y - points.get(0).toList().get(i).y;
                //if( Math.abs(tempY) <= Math.abs(dev_arr[1]) && Math.abs(tempX) <= Math.abs(dev_arr[0]) && Math.sqrt(Math.abs(Math.pow(tempX, 2) + Math.pow(tempY, 2))) > 1) {
                if( Math.abs(tempY) <= Math.abs(dev_arr[1]) && Math.abs(tempX) <= Math.abs(dev_arr[0]) ) {
                    arr[0] += tempX;
                    outX++;
                    arr[1] += tempY;
                    outY++;
                }
            }
            arr[0] /= outX;
            arr[1] /= outY;
            arr[0] *= 2.5; //make it more pronounced
            arr[1] *= 2.5;

            // value of arr[0] here is delta X
            // value of arr[1] here is delta Y
            //drawThing( arr[0], arr[1] ); ???
            if( count % 4 == 0 ) {
                pushFB( arr[0], arr[1] );
            }
            //maybe some type of exponential scaling?
             arr[0] += 400;
            arr[1] += 300;
            avgAfter.set( arr );

            Core.line(color, avgBefore, avgAfter, new Scalar(0, 0, 255), 5, 8, 0);

        }


        m.copyTo(mPrev);


        return color;
        //return newM;
    }
    //this function is called on each frame.

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //Mat m = new Mat();
        //m.create(800, 600, CvType.CV_32FC1);
        return getFlow(inputFrame.gray(), 25, 0.01, 10.0, inputFrame.rgba());
        //return inputFrame.rgba();

    }


}

class fbClass {
    private Double x;
    private Double y;
    public void setX (Double xx) {
        x = xx;
    }
    public void setY (Double yy) {
        y = yy;
    }
}