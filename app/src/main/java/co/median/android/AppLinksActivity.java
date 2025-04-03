package co.median.android;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AppLinksActivity extends AppCompatActivity {

    public static final String LAUNCH_SOURCE_APP_LINKS = "app_links";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchApp();
    }

    private void launchApp() {
        Intent intent = new Intent(this, LaunchActivity.class);
        if (getIntent().getData() != null) {
            intent.setData(getIntent().getData());
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("source", LAUNCH_SOURCE_APP_LINKS);
        }
        startActivity(intent);
        finish();
    }
}
