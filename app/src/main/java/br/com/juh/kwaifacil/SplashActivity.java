package br.com.juh.kwaifacil;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class SplashActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFFFFF7ED);
        getWindow().setNavigationBarColor(0xFFFFF7ED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(28, 20, 28, 30);
        l.setBackgroundColor(0xFFFFF7ED);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER);
        ImageView im = new ImageView(this);
        im.setImageResource(getResources().getIdentifier("app_mascot", "drawable", getPackageName()));
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.addView(im, new LinearLayout.LayoutParams(150, 150));
        TextView t = new TextView(this);
        t.setText("Kwai Fácil");
        t.setTextColor(0xFF42271D);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = 8;
        brand.addView(t, tp);
        l.addView(brand);
        TextView s = new TextView(this);
        s.setText("Sua fila de vídeos, com carinho e controle");
        s.setTextColor(0xFF82695C);
        s.setTextSize(15);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 8;
        l.addView(s, sp);
        setContentView(l);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 900);
    }
}
