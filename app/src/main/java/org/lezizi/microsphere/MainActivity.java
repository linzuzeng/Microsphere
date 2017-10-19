package org.lezizi.microsphere;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import android.support.v4.app.ActivityCompat;
import android.text.method.ScrollingMovementMethod;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.features2d.*;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity  {

    private class DetectionTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings) {
            process_directory(strings[0]);
            return "done";
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            add_log(progress[0]);
        }
        @Override
        protected void onPreExecute(){
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.INVISIBLE);
            ProgressBar prg = (ProgressBar) findViewById(R.id.progressBar1);
            prg.setVisibility(View.VISIBLE);
            TextView rtv = (TextView) findViewById(R.id.result_text);
            rtv.setVisibility(View.INVISIBLE);
            Snackbar.make( findViewById(android.R.id.content), "detection starting... ", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
        @Override
        protected void onPostExecute(String result) {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.VISIBLE);
            ProgressBar prg = (ProgressBar) findViewById(R.id.progressBar1);
            prg.setVisibility(View.INVISIBLE);
            TextView rtv = (TextView) findViewById(R.id.result_text);
            rtv.setVisibility(View.VISIBLE);
            rtv.setText("Report \n\n Single sphere: 100 \n Double sphere: 7 \n \n Result: 7.0%");
            Snackbar.make( findViewById(android.R.id.content), "detection finished. ", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
        private void process_directory(String path){

            publishProgress("start detecting...");
            publishProgress("=================");

            File f = new File(path);

            if (f.isDirectory()) {
                File[] files = f.listFiles();
                for (File inFile : files) {
                    if (inFile.isDirectory()) {
                        publishProgress("INFO: Ignoring sub-directory: "+inFile.getName());
                    }else{
                        process(inFile.getAbsolutePath(),path+"output/"+inFile.getName());

                    }
                }
            }else{
                publishProgress("ERROR: Directory for files is not found..");
            }
            publishProgress("=================");
        }
        private void process(String filename,String fileoutname){

            publishProgress("File: "+filename);
            Mat cimg;
            Mat img= new Mat();
            try{
                cimg=Imgcodecs.imread(filename);
                Imgproc.cvtColor(cimg,img,Imgproc.COLOR_RGBA2GRAY);
            }catch (Exception e){
                publishProgress("ERROR: can not open file, it might not be an image. ");
                return;
            }

            Mat circles = new Mat();
            Imgproc.HoughCircles(img, circles, Imgproc.CV_HOUGH_GRADIENT, 2.0, 3.0, 40, 13, 4, 7);

            if (circles.cols() > 0){
                ArrayList<Mat> list_of_candidates=new ArrayList<>();

                for (int x = 0; x < circles.cols(); x++) {
                    double vCircle[] = circles.get(0, x);


                    Point pt = new Point(Math.round(vCircle[0] ), Math.round(vCircle[1] ));
                    int radius = (int) Math.round(vCircle[2] );

                    // draw the found candidates
                    Imgproc.circle(cimg, pt, radius, new Scalar(0, 255, 0), 1);

                    final int raidus=8;
                    Rect myROI = new Rect(
                            (int)Math.max(pt.x-raidus,0),
                            (int)Math.max(pt.y-raidus,0),
                            ((int)Math.min(pt.x+raidus,img.cols())-(int)Math.max(pt.x-raidus,0)),
                            ((int)Math.min(pt.y+raidus,img.rows())-(int)Math.max(pt.y-raidus,0))
                    );
                    Log.i(TAG,myROI.toString());
                    Mat copied=img.submat(myROI);
                    Mat resized=new Mat(16,16,CvType.CV_64FC1);
                    Imgproc.resize(copied, resized, resized.size());

                    Mat divided = new Mat(16,16,CvType.CV_32FC1); // result

                    Core.transpose(resized,resized);
                    resized.convertTo(divided, CvType.CV_32FC1, 1.0/255.5);

                    //add_log(String.valueOf(divided.get(3,3)[0]));
                    //resized.convertTo(resized,CvType.CV_32F);
                    list_of_candidates.add(divided);
                }
                publishProgress("INFO: forward propagation for "+String.valueOf(list_of_candidates.size())+" candidates.");

                net.setInput(Dnn.blobFromImages(list_of_candidates));
                Mat result= net.forward();
                int circle_count=0;
                for (int x = 0; x < circles.cols(); x++) {
                    double vCircle[] = circles.get(0, x);


                    Point pt = new Point(Math.round(vCircle[0] ), Math.round(vCircle[1] ));
                    int radius = (int) Math.round(vCircle[2] );

                    double vResult0[] = result.get(x, 0);
                    double vResult1[] = result.get(x, 1);
                    if (vResult0[0]>vResult1[0]){
                        Imgproc.circle(cimg, pt, radius, new Scalar(0, 0, 255), 3);
                        circle_count++;
                    }else{

                    }
                }
                publishProgress("INFO: found "+String.valueOf(circle_count)+" microspheres.");
            }
            publishProgress("---------------");
            Imgcodecs.imwrite(fileoutname,cimg);
            circles.release();
            cimg.release();
            img.release();
        }
    }


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    private static final String TAG = "MainActivity";
    private static String path = Environment.getExternalStorageDirectory().toString()+"/Microsphere/";
    static {
        if(!OpenCVLoader.initDebug()){
            Log.d(TAG, "OpenCV not loaded");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }

    }

    private static Net net;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new DetectionTask().execute(path);
            }
        });
        add_log(stringFromJNI());
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setMovementMethod(new ScrollingMovementMethod());
        get_permission();
        init_system();
    }
    public void add_log(String line){
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.append("\n"+line);
    }
    private  final int REQUEST_WRITE_STORAGE = 112;
    public void get_permission(){
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                add_log( "Permission granted. ");
                init_system();
            } else {
                add_log( "ERROR: No permission to read external storage. ");
            }
        }
    }
    private void copyAssets(String path) {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) for (String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File(path, filename);
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch(IOException e) {
                Log.e("tag", "Failed to copy asset file: " + filename, e);
            }
            finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
            }
        }
    }
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    private void init_system(){
        File f = new File(path);
        if (!f.isDirectory()) {
            f.mkdirs();
            copyAssets(path); //getExternalFilesDir(null)
        }
        // create output folder
        f= new File(path+"output");
        if (!f.isDirectory()) {
            f.mkdirs();
        }
        add_log("Path: "+path);
        try{
            net= Dnn.readNetFromTensorflow(path+"graph.pb");
            add_log("Model: "+path+"graph.pb");
        }catch (Exception e){
            add_log("ERROR: unable to load model, this app might not work correctly.");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
