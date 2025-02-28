package com;

import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.compose.setContent;
import androidx.compose.foundation.layout.fillMaxSize;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.material3.Surface;
import androidx.compose.ui.Modifier;

import com.wooauto.presentation.WooAutoApp;

public class MainActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContent(() -> {
            WooAutoApp.Companion.getTheme(() -> {
                return new Surface(
                    new Modifier().fillMaxSize(),
                    (color) -> MaterialTheme.INSTANCE.getColorScheme().getBackground(),
                    null,
                    null,
                    WooAutoApp.Companion.getContent()
                );
            });
            return null;
        });
    }
}
