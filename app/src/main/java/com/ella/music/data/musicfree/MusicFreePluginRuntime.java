package com.ella.music.data.musicfree;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class MusicFreePluginRuntime implements AutoCloseable {
    private static final String TAG = "MusicFreeRuntime";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0";

    private final OkHttpClient client;
    private final Context context;
    private QuickJSContext jsContext;
    private String callResult;

    public MusicFreePluginRuntime(Context context, OkHttpClient client) {
        this.client = client;
        this.context = context;
    }

    public JSONObject search(String script, String keyword, int page) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_search").call(keyword, page);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("搜索失败");
        JSONObject value = result.optJSONObject("value");
        if (value != null) return value;
        JSONArray data = result.optJSONArray("value");
        return new JSONObject().put("data", data == null ? new JSONArray() : data);
    }

    public JSONObject getMediaSource(String script, String musicItemJson, String quality) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_media_source").call(musicItemJson, quality);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("解析播放地址失败");
        JSONObject value = result.optJSONObject("value");
        return value == null ? new JSONObject() : value;
    }

    public JSONObject getLyric(String script, String musicItemJson) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_lyric").call(musicItemJson);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("获取歌词失败");
        JSONObject value = result.optJSONObject("value");
        return value == null ? new JSONObject() : value;
    }

    public JSONObject inspectPlugin(String script) throws Exception {
        load(script);
        Object result = jsContext.evaluate("JSON.stringify({"
                + "platform:(__mf_plugin&&__mf_plugin.platform)||'',"
                + "name:(__mf_plugin&&__mf_plugin.name)||'',"
                + "author:(__mf_plugin&&__mf_plugin.author)||'',"
                + "hasSearch:typeof(__mf_plugin&&__mf_plugin.search)==='function',"
                + "hasMediaSource:typeof(__mf_plugin&&__mf_plugin.getMediaSource)==='function',"
                + "hasImportMusicItem:typeof(__mf_plugin&&__mf_plugin.importMusicItem)==='function',"
                + "hasLyric:typeof(__mf_plugin&&__mf_plugin.getLyric)==='function'"
                + "})");
        return new JSONObject(String.valueOf(result));
    }

    private void load(String script) throws Exception {
        QuickJSLoader.init();
        jsContext = QuickJSContext.create();
        jsContext.setMemoryLimit(64 * 1024 * 1024);
        jsContext.setMaxStackSize(512 * 1024);
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
        jsContext.evaluate(prelude());
        jsContext.evaluate(compat());
        jsContext.evaluate("(function(){\n" + script + "\n;globalThis.__mf_plugin=(module.exports&&module.exports.default)||module.exports||exports;})();");
        jsContext.evaluate(bridge());
    }

    private void createEnv() {
        jsContext.getGlobalObject().setProperty("__mf_native_result", args -> {
            callResult = String.valueOf(args[0]);
            return null;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_http", args ->
                safeString(() -> executeHttp(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
        jsContext.getGlobalObject().setProperty("__mf_native_hash", args ->
                safeString(() -> hash(String.valueOf(args[0]), String.valueOf(args[1]))));
        jsContext.getGlobalObject().setProperty("__mf_native_b64", args ->
                Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        jsContext.getGlobalObject().setProperty("__mf_native_b64_decode", args ->
                new String(Base64.decode(String.valueOf(args[0]), Base64.DEFAULT), StandardCharsets.UTF_8));
        jsContext.getGlobalObject().setProperty("__mf_native_hex_to_text", args ->
                safeString(() -> hexToText(String.valueOf(args[0]))));
        jsContext.getGlobalObject().setProperty("__mf_native_hmac", args ->
                safeString(() -> hmac(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
        jsContext.getGlobalObject().setProperty("__mf_native_cipher", args ->
                safeString(() -> cipher(
                        String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]),
                        String.valueOf(args[3]), String.valueOf(args[4]))));
        jsContext.getGlobalObject().setProperty("__mf_native_random_bytes", args ->
                Base64.encodeToString(randomBytes(Integer.parseInt(String.valueOf(args[0]))), Base64.NO_WRAP));
        jsContext.getGlobalObject().setProperty("__mf_native_hash_bin", args ->
                safeString(() -> hashBytes(String.valueOf(args[0]), String.valueOf(args[1]))));
        jsContext.getGlobalObject().setProperty("__mf_native_hmac_bin", args ->
                safeString(() -> hmacBytes(String.valueOf(args[0]), String.valueOf(args[1]), String.valueOf(args[2]))));
        jsContext.getGlobalObject().setProperty("__mf_native_latin1_of", args ->
                latin1(Base64.decode(String.valueOf(args[0]), Base64.DEFAULT)));
        jsContext.getGlobalObject().setProperty("__mf_native_b64_of_latin1", args ->
                Base64.encodeToString(String.valueOf(args[0]).getBytes(StandardCharsets.ISO_8859_1), Base64.NO_WRAP));
        jsContext.getGlobalObject().setProperty("__mf_native_sleep", args -> {
            try {
                Thread.sleep(Long.parseLong(String.valueOf(args[0])));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_store_get", args -> {
            if (context == null) return "";
            String value = context.getSharedPreferences("musicfree_plugin_kv", Context.MODE_PRIVATE)
                    .getString(String.valueOf(args[0]), null);
            return value == null ? "" : value;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_store_set", args -> {
            if (context == null) return null;
            context.getSharedPreferences("musicfree_plugin_kv", Context.MODE_PRIVATE)
                    .edit()
                    .putString(String.valueOf(args[0]), String.valueOf(args[1]))
                    .apply();
            return null;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_store_delete", args -> {
            if (context == null) return null;
            context.getSharedPreferences("musicfree_plugin_kv", Context.MODE_PRIVATE)
                    .edit().remove(String.valueOf(args[0])).apply();
            return null;
        });
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[Math.max(1, Math.min(count, 4096))];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private String hashBytes(String algorithm, String dataB64) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm.replace("-", ""));
        return hex(digest.digest(Base64.decode(dataB64, Base64.DEFAULT)));
    }

    private String hmacBytes(String algorithm, String keyB64, String dataB64) throws Exception {
        Mac mac = Mac.getInstance("Hmac" + algorithm.replace("-", ""));
        mac.init(new SecretKeySpec(Base64.decode(keyB64, Base64.DEFAULT), mac.getAlgorithm()));
        return hex(mac.doFinal(Base64.decode(dataB64, Base64.DEFAULT)));
    }

    private String cipher(String op, String algorithm, String mode, String keyB64, String restJson) throws Exception {
        JSONObject rest = new JSONObject(restJson);
        byte[] key = Base64.decode(keyB64, Base64.DEFAULT);
        byte[] iv = rest.optString("iv").isEmpty() ? null : Base64.decode(rest.optString("iv"), Base64.DEFAULT);
        byte[] data = Base64.decode(rest.optString("data"), Base64.DEFAULT);
        String transform = "RC4".equals(algorithm) ? "RC4" : algorithm + "/" + mode + "/NoPadding";
        javax.crypto.Cipher engine;
        try {
            engine = javax.crypto.Cipher.getInstance(transform);
        } catch (Exception e) {
            engine = javax.crypto.Cipher.getInstance("RC4".equals(algorithm) ? "ARCFOUR" : transform);
        }
        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
        if (iv == null) {
            engine.init("encrypt".equals(op) ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE, keySpec);
        } else {
            engine.init(
                    "encrypt".equals(op) ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
                    keySpec, new javax.crypto.spec.IvParameterSpec(iv));
        }
        return Base64.encodeToString(engine.doFinal(data), Base64.NO_WRAP);
    }

    private JSONObject readResult(String fallback) throws Exception {
        if (callResult == null) throw new IllegalStateException(fallback);
        JSONObject result = new JSONObject(callResult);
        if (!result.optBoolean("ok")) {
            throw new IllegalStateException(result.optString("error", fallback));
        }
        return result;
    }

    private String executeHttp(String method, String rawUrl, String optionsJson) throws Exception {
        JSONObject options = optionsJson == null || optionsJson.isEmpty() ? new JSONObject() : new JSONObject(optionsJson);
        String url = appendParams(rawUrl, options.optJSONObject("params"));
        Request.Builder builder = new Request.Builder().url(url);

        JSONObject headers = options.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.optString(key);
                if (!value.isEmpty()) builder.header(key, value);
            }
        }
        if (headerValue(headers, "User-Agent").isEmpty()) builder.header("User-Agent", USER_AGENT);

        String upperMethod = method.toUpperCase(Locale.US);
        RequestBody body = null;
        Object data = options.opt("data");
        if (data == null || data == JSONObject.NULL) data = options.opt("body");
        if (data == null || data == JSONObject.NULL) data = options.opt("formData");
        if (data != null && data != JSONObject.NULL) {
            String contentType = headerValue(headers, "Content-Type");
            if (contentType.isEmpty()) contentType = options.has("formData") ? "multipart/form-data" : "application/json";
            body = RequestBody.create(String.valueOf(data), MediaType.parse(contentType));
        }
        if ("GET".equals(upperMethod)) builder.get();
        else builder.method(upperMethod, body != null ? body : RequestBody.create(new byte[0]));

        // Plugins may opt out of redirect following (options.followRedirects === false) to read
        // 3xx Location headers themselves — some platforms hand out stream URLs that way.
        OkHttpClient callClient = options.has("followRedirects") && !options.optBoolean("followRedirects", true)
                ? client.newBuilder().followRedirects(false).build()
                : client;
        try (okhttp3.Response response = callClient.newCall(builder.build()).execute()) {
            String bodyText = response.body() == null ? "" : response.body().string();
            Object dataValue = bodyText;
            try {
                String trimmed = bodyText.trim();
                dataValue = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
            } catch (Exception ignored) {
            }
            JSONObject headersJson = new JSONObject();
            for (String name : response.headers().names()) {
                if ("set-cookie".equalsIgnoreCase(name)) {
                    // Flatten repeated Set-Cookie into a reusable "a=1; b=2" cookie string —
                    // plugins pass it back as a Cookie header for login flows.
                    java.util.List<String> cookies = response.headers(name);
                    StringBuilder joined = new StringBuilder();
                    for (String c : cookies) {
                        int semi = c.indexOf(';');
                        String pair = (semi >= 0 ? c.substring(0, semi) : c).trim();
                        if (pair.isEmpty()) continue;
                        if (joined.length() > 0) joined.append("; ");
                        joined.append(pair);
                    }
                    headersJson.put(name, joined.toString());
                } else {
                    headersJson.put(name, response.header(name));
                }
            }
            return new JSONObject()
                    .put("status", response.code())
                    .put("statusText", response.message())
                    .put("headers", headersJson)
                    .put("data", dataValue)
                    .toString();
        }
    }

    private String appendParams(String url, JSONObject params) throws Exception {
        if (params == null || params.length() == 0) return url;
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        Iterator<String> keys = params.keys();
        boolean first = true;
        while (keys.hasNext()) {
            String key = keys.next();
            if (!first) builder.append("&");
            first = false;
            builder
                    .append(URLEncoder.encode(key, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(params.optString(key), "UTF-8"));
        }
        return builder.toString();
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

    private String hash(String algorithm, String input) throws Exception {
        String normalized = algorithm.replace("-", "");
        MessageDigest digest = MessageDigest.getInstance(normalized);
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private String hmac(String algorithm, String key, String input) throws Exception {
        Mac mac = Mac.getInstance("Hmac" + algorithm.replace("-", ""));
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), mac.getAlgorithm()));
        byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
    }

    private String hexToText(String hex) {
        String normalized = hex.replaceAll("[^0-9a-fA-F]", "");
        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void waitFor(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                jsContext.evaluate("__mf_pump()");
            } catch (Exception ignored) {
            }
            Thread.sleep(8L);
        }
    }

    private String safeString(ThrowingStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            Log.w(TAG, "Native call failed", e);
            return "";
        }
    }

    private interface ThrowingStringSupplier {
        String get() throws Exception;
    }

    private String prelude() { return PluginJsShared.prelude(); }

    private String compat() { return PluginJsShared.compat(); }

    private String bridge() {
        return ""
                + "function __mf_finish(ok,value,error){ __mf_native_result(JSON.stringify({ok:ok,value:value||null,error:error?String(error):''})); }\n"
                + "function __mf_call_search(query,page){ try{ var p=__mf_plugin&&__mf_plugin.search; if(!p) throw new Error('插件不支持搜索'); Promise.resolve(p.call(__mf_plugin,query,page,'music')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n"
                + "function __mf_call_media_source(itemJson,quality){ try{ var item=JSON.parse(itemJson); var p=__mf_plugin&&__mf_plugin.getMediaSource; if(typeof p!=='function'){var q=item.qualities&&item.qualities[quality||'standard'];var fallback=q&&q.url?q:item;if(fallback.url){__mf_finish(true,{url:fallback.url,headers:fallback.headers||item.headers,userAgent:fallback.userAgent||item.userAgent},null);return;} throw new Error('插件不支持播放地址解析');} Promise.resolve(p.call(__mf_plugin,item,quality||'standard')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n"
                + "function __mf_call_lyric(itemJson){ try{ var item=JSON.parse(itemJson); var p=__mf_plugin&&__mf_plugin.getLyric; if(!p){__mf_finish(true,{},null);return;} Promise.resolve(p.call(__mf_plugin,item)).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n";
    }

    @Override
    public void close() {
        if (jsContext != null) {
            jsContext.destroy();
            jsContext = null;
        }
    }
}
