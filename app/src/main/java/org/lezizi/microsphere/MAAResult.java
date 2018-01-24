package org.lezizi.microsphere;

import android.content.Intent;
import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.List;

public class MAAResult extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maaresult);

        Intent intent = getIntent();


      
        PieChart mChart = (PieChart) findViewById(R.id.result_text);
        mChart.setVisibility(View.VISIBLE);

        List<PieEntry> entries_double = new ArrayList<>();
        entries_double.add(new PieEntry(intent.getIntExtra(MainActivity.MAAResult_SingleBeads,0),"Single beads"));
        entries_double.add(new PieEntry(intent.getIntExtra(MainActivity.MAAResult_Dimers,0),"Dimers"));
        PieDataSet dataset_double = new PieDataSet(entries_double, "");

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
        mChart.setCenterTextSize(17f);
        mChart.setCenterTextColor(Color.BLACK);

        mChart.setUsePercentValues(true);
        mChart.setDrawEntryLabels(true);

        Legend l = mChart.getLegend();
        l.setEnabled(true);
        l.setTextSize(17f);

        mChart.getDescription().setEnabled(false);
        mChart.setData(pieData);
        mChart.invalidate();
    }
}
