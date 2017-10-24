package org.lezizi.microsphere;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
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

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static String path = Environment.getExternalStorageDirectory().toString() + "/Microsphere/";
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

    public void add_log(String line) {
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.append("\n" + line);
    }

    public void get_permission() {
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
                add_log("Permission granted. ");
                init_system();
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

    private void init_system() {
        File f = new File(path);
        if (!f.isDirectory()) {
            f.mkdirs();
            copyAssets(path); //getExternalFilesDir(null)
        }
        // create output folder
        f = new File(path + "output");
        if (!f.isDirectory()) {
            f.mkdirs();
        }
        add_log("Path: " + path);
        try {
            net = Dnn.readNetFromTensorflow(path + "graph.pb");
            add_log("Model: " + path + "graph.pb");
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            add_log(stringFromJNI());
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

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
        protected void onPreExecute() {
            FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
            fab.setVisibility(View.INVISIBLE);
            ProgressBar prg = (ProgressBar) findViewById(R.id.progressBar1);
            prg.setVisibility(View.VISIBLE);
            PieChart rtv = (PieChart) findViewById(R.id.result_text);
            rtv.setVisibility(View.INVISIBLE);
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
            PieChart mChart = (PieChart) findViewById(R.id.result_text);
            mChart.setVisibility(View.VISIBLE);

            add_log("Generating report...");
            String report= String.format("Report \n\n Single beads: %, 5d \n Dimers: %, 17d \n \n Percentage of dimer: %2.2f %%",total_single_count,total_double_count,(double)total_double_count*100.0/((double)total_double_count+(double)total_single_count)+Math.ulp(1.0));
            //rtv.setText(report);
            add_log(report);



            List<PieEntry> entries_double = new ArrayList<>();
            entries_double.add(new PieEntry(total_single_count,"Single beads"));
            entries_double.add(new PieEntry(total_double_count,"Dimers"));
            PieDataSet dataset_double = new PieDataSet(entries_double, "Detection result");

            dataset_double.setColors(ColorTemplate.MATERIAL_COLORS);
            PieData pieData = new PieData(dataset_double);

            pieData.setDrawValues(true);
            pieData.setValueTextColor(Color.BLUE);
            pieData.setValueTextSize(20f);

            pieData.setValueFormatter(new PercentFormatter());

            mChart.setEntryLabelColor(Color.BLACK);
            mChart.setEntryLabelTextSize(22f);

            mChart.setDrawHoleEnabled(true);
            mChart.setHoleRadius(40f);
            mChart.setTransparentCircleRadius(48f);
            mChart.setTransparentCircleColor(Color.BLACK);
            mChart.setTransparentCircleAlpha(50);
            mChart.setHoleColor(Color.WHITE);
            mChart.setDrawCenterText(true);

            mChart.setCenterText("Detection result");
            mChart.setCenterTextSize(14f);
            mChart.setCenterTextColor(Color.BLACK);

            mChart.setUsePercentValues(true);
            mChart.setDrawEntryLabels(true);

            Legend l = mChart.getLegend();
            l.setEnabled(true);

            mChart.getDescription().setEnabled(false);
            mChart.setData(pieData);
            mChart.invalidate();
            Snackbar.make(findViewById(android.R.id.content), "Detection finished. ", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
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
