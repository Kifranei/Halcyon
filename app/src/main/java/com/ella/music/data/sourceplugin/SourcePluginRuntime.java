package com.ella.music.data.sourceplugin;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.ella.music.data.musicfree.PluginJsShared;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Runtime for Halcyon account-source plugins (Source Plugin API v1). Shares the prelude and
 * compatibility layer with the MusicFree sandbox via {@link PluginJsShared} but exposes a
 * generic method-dispatch bridge instead of the fixed MusicFree call set: every host call is
 * {@code plugin.<method>(ctx, ...args)} where ctx carries the persisted credential and the
 * requested stream quality. Scripts are always user-supplied; the app ships no source plugins.
 */
public final class SourcePluginRuntime implements AutoCloseable {
    private static final String TAG = "SourcePluginRuntime";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 EllaMusic/1.0";
    private static final long CALL_TIMEOUT_MS = 30_000L;

    private final OkHttpClient client;
    private final Context context;
    private final String kvPrefsName;
    private QuickJSContext jsContext;
    private String callResult;

    public SourcePluginRuntime(Context context, OkHttpClient client, String kvPrefsName) {
        this.client = client;
        this.context = context;
        this.kvPrefsName = kvPrefsName;
    }

    /**
     * Invokes {@code plugin.<method>(ctx, args...)}. ctxJson/argsJson may be null; the result is
     * the raw envelope {ok, value, error} — callers decide which methods tolerate absence.
     */
    public JSONObject call(String script, String method, String ctxJson, String argsJson) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__sp_call")
                .call(method, ctxJson == null ? "{}" : ctxJson, argsJson == null ? "[]" : argsJson);
        waitFor(() -> callResult != null, CALL_TIMEOUT_MS);
        if (callResult == null) throw new IllegalStateException("插件调用超时: " + method);
        return new JSONObject(callResult);
    }

    /** Reads the plugin manifest without invoking any of its functions. */
    public JSONObject inspect(String script) throws Exception {
        load(script);
        Object result = jsContext.evaluate("JSON.stringify({"
                + "platform:(__sp_plugin&&__sp_plugin.platform)||'',"
                + "id:(__sp_plugin&&__sp_plugin.id)||'',"
                + "version:(__sp_plugin&&__sp_plugin.version)||'',"
                + "author:(__sp_plugin&&__sp_plugin.author)||'',"
                + "description:(__sp_plugin&&__sp_plugin.description)||'',"
                + "apiVersion:(__sp_plugin&&__sp_plugin.apiVersion)||0,"
                + "account:(__sp_plugin&&__sp_plugin.account)||null,"
                + "capabilities:(__sp_plugin&&__sp_plugin.capabilities)||null,"
                + "functions:(function(){var o=[];if(__sp_plugin){for(var k in __sp_plugin){if(typeof __sp_plugin[k]==='function')o.push(k)}}return o})()"
                + "})");
        return new JSONObject(String.valueOf(result));
    }

    private void load(String script) throws Exception {
        QuickJSLoader.init();
        jsContext = QuickJSContext.create();
        jsContext.setMemoryLimit(64 * 1024 * 1024);
        jsContext.setMaxStackSize(512 * 1024);
        jsContext.setConsole(new QuickJSContext.Console() {
            @Override public void log(String message) { Log.d(TAG, message); }
            @Override public void info(String message) { Log.i(TAG, message); }
            @Override public void warn(String message) { Log.w(TAG, message); }
            @Override public void error(String message) { Log.e(TAG, message); }
        });
        createEnv();
        jsContext.evaluate(PluginJsShared.prelude());
        jsContext.evaluate(PluginJsShared.compat());
        jsContext.evaluate("(function(){\n" + script
                + "\n;globalThis.__sp_plugin=(module.exports&&module.exports.default)||module.exports||exports;})();");
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
            String value = context.getSharedPreferences(kvPrefsName, Context.MODE_PRIVATE)
                    .getString(String.valueOf(args[0]), null);
            return value == null ? "" : value;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_store_set", args -> {
            if (context == null) return null;
            context.getSharedPreferences(kvPrefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putString(String.valueOf(args[0]), String.valueOf(args[1]))
                    .apply();
            return null;
        });
        jsContext.getGlobalObject().setProperty("__mf_native_store_delete", args -> {
            if (context == null) return null;
            context.getSharedPreferences(kvPrefsName, Context.MODE_PRIVATE).edit().remove(String.valueOf(args[0])).apply();
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

    private String hashBytes(String algorithm, String dataB64) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm.replace("-", ""));
        return hex(digest.digest(Base64.decode(dataB64, Base64.DEFAULT)));
    }

    private String hmacBytes(String algorithm, String keyB64, String dataB64) throws Exception {
        Mac mac = Mac.getInstance("Hmac" + algorithm.replace("-", ""));
        mac.init(new SecretKeySpec(Base64.decode(keyB64, Base64.DEFAULT), mac.getAlgorithm()));
        return hex(mac.doFinal(Base64.decode(dataB64, Base64.DEFAULT)));
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(String.format(Locale.US, "%02x", b));
        return builder.toString();
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
                    keySpec, new IvParameterSpec(iv));
        }
        return Base64.encodeToString(engine.doFinal(data), Base64.NO_WRAP);
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

    /**
     * Generic dispatch bridge: __sp_call routes every host call to plugin.<method>(ctx, ...args).
     * ctx is a JSON object ({auth, quality}); args is a JSON array. Async results resolve via
     * Promise; synchronous returns work too. __sp_auth is set by the host before each call so
     * plugins may also read credentials without taking ctx.
     */
    private String bridge() {
        return ""
                + "function __sp_finish(ok,value,error){ __mf_native_result(JSON.stringify({ok:ok,value:value===undefined?null:value,error:error?String(error):''})); }\n"
                + "function __sp_call(method, ctxJson, argsJson){"
                + " try{"
                + "  var fn=__sp_plugin&&__sp_plugin[method];"
                + "  if(typeof fn!=='function'){__sp_finish(false,null,'PLUGIN_NO_METHOD:'+method);return;}"
                + "  var ctx={};try{ctx=JSON.parse(ctxJson||'{}')}catch(e){}"
                + "  var args=[];try{args=JSON.parse(argsJson||'[]')}catch(e){}"
                + "  Promise.resolve(fn.apply(__sp_plugin,[ctx].concat(args))).then("
                + "   function(r){__sp_finish(true,r===undefined?null:r,null)},"
                + "   function(e){__sp_finish(false,null,e&&(e.message||e)||'error')});"
                + " }catch(e){__sp_finish(false,null,e&&e.message||e)}"
                + "}\n";
    }

    @Override
    public void close() {
        if (jsContext != null) {
            jsContext.destroy();
            jsContext = null;
        }
    }
}
