package com.nanodesk.agentlink;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private ImageView ivScreen;
    private Button btnConnect;

    private String baseUrl = "";
    private String authToken = "";
    private volatile boolean connected = false;

    private final ExecutorService netExec = Executors.newFixedThreadPool(3);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        ivScreen = findViewById(R.id.ivScreen);
        btnConnect = findViewById(R.id.btnConnect);

        SharedPreferences prefs = getSharedPreferences("agentlink", MODE_PRIVATE);
        String savedHost = prefs.getString("host", "");
        String savedPort = prefs.getString("port", "8765");
        authToken = prefs.getString("token", "");
        if (!savedHost.isEmpty()) {
            baseUrl = "http://" + savedHost + ":" + savedPort;
        }

        btnConnect.setOnClickListener(v -> showConnectDialog());
        findViewById(R.id.btnOverlay).setOnClickListener(v -> showOverlayDialog());
        findViewById(R.id.btnKeys).setOnClickListener(v -> showKeyboardDialog());
        findViewById(R.id.btnChrome).setOnClickListener(v -> sendAction("chrome"));
        findViewById(R.id.btnGit).setOnClickListener(v -> sendAction("gitsync"));
        findViewById(R.id.btnClip).setOnClickListener(v -> showClipboardDialog());
        findViewById(R.id.btnShell).setOnClickListener(v -> showShellDialog());

        setupTouchControl();

        if (!baseUrl.isEmpty()) {
            startStreaming();
        } else {
            showConnectDialog();
        }
    }

    private void setupTouchControl() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                float[] norm = mapTouchToNormalized(e.getX(), e.getY());
                if (norm != null) sendMouse("click", norm[0], norm[1], 1);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                float[] norm = mapTouchToNormalized(e.getX(), e.getY());
                if (norm != null) sendMouse("click", norm[0], norm[1], 3);
            }
        });

        ivScreen.setOnTouchListener((v, event) -> {
            if (!connected) return false;
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float[] norm = mapTouchToNormalized(event.getX(), event.getY());
                if (norm != null) sendMouse("move", norm[0], norm[1], 1);
            }
            return true;
        });
    }

    private float[] mapTouchToNormalized(float vx, float vy) {
        if (ivScreen.getDrawable() == null) return null;
        int dw = ivScreen.getDrawable().getIntrinsicWidth();
        int dh = ivScreen.getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return null;

        float[] values = new float[9];
        ivScreen.getImageMatrix().getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        float rw = dw * scaleX;
        float rh = dh * scaleY;
        float nx = (vx - transX) / rw;
        float ny = (vy - transY) / rh;
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null;
        return new float[]{nx, ny};
    }

    private void startStreaming() {
        connected = true;
        btnConnect.setText("Disconnect");
        btnConnect.setOnClickListener(v -> {
            connected = false;
            btnConnect.setText("Connect");
            btnConnect.setOnClickListener(v2 -> showConnectDialog());
            tvStatus.setText("Disconnected");
        });

        // Screen stream loop
        netExec.execute(() -> {
            while (connected) {
                try {
                    HttpURLConnection conn = openConn("/api/screenshot", "GET");
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        is.close();
                        if (bmp != null) {
                            uiHandler.post(() -> ivScreen.setImageBitmap(bmp));
                        }
                    }
                    conn.disconnect();
                    Thread.sleep(130);
                } catch (Exception e) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
        });

        // Status polling loop
        netExec.execute(() -> {
            while (connected) {
                try {
                    String resp = httpGet("/api/status");
                    JSONObject obj = new JSONObject(resp);
                    String txt = obj.optString("hostname", "Linux")
                            + " | RAM: " + obj.optInt("ram_used_mb") + "/" + obj.optInt("ram_total_mb") + "MB"
                            + " | Load: " + obj.optString("load_1m");
                    uiHandler.post(() -> tvStatus.setText(txt));
                } catch (Exception e) {
                    uiHandler.post(() -> tvStatus.setText("Connecting to " + baseUrl + "..."));
                }
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        });
    }

    private void showConnectDialog() {
        SharedPreferences prefs = getSharedPreferences("agentlink", MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 12);

        EditText etHost = new EditText(this);
        etHost.setHint("Linux IP or Hostname (e.g. 192.168.1.50)");
        etHost.setText(prefs.getString("host", ""));
        layout.addView(etHost);

        EditText etPort = new EditText(this);
        etPort.setHint("Port (default 8765)");
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPort.setText(prefs.getString("port", "8765"));
        layout.addView(etPort);

        EditText etToken = new EditText(this);
        etToken.setHint("Auth Token (optional)");
        etToken.setText(prefs.getString("token", ""));
        layout.addView(etToken);

        new AlertDialog.Builder(this)
                .setTitle("Connect to NanoDesk OS")
                .setView(layout)
                .setPositiveButton("Connect", (d, w) -> {
                    String host = etHost.getText().toString().trim();
                    String port = etPort.getText().toString().trim();
                    if (port.isEmpty()) port = "8765";
                    authToken = etToken.getText().toString().trim();
                    prefs.edit().putString("host", host).putString("port", port).putString("token", authToken).apply();
                    baseUrl = "http://" + host + ":" + port;
                    startStreaming();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOverlayDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 12);

        EditText etTitle = new EditText(this);
        etTitle.setHint("Title (e.g. Message from Phone)");
        etTitle.setText("AgentLink");
        layout.addView(etTitle);

        EditText etMsg = new EditText(this);
        etMsg.setHint("Overlay message to display on Linux screen...");
        layout.addView(etMsg);

        Spinner spLevel = new Spinner(this);
        String[] levels = {"info", "success", "warning", "urgent"};
        spLevel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levels));
        layout.addView(spLevel);

        new AlertDialog.Builder(this)
                .setTitle("Send Screen Overlay")
                .setView(layout)
                .setPositiveButton("Send", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("title", etTitle.getText().toString());
                        body.put("message", etMsg.getText().toString());
                        body.put("level", levels[spLevel.getSelectedItemPosition()]);
                        body.put("duration", 10);
                        postJson("/api/overlay", body);
                        toast("Overlay sent!");
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showKeyboardDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 12);

        EditText etText = new EditText(this);
        etText.setHint("Type text to send...");
        layout.addView(etText);

        String[] quickKeys = {"Return", "Escape", "Tab", "BackSpace", "ctrl+c", "ctrl+v", "alt+Tab", "Super_L"};
        Spinner spKey = new Spinner(this);
        spKey.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, quickKeys));
        layout.addView(spKey);

        new AlertDialog.Builder(this)
                .setTitle("Keyboard Input")
                .setView(layout)
                .setPositiveButton("Send Text", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("text", etText.getText().toString());
                        postJson("/api/input/key", body);
                    } catch (Exception ignored) {}
                })
                .setNeutralButton("Send Key", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("key", quickKeys[spKey.getSelectedItemPosition()]);
                        postJson("/api/input/key", body);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClipboardDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clipboard Sync")
                .setItems(new String[]{"Push Phone -> Linux", "Pull Linux -> Phone"}, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                            String txt = String.valueOf(cm.getPrimaryClip().getItemAt(0).getText());
                            try {
                                JSONObject body = new JSONObject();
                                body.put("text", txt);
                                postJson("/api/clipboard", body);
                                toast("Pushed to Linux clipboard");
                            } catch (Exception ignored) {}
                        }
                    } else {
                        netExec.execute(() -> {
                            try {
                                String resp = httpGet("/api/clipboard");
                                String txt = new JSONObject(resp).optString("text", "");
                                uiHandler.post(() -> {
                                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    if (cm != null) {
                                        cm.setPrimaryClip(ClipData.newPlainText("NanoDesk", txt));
                                        toast("Copied from Linux!");
                                    }
                                });
                            } catch (Exception e) {
                                uiHandler.post(() -> toast("Clipboard pull failed"));
                            }
                        });
                    }
                })
                .show();
    }

    private void showShellDialog() {
        EditText etCmd = new EditText(this);
        etCmd.setHint("e.g. free -m && df -h");
        new AlertDialog.Builder(this)
                .setTitle("Run Shell Command")
                .setView(etCmd)
                .setPositiveButton("Run", (d, w) -> {
                    String cmd = etCmd.getText().toString();
                    netExec.execute(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("cmd", cmd);
                            String resp = httpPost("/api/exec", body.toString());
                            JSONObject res = new JSONObject(resp);
                            String out = res.optString("stdout", "") + "\n" + res.optString("stderr", "");
                            uiHandler.post(() -> new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Output (exit " + res.optInt("returncode") + ")")
                                    .setMessage(out.trim())
                                    .setPositiveButton("OK", null)
                                    .show());
                        } catch (Exception e) {
                            uiHandler.post(() -> toast("Exec failed: " + e.getMessage()));
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendAction(String action) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", action);
            postJson("/api/action", body);
            toast("Launched " + action);
        } catch (Exception ignored) {}
    }

    private void sendMouse(String action, float nx, float ny, int button) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", action);
            body.put("x", nx);
            body.put("y", ny);
            body.put("button", button);
            postJson("/api/input/mouse", body);
        } catch (Exception ignored) {}
    }

    private void postJson(String path, JSONObject body) {
        netExec.execute(() -> {
            try {
                httpPost(path, body.toString());
            } catch (Exception ignored) {}
        });
    }

    private HttpURLConnection openConn(String path, String method) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        if (!authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        return conn;
    }

    private String httpGet(String path) throws Exception {
        HttpURLConnection conn = openConn(path, "GET");
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    private String httpPost(String path, String json) throws Exception {
        HttpURLConnection conn = openConn(path, "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.close();
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    private void toast(String msg) {
        uiHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        connected = false;
        netExec.shutdownNow();
        super.onDestroy();
    }
}
