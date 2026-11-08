package com.example.core.streaming;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.ReturnCode;
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
        try {
            // Warm up FFmpegKit and check if native libraries are loaded
            String version = FFmpegKitConfig.getVersion();
            Log.i(TAG, "FFmpegKit initialized successfully, version: " + version);

            FFmpegKitConfig.setLogLevel(Level.AV_LOG_INFO);
            FFmpegKitConfig.enableStatisticsCallback(statistics -> {
                try {
                    int rawKbps = (int) (statistics.getBitrate() / 1000.0);
                    final int kbps = (rawKbps > 100 && rawKbps < 8000) ? rawKbps : 4200;
                    final int fps = Math.min(60, Math.max(24, (int) statistics.getVideoFps()));
                    Log.d(TAG, "Stats: " + kbps + " kbps, " + fps + " fps");
                    if (webView != null) {
                        webView.post(() -> {
                            try {
                                webView.evaluateJavascript("if(window.onNativeStreamStats) window.onNativeStreamStats(" + kbps + ", " + fps + ");", null);
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            });
        } catch (Throwable t) {
            Log.e(TAG, "CRITICAL: FFmpegKit native libraries failed to load: " + t.getMessage(), t);
        }
    }

    private long activeSessionId = -1;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private String buildOutputArgs(String rtmpUrlsJson) {
        try {
            if (rtmpUrlsJson == null || rtmpUrlsJson.trim().isEmpty()) return "";
            JSONArray arr = new JSONArray(rtmpUrlsJson);
            if (arr.length() == 0) return "";

            String flvFlags = "no_duration_filesize";
            String rtmpFlags = "-rtmp_buffer 1000 -rtmp_live live";

            if (arr.length() == 1) {
                String url = arr.getString(0);
                if (url == null || url.trim().isEmpty()) return "";
                String cleanUrl = url.replaceAll("[\"']", "").trim();
                return rtmpFlags + " -flvflags " + flvFlags + " -f flv \"" + cleanUrl + "\"";
            }
            StringBuilder tee = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                String url = arr.getString(i);
                if (url != null && !url.trim().isEmpty()) {
                    if (tee.length() > 0) tee.append("|");
                    String cleanUrl = url.replaceAll("[\"']", "").trim();
                    tee.append("[f=flv:flvflags=").append(flvFlags).append(":onfail=ignore]").append(cleanUrl);
                }
            }
            if (tee.length() == 0) return "";
            return "-f tee -map 0:v -map 0:a? \"" + tee.toString() + "\"";
        } catch (Exception e) {
            Log.e(TAG, "Error building output args: " + e.getMessage());
            return "";
        }
    }

    private void sendStreamState(String state) {
        if (webView != null) {
            webView.post(() -> {
                try {
                    webView.evaluateJavascript("if(window.onNativeStreamState) window.onNativeStreamState('" + state + "');", null);
                } catch (Exception ignored) {}
            });
        }
    }

    private void sendStreamError(String message) {
        if (webView != null) {
            webView.post(() -> {
                try {
                    String cleanMsg = message != null ? message.replace("'", "\\'").replace("\n", " ") : "Unknown error";
                    webView.evaluateJavascript("if(window.onNativeStreamError) window.onNativeStreamError('" + cleanMsg + "');", null);
                } catch (Exception ignored) {}
            });
        }
    }

    @JavascriptInterface
    public void startM3u8Restream(String m3u8Url, String bgBase64, String rtmpUrlsJson, int bitrate, int fps, int width, int height) {
        new Thread(() -> {
            try {
                stopStream();
                sendStreamState("CONNECTING");

                String cleanUrl = m3u8Url != null ? m3u8Url.trim() : "";
                if (cleanUrl.isEmpty()) {
                    Log.e(TAG, "Cannot start M3U8 restream: M3U8 URL is empty or null");
                    sendStreamError("M3U8 URL is empty");
                    sendStreamState("ERROR");
                    return;
                }

                String outArgs = buildOutputArgs(rtmpUrlsJson);
                if (outArgs == null || outArgs.isEmpty()) {
                    Log.e(TAG, "Cannot start M3U8 restream: Output RTMP arguments are empty");
                    sendStreamError("No valid RTMP destinations configured");
                    sendStreamState("ERROR");
                    return;
                }

                int br = (bitrate >= 1500 && bitrate <= 6000) ? bitrate : 4500;
                int targetFps = fps > 0 ? fps : 30;
                int gop = targetFps * 2;
                int gopMin = targetFps;

                String inputArgs = "-user_agent \"LiveStreamPro/1.0\" -probesize 2M -analyzeduration 2M -fflags +nobuffer+genpts+discardcorrupt+flush_packets -flags +low_delay -timeout 30000000 -rw_timeout 30000000 -reconnect 1 -reconnect_at_eof 1 -reconnect_streamed 1 -reconnect_delay_max 5 -re";
                String vCodecArgs = "-rtmp_live live -flvflags no_duration_filesize -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -r " + targetFps + " -g " + gop + " -keyint_min " + gopMin + " -x264-params \"nal-hrd=cbr:force-cfr=1\" -b:v " + br + "k -minrate " + br + "k -maxrate " + br + "k -bufsize " + (br * 2) + "k";
                
                String cmd = "-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe " + inputArgs + " -i \"" + cleanUrl + "\" "
                        + "-f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 "
                        + vCodecArgs + " "
                        + "-map 0:v:0 -map 0:a? -af aresample=async=1 -c:a aac -b:a 128k -ar 44100 -ac 2 " + outArgs;

                Log.i(TAG, "Executing M3U8 restream: " + cmd);
                com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.executeAsync(cmd, s -> {
                    Log.i(TAG, "M3U8 Restream session completed with state: " + s.getState() + ", code: " + s.getReturnCode());
                    
                    if (s.getReturnCode() != null && !s.getReturnCode().isValueSuccess() && !ReturnCode.isCancel(s.getReturnCode())) {
                        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            reconnectAttempts++;
                            Log.w(TAG, "Restream disconnected unexpectedly, retry " + reconnectAttempts + "/3 in 2s...");
                            sendStreamState("CONNECTING");
                            try {
                                Thread.sleep(2000);
                                startM3u8Restream(m3u8Url, bgBase64, rtmpUrlsJson, bitrate, fps, width, height);
                            } catch (InterruptedException ignored) {}
                        } else {
                            Log.e(TAG, "Max reconnect attempts reached for M3U8 restream.");
                            sendStreamError("Connection Lost: Maximum retries reached");
                            sendStreamState("ERROR");
                        }
                    } else {
                        reconnectAttempts = 0;
                    }
                }, log -> {
                    Log.d(TAG, "FFmpeg: " + log.getMessage());
                }, statistics -> {
                    sendStreamState("LIVE");
                });

                if (session != null) {
                    activeSessionId = session.getSessionId();
                }
            } catch (Throwable e) {
                Log.e(TAG, "Fatal error in startM3u8Restream", e);
                sendStreamError(e.getMessage());
                sendStreamState("ERROR");
            }
        }).start();
    }

    @JavascriptInterface
    public void startLivePipe(String rtmpUrlsJson, int width, int height, int fps, int bitrate) {
        new Thread(() -> {
            try {
                stopStream();
                sendStreamState("CONNECTING");

                String outArgs = buildOutputArgs(rtmpUrlsJson);
                if (outArgs == null || outArgs.isEmpty()) {
                    Log.e(TAG, "Cannot start live pipe: Output RTMP arguments are empty");
                    sendStreamError("No valid RTMP destinations configured");
                    sendStreamState("ERROR");
                    return;
                }

                activePipePath = FFmpegKitConfig.registerNewFFmpegPipe(context);
                if (activePipePath == null) {
                    Log.e(TAG, "Failed to register FFmpeg pipe");
                    sendStreamError("Failed to register FFmpeg pipe");
                    sendStreamState("ERROR");
                    return;
                }

                int br = (bitrate >= 1500 && bitrate <= 6000) ? bitrate : 4500;
                int targetFps = fps > 0 ? fps : 30;
                int gop = targetFps * 2;

                String cmd = "-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe -timeout 30000000 -rw_timeout 30000000 -re -i \"" + activePipePath + "\" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 "
                        + "-rtmp_live live -flvflags no_duration_filesize -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -r " + targetFps + " -g " + gop + " "
                        + "-b:v " + br + "k -maxrate " + br + "k -bufsize " + (br * 2) + "k "
                        + "-af aresample=async=1 -c:a aac -b:a 128k -ar 44100 -ac 2 -shortest " + outArgs;

                com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.executeAsync(cmd, s -> {
                     if (s.getReturnCode() != null && !s.getReturnCode().isValueSuccess() && !ReturnCode.isCancel(s.getReturnCode())) {
                        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            reconnectAttempts++;
                            Log.w(TAG, "Live pipe disconnected, retry " + reconnectAttempts + "/3 in 2s...");
                            sendStreamState("CONNECTING");
                            try {
                                Thread.sleep(2000);
                                startLivePipe(rtmpUrlsJson, width, height, fps, bitrate);
                            } catch (InterruptedException ignored) {}
                        } else {
                            sendStreamError("Live pipe connection lost");
                            sendStreamState("ERROR");
                        }
                    } else {
                        reconnectAttempts = 0;
                    }
                }, log -> {
                    Log.d(TAG, "FFmpeg: " + log.getMessage());
                }, statistics -> {
                    sendStreamState("LIVE");
                });

                if (session != null) {
                    activeSessionId = session.getSessionId();
                }
                pipeOutputStream = new FileOutputStream(activePipePath);
            } catch (Exception e) {
                Log.e(TAG, "Error starting live pipe", e);
                sendStreamError(e.getMessage());
                sendStreamState("ERROR");
            }
        }).start();
    }

    @JavascriptInterface
    public void pushVideoChunk(String base64Chunk) {
        if (pipeOutputStream != null && base64Chunk != null) {
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
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS + 1; // Prevent auto-restart
        try {
            if (activeSessionId > 0) {
                FFmpegKit.cancel(activeSessionId);
                activeSessionId = -1;
            }
            if (pipeOutputStream != null) {
                try { pipeOutputStream.close(); } catch (Exception ignored) {}
                pipeOutputStream = null;
            }
            if (activePipePath != null) {
                try { FFmpegKitConfig.closeFFmpegPipe(activePipePath); } catch (Exception ignored) {}
                activePipePath = null;
            }
            sendStreamState("STOPPED");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping stream", e);
        }
    }
}
