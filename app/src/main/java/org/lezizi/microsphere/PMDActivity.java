package org.lezizi.microsphere;

import android.content.Intent;
import android.graphics.Bitmap;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;


import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;


public class PMDActivity extends AppCompatActivity {
    protected MatOfKeyPoint keypoints;
    protected Mat cimg;
    protected Mat resized;
    protected float barValue1;
    protected float barValue2;
    protected float barValue3;
    protected void update_preview(){
        SeekBar  bar1 =  findViewById(R.id.seekBar1);
        SeekBar  bar2 =  findViewById(R.id.seekBar2);
        SeekBar  bar3 =  findViewById(R.id.seekBar3);
        barValue1 = bar1.getProgress()/(float)100.0;
        barValue2 = bar2.getProgress()/(float)100.0;
        barValue3 = bar3.getProgress()/(float)100.0;
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

        result.setText(String.format("(d<%.2f)=%d  (%.2f<d<%.2f)=%d (%.2f<d<%.2f)=%d",
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
    protected void draw_histogram(){
        final int bin_num=50;
        SeekBar bar1 =  findViewById(R.id.seekBar1);
        float bin_step= (bar1.getMax()/(float)100.0)/(float)bin_num;
        int histogram[] = new int[bin_num];
        for (KeyPoint vkp :  keypoints.toList())
        {
            int i = (int)(vkp.size/bin_step);
            if (i>=bin_num) i = bin_num-1;
            if (i<=0) i=0;
            histogram[i]+=1;
        }

        LineChart mChart =  findViewById(R.id.result_barchart);

        ArrayList<Entry> BARENTRY1= new ArrayList<>();
        ArrayList<Entry> BARENTRY2= new ArrayList<>();
        ArrayList<Entry> BARENTRY3= new ArrayList<>();
        ArrayList<Entry> BARENTRY4= new ArrayList<>();
        for (int i=0;i<bin_num;i++){
            float size = (float)i*bin_step;
            if (i<=(int)(barValue1/bin_step)){
                BARENTRY1.add(new Entry(size,histogram[i]));
            }
            if ((int)(barValue1/bin_step)<=i && i<=(int)(barValue2/bin_step)){
                BARENTRY2.add(new Entry(size,histogram[i]));
            }
            if ((int)(barValue2/bin_step)<=i && i<=(int)(barValue3/bin_step)){
                BARENTRY3.add(new Entry(size,histogram[i]));
            }
            if (i>=(int)(barValue3/bin_step)){
                BARENTRY4.add(new Entry(size,histogram[i]));
            }
        }
        List<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
        if (BARENTRY1.size()>0){
            LineDataSet DS1=new LineDataSet(BARENTRY1, String.format("(d <%.1f)",barValue1));
            DS1.setFillColor(getResources().getColor(R.color.chart_color1));
            DS1.setColor(getResources().getColor(R.color.chart_color1));
            DS1.setDrawCircles(false);
            DS1.setDrawValues(false);
            DS1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            DS1.setDrawFilled(true);
            dataSets.add( DS1);
        }

        if (BARENTRY2.size()>0){
            LineDataSet DS1= new LineDataSet(BARENTRY2, String.format("(%.1f< d <%.1f)",barValue1,barValue2));
            DS1.setFillColor(getResources().getColor(R.color.chart_color2));
            DS1.setColor(getResources().getColor(R.color.chart_color2));
            DS1.setDrawCircles(false);
            DS1.setDrawValues(false);
            DS1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            DS1.setDrawFilled(true);
            dataSets.add( DS1);
        }

        if (BARENTRY3.size()>0){
            LineDataSet DS1=new LineDataSet(BARENTRY3, String.format("(%.1f< d <%.1f)",barValue2,barValue3));
            DS1.setFillColor(getResources().getColor(R.color.chart_color3));
            DS1.setColor(getResources().getColor(R.color.chart_color3));
            DS1.setDrawCircles(false);
            DS1.setDrawValues(false);
            DS1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            DS1.setDrawFilled(true);
            dataSets.add( DS1);
        }

        if (BARENTRY4.size()>0){
            LineDataSet DS1=new LineDataSet(BARENTRY4, String.format("(%.1f< d)",barValue3));
            DS1.setFillColor(getResources().getColor(R.color.chart_color4));
            DS1.setColor(getResources().getColor(R.color.chart_color4));
            DS1.setDrawCircles(false);
            DS1.setDrawValues(false);
            DS1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            DS1.setDrawFilled(true);
            dataSets.add( DS1);
        }


        LineData BARDATA = new LineData( dataSets);
        XAxis xAxis = mChart.getXAxis();

        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setAxisMinimum(0);
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setAxisMinimum(0);
        rightAxis.setDrawAxisLine(false);
        rightAxis.setDrawLabels(false);

        mChart.setMinOffset(0);
        mChart.setData(BARDATA);
        mChart.getDescription().setText("Diameter");
       // mChart.getDescription().setYOffset(-25);

        mChart.invalidate();
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
        bar1.setProgress((int)(max_size*30.0));
        bar2.setProgress((int)(max_size*60.0));
        bar3.setProgress((int)(max_size*90.0));
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
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LinearLayout result_barchart = findViewById(R.id.result_layout);
                if (result_barchart.getVisibility()==View.VISIBLE) {
                    result_barchart.setVisibility(View.INVISIBLE);
                }else{
                    result_barchart.setVisibility(View.VISIBLE);
                    draw_histogram();
                }
            }
        });
    }

}
