package com.example.autolaunchtechstern.ui;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.autolaunchtechstern.R;

public class SecondActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Start LockTask again to remain in kiosk mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask();
        }
        TextView tv = new TextView(this);
        tv.setText("This is the Second Activity");
        tv.setTextSize(24);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
