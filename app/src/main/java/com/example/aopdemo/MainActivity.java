package com.example.aopdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.bug.calculator.BugCalculator;
import com.example.annotation.ClickOnce;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        textView.setOnClickListener(this);

        try {
            // 通过反射调用jar包中新增的方法
            // 注意不能直接显式调用fAdd方法，否则编译不通过
            Class<?> clz = BugCalculator.class;
            Method fAdd = clz.getDeclaredMethod("fAdd", float.class, float.class);
            Object result = fAdd.invoke(clz, 1.5f, 2.2f);
            Log.d("BugCalculator", "fAdd(1.5 + 2.2) = " + result);
            // 调用原来的add方法，返回的是丢失了精度的值
            Log.d("BugCalculator", "add(1.5 + 2.2) = " + BugCalculator.add(1.5f, 2.2f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @ClickOnce
    public void onClick(View v) {
        startActivity(new Intent(this, OtherActivity.class));
    }
}