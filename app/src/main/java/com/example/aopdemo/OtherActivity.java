package com.example.aopdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import com.example.annotation.CountTime;

public class OtherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        findViewById(R.id.textView).setOnClickListener(v -> {
            doSomething();
        });
    }

    @CountTime
    private void doSomething() {
        Toast.makeText(this, "fb(30) = " + fb(30), Toast.LENGTH_SHORT).show();
    }

    // 计算非波拉契数列第n项的值
    private int fb(int n) {
        if (n == 0 || n == 1) {
            return n;
        }
        return fb(n - 1) + fb(n - 2);
    }
}