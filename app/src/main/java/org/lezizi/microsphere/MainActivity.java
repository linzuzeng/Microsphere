package org.lezizi.microsphere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.yanzhenjie.album.Action;
import com.yanzhenjie.album.Album;
import com.yanzhenjie.album.AlbumFile;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String MAAResult_Dimers = "org.lezizi.microsphere.MAAResult_Dimers";
    public static final String MAAResult_SingleBeads = "org.lezizi.microsphere.MAAResult_SingleBeads";
    public static final String PMDActivity_Filename = "org.lezizi.microsphere.PMDActivity_Filename";

    public static String app_mainpath = Environment.getExternalStorageDirectory().toString() + "/Microsphere/";

    private static final String TAG = "MainActivity";
    private static Net net;
    private static int total_single_count;
    private  static int total_double_count;
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV not loaded");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }

    }

    private final int REQUEST_WRITE_STORAGE = 112;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // set textview
        add_log(stringFromJNI());
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setMovementMethod(new ScrollingMovementMethod());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (detection_mode == R.id.mode_maa)
                    new MAADetectionTask().execute(app_mainpath);
                else{
                    Album.image(MainActivity.this) // Image selection.
                            .singleChoice()
                            .requestCode(0)
                            .camera(true)
                            .columnCount(3)
                            .onResult(new Action<ArrayList<AlbumFile>>() {
                                @Override
                                public void onAction(int requestCode, @NonNull ArrayList<AlbumFile> result) {
                                    for (AlbumFile file: result){
                                        add_log("PMD: "+file.getPath());
                                        Intent intent = new Intent(MainActivity.this, PMDActivity.class);
                                        intent.putExtra(PMDActivity_Filename, file.getPath());

                                        startActivity(intent);
                                    }

                                }
                            })
                            .onCancel(new Action<String>() {
                                @Override
                                public void onAction(int requestCode, @NonNull String result) {
                                }
                            })
                            .start();
                }


            }
        });

        get_permission();
        init_filesystem();
    }

    public void add_log(String line) {
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.append("\n" + line);
    }

    public void get_permission() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                add_log("Permission granted. ");
                init_filesystem();
            } else {
                add_log("ERROR: No permission to read external storage. ");
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
            } catch (IOException e) {
                Log.e("tag", "Failed to copy asset file: " + filename, e);
            } finally {
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
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void init_filesystem() {
        File f = new File(app_mainpath);
        if (!f.isDirectory()) {
            f.mkdirs();
            add_log("copy assets...");
            copyAssets(app_mainpath); //getExternalFilesDir(null)
        }
        // create output folder
        f = new File(app_mainpath + "output");
        if (!f.isDirectory()) {
            f.mkdirs();
        }
        add_log("Path: " + app_mainpath);
        try {
            net = Dnn.readNetFromTensorflow(app_mainpath + "graph.pb");
            add_log("Model: " + app_mainpath + "graph.pb");
        } catch (Exception e) {
            add_log("ERROR: unable to load model, this app might not work correctly.");
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    public int detection_mode = 0;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        detection_mode = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (detection_mode == R.id.mode_about) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.INVISIBLE);
            TextView tv = (TextView) findViewById(R.id.sample_text);
            tv.setText(getString(R.string.action_about));
            add_log(stringFromJNI());
            return true;
        }
        if (detection_mode == R.id.mode_maa) {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.VISIBLE);
            getSupportActionBar().setTitle(getString(R.string.action_maa));
            Snackbar.make(findViewById(android.R.id.content), "Mode: "+getString(R.string.action_maa), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return true;

        }
        if (detection_mode == R.id.mode_pmd) {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.VISIBLE);
            getSupportActionBar().setTitle(getString(R.string.action_pmd));
            Snackbar.make(findViewById(android.R.id.content), "Mode: "+getString(R.string.action_pmd), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    private class MAADetectionTask extends AsyncTask<String, String, String> {

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
        protected void onPreExecute() {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.INVISIBLE);
            ProgressBar prg = (ProgressBar) findViewById(R.id.progressBar1);
            prg.setVisibility(View.VISIBLE);


            Snackbar.make(findViewById(android.R.id.content), "Starting detection... ", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }

        @Override
        protected void onPostExecute(String result) {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.VISIBLE);
            ProgressBar prg = (ProgressBar) findViewById(R.id.progressBar1);
            prg.setVisibility(View.INVISIBLE);
            //TextView rtv = (TextView) findViewById(R.id.result_text);

            Snackbar.make(findViewById(android.R.id.content), "Detection finished. ", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            add_log("Generating report...");
            String report= String.format("Report \n\n Single beads: %, 5d \n Dimers: %, 17d \n \n Percentage of dimer: %2.2f %%",total_single_count,total_double_count,(double)total_double_count*100.0/((double)total_double_count+(double)total_single_count)+Math.ulp(1.0));
            //rtv.setText(report);
            add_log(report);
            //start MAAResult Activity
            Intent intent = new Intent(MainActivity.this, MAAResult.class);
            intent.putExtra(MAAResult_SingleBeads, total_single_count);
            intent.putExtra(MAAResult_Dimers, total_single_count);
            startActivity(intent);
        }

        private void process_directory(String path) {

            publishProgress("\n \nStarting a detection task...");
            total_single_count=0;
            total_double_count=0;
            publishProgress("=================");

            File f = new File(path);

            if (f.isDirectory()) {
                File[] files = f.listFiles();
                for (File inFile : files) {
                    if (inFile.isDirectory()) {
                        publishProgress("WARN: Ignoring sub-directory: " + inFile.getName());
                    } else {
                        process(inFile.getAbsolutePath(), path + "output/" + inFile.getName());

                    }
                }
            } else {
                publishProgress("ERROR: The directory for detection is not found..");
            }
            publishProgress("\nDetection finished.");
           }

        private ArrayList<int[]> max_pooling(ArrayList<int[]> circles, Mat bb, int region) {
            ArrayList<int[]> candidate_circles = new ArrayList<>();

                java.util.Collections.sort(circles, new java.util.Comparator<int[]>() {
                    @Override
                    public int compare(int[] s1, int[] s2) {
                        return s2[2] - s1[2];//comparision
                    }
                });

            for (int[] i : circles) {
                int y = i[0];
                int x = i[1];
                if ((i[2] == 0) || (bb.get(y, x)[0] == 0)) {
                    continue;
                } else {
                    candidate_circles.add(new int[]{y, x, i[2]});
                    for (int cy = y - region; cy <= y + region; cy++) {
                        for (int cx = x - region; cx <= x + region; cx++) {
                            int chk0 = Math.min(Math.max(cy, 0), bb.rows() - 1);
                            int chk1 = Math.min(Math.max(cx, 0), bb.cols() - 1);
                            bb.put(chk0, chk1, new int[]{0});
                        }
                    }
                }
            }
            return candidate_circles;
        }

        private void process(String filename, String fileoutname) {

            publishProgress("opening file " + filename+"...");
            Mat cimg;
            Mat img = new Mat();
            try {
                cimg = Imgcodecs.imread(filename);
                Imgproc.cvtColor(cimg, img, Imgproc.COLOR_RGBA2GRAY);
            } catch (Exception e) {
                publishProgress("ERROR: File contains an unsupported image format. ");
                return;
            }
            Mat th3 = new Mat();
            Imgproc.adaptiveThreshold(img, th3, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 2);
            Imgproc.dilate(th3, th3, Mat.ones(2, 2, CvType.CV_8U));
            // candidate generator
            ArrayList<int[]> circles = new ArrayList<>();
            Mat th3_integral = new Mat();
            Imgproc.integral(th3, th3_integral);
            int radius = 2;
            Mat bw = Mat.zeros(img.rows(), img.cols(), CvType.CV_32S);
            for (int y = 8; y < img.rows() - 8; y += 2) {
                for (int x = 8; x < img.cols() - 8; x += 2) {
                    int y0 = Math.max(y - radius, 0);
                    int y1 = Math.min(y + radius, img.rows());
                    int x0 = Math.max(x - radius, 0);
                    int x1 = Math.min(x + radius, img.cols());
                    int acc = (int) (th3_integral.get(y1, x1)[0] - th3_integral.get(y0, x1)[0] - th3_integral.get(y1, x0)[0] + th3_integral.get(y0, x0)[0]);
                    acc = (radius * radius * 4 - acc / 255);
                    if (acc > 16 - 11) {
                        circles.add(new int[]{y, x, acc* 30});
                        bw.put(y, x, new int[]{acc * 30});
                    }
                }
            }
            ArrayList<int[]> candidate_circles = max_pooling(circles, bw, 2);
            // crop and forward
            ArrayList<Mat> list_of_imags = new ArrayList<>();
            for (int[] i : candidate_circles) {

                // draw the found candidates
                Imgproc.circle(cimg, new Point(i[1], i[0]), radius * radius, new Scalar(0, 255, 0), 1);
                final int window = 8;
                int x = i[1];
                int y = i[0];
                Rect myROI = new Rect(
                        Math.max(x - window, 0),
                        Math.max(y - window, 0),
                        (Math.min(x + window, img.cols()) - Math.max(x - window, 0)),
                        (Math.min(y + window, img.rows()) - Math.max(y - window, 0))
                );
                Log.i(TAG, myROI.toString());
                Mat copied = img.submat(myROI);
                Mat resized = new Mat(16, 16, CvType.CV_64FC1);
                Imgproc.resize(copied, resized, resized.size());

                Mat divided = new Mat(16, 16, CvType.CV_32FC1); // result

                Core.transpose(resized, resized);
                resized.convertTo(divided, CvType.CV_32FC1, 1.0 / 255.5);

                //add_log(String.valueOf(divided.get(3,3)[0]));
                //resized.convertTo(resized,CvType.CV_32F);
                list_of_imags.add(divided);
            }
            publishProgress( String.format("forward propagation for %,d candidates.",list_of_imags.size()));

            net.setInput(Dnn.blobFromImages(list_of_imags));
            Mat predictions = net.forward();
            // perception pooling
            Mat prd = Mat.zeros(img.rows(), img.cols(), CvType.CV_32S);

            for (int idx = 0; idx < predictions.rows(); idx++) {
                int[] this_candidate = candidate_circles.get(idx);

                if (predictions.get(idx, 2)[0] > 0.6) {
                    candidate_circles.set(idx, new int[]{this_candidate[0], this_candidate[1], 2000});
                    prd.put(this_candidate[0], this_candidate[1], new int[]{this_candidate[2]});
                } else {
                    if (predictions.get(idx,0)[0] > 0.5) {
                        candidate_circles.set(idx, new int[]{this_candidate[0], this_candidate[1],(int)(predictions.get(idx,0)[0]*1000)});
                        prd.put(this_candidate[0], this_candidate[1], new int[]{(int)(predictions.get(idx,0)[0]*1000)});
                    }else
                    {
                        candidate_circles.set(idx, new int[]{this_candidate[0], this_candidate[1], 0});
                    }

                }

            }
            ArrayList<int[]> predicted_circles = max_pooling(candidate_circles, prd, 14);
            // print
            int double_count=0;
            int single_count=0;
            for (int[] iww : predicted_circles) {
                if (iww[2]>1000){
                    double_count++;
                    Imgproc.circle(cimg, new Point(iww[1],iww[0]), radius*radius*2, new Scalar(255, 0, 0), 2);
                }

                else{
                    single_count++;
                    Imgproc.circle(cimg, new Point(iww[1],iww[0]), radius*radius, new Scalar(0, 0, 255), 2);
                }

            }
            publishProgress( String.valueOf(single_count) + " single beads, and " + String.valueOf(double_count) + " dimers are found. ");
            Imgcodecs.imwrite(fileoutname, cimg);
            total_double_count+=double_count;
            total_single_count+=single_count;
            publishProgress("---------------");

            cimg.release();
            img.release();
        }
    }
}
