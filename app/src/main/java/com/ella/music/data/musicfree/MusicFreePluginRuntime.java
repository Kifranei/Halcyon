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
                + "hasImportMusicItem:typeof(__mf_plugin&&__mf_plugin.importMusicItem)==='function'"
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

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            String bodyText = response.body() == null ? "" : response.body().string();
            Object dataValue = bodyText;
            try {
                String trimmed = bodyText.trim();
                dataValue = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
            } catch (Exception ignored) {
            }
            JSONObject headersJson = new JSONObject();
            for (String name : response.headers().names()) {
                headersJson.put(name, response.header(name));
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

    private String prelude() {
        return ""
                + "var module={exports:{}}; var exports=module.exports; var global=globalThis; var window=globalThis; var self=globalThis;\n"
                + "function __mf_parse(v){ try{return JSON.parse(v)}catch(e){return v} }\n"
                + "function __mf_qs_stringify(obj){if(!obj)return'';var parts=[];Object.keys(obj).forEach(function(k){var v=obj[k];if(v==null)return;if(Array.isArray(v)){v.forEach(function(x){parts.push(encodeURIComponent(k)+'='+encodeURIComponent(x==null?'':String(x)))})}else if(typeof v==='object'){parts.push(encodeURIComponent(k)+'='+encodeURIComponent(JSON.stringify(v)))}else parts.push(encodeURIComponent(k)+'='+encodeURIComponent(String(v)))});return parts.join('&')}\n"
                + "function __mf_qs_parse(s){var out={};String(s||'').replace(/^\\?/,'').split('&').forEach(function(p){if(!p)return;var i=p.indexOf('=');var k=decodeURIComponent(i<0?p:p.slice(0,i));var v=decodeURIComponent(i<0?'':p.slice(i+1));if(out[k]!==undefined){if(!Array.isArray(out[k]))out[k]=[out[k]];out[k].push(v)}else out[k]=v});return out}\n"
                + "var qs={stringify:__mf_qs_stringify,parse:__mf_qs_parse}; qs.default=qs;\n"
                + "function __mf_apply_params_serializer(config){if(!config||!config.params)return config;try{if(typeof config.paramsSerializer==='function'){var q=config.paramsSerializer(config.params);if(q){config.url+=(String(config.url).indexOf('?')>=0?'&':'?')+q;delete config.params}}else if(config.paramsSerializer&&typeof config.paramsSerializer.serialize==='function'){var q2=config.paramsSerializer.serialize(config.params);if(q2){config.url+=(String(config.url).indexOf('?')>=0?'&':'?')+q2;delete config.params}}}catch(e){}return config}\n"
                + "function __mf_http(method,url,options){ options=__mf_apply_params_serializer(options||{}); var r=__mf_parse(__mf_native_http(method,url,JSON.stringify(options||{}))); if(r&&r.headers&&!r.headers.get){r.headers.get=function(k){k=String(k);return this[k]||this[k.toLowerCase()]||this[k.toUpperCase()]||null};} return r; }\n"
                + "var axios=function(config,maybeConfig){ if(typeof config==='string')config=Object.assign({},maybeConfig||{},{url:config}); config=config||{}; return Promise.resolve(__mf_http(config.method||'GET', config.url, config)); };\n"
                + "axios.get=function(url,config){ config=Object.assign({},config||{},{url:url}); return Promise.resolve(__mf_http('GET',url,config)); };\n"
                + "axios.post=function(url,data,config){ config=Object.assign({},config||{},{url:url,data:data}); return Promise.resolve(__mf_http('POST',url,config)); };\n"
                + "axios.request=axios; axios.create=function(defaults){var inst=function(config){return axios(Object.assign({},defaults||{},config||{}))};Object.keys(axios).forEach(function(k){inst[k]=axios[k]});inst.defaults=Object.assign({},axios.defaults,defaults||{});return inst}; axios.defaults={headers:{common:{}}};\n"
                + "axios.default=axios;\n"
                + "var request=function(url,options){ return axios(Object.assign({}, options||{}, {url:url})); };\n"
                + "request.get=axios.get; request.post=axios.post; request.default=request;\n"
                + "var __mf_memory_store={};\n"
                + "var AsyncStorage={"
                + "getItem:function(k){return Promise.resolve(Object.prototype.hasOwnProperty.call(__mf_memory_store,k)?__mf_memory_store[k]:null)},"
                + "setItem:function(k,v){__mf_memory_store[k]=String(v);return Promise.resolve()},"
                + "removeItem:function(k){delete __mf_memory_store[k];return Promise.resolve()},"
                + "multiGet:function(keys){return Promise.resolve((keys||[]).map(function(k){return [k,Object.prototype.hasOwnProperty.call(__mf_memory_store,k)?__mf_memory_store[k]:null]}))},"
                + "multiSet:function(items){(items||[]).forEach(function(it){__mf_memory_store[it[0]]=String(it[1])});return Promise.resolve()},"
                + "clear:function(){__mf_memory_store={};return Promise.resolve()}"
                + "}; AsyncStorage.default=AsyncStorage;\n"
                + "var Dimensions={get:function(){return {width:1080,height:1920,scale:1,fontScale:1}}};\n"
                + "var Platform={OS:'android',Version:35,select:function(v){return v&&((v.android!==undefined?v.android:v.default))}};\n"
                + "var env={appVersion:'1.0.0',os:'android',lang:'zh-CN',getUserVariables:function(){var o={};try{var uv=(__mf_plugin&&__mf_plugin.userVariables)||[];for(var i=0;i<uv.length;i++){var e=uv[i]||{};if(e.key)o[e.key]=(e.value!==undefined?e.value:(e.default!==undefined?e.default:''))}}catch(x){}return o},get userVariables(){return this.getUserVariables()}};\n"
                + "var process={platform:'android',version:'1.0.0',env:{}};\n"
                + "function __mf_decode_html(s){return String(s==null?'':s).replace(/&#(\\d+);/g,function(_,n){return String.fromCharCode(parseInt(n,10))}).replace(/&#x([0-9a-fA-F]+);/g,function(_,n){return String.fromCharCode(parseInt(n,16))}).replace(/&quot;/g,'\\\"').replace(/&apos;/g,\"'\").replace(/&#39;/g,\"'\").replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&nbsp;/g,' ')}\n"
                + "function __mf_encode_html(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\\\"/g,'&quot;').replace(/'/g,'&#39;')}\n"
                + "var he={decode:__mf_decode_html,encode:__mf_encode_html}; he.default=he;\n"
                + "function __mf_strip_tags(s){return __mf_decode_html(String(s||'').replace(/<script[\\s\\S]*?<\\/script>/gi,'').replace(/<style[\\s\\S]*?<\\/style>/gi,'').replace(/<[^>]+>/g,''));}\n"
                + "function __mf_has_class(node,c){var m=String(node||'').match(/class=[\\\"']([^\\\"']*)/i);return !!(m&&(' '+m[1]+' ').indexOf(' '+c+' ')>=0);}\n"
                + "function __mf_find_nodes(html,selector){html=String(html||'');selector=String(selector||'').trim();if(!selector)return [html];var m=selector.match(/^([a-zA-Z][\\w-]*)(?:\\.([\\w-]+))?$/);var tag=m&&m[1];var cls=m&&m[2];var out=[];if(tag){var re=new RegExp('<'+tag+'\\\\b[^>]*>[\\\\s\\\\S]*?<\\\\/'+tag+'>','gi');var x;while((x=re.exec(html))){if(!cls||__mf_has_class(x[0],cls))out.push(x[0]);}}else if(selector.charAt(0)==='.') {var c=selector.slice(1);var re2=/<([a-zA-Z][\\w-]*)\\b[^>]*>[\\s\\S]*?<\\/\\1>/gi;var y;while((y=re2.exec(html))){if(__mf_has_class(y[0],c))out.push(y[0]);}}return out.length?out:[html];}\n"
                + "function __mf_collection(nodes){nodes=nodes||[];return {length:nodes.length,text:function(){return nodes.map(__mf_strip_tags).join('')},html:function(){return nodes[0]||''},attr:function(name){var attrs=String(nodes[0]||'').match(/[\\w:-]+=[\\\"'][^\\\"']*[\\\"']/g)||[];for(var i=0;i<attrs.length;i++){var p=attrs[i].match(/^([\\w:-]+)=[\\\"']([^\\\"']*)/);if(p&&p[1]===String(name))return __mf_decode_html(p[2]);}return undefined},each:function(fn){nodes.forEach(function(n,i){fn&&fn.call(__mf_collection([n]),i,__mf_collection([n]))});return this},map:function(fn){var arr=nodes.map(function(n,i){return fn&&fn.call(__mf_collection([n]),i,__mf_collection([n]))});return {get:function(){return arr},toArray:function(){return arr}}},get:function(i){return i==null?nodes:nodes[i]},toArray:function(){return nodes}}}\n"
                + "var cheerio={load:function(html){var root=String(html||'');var $=function(selector){return __mf_collection(__mf_find_nodes(root,selector))};$.root=function(){return __mf_collection([root])};return $;}}; cheerio.default=cheerio;\n"
                + "var dayjs=function(v){var d=(v==null)?new Date():(v instanceof Date?v:new Date(v));function pad(n,l){l=l||2;var s=String(n);while(s.length<l)s='0'+s;return s}return {format:function(fmt){if(!fmt)return d.toISOString();var map={YYYY:d.getFullYear(),YY:String(d.getFullYear()).slice(-2),MM:pad(d.getMonth()+1),M:d.getMonth()+1,DD:pad(d.getDate()),D:d.getDate(),HH:pad(d.getHours()),H:d.getHours(),hh:pad(d.getHours()%12||12),h:d.getHours()%12||12,mm:pad(d.getMinutes()),m:d.getMinutes(),ss:pad(d.getSeconds()),s:d.getSeconds(),SSS:pad(d.getMilliseconds(),3),A:d.getHours()<12?'AM':'PM',a:d.getHours()<12?'am':'pm',d:d.getDay(),X:Math.floor(d.getTime()/1000),x:d.getTime()};return String(fmt).replace(/YYYY|YY|SSS|MM|DD|HH|hh|mm|ss|M|D|H|h|m|s|A|a|d|X|x/g,function(t){return String(map[t])})},unix:function(){return Math.floor(d.getTime()/1000)},valueOf:function(){return d.getTime()},toDate:function(){return d},add:function(n,unit){var ms=n;if(unit){var u=String(unit).replace(/s$/,'');var mult={millisecond:1,second:1000,minute:60000,hour:3600000,day:86400000,week:604800000};if(mult[u])ms=n*mult[u];else if(u==='month'){var c=new Date(d);c.setMonth(c.getMonth()+n);return dayjs(c)}else if(u==='year'){var c2=new Date(d);c2.setFullYear(c2.getFullYear()+n);return dayjs(c2)}}return dayjs(new Date(d.getTime()+ms))},subtract:function(n,unit){return this.add(-(n||0),unit)},isBefore:function(o){return d.getTime()<dayjs(o).valueOf()},isAfter:function(o){return d.getTime()>dayjs(o).valueOf()},toISOString:function(){return d.toISOString()}}}; dayjs.default=dayjs;\n"
                + "function bigInt(v){var n=BigInt(String(v||0));return {value:n,toString:function(radix){return n.toString(radix||10)},add:function(x){return bigInt(n+BigInt(String(x&&x.value!==undefined?x.value:x)))},minus:function(x){return bigInt(n-BigInt(String(x&&x.value!==undefined?x.value:x)))},subtract:function(x){return this.minus(x)},multiply:function(x){return bigInt(n*BigInt(String(x&&x.value!==undefined?x.value:x)))},divide:function(x){return bigInt(n/BigInt(String(x&&x.value!==undefined?x.value:x)))},mod:function(x){return bigInt(n%BigInt(String(x&&x.value!==undefined?x.value:x)))},pow:function(x){return bigInt(n**BigInt(String(x&&x.value!==undefined?x.value:x)))},equals:function(x){return n===BigInt(String(x&&x.value!==undefined?x.value:x))}}}; bigInt.default=bigInt;\n"
                + "var CookieManager={get:function(){return Promise.resolve({})},set:function(){return Promise.resolve(true)},clearAll:function(){return Promise.resolve(true)},flush:function(){return Promise.resolve()}}; CookieManager.default=CookieManager;\n"
                + "var webdav={createClient:function(){return {getDirectoryContents:function(){return Promise.resolve([])},getFileContents:function(){return Promise.resolve('')},putFileContents:function(){return Promise.resolve(true)},deleteFile:function(){return Promise.resolve(true)},exists:function(){return Promise.resolve(false)}}}}; webdav.default=webdav;\n"
                + "function require(name){"
                + "if(name==='axios')return axios;"
                + "if(name==='request')return request;"
                + "if(name==='crypto-js')return CryptoJS;"
                + "if(name==='qs'||name==='querystring')return qs;"
                + "if(name==='he')return he;"
                + "if(name==='cheerio')return cheerio;"
                + "if(name==='dayjs')return dayjs;"
                + "if(name==='big-integer')return bigInt;"
                + "if(name==='@react-native-cookies/cookies')return CookieManager;"
                + "if(name==='webdav')return webdav;"
                + "if(name==='@react-native-async-storage/async-storage')return AsyncStorage;"
                + "if(name==='@react-native-async-storage/react-native')return AsyncStorage;"
                + "if(name==='react-native')return {Dimensions:Dimensions,Platform:Platform};"
                + "throw new Error('暂不支持插件依赖: '+name);"
                + "}\n"
                + "var __musicfree_require=require;\n";
    }

    /**
     * Compatibility layer synced toward the MusicFree plugin environment: real timers,
     * binary-safe codecs, the full CryptoJS surface plugins sign requests with (AES/DES/
     * 3DES/RC4 + EvpKDF + OpenSSL format), a promise-aware axios, and the node module
     * shims bundled plugin code commonly requires.
     */
    private String compat() {
        return ""
                // ---------- timers: real deferred queue pumped during waitFor ----------
                + "var __mf_timer_seq=1,__mf_timers=[];\n"
                + "function setTimeout(fn,ms){var id=__mf_timer_seq++;__mf_timers.push({id:id,at:Date.now()+(ms||0),fn:fn,iv:0});return id}\n"
                + "function setInterval(fn,ms){var id=__mf_timer_seq++;var iv=Math.max(ms||1,1);__mf_timers.push({id:id,at:Date.now()+iv,fn:fn,iv:iv});return id}\n"
                + "function clearTimeout(id){__mf_timers=__mf_timers.filter(function(t){return t.id!==id})}\n"
                + "function clearInterval(id){clearTimeout(id)}\n"
                + "function __mf_pump(){var now=Date.now(),keep=[];for(var i=0;i<__mf_timers.length;i++){var t=__mf_timers[i];if(t.at<=now){try{if(typeof t.fn==='function')t.fn()}catch(e){console.warn('timer error: '+e)}if(t.iv>0){t.at=now+t.iv;keep.push(t)}}else keep.push(t)}__mf_timers=keep}\n"
                // ---------- binary strings (latin1 = 1 char per byte) ----------
                + "function __mf_utf8_encode(s){s=String(s);var out='';for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);if(c<0x80)out+=String.fromCharCode(c);else if(c<0x800)out+=String.fromCharCode(0xC0|(c>>6),0x80|(c&63));else if(c>=0xD800&&c<=0xDBFF&&i+1<s.length){var c2=s.charCodeAt(++i);var cp=0x10000+((c-0xD800)<<10)+(c2-0xDC00);out+=String.fromCharCode(0xF0|(cp>>18),0x80|((cp>>12)&63),0x80|((cp>>6)&63),0x80|(cp&63))}else out+=String.fromCharCode(0xE0|(c>>12),0x80|((c>>6)&63),0x80|(c&63))}return out}\n"
                + "function __mf_utf8_decode(s){s=String(s);var out='',i=0;while(i<s.length){var c=s.charCodeAt(i++);if(c<0x80)out+=String.fromCharCode(c);else if(c<0xE0)out+=String.fromCharCode(((c&31)<<6)|(s.charCodeAt(i++)&63));else if(c<0xF0)out+=String.fromCharCode(((c&15)<<12)|((s.charCodeAt(i++)&63)<<6)|(s.charCodeAt(i++)&63));else{var cp=((c&7)<<18)|((s.charCodeAt(i++)&63)<<12)|((s.charCodeAt(i++)&63)<<6)|(s.charCodeAt(i++)&63);cp-=0x10000;out+=String.fromCharCode(0xD800+(cp>>10),0xDC00+(cp&1023))}}return out}\n"
                + "function __mf_latin1_of_hex(h){h=String(h||'');var o='';for(var i=0;i+1<h.length;i+=2)o+=String.fromCharCode(parseInt(h.substr(i,2),16));return o}\n"
                + "function __mf_hex_of_latin1(s){var h='';for(var i=0;i<s.length;i++)h+=('0'+s.charCodeAt(i).toString(16)).slice(-2);return h}\n"
                + "function btoa(s){return __mf_native_b64_of_latin1(String(s))}\n"
                + "function atob(b){return __mf_native_latin1_of(String(b))}\n"
                // ---------- Buffer / TextEncoder ----------
                + "function __mf_buf(b){return{__mf_buf:true,__bytes:b,length:b.length,toString:function(enc){enc=String(enc||'utf8').toLowerCase();if(enc==='base64')return __mf_native_b64_of_latin1(this.__bytes);if(enc==='hex')return __mf_hex_of_latin1(this.__bytes);if(enc==='latin1'||enc==='binary')return this.__bytes;return __mf_utf8_decode(this.__bytes)}}}\n"
                + "var Buffer={from:function(v,enc){enc=String(enc||'utf8').toLowerCase();if(Array.isArray(v)){var s='';for(var i=0;i<v.length;i++)s+=String.fromCharCode(v[i]&255);return __mf_buf(s)}if(enc==='base64')return __mf_buf(atob(String(v)));if(enc==='hex')return __mf_buf(__mf_latin1_of_hex(String(v)));if(enc==='latin1'||enc==='binary')return __mf_buf(String(v));if(v&&v.__bytes!==undefined)return __mf_buf(v.__bytes);return __mf_buf(__mf_utf8_encode(String(v)))},alloc:function(n){var s='';for(var i=0;i<(n||0);i++)s+=String.fromCharCode(0);return __mf_buf(s)},isBuffer:function(v){return!!(v&&v.__mf_buf)},concat:function(list){var s='';(list||[]).forEach(function(b){s+=(b&&b.__bytes!==undefined)?b.__bytes:String(b)});return __mf_buf(s)},byteLength:function(v){return __mf_utf8_encode(String(v)).length}};\n"
                + "var TextEncoder=function(){};TextEncoder.prototype.encode=function(s){var b=__mf_utf8_encode(s),a=[];for(var i=0;i<b.length;i++)a.push(b.charCodeAt(i));return a};\n"
                + "var TextDecoder=function(){};TextDecoder.prototype.decode=function(a){var s='';if(a&&a.__bytes!==undefined)s=a.__bytes;else{var arr=a||[];for(var i=0;i<arr.length;i++)s+=String.fromCharCode(arr[i]&255)}return __mf_utf8_decode(s)};\n"
                // ---------- storage (persisted via SharedPreferences) ----------
                + "function __mf_store_get(k){var v=__mf_native_store_get(k);return v===''?null:v}\n"
                + "var localStorage={getItem:function(k){return __mf_store_get(k)},setItem:function(k,v){__mf_native_store_set(k,String(v))},removeItem:function(k){__mf_native_store_delete(k)},clear:function(){}};\n"
                + "var navigator={userAgent:'" + USER_AGENT + "',product:'ReactNative',appVersion:'1.0'};\n"
                // ---------- URL / URLSearchParams ----------
                + "function URLSearchParams(init){var self=this;self._m={};function add(k,v){k=String(k);(self._m[k]=self._m[k]||[]).push(String(v))}if(typeof init==='string'){String(init).replace(/^\\?/,'').split('&').forEach(function(p){if(!p)return;var i=p.indexOf('=');add(decodeURIComponent(i<0?p:p.slice(0,i)),decodeURIComponent(i<0?'':p.slice(i+1)))})}else if(init){Object.keys(init).forEach(function(k){add(k,init[k])})}self.get=function(k){var v=self._m[String(k)];return v&&v.length?v[0]:null};self.getAll=function(k){return self._m[String(k)]||[]};self.set=function(k,v){self._m[String(k)]=[String(v)]};self.append=function(k,v){add(k,v)};self.has=function(k){return self._m[String(k)]!==undefined};self.delete=function(k){delete self._m[String(k)]};self.toString=function(){var p=[];Object.keys(self._m).forEach(function(k){self._m[k].forEach(function(v){p.push(encodeURIComponent(k)+'='+encodeURIComponent(v))})});return p.join('&')};self.forEach=function(fn){Object.keys(self._m).forEach(function(k){self._m[k].forEach(function(v){fn(v,k,self)})})}}\n"
                + "function URL(u,base){var self=this;if(base&&u&&u.charAt(0)==='/'&&u.charAt(1)!=='/'){var bm=String(base).match(/^([a-zA-Z][\\w+.-]*:\\/\\/[^/]+)/);u=(bm?bm[1]:'')+u}var m=String(u||'').match(/^([a-zA-Z][\\w+.-]*):\\/\\/([^/?#]*)([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$/);self.protocol=m?m[1]+':':'';self.host=m?m[2]:'';self.hostname=self.host.split(':')[0];self.port=self.host.indexOf(':')>=0?self.host.split(':').pop():'';self.pathname=(m&&m[3])||'/';self.search=(m&&m[4]!==undefined)?('?'+m[4]):'';self.hash=(m&&m[5]!==undefined)?('#'+m[5]):'';self.origin=self.protocol?(self.protocol+'//'+self.host):'';self.searchParams=new URLSearchParams(self.search);self.href=String(u||'');self.toString=function(){return self.href}}\n"
                // ---------- FormData ----------
                + "function FormData(){var self=this;var fields=[];var boundary='----MFBoundary'+Math.random().toString(16).slice(2);self.append=function(k,v){fields.push([k,v])};self.__payload=function(){var out='';fields.forEach(function(f){out+='--'+boundary+'\\r\\nContent-Disposition: form-data; name=\"'+f[0]+'\"'+(f[1]&&f[1].__filename?'; filename=\"'+f[1].__filename+'\"':'')+'\\r\\n\\r\\n'+(f[1]&&f[1].__bytes!==undefined?f[1].__bytes:String(f[1]==null?'':f[1]))+'\\r\\n'});return out+'--'+boundary+'--\\r\\n'};self.getHeaders=function(){return{'Content-Type':'multipart/form-data; boundary='+boundary}};self.getBoundary=function(){return boundary}}\n"
                // ---------- fetch / XMLHttpRequest ----------
                + "function fetch(url,init){init=init||{};var opts={method:init.method||'GET',url:String(url),headers:init.headers||{}};if(init.body!=null)opts.data=init.body;try{var r=__mf_http(opts.method,opts.url,opts);return Promise.resolve({ok:r.status>=200&&r.status<300,status:r.status,statusText:r.statusText||'',headers:r.headers,json:function(){return Promise.resolve(r.data)},text:function(){return Promise.resolve(typeof r.data==='string'?r.data:JSON.stringify(r.data))}})}catch(e){return Promise.reject(e)}}\n"
                + "function XMLHttpRequest(){var self=this;self.readyState=0;self.status=0;self.responseText='';self.response='';self.open=function(method,url){self.__method=method;self.__url=url;self.readyState=1};self.setRequestHeader=function(k,v){(self.__headers=self.__headers||{})[k]=v};self.getResponseHeader=function(k){return self.__rh?self.__rh[k]||null:null};self.send=function(data){try{var r=__mf_http(self.__method||'GET',self.__url,{headers:self.__headers||{},data:data});self.status=r.status;self.__rh=r.headers||{};self.responseText=typeof r.data==='string'?r.data:JSON.stringify(r.data);self.response=self.responseText}catch(e){self.status=0;self.responseText=''}self.readyState=4;if(self.onreadystatechange)self.onreadystatechange();if(self.onload)self.onload()};self.abort=function(){}}\n"
                // ---------- CryptoJS ----------
                + "var CryptoJS={lib:{},enc:{},algo:{},mode:{CBC:'CBC',ECB:'ECB',CFB:'CFB',OFB:'OFB',CTR:'CTR'},pad:{Pkcs7:'Pkcs7',NoPadding:'NoPadding',ZeroPadding:'ZeroPadding',AnsiX923:'AnsiX923',Iso10126:'Iso10126',Iso97971:'Iso97971'},format:{},kdf:{}};\n"
                + "function __mf_WA(b,sig){var wa={__bytes:String(b==null?'':b),sigBytes:(sig==null?(b==null?0:b.length):sig),words:null};wa.toString=function(enc){enc=enc||CryptoJS.enc.Hex;if(enc===CryptoJS.enc.Hex)return __mf_hex_of_latin1(this.__bytes);if(enc===CryptoJS.enc.Base64)return __mf_native_b64_of_latin1(this.__bytes);if(enc===CryptoJS.enc.Latin1)return this.__bytes;if(enc===CryptoJS.enc.Utf8)return __mf_utf8_decode(this.__bytes);if(enc===CryptoJS.enc.Utf16LE||enc===CryptoJS.enc.Utf16){var o='';for(var i=0;i+1<this.__bytes.length;i+=2)o+=String.fromCharCode(this.__bytes.charCodeAt(i)|(this.__bytes.charCodeAt(i+1)<<8));return o}if(enc===CryptoJS.enc.Utf16BE){var o2='';for(var j=0;j+1<this.__bytes.length;j+=2)o2+=String.fromCharCode((this.__bytes.charCodeAt(j)<<8)|this.__bytes.charCodeAt(j+1));return o2}return this.__bytes};wa.clone=function(){return __mf_WA(this.__bytes.slice(0,this.sigBytes),this.sigBytes)};wa.concat=function(o){var ob=__mf_latin1_from(o);this.__bytes=this.__bytes.slice(0,this.sigBytes)+ob;this.sigBytes+=ob.length;return this};wa.clamp=function(){this.__bytes=this.__bytes.slice(0,this.sigBytes);return this};wa.nSigBytes=function(n){this.sigBytes=n;return this};return wa}\n"
                + "function __mf_latin1_from(v){if(v==null)return'';if(v.__bytes!==undefined)return v.__bytes;if(v.__mf_buf)return v.__bytes;if(typeof v==='string')return v;if(v.toString)return v.toString();return String(v)}\n"
                + "CryptoJS.lib.WordArray={create:function(w,sig){if(typeof w==='string')return __mf_WA(w,sig);if(w&&w.__bytes!==undefined)return __mf_WA(w.__bytes,sig);if(Array.isArray(w)){var b='';var n=(sig==null?w.length*4:sig);for(var i=0;i<n;i++){var word=w[i>>>2]||0;b+=String.fromCharCode((word>>>(24-(i%4)*8))&255)}return __mf_WA(b,n)}return __mf_WA('',sig||0)},random:function(n){return __mf_WA(__mf_native_latin1_of(__mf_native_random_bytes(n)),n)}};\n"
                + "CryptoJS.lib.CipherParams={create:function(o){var c=o||{};c.toString=function(fmt){return(fmt||CryptoJS.format.OpenSSL).stringify(this)};return c}};\n"
                + "CryptoJS.enc={"
                + "Utf8:{parse:function(v){return __mf_WA(__mf_utf8_encode(v))},stringify:function(v){return __mf_utf8_decode(v.__bytes)}},"
                + "Latin1:{parse:function(v){return __mf_WA(String(v))},stringify:function(v){return v.__bytes}},"
                + "Hex:{parse:function(v){return __mf_WA(__mf_latin1_of_hex(v))},stringify:function(v){return __mf_hex_of_latin1(v.__bytes)}},"
                + "Base64:{parse:function(v){return __mf_WA(atob(String(v)))},stringify:function(v){return __mf_native_b64_of_latin1(v.__bytes)}},"
                + "Utf16:{parse:function(v){var b='';var s=String(v);for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);b+=String.fromCharCode(c>>8&255,c&255)}return __mf_WA(b)},stringify:function(v){var b=v.__bytes,o='';for(var i=0;i+1<b.length;i+=2)o+=String.fromCharCode((b.charCodeAt(i)<<8)|b.charCodeAt(i+1));return o}},"
                + "Utf16BE:null,Utf16LE:{parse:function(v){var b='';var s=String(v);for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);b+=String.fromCharCode(c&255,c>>8&255)}return __mf_WA(b)},stringify:function(v){var b=v.__bytes,o='';for(var i=0;i+1<b.length;i+=2)o+=String.fromCharCode(b.charCodeAt(i)|(b.charCodeAt(i+1)<<8));return o}}"
                + "};CryptoJS.enc.Utf16BE=CryptoJS.enc.Utf16;\n"
                + "function __mf_hash_wa(algo,v){return __mf_WA(__mf_latin1_of_hex(__mf_native_hash_bin(algo,__mf_native_b64_of_latin1(__mf_latin1_from(v)))))}\n"
                + "CryptoJS.MD5=function(v){return __mf_hash_wa('MD5',v)};\n"
                + "CryptoJS.SHA1=function(v){return __mf_hash_wa('SHA-1',v)};\n"
                + "CryptoJS.SHA224=function(v){return __mf_hash_wa('SHA-224',v)};\n"
                + "CryptoJS.SHA256=function(v){return __mf_hash_wa('SHA-256',v)};\n"
                + "CryptoJS.SHA384=function(v){return __mf_hash_wa('SHA-384',v)};\n"
                + "CryptoJS.SHA512=function(v){return __mf_hash_wa('SHA-512',v)};\n"
                + "CryptoJS.SHA3=function(v){return __mf_hash_wa('SHA3-256',v)};\n"
                + "function __mf_hmac_wa(algo,v,k){return __mf_WA(__mf_latin1_of_hex(__mf_native_hmac_bin(algo,__mf_native_b64_of_latin1(__mf_latin1_from(k)),__mf_native_b64_of_latin1(__mf_latin1_from(v)))))}\n"
                + "CryptoJS.HmacMD5=function(v,k){return __mf_hmac_wa('MD5',v,k)};\n"
                + "CryptoJS.HmacSHA1=function(v,k){return __mf_hmac_wa('SHA-1',v,k)};\n"
                + "CryptoJS.HmacSHA224=function(v,k){return __mf_hmac_wa('SHA-224',v,k)};\n"
                + "CryptoJS.HmacSHA256=function(v,k){return __mf_hmac_wa('SHA-256',v,k)};\n"
                + "CryptoJS.HmacSHA384=function(v,k){return __mf_hmac_wa('SHA-384',v,k)};\n"
                + "CryptoJS.HmacSHA512=function(v,k){return __mf_hmac_wa('SHA-512',v,k)};\n"
                + "CryptoJS.PBKDF2=function(password,salt,cfg){cfg=cfg||{};var iters=cfg.iterations||1;var out='';var pw=__mf_latin1_from(typeof password==='string'?CryptoJS.enc.Utf8.parse(password):password);var sa=__mf_latin1_from(typeof salt==='string'?CryptoJS.enc.Utf8.parse(salt):salt);var block=1;while(out.length<(cfg.keySize||4)*4){var u=sa+String.fromCharCode(block>>24&255,block>>16&255,block>>8&255,block&255);var t=u;for(var i=0;i<iters;i++){u=__mf_latin1_of_hex(__mf_native_hmac_bin('SHA-1',__mf_native_b64_of_latin1(pw),__mf_native_b64_of_latin1(u)));var x='';for(var j=0;j<u.length;j++)x+=String.fromCharCode(u.charCodeAt(j)^t.charCodeAt(j));t=x}out+=t;block++}return __mf_WA(out)};\n"
                + "CryptoJS.kdf.OpenSSL={execute:function(password,keySize,ivSize,salt){salt=salt||CryptoJS.lib.WordArray.random(8);var pw=CryptoJS.enc.Utf8.parse(password);var derived=CryptoJS.lib.WordArray.create();var block=null;while(derived.sigBytes<(keySize+ivSize)*4){var b=CryptoJS.lib.WordArray.create();if(block)b.concat(block);b.concat(pw);b.concat(salt);block=CryptoJS.MD5(b);derived.concat(block)}derived.sigBytes=(keySize+ivSize)*4;derived.clamp();var key=__mf_WA(derived.__bytes.slice(0,keySize*4),keySize*4);var iv=__mf_WA(derived.__bytes.slice(keySize*4),ivSize*4);return CryptoJS.lib.CipherParams.create({key:key,iv:iv,salt:salt})}};\n"
                + "CryptoJS.format.OpenSSL={stringify:function(cp){var b='Salted__';var ct=__mf_latin1_from(cp.ciphertext);var data=cp.salt?(b+cp.salt.__bytes+ct):ct;return __mf_native_b64_of_latin1(data)},parse:function(s){var b=atob(String(s));if(b.slice(0,8)==='Salted__'){return CryptoJS.lib.CipherParams.create({ciphertext:__mf_WA(b.slice(16)),salt:__mf_WA(b.slice(8,16))})}return CryptoJS.lib.CipherParams.create({ciphertext:__mf_WA(b)})}};\n"
                + "function __mf_pad(d,bs,pad){if(pad===CryptoJS.pad.NoPadding||bs<=0)return d;var n=bs-(d.length%bs);if(pad===CryptoJS.pad.ZeroPadding){while(d.length%bs)d+=String.fromCharCode(0);return d}if(pad===CryptoJS.pad.Iso97971){d+=String.fromCharCode(0x80);while(d.length%bs)d+=String.fromCharCode(0);return d}if(pad===CryptoJS.pad.AnsiX923||pad===CryptoJS.pad.Iso10126){for(var i=0;i<n-1;i++)d+=String.fromCharCode(0);return d+String.fromCharCode(n)}for(var j=0;j<n;j++)d+=String.fromCharCode(n);return d}\n"
                + "function __mf_unpad(d,pad){if(!d.length)return d;if(pad===CryptoJS.pad.NoPadding)return d;if(pad===CryptoJS.pad.ZeroPadding){while(d.length&&d.charCodeAt(d.length-1)===0)d=d.slice(0,-1);return d}if(pad===CryptoJS.pad.Iso97971){while(d.length&&d.charCodeAt(d.length-1)!==0x80)d=d.slice(0,-1);return d.slice(0,-1)}var n=d.charCodeAt(d.length-1);if(n>0&&n<=d.length)return d.slice(0,d.length-n);return d}\n"
                + "function __mf_cipher_run(op,algo,mode,key,iv,data){return __mf_native_cipher(op,algo,mode||'',__mf_native_b64_of_latin1(key),JSON.stringify({iv:iv?__mf_native_b64_of_latin1(iv):'',data:__mf_native_b64_of_latin1(data)}))}\n"
                + "function __mf_cipher_impl(name,jalgo,bs){var impl={_bs:bs,_name:name,"
                + "encrypt:function(msg,key,cfg){cfg=cfg||{};var data=__mf_latin1_from(typeof msg==='string'?CryptoJS.enc.Utf8.parse(msg):msg);var keyWA,salt=null,iv;var mode=cfg.mode||CryptoJS.mode.CBC;var pad=cfg.padding||CryptoJS.pad.Pkcs7;if(typeof key==='string'){salt=CryptoJS.lib.WordArray.random(8);var k=CryptoJS.kdf.OpenSSL.execute(key,bs>0?bs/4:8,4,salt);keyWA=k.key;iv=cfg.iv||k.iv}else{keyWA=key;iv=cfg.iv}var pt=(jalgo==='RC4')?data:__mf_pad(data,bs,pad);var ctB64=__mf_cipher_run('encrypt',jalgo,jalgo==='RC4'?'':mode,__mf_latin1_from(keyWA),iv?__mf_latin1_from(iv):null,pt);var ct=__mf_WA(atob(ctB64));return CryptoJS.lib.CipherParams.create({ciphertext:ct,key:keyWA,iv:iv,salt:salt,algorithm:impl,blockSize:Math.max(1,bs/4)})},"
                + "decrypt:function(ct,key,cfg){cfg=cfg||{};var salt=null,data;if(typeof ct==='string'){var raw=atob(ct);if(raw.slice(0,8)==='Salted__'){salt=__mf_WA(raw.slice(8,16));data=raw.slice(16)}else data=raw}else{data=__mf_latin1_from(ct&&ct.ciphertext!==undefined?ct.ciphertext:ct);salt=ct&&ct.salt?ct.salt:null}var keyWA,iv;var mode=cfg.mode||CryptoJS.mode.CBC;var pad=cfg.padding||CryptoJS.pad.Pkcs7;if(typeof key==='string'){salt=salt||CryptoJS.lib.WordArray.random(8);var k=CryptoJS.kdf.OpenSSL.execute(key,bs>0?bs/4:8,4,salt);keyWA=k.key;iv=cfg.iv||k.iv}else{keyWA=key;iv=cfg.iv||(ct&&ct.iv)}var ptB64=__mf_cipher_run('decrypt',jalgo,jalgo==='RC4'?'':mode,__mf_latin1_from(keyWA),iv?__mf_latin1_from(iv):null,data);var pt=atob(ptB64);return __mf_WA(jalgo==='RC4'?pt:__mf_unpad(pt,pad))}};return impl}\n"
                + "CryptoJS.AES=__mf_cipher_impl('AES','AES',16);\n"
                + "CryptoJS.DES=__mf_cipher_impl('DES','DES',8);\n"
                + "CryptoJS.TripleDES=__mf_cipher_impl('TripleDES','DESede',8);\n"
                + "CryptoJS.RC4=__mf_cipher_impl('RC4','RC4',0);\n"
                + "CryptoJS.RC4Drop=CryptoJS.RC4;CryptoJS.Rabbit=CryptoJS.RC4;\n"
                + "CryptoJS.algo.AES=CryptoJS.AES;CryptoJS.algo.DES=CryptoJS.DES;CryptoJS.algo.TripleDES=CryptoJS.TripleDES;CryptoJS.algo.RC4=CryptoJS.RC4;\n"
                + "CryptoJS.default=CryptoJS;\n"
                // ---------- axios upgrades ----------
                + "function __mf_interceptors(){return{__h:[],use:function(f,r){this.__h.push({f:f,r:r});return this.__h.length-1},eject:function(id){this.__h[id]=null},clear:function(){this.__h=[]}}}\n"
                + "var __mf_http0=__mf_http;\n"
                + "__mf_http=function(method,url,options){options=options||{};var d=(options.data!=null?options.data:(options.body!=null?options.body:options.formData));if(d&&typeof d.__payload==='function'){options.headers=Object.assign({},d.getHeaders(),options.headers||{});options.data=d.__payload()}else if(d&&typeof d==='object'&&typeof d!=='string'&&!d.__bytes){try{options.headers=Object.assign({'Content-Type':'application/json'},options.headers||{});options.data=JSON.stringify(d)}catch(e){}}var r=__mf_http0(method,url,options);if(r&&options.responseType==='text'&&typeof r.data!=='string')r.data=JSON.stringify(r.data);var vs=options.validateStatus||(axios.defaults&&axios.defaults.validateStatus);var ok=vs?vs(r&&r.status):(r&&r.status>=200&&r.status<300);if(r&&typeof r.status==='number'&&!ok){var err=new Error('Request failed with status code '+r.status);err.isAxiosError=true;err.response=r;err.config=options;throw err}return r};\n"
                + "var __mf_axios0=axios;\n"
                + "axios=function(config,maybeConfig){if(typeof config==='string')config=Object.assign({},maybeConfig||{},{url:config});config=config||{};var dfl=axios.defaults||{};var m=String(config.method||'GET').toLowerCase();config.headers=Object.assign({},(dfl.headers&&dfl.headers.common)||{},(dfl.headers&&dfl.headers[m])||{},config.headers||{});config=Object.assign({},dfl,config,{headers:config.headers});var p=Promise.resolve(config);axios.interceptors.request.__h.forEach(function(h){if(h&&h.f)p=p.then(h.f,h.r||undefined)});p=p.then(function(c){var trs=[].concat(c.transformRequest||[]);trs.forEach(function(t){c.data=t(c.data,c.headers)});try{var r=__mf_http(c.method||'GET',c.url,c);var tr2=[].concat(c.transformResponse||[]);tr2.forEach(function(t){r.data=t(r.data,r.headers)});return r}catch(e){return Promise.reject(e)}});axios.interceptors.response.__h.forEach(function(h){if(h&&h.f)p=p.then(h.f,h.r||undefined)});return p};\n"
                + "axios.get=function(url,config){config=Object.assign({},config||{},{url:url,method:'GET'});return axios(config)};\n"
                + "axios.post=function(url,data,config){config=Object.assign({},config||{},{url:url,data:data,method:'POST'});return axios(config)};\n"
                + "axios.put=function(url,data,config){config=Object.assign({},config||{},{url:url,data:data,method:'PUT'});return axios(config)};\n"
                + "axios.patch=function(url,data,config){config=Object.assign({},config||{},{url:url,data:data,method:'PATCH'});return axios(config)};\n"
                + "axios.delete=function(url,config){config=Object.assign({},config||{},{url:url,method:'DELETE'});return axios(config)};\n"
                + "axios.head=function(url,config){config=Object.assign({},config||{},{url:url,method:'HEAD'});return axios(config)};\n"
                + "axios.options=function(url,config){config=Object.assign({},config||{},{url:url,method:'OPTIONS'});return axios(config)};\n"
                + "axios.request=axios;\n"
                + "axios.all=function(list){return Promise.all(list)};\n"
                + "axios.spread=function(fn){return function(arr){return fn.apply(null,arr)}};\n"
                + "axios.isAxiosError=function(e){return!!(e&&e.isAxiosError)};\n"
                + "axios.isCancel=function(e){return!!(e&&e.__CANCEL__)};\n"
                + "axios.CancelToken={source:function(){return{token:{},cancel:function(){}}}};\n"
                + "axios.interceptors={request:__mf_interceptors(),response:__mf_interceptors()};\n"
                + "axios.create=function(defaults){var inst=function(config,maybeConfig){return axios(Object.assign({},inst.defaults,config||{}))};Object.keys(axios).forEach(function(k){inst[k]=axios[k]});inst.defaults=Object.assign({},axios.defaults,defaults||{});inst.interceptors={request:__mf_interceptors(),response:__mf_interceptors()};return inst};\n"
                + "axios.default=axios;\n"
                + "var request=function(url,options,cb){if(typeof options==='function'){cb=options;options={}}options=options||{};try{var r=axios(Object.assign({},options,{url:url}));if(cb){r=r.then(function(res){cb(null,{statusCode:res.status,headers:res.headers,body:typeof res.data==='string'?res.data:JSON.stringify(res.data)},typeof res.data==='string'?res.data:JSON.stringify(res.data));return res})}return r}catch(e){if(cb){cb(e,null,null);return Promise.reject(e)}return Promise.reject(e)}};\n"
                + "request.get=axios.get;request.post=axios.post;request.put=axios.put;request.default=request;\n"
                // ---------- AsyncStorage over the persistent store ----------
                + "AsyncStorage={getItem:function(k){return Promise.resolve(__mf_store_get(k))},setItem:function(k,v){__mf_native_store_set(k,String(v));return Promise.resolve()},removeItem:function(k){__mf_native_store_delete(k);return Promise.resolve()},multiGet:function(keys){return Promise.resolve((keys||[]).map(function(k){return[k,__mf_store_get(k)]}))},multiSet:function(items){(items||[]).forEach(function(it){__mf_native_store_set(it[0],String(it[1]))});return Promise.resolve()},clear:function(){return Promise.resolve()}};AsyncStorage.default=AsyncStorage;\n"
                // ---------- node shims ----------
                + "var __mf_node_crypto={createHash:function(algo){var parts=[];return{update:function(d){parts.push(typeof d==='string'?__mf_utf8_encode(d):__mf_latin1_from(d));return this},digest:function(enc){var hex=__mf_native_hash_bin(algo,__mf_native_b64_of_latin1(parts.join('')));if(enc==='hex'||!enc)return hex;if(enc==='base64')return __mf_native_b64_of_latin1(__mf_latin1_of_hex(hex));return Buffer.from(hex,'hex')}}},createHmac:function(algo,key){var parts=[];var kb=__mf_native_b64_of_latin1(typeof key==='string'?__mf_utf8_encode(key):__mf_latin1_from(key));return{update:function(d){parts.push(typeof d==='string'?__mf_utf8_encode(d):__mf_latin1_from(d));return this},digest:function(enc){var hex=__mf_native_hmac_bin(algo,kb,__mf_native_b64_of_latin1(parts.join('')));if(enc==='hex'||!enc)return hex;if(enc==='base64')return __mf_native_b64_of_latin1(__mf_latin1_of_hex(hex));return Buffer.from(hex,'hex')}}},randomBytes:function(n){return __mf_buf(atob(__mf_native_random_bytes(n)))},createCipheriv:function(algo,key,iv){var m=String(algo).match(/^(aes|des|des3|desede)-?\\d*-(\\w+)$/i);var a=m?m[1].toUpperCase():'AES';var mode=m?m[2].toUpperCase():'CBC';var kb=__mf_latin1_from(key);var ivb=iv?__mf_latin1_from(iv):null;var buf='';return{update:function(d,inEnc,outEnc){var bin=typeof d==='string'?__mf_utf8_encode(d):__mf_latin1_from(d);var pad=__mf_pad(bin,(a==='AES'?16:8),CryptoJS.pad.Pkcs7);buf=atob(__mf_cipher_run('encrypt',a==='DES3'||a==='DESEDE'?'DESede':a,mode,kb,ivb,pad));return outEnc==='hex'?__mf_hex_of_latin1(buf):(outEnc==='base64'?__mf_native_b64_of_latin1(buf):__mf_buf(buf))},final:function(){return buf}}},createDecipheriv:function(algo,key,iv){var m=String(algo).match(/^(aes|des|des3|desede)-?\\d*-(\\w+)$/i);var a=m?m[1].toUpperCase():'AES';var mode=m?m[2].toUpperCase():'CBC';var kb=__mf_latin1_from(key);var ivb=iv?__mf_latin1_from(iv):null;var out='';return{update:function(d,inEnc){var bin=typeof d==='string'?(inEnc==='hex'?__mf_latin1_of_hex(d):(inEnc==='base64'?atob(d):__mf_utf8_encode(d))):(d&&d.__bytes!==undefined?d.__bytes:__mf_latin1_from(d));var pt=atob(__mf_cipher_run('decrypt',a==='DES3'||a==='DESEDE'?'DESede':a,mode,kb,ivb,bin));out=__mf_unpad(pt,CryptoJS.pad.Pkcs7);return __mf_buf(out)},final:function(enc){return enc?__mf_buf(out).toString(enc):__mf_buf(out)}}},getRandomValues:function(arr){var b=atob(__mf_native_random_bytes(arr.length));for(var i=0;i<arr.length;i++)arr[i]=b.charCodeAt(i);return arr}};\n"
                + "function __mf_EventEmitter(){this._l={}}__mf_EventEmitter.prototype.on=function(e,f){(this._l[e]=this._l[e]||[]).push(f);return this};__mf_EventEmitter.prototype.once=function(e,f){var s=this;var g=function(){s.off(e,g);f.apply(s,arguments)};return s.on(e,g)};__mf_EventEmitter.prototype.off=function(e,f){if(this._l[e])this._l[e]=this._l[e].filter(function(x){return x!==f});return this};__mf_EventEmitter.prototype.removeListener=__mf_EventEmitter.prototype.off;__mf_EventEmitter.prototype.emit=function(e){var a=[].slice.call(arguments,1);(this._l[e]||[]).slice().forEach(function(f){try{f.apply(this,a)}catch(x){}});return true};__mf_EventEmitter.prototype.removeAllListeners=function(){this._l={};return this};\n"
                + "var __mf_path={join:function(){return[].slice.call(arguments).filter(Boolean).join('/').replace(/\\/+/g,'/')},resolve:function(){return[].slice.call(arguments).filter(Boolean).join('/')},basename:function(p){return String(p).split('/').pop()},dirname:function(p){var i=String(p).lastIndexOf('/');return i<0?'.':String(p).slice(0,i)},extname:function(p){var b=String(p).split('/').pop();var i=b.lastIndexOf('.');return i<0?'':b.slice(i)},sep:'/'};\n"
                + "var __mf_util={inspect:function(v){try{return JSON.stringify(v)}catch(e){return String(v)}},format:function(f){var a=[].slice.call(arguments,1),i=0;return String(f).replace(/%[sdjifoO]/g,function(m){var v=a[i++];return(m==='%j'||m==='%o'||m==='%O')?JSON.stringify(v):String(v)})},promisify:function(fn){return function(){var a=[].slice.call(arguments),self=this;return new Promise(function(res,rej){fn.apply(self,a.concat([function(e,r){e?rej(e):res(r)}]))})}},inherits:function(c,s){c.prototype=Object.create(s.prototype);c.prototype.constructor=c}};\n"
                + "var __mf_base_require=require;\n"
                + "require=function(name){"
                + "if(name==='crypto'||name==='node:crypto')return __mf_node_crypto;"
                + "if(name==='buffer'||name==='node:buffer')return{Buffer:Buffer};"
                + "if(name==='url'||name==='node:url')return{URL:URL,URLSearchParams:URLSearchParams};"
                + "if(name==='path'||name==='node:path')return __mf_path;"
                + "if(name==='util'||name==='node:util')return __mf_util;"
                + "if(name==='os'||name==='node:os')return{platform:function(){return 'android'},EOL:'\\n',arch:function(){return 'arm64'}};"
                + "if(name==='events'||name==='node:events')return{EventEmitter:__mf_EventEmitter,default:__mf_EventEmitter};"
                + "if(name==='form-data')return FormData;"
                + "if(name==='js-base64'||name==='base-64')return{Base64:{encode:function(s){return btoa(__mf_utf8_encode(String(s)))},decode:function(s){return __mf_utf8_decode(atob(String(s)))},encodeURI:function(s){return btoa(__mf_utf8_encode(String(s))).replace(/\\+/g,'-').replace(/\\//g,'_').replace(/=+$/,'')}}};"
                + "if(name==='base64-js')return{toByteArray:function(s){var b=atob(String(s)),a=[];for(var i=0;i<b.length;i++)a.push(b.charCodeAt(i));return a},fromByteArray:function(a){var s='';for(var i=0;i<a.length;i++)s+=String.fromCharCode(a[i]&255);return btoa(s)}};"
                + "if(name==='react-native')return{Dimensions:Dimensions,Platform:Platform,NativeModules:{},ToastAndroid:{show:function(){},SHORT:0,LONG:1}};"
                + "if(name&&String(name).slice(0,5)==='node:')return{};"
                + "return __mf_base_require(name)};\n"
                + "var __musicfree_require=require;\n";
    }

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
