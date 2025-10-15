package com.prism.plyviewer360;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.InputStream;

/**
 * Main Activity for PLY Viewer 360°
 * Handles file selection, permissions, and UI coordination
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private ModelView modelView;
    private ProgressBar progressBar;
    private View instructionsLayout;
    private View controlBar;
    private FloatingActionButton fabLoad;
    private Button btnReset, btnAutoRotate, btnLoadNew;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        modelView = findViewById(R.id.modelView);
        progressBar = findViewById(R.id.progressBar);
        instructionsLayout = findViewById(R.id.instructionsLayout);
        controlBar = findViewById(R.id.controlBar);
        fabLoad = findViewById(R.id.fabLoad);
        btnReset = findViewById(R.id.btnReset);
        btnAutoRotate = findViewById(R.id.btnAutoRotate);
        btnLoadNew = findViewById(R.id.btnLoadNew);

        // Setup file picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            loadPLYFile(uri);
                        }
                    }
                }
        );

        // Setup button listeners
        fabLoad.setOnClickListener(v -> checkPermissionAndOpenFilePicker());
        btnLoadNew.setOnClickListener(v -> checkPermissionAndOpenFilePicker());
        btnReset.setOnClickListener(v -> modelView.resetView());
        btnAutoRotate.setOnClickListener(v -> {
            modelView.toggleAutoRotate();
            updateAutoRotateButton();
        });

        // Check if we have a file passed via intent
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /**
     * Handle file opened from external apps
     */
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                loadPLYFile(uri);
            }
        }
    }

    /**
     * Check storage permission and open file picker
     */
    private void checkPermissionAndOpenFilePicker() {
        // Android 13+ doesn't need READ_EXTERNAL_STORAGE for file picker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openFilePicker();
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this,
                        "Storage permission is required to load PLY files",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Open system file picker for PLY files
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        // Try to filter for PLY files
        String[] mimeTypes = {"application/octet-stream", "text/plain", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        filePickerLauncher.launch(intent);
    }

    /**
     * Load and parse PLY file
     */
    private void loadPLYFile(Uri uri) {
        Log.d(TAG, "Loading PLY file: " + uri);

        // Show loading UI
        progressBar.setVisibility(View.VISIBLE);
        instructionsLayout.setVisibility(View.GONE);

        // Load in background thread
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> showError("Failed to open file"));
                    return;
                }

                // Parse PLY file
                PLYParser parser = new PLYParser();
                PLYParser.ParseResult result = parser.parse(inputStream);
                inputStream.close();

                if (!result.isValid()) {
                    runOnUiThread(() -> showError("Invalid PLY file format"));
                    return;
                }

                // Load into OpenGL on UI thread
                runOnUiThread(() -> {
                    modelView.loadModel(result);
                    progressBar.setVisibility(View.GONE);
                    controlBar.setVisibility(View.VISIBLE);
                    fabLoad.hide();
                    updateAutoRotateButton();

                    Toast.makeText(this,
                            "Model loaded successfully!\n" +
                                    "Vertices: " + (result.vertices.length / 3) + "\n" +
                                    "Faces: " + (result.indices.length / 3),
                            Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Model loaded successfully");
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading PLY file", e);
                runOnUiThread(() -> showError("Error loading file: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        instructionsLayout.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Update auto-rotate button text
     */
    private void updateAutoRotateButton() {
        if (modelView.getRenderer().isAutoRotate()) {
            btnAutoRotate.setText("Stop Rotation");
        } else {
            btnAutoRotate.setText("Auto Rotate");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        modelView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        modelView.onPause();
    }
}