package org.lezizi.microsphere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class PMDActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pmd);
        Intent intent = getIntent();
        String filename=intent.getStringExtra(MainActivity.PMDActivity_Filename);

        FeatureDetector detector = FeatureDetector.create(FeatureDetector.SIMPLEBLOB);
        detector.read(MainActivity.app_mainpath + "detector.xml");
        Mat cimg;

        try {
            cimg = Imgcodecs.imread(filename);

        } catch (Exception e) {
            finish();
            return;
        }
        Mat resized = new Mat(cimg.rows()/4, cimg.cols()/4, CvType.CV_64FC3);
        Imgproc.resize(cimg, resized, resized.size(),0,0,Imgproc.INTER_AREA);
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        detector.detect(resized,keypoints);

        for (KeyPoint vkp :  keypoints.toList())
        {

        }

        Mat out=new Mat(cimg.rows()/4, cimg.cols()/4, CvType.CV_64FC3);
        Mat m=new Mat(cimg.rows(), cimg.cols(), CvType.CV_64FC3);

        Features2d.drawKeypoints(resized,keypoints,out,new org.opencv.core.Scalar(0,255,0), Features2d.DRAW_RICH_KEYPOINTS );
        Imgproc.resize(out, m, m.size(),0,0,Imgproc.INTER_AREA);


        // convert to bitmap:
        Bitmap bm = Bitmap.createBitmap(m.cols(), m.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bm);

        // find the imageview and draw it!
        ImageView iv = findViewById(R.id.imageView1);
        iv.setImageBitmap(bm);
    }

}
