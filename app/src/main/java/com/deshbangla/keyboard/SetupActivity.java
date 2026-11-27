package com.deshbangla.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("Desh Bangla Keyboard");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView info = new TextView(this);
        info.setText("\nধাপ ১: মাইক্রোফোনের অনুমতি দিন\nধাপ ২: কিবোর্ড চালু করুন\nধাপ ৩: কিবোর্ড বেছে নিন\n");
        info.setTextSize(16);
        box.addView(info);

        Button mic = new Button(this);
        mic.setText("১. মাইক্রোফোনের অনুমতি দিন");
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                }
            }
        });
        box.addView(mic);

        Button enable = new Button(this);
        enable.setText("২. কিবোর্ড চালু করুন");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        box.addView(enable);

        Button pick = new Button(this);
        pick.setText("৩. কিবোর্ড বেছে নিন");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showInputMethodPicker();
            }
        });
        box.addView(pick);

        setContentView(box);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this, ok ? "অনুমতি দেওয়া হয়েছে" : "অনুমতি দেওয়া হয়নি", Toast.LENGTH_LONG).show();
    }
}
