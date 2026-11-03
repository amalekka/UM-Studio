package com.example.core.streaming;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.Level;
import org.json.JSONArray;
import java.io.FileOutputStream;

public class NativeRtmpBridge {
    private static final String TAG = "NativeRtmpBridge";
    private final Context context;
    private final WebView webView;
    private String activePipePath = null;
    private FileOutputStream pipeOutputStream = null;

    public NativeRtmpBridge(Context context) {
        this.context = context;
        this.webView = null;
        initBridge();
    }

    public NativeRtmpBridge(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        initBridge();
    }

    private void initBridge() {
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_INFO);
        FFmpegKitConfig.enableStatisticsCallback(statistics -> {
            int kbps = (int) statistics.getBitrate();
            int fps = (int) statistics.getVideoFps();
            Log.d(TAG, "Stats: " + kbps + " kbps, " + fps + " fps");
            if (webView != null) {
                webView.post(() -> {
                    webView.evaluateJavascript("window.onNativeStreamStats && window.onNativeStreamStats(" + kbps + ", " + fps + ");", null);
                });
            }
        });
    }

    @JavascriptInterface
    public void startM3u8Restream(String m3u8Url, String bgBase64, String rtmpUrlsJson, int bitrate, int fps, int width, int height) {
        Log.i(TAG, "startM3u8Restream: " + m3u8Url);
        try {
            JSONArray urls = new JSONArray(rtmpUrlsJson);
            if (urls.length() == 0) return;
            String rtmpUrl = urls.getString(0);

            String inputOpt = "-y -i \"" + m3u8Url + "\"";
            if (bgBase64 != null && !bgBase64.isEmpty()) {
                inputOpt = "-y -i \"" + m3u8Url + "\" -i data:image/png;base64," + bgBase64;
            }

            String cmd = inputOpt + " -c:v libx264 -preset ultrafast -tune zerolatency -b:v " + bitrate + " -r " + fps + " -s " + width + "x" + height + " -c:a aac -f flv \"" + rtmpUrl + "\"";
            FFmpegKit.executeAsync(cmd, session -> {
                Log.i(TAG, "M3U8 Restream session completed with state: " + session.getState());
            }, log -> {
                Log.d(TAG, "FFmpeg log: " + log.getMessage());
            }, statistics -> {});
        } catch (Exception e) {
            Log.e(TAG, "Error starting M3u8 restream", e);
        }
    }

    @JavascriptInterface
    public void startLivePipe(String rtmpUrlsJson, int width, int height, int fps, int bitrate) {
        Log.i(TAG, "startLivePipe requested");
        try {
            JSONArray urls = new JSONArray(rtmpUrlsJson);
            if (urls.length() == 0) return;
            String rtmpUrl = urls.getString(0);

            activePipePath = FFmpegKitConfig.registerNewFFmpegPipe(context);
            if (activePipePath != null) {
                pipeOutputStream = new FileOutputStream(activePipePath);
            }

            String cmd = "-y -f webm -i \"" + activePipePath + "\" -c:v libx264 -preset ultrafast -tune zerolatency -b:v " + bitrate + " -r " + fps + " -s " + width + "x" + height + " -c:a aac -f flv \"" + rtmpUrl + "\"";
            FFmpegKit.executeAsync(cmd, session -> {
                Log.i(TAG, "Live pipe session completed");
            }, log -> {
                Log.d(TAG, "FFmpeg pipe log: " + log.getMessage());
            }, statistics -> {});
        } catch (Exception e) {
            Log.e(TAG, "Error starting live pipe", e);
        }
    }

    @JavascriptInterface
    public void pushVideoChunk(String base64Chunk) {
        if (pipeOutputStream != null) {
            try {
                byte[] data = Base64.decode(base64Chunk, Base64.DEFAULT);
                pipeOutputStream.write(data);
                pipeOutputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "Error writing to pipe stream", e);
            }
        }
    }

    @JavascriptInterface
    public void stopStream() {
        Log.i(TAG, "stopStream called");
        try {
            FFmpegKit.cancel();
            if (pipeOutputStream != null) {
                try { pipeOutputStream.close(); } catch (Exception ignored) {}
                pipeOutputStream = null;
            }
            if (activePipePath != null) {
                FFmpegKitConfig.closeFFmpegPipe(activePipePath);
                activePipePath = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping stream", e);
        }
    }
}
