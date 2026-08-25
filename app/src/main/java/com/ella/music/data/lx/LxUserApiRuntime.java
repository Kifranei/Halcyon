package com.ella.music.data.lx;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.ella.music.data.model.Song;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class LxUserApiRuntime implements AutoCloseable {
    private static final String TAG = "LxUserApiRuntime";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Halcyon/1.0";

    private final Context context;
    private final OkHttpClient client;
    private final String key = UUID.randomUUID().toString();
    private QuickJSContext jsContext;
    private JSONObject initInfo;
    private JSONObject requestResponse;
    private String lastError;
    private final PriorityQueue<PendingTimeout> pendingTimeouts =
            new PriorityQueue<>(Comparator.comparingLong(timeout -> timeout.runAtMs));
    private final ConcurrentHashMap<String, Call> activeRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> pendingJsActions = new ConcurrentLinkedQueue<>();

    public LxUserApiRuntime(Context context, OkHttpClient client) {
        this.context = context.getApplicationContext();
        this.client = client;
    }

    public JSONObject load(String script, String id, String name, String url) throws Exception {
        QuickJSLoader.init();
        jsContext = QuickJSContext.create();
        jsContext.setConsole(new QuickJSContext.Console() {
            @Override
            public void log(String message) {
                Log.d(TAG, message);
            }

            @Override
            public void info(String message) {
                Log.i(TAG, message);
            }

            @Override
            public void warn(String message) {
                Log.w(TAG, message);
            }

            @Override
            public void error(String message) {
                Log.e(TAG, message);
            }
        });
        createEnv();
        jsContext.evaluate(readPreloadScript());
        jsContext.getGlobalObject().getJSFunction("lx_setup").call(
                key,
                id == null ? "" : id,
                name == null || name.isEmpty() ? "LX源" : name,
                url == null ? "" : url,
                "",
                "",
                url == null ? "" : url,
                script
        );
        jsContext.evaluate(script);
        waitFor(() -> initInfo != null, 8_000L);
        if (initInfo == null) {
            throw new IllegalStateException(lastError != null ? lastError : "源未调用 lx.send(EVENT_NAMES.inited)");
        }
        if (!initInfo.optBoolean("status")) {
            throw new IllegalStateException(initInfo.optString("errorMessage", "源初始化失败"));
        }
        return initInfo.optJSONObject("info");
    }

    public String requestMusicUrl(LxOnlineSong item, String script, String sourceName) throws Exception {
        JSONObject info = load(script, sourceName, sourceName, "");
        JSONObject sources = info == null ? null : info.optJSONObject("sources");
        JSONObject source = sources == null ? null : sources.optJSONObject(item.getSource());
        if (source == null) {
            throw new IllegalStateException("当前源不支持 " + item.getSource());
        }
        if (!contains(source.optJSONArray("actions"), "musicUrl")) {
            throw new IllegalStateException("当前源不支持播放地址解析");
        }
        String quality = bestQuality(source.optJSONArray("qualitys"), item.getQuality());
        requestResponse = null;
        lastError = null;

        JSONObject request = new JSONObject()
                .put("requestKey", "ella_" + System.nanoTime())
                .put("data", new JSONObject()
                        .put("source", item.getSource())
                        .put("action", "musicUrl")
                        .put("info", new JSONObject()
                                .put("type", quality)
                                .put("musicInfo", buildMusicInfo(item.getSong(), item.getSource(), item.getSongmid(), quality))));

        callJs("request", request.toString());
        waitFor(() -> requestResponse != null || lastError != null, 25_000L);
        if (requestResponse == null) {
            throw new IllegalStateException(lastError != null ? lastError : "源没有返回播放地址");
        }
        if (!requestResponse.optBoolean("status")) {
            throw new IllegalStateException(requestResponse.optString("errorMessage", "源解析失败"));
        }
        JSONObject result = requestResponse.optJSONObject("result");
        JSONObject data = result == null ? null : result.optJSONObject("data");
        String url = data == null ? "" : data.optString("url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalStateException("源返回的播放地址无效");
        }
        return url;
    }

    private JSONObject buildMusicInfo(Song song, String source, String songmid, String quality) throws Exception {
        String interval = formatDuration(song.getDuration());
        JSONObject qualityInfo = new JSONObject().put("type", quality).put("size", JSONObject.NULL);
        JSONArray qualitys = new JSONArray().put(qualityInfo);
        JSONObject qualityMap = new JSONObject().put(quality, new JSONObject().put("size", JSONObject.NULL));
        JSONObject meta = new JSONObject()
                .put("songId", songmid)
                .put("albumName", song.getAlbum())
                .put("picUrl", song.getCoverUrl().isEmpty() ? JSONObject.NULL : song.getCoverUrl())
                .put("qualitys", qualitys)
                .put("_qualitys", qualityMap);

        return new JSONObject()
                .put("id", source + "_" + songmid)
                .put("name", song.getTitle())
                .put("singer", song.getArtist())
                .put("source", source)
                .put("songmid", songmid)
                .put("albumName", song.getAlbum())
                .put("interval", interval)
                .put("types", qualitys)
                .put("_types", qualityMap)
                .put("typeUrl", new JSONObject())
                .put("meta", meta);
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void createEnv() {
        jsContext.getGlobalObject().setProperty("__lx_native_call__", args -> {
            if (key.equals(String.valueOf(args[0]))) {
                callNative(String.valueOf(args[1]), String.valueOf(args[2]));
            }
            return null;
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__set_timeout", args -> {
            long delayMs = Math.max(0L, Long.parseLong(String.valueOf(args[1])));
            pendingTimeouts.add(new PendingTimeout(String.valueOf(args[0]), System.currentTimeMillis() + delayMs));
            return null;
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_str2b64", args ->
                Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_buf2b64", args ->
                safeString(() -> byteArrayToBase64(String.valueOf(args[0]))));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_b642buf", args -> {
            byte[] bytes = Base64.decode(String.valueOf(args[0]), Base64.NO_WRAP);
            JSONArray array = new JSONArray();
            for (byte b : bytes) array.put(b & 0xff);
            return array.toString();
        });
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_str2md5", args ->
                safeString(() -> md5(String.valueOf(args[0]))));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_aes_encrypt", args ->
                safeString(() -> aesEncrypt(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]), String.valueOf(args[3]))));
        jsContext.getGlobalObject().setProperty("__lx_native_call__utils_rsa_encrypt", args ->
                safeString(() -> rsaEncrypt(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
    }

    private void callNative(String action, String data) {
        try {
            switch (action) {
                case "init":
                    initInfo = new JSONObject(data);
                    break;
                case "request":
                    handleScriptHttpRequest(new JSONObject(data));
                    break;
                case "response":
                    requestResponse = new JSONObject(data);
                    break;
                case "showUpdateAlert":
                    break;
                case "cancelRequest":
                    cancelScriptRequest(data);
                    break;
                default:
                    Log.d(TAG, "Unknown script action: " + action);
                    break;
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            Log.w(TAG, "Script action failed: " + action, e);
        }
    }

    private void handleScriptHttpRequest(JSONObject data) throws Exception {
        String requestKey = data.optString("requestKey");
        String url = data.optString("url");
        JSONObject options = data.optJSONObject("options");
        if (options == null) options = new JSONObject();

        Request.Builder builder = new Request.Builder().url(url);
        JSONObject headers = options.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String header = keys.next();
                String value = headers.optString(header);
                if (!value.isEmpty()) builder.header(header, value);
            }
        }
        if (headerValue(headers, "User-Agent").isEmpty()) {
            builder.header("User-Agent", USER_AGENT);
        }
        if (headerValue(headers, "Accept").isEmpty()) {
            builder.header("Accept", "*/*");
        }

        String method = options.optString("method", "get").toUpperCase(Locale.US);
        RequestBody body = buildRequestBody(options);
        if ("GET".equals(method)) builder.get();
        else builder.method(method, body != null ? body : RequestBody.create(new byte[0]));

        final JSONObject requestOptions = options;
        final Call call = client.newCall(builder.build());
        long timeoutMs = options.optLong("timeout", 0L);
        if (timeoutMs > 0L) {
            call.timeout().timeout(Math.min(timeoutMs, 60_000L), TimeUnit.MILLISECONDS);
        }
        activeRequests.put(requestKey, call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException error) {
                activeRequests.remove(requestKey, failedCall);
                enqueueHttpResponse(requestKey, null, requestOptions, error);
            }

            @Override
            public void onResponse(Call completedCall, Response response) {
                activeRequests.remove(requestKey, completedCall);
                try (Response closeableResponse = response) {
                    enqueueHttpResponse(requestKey, closeableResponse, requestOptions, null);
                } catch (Exception error) {
                    enqueueHttpResponse(requestKey, null, requestOptions, error);
                }
            }
        });
    }

    /**
     * OkHttp callbacks run off the QuickJS owner thread. Queue the result and let waitFor pump it
     * on that owner thread; entering QuickJS recursively from the native request callback breaks
     * promise-heavy and encrypted LX sources.
     */
    private void enqueueHttpResponse(
            String requestKey,
            Response response,
            JSONObject options,
            Exception error
    ) {
        try {
            JSONObject responseData = new JSONObject().put("requestKey", requestKey);
            if (error != null || response == null) {
                String message = error == null ? "Request failed" : error.getMessage();
                responseData.put("error", message == null ? error.getClass().getSimpleName() : message)
                        .put("response", JSONObject.NULL);
            } else {
                JSONObject headersJson = new JSONObject();
                for (String name : response.headers().names()) {
                    headersJson.put(name, response.header(name));
                }
                Object bodyValue;
                if (options.optBoolean("binary")) {
                    byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
                    JSONArray array = new JSONArray();
                    for (byte value : bytes) array.put(value & 0xff);
                    bodyValue = array;
                } else {
                    String bodyString = response.body() == null ? "" : response.body().string();
                    bodyValue = parseJsonBodyOrString(bodyString);
                }
                responseData
                        .put("error", JSONObject.NULL)
                        .put("response", new JSONObject()
                                .put("statusCode", response.code())
                                .put("statusMessage", response.message())
                                .put("headers", headersJson)
                                .put("url", response.request().url().toString())
                                .put("ok", response.isSuccessful())
                                .put("body", bodyValue));
            }
            final String payload = responseData.toString();
            pendingJsActions.add(() -> callJs("response", payload));
        } catch (Exception buildError) {
            final String message = buildError.getMessage();
            pendingJsActions.add(() -> lastError = message == null ? "Failed to decode source response" : message);
        }
    }

    private Object parseJsonBodyOrString(String body) {
        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return body;
        try {
            return new JSONTokener(trimmed).nextValue();
        } catch (Exception ignored) {
            return body;
        }
    }

    private void cancelScriptRequest(String rawData) {
        String requestKey = rawData;
        try {
            Object parsed = new JSONTokener(rawData).nextValue();
            requestKey = String.valueOf(parsed);
        } catch (Exception ignored) {
            // Older preload variants sent the key as a raw string.
        }
        Call call = activeRequests.remove(requestKey);
        if (call != null) call.cancel();
    }

    private RequestBody buildRequestBody(JSONObject options) {
        if (options.has("form")) {
            JSONObject form = options.optJSONObject("form");
            FormBody.Builder builder = new FormBody.Builder();
            if (form != null) {
                Iterator<String> keys = form.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    builder.add(key, form.optString(key));
                }
            }
            return builder.build();
        }
        if (options.has("body")) {
            Object body = options.opt("body");
            if (body == null || body == JSONObject.NULL) return null;
            String contentType = headerValue(options.optJSONObject("headers"), "Content-Type");
            if (contentType.isEmpty()) contentType = "application/json";
            return RequestBody.create(body.toString(), MediaType.parse(contentType));
        }
        if (options.has("formData")) {
            Object body = options.opt("formData");
            if (body == null || body == JSONObject.NULL) return null;
            if (body instanceof JSONObject) {
                MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
                JSONObject fields = (JSONObject) body;
                Iterator<String> keys = fields.keys();
                while (keys.hasNext()) {
                    String field = keys.next();
                    multipart.addFormDataPart(field, fields.optString(field));
                }
                return multipart.build();
            }
            return RequestBody.create(body.toString(), MediaType.parse("multipart/form-data"));
        }
        return null;
    }

    private String headerValue(JSONObject headers, String name) {
        if (headers == null) return "";
        Iterator<String> keys = headers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (name.equalsIgnoreCase(key)) return headers.optString(key);
        }
        return "";
    }

    private Object callJs(String action, String data) {
        return jsContext.getGlobalObject().getJSFunction("__lx_native__").call(key, action, data);
    }

    private void waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            boolean didWork = runNextJsAction();
            didWork = runNextDueTimeout() || didWork;
            if (!didWork) Thread.sleep(Math.min(8L, Math.max(1L, deadline - System.currentTimeMillis())));
        }
    }

    private boolean runNextJsAction() {
        Runnable action = pendingJsActions.poll();
        if (action == null) return false;
        action.run();
        return true;
    }

    private boolean runNextDueTimeout() throws Exception {
        PendingTimeout timeout = pendingTimeouts.peek();
        if (timeout == null) return false;
        if (System.currentTimeMillis() < timeout.runAtMs) return false;
        pendingTimeouts.poll();
        callJs("__set_timeout__", timeout.id);
        return true;
    }

    private String readPreloadScript() throws Exception {
        try (InputStream input = context.getAssets().open("script/user-api-preload.js")) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    private boolean contains(JSONArray array, String value) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }

    private String bestQuality(JSONArray available, String requested) {
        if (contains(available, requested)) return requested;
        String[] preference = new String[]{"flac24bit", "flac", "320k", "128k"};
        for (String quality : preference) {
            if (contains(available, quality)) return quality;
        }
        return requested;
    }

    private String md5(String input) throws Exception {
        String decoded = URLDecoder.decode(input, "UTF-8");
        byte[] digest = MessageDigest.getInstance("MD5").digest(decoded.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private String byteArrayToBase64(String json) throws Exception {
        JSONArray values = new JSONArray(json);
        byte[] bytes = new byte[values.length()];
        for (int index = 0; index < values.length(); index++) {
            bytes[index] = (byte) values.getInt(index);
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private interface ThrowingStringSupplier {
        String get() throws Exception;
    }

    private String safeString(ThrowingStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            Log.w(TAG, "Native util failed", e);
            return "";
        }
    }

    private String aesEncrypt(String dataB64, String keyB64, String ivB64, String transformation) throws Exception {
        byte[] data = Base64.decode(dataB64, Base64.NO_WRAP);
        byte[] keyBytes = Base64.decode(keyB64, Base64.NO_WRAP);
        String normalizedTransformation = "AES".equals(transformation)
                ? "AES/ECB/NoPadding"
                : transformation.replace("PKCS7Padding", "PKCS5Padding");
        Cipher cipher = Cipher.getInstance(normalizedTransformation);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        if (transformation.contains("/CBC/")) {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(Base64.decode(ivB64, Base64.NO_WRAP)));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP);
    }

    private String rsaEncrypt(String dataB64, String publicKeyB64, String transformation) throws Exception {
        byte[] keyBytes = Base64.decode(publicKeyB64, Base64.NO_WRAP);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom());
        return Base64.encodeToString(cipher.doFinal(Base64.decode(dataB64, Base64.NO_WRAP)), Base64.NO_WRAP);
    }

    @Override
    public void close() {
        for (Call call : activeRequests.values()) call.cancel();
        activeRequests.clear();
        pendingJsActions.clear();
        pendingTimeouts.clear();
        if (jsContext != null) {
            jsContext.destroy();
            jsContext = null;
        }
    }

    private static final class PendingTimeout {
        final String id;
        final long runAtMs;

        PendingTimeout(String id, long runAtMs) {
            this.id = id;
            this.runAtMs = runAtMs;
        }
    }
}
