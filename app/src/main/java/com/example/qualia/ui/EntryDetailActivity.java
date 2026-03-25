package com.example.qualia.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.example.qualia.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EntryDetailActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry_detail);

        TextView btnBack = findViewById(R.id.btnBack);
        TextView txtDate = findViewById(R.id.txtDate);
        TextView txtBody = findViewById(R.id.txtEntryBody);

        String text = getIntent().getStringExtra("entry_text");
        long timestamp = getIntent().getLongExtra("entry_date", 0);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        txtDate.setText(sdf.format(new Date(timestamp)));
        txtBody.setText(text);

        txtBody.animate().alpha(0f).setDuration(0).start();
        txtBody.animate().alpha(1f).setStartDelay(300).setDuration(800).start();

        btnBack.setOnClickListener(v -> finish());
    }
}