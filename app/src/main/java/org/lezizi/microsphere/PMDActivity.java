package org.lezizi.microsphere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;

public class PMDActivity extends AppCompatActivity {
    protected MatOfKeyPoint keypoints;
    protected Mat cimg;
    protected Mat resized;
    protected void update_preview(){
        SeekBar  bar1 =  findViewById(R.id.seekBar1);
        SeekBar  bar2 =  findViewById(R.id.seekBar2);
        SeekBar  bar3 =  findViewById(R.id.seekBar3);
        float barValue1 = bar1.getProgress()/(float)100.0;
        float barValue2 = bar2.getProgress()/(float)100.0;
        float barValue3 = bar3.getProgress()/(float)100.0;
        TextView result= findViewById(R.id.textView2);

        Mat out=new Mat(cimg.rows()/4, cimg.cols()/4, CvType.CV_64FC3);

        ArrayList<KeyPoint> samll_list=new ArrayList<>();
        ArrayList<KeyPoint> middle_list=new ArrayList<>();
        ArrayList<KeyPoint> big_list=new ArrayList<>();
        for (KeyPoint vkp :  keypoints.toList())
        {
            if (vkp.size<barValue1){
                samll_list.add(vkp);
            }
            if (barValue1<=vkp.size && vkp.size<barValue2){
                middle_list.add(vkp);
            }
            if (barValue2<=vkp.size && vkp.size<barValue3){
                big_list.add(vkp);
            }
        }

        result.setText(String.format("(%.2f pix<d)=%d; (%.2f pix< d <%.2f pix)=%d; (%.2f pix< d <%.2f pix)=%d;",
                barValue1,
                samll_list.size(),
                barValue1,
                barValue2,
                middle_list.size(),
                barValue2,
                barValue3,
                big_list.size()
                )  );
        MatOfKeyPoint small=new MatOfKeyPoint();
        small.fromList(samll_list);
        Features2d.drawKeypoints(resized,small,out,new org.opencv.core.Scalar(0,255,0), Features2d.DRAW_RICH_KEYPOINTS );

        MatOfKeyPoint middle=new MatOfKeyPoint();
        middle.fromList(middle_list);
        Features2d.drawKeypoints(out,middle,out,new org.opencv.core.Scalar(0,0,255), Features2d.DRAW_RICH_KEYPOINTS );

        MatOfKeyPoint big=new MatOfKeyPoint();
        big.fromList(big_list);
        Features2d.drawKeypoints(out,big,out,new org.opencv.core.Scalar(255,0,0), Features2d.DRAW_RICH_KEYPOINTS );

        Mat m=new Mat(cimg.rows(), cimg.cols(), CvType.CV_64FC3);
        Imgproc.resize(out, m, m.size(),0,0,Imgproc.INTER_AREA);


        // convert to bitmap:
        Bitmap bm = Bitmap.createBitmap(m.cols(), m.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bm);

        // find the imageview and draw it!
        ImageView iv = findViewById(R.id.imageView1);
        iv.setImageBitmap(bm);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pmd);

        Intent intent = getIntent();
        String filename=intent.getStringExtra(MainActivity.PMDActivity_Filename);


        try {
            FeatureDetector detector = FeatureDetector.create(FeatureDetector.SIMPLEBLOB);
            detector.read(MainActivity.app_mainpath + "detector.xml");
            cimg = Imgcodecs.imread(filename);
            resized = new Mat(cimg.rows()/4, cimg.cols()/4, CvType.CV_64FC3);
            Imgproc.resize(cimg, resized, resized.size(),0,0,Imgproc.INTER_AREA);
            keypoints=new MatOfKeyPoint();
            detector.detect(resized,keypoints);
        } catch (Exception e) {
            finish();
            return;
        }
        float max_size=0;
        for (KeyPoint vkp :  keypoints.toList())
        {
            if (vkp.size>max_size) max_size=vkp.size;
        }
        SeekBar  bar1 =  findViewById(R.id.seekBar1);
        SeekBar  bar2 =  findViewById(R.id.seekBar2);
        SeekBar  bar3 =  findViewById(R.id.seekBar3);
        bar1.setMax((int)(max_size*100.0));
        bar2.setMax((int)(max_size*100.0));
        bar3.setMax((int)(max_size*100.0));


        SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                if (b){
                    update_preview();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        };

        bar1.setOnSeekBarChangeListener(listener);
        bar2.setOnSeekBarChangeListener(listener);
        bar3.setOnSeekBarChangeListener(listener);

        update_preview();
    }

}
