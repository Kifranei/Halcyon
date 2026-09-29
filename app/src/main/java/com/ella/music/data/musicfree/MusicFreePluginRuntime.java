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
    private QuickJSContext jsContext;
    private String callResult;

    public MusicFreePluginRuntime(Context context, OkHttpClient client) {
        this.client = client;
    }

    public JSONArray search(String script, String keyword, int page) throws Exception {
        load(script);
        callResult = null;
        jsContext.getGlobalObject().getJSFunction("__mf_call_search").call(keyword, page);
        waitFor(() -> callResult != null, 25_000L);
        JSONObject result = readResult("搜索失败");
        JSONObject value = result.optJSONObject("value");
        if (value == null) return new JSONArray();
        JSONArray data = value.optJSONArray("data");
        return data == null ? new JSONArray() : data;
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
                + "var module={exports:{}}; var exports=module.exports; var global=globalThis;\n"
                + "function setTimeout(fn){ if (typeof fn==='function') fn(); return 0; }\n"
                + "function clearTimeout(){}\n"
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
                + "function __mf_word(text){return {__text:String(text==null?'':text),toString:function(enc){if(enc===CryptoJS.enc.Base64)return __mf_native_b64(this.__text);if(enc===CryptoJS.enc.Hex){var h='';for(var i=0;i<this.__text.length;i++)h+=('0'+this.__text.charCodeAt(i).toString(16)).slice(-2);return h}return this.__text}}}\n"
                + "function __mf_to_text(v){return v&&v.__text!==undefined?v.__text:String(v==null?'':v)}\n"
                + "function __mf_digest(algo,v){return {toString:function(enc){var hex=__mf_native_hash(algo,__mf_to_text(v));return enc===CryptoJS.enc.Utf8?__mf_native_hex_to_text(hex):hex}}}\n"
                + "var CryptoJS={enc:{Utf8:{parse:function(v){return __mf_word(v)},stringify:function(v){return __mf_to_text(v)}},Hex:{parse:function(v){return __mf_word(__mf_native_hex_to_text(String(v)))},stringify:function(v){return __mf_word(v).toString(CryptoJS.enc.Hex)}},Base64:{parse:function(v){return __mf_word(__mf_native_b64_decode(String(v)))},stringify:function(v){return __mf_native_b64(__mf_to_text(v))}}},"
                + "MD5:function(v){return __mf_digest('MD5',v)},"
                + "SHA1:function(v){return __mf_digest('SHA-1',v)},"
                + "SHA256:function(v){return __mf_digest('SHA-256',v)},"
                + "HmacMD5:function(v,k){return {toString:function(){return __mf_native_hmac('MD5',__mf_to_text(k),__mf_to_text(v))}}},"
                + "HmacSHA1:function(v,k){return {toString:function(){return __mf_native_hmac('SHA-1',__mf_to_text(k),__mf_to_text(v))}}},"
                + "HmacSHA256:function(v,k){return {toString:function(){return __mf_native_hmac('SHA-256',__mf_to_text(k),__mf_to_text(v))}}}};\n"
                + "CryptoJS.default=CryptoJS;\n"
                + "var env={appVersion:'1.0.0',os:'android',lang:'zh-CN',getUserVariables:function(){return {}},get userVariables(){return this.getUserVariables()}};\n"
                + "var process={platform:'android',version:'1.0.0',env:{}};\n"
                + "function __mf_decode_html(s){return String(s==null?'':s).replace(/&#(\\d+);/g,function(_,n){return String.fromCharCode(parseInt(n,10))}).replace(/&#x([0-9a-fA-F]+);/g,function(_,n){return String.fromCharCode(parseInt(n,16))}).replace(/&quot;/g,'\\\"').replace(/&apos;/g,\"'\").replace(/&#39;/g,\"'\").replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&nbsp;/g,' ')}\n"
                + "function __mf_encode_html(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\\\"/g,'&quot;').replace(/'/g,'&#39;')}\n"
                + "var he={decode:__mf_decode_html,encode:__mf_encode_html}; he.default=he;\n"
                + "function __mf_strip_tags(s){return __mf_decode_html(String(s||'').replace(/<script[\\s\\S]*?<\\/script>/gi,'').replace(/<style[\\s\\S]*?<\\/style>/gi,'').replace(/<[^>]+>/g,''));}\n"
                + "function __mf_has_class(node,c){var m=String(node||'').match(/class=[\\\"']([^\\\"']*)/i);return !!(m&&(' '+m[1]+' ').indexOf(' '+c+' ')>=0);}\n"
                + "function __mf_find_nodes(html,selector){html=String(html||'');selector=String(selector||'').trim();if(!selector)return [html];var m=selector.match(/^([a-zA-Z][\\w-]*)(?:\\.([\\w-]+))?$/);var tag=m&&m[1];var cls=m&&m[2];var out=[];if(tag){var re=new RegExp('<'+tag+'\\\\b[^>]*>[\\\\s\\\\S]*?<\\\\/'+tag+'>','gi');var x;while((x=re.exec(html))){if(!cls||__mf_has_class(x[0],cls))out.push(x[0]);}}else if(selector.charAt(0)==='.') {var c=selector.slice(1);var re2=/<([a-zA-Z][\\w-]*)\\b[^>]*>[\\s\\S]*?<\\/\\1>/gi;var y;while((y=re2.exec(html))){if(__mf_has_class(y[0],c))out.push(y[0]);}}return out.length?out:[html];}\n"
                + "function __mf_collection(nodes){nodes=nodes||[];return {length:nodes.length,text:function(){return nodes.map(__mf_strip_tags).join('')},html:function(){return nodes[0]||''},attr:function(name){var attrs=String(nodes[0]||'').match(/[\\w:-]+=[\\\"'][^\\\"']*[\\\"']/g)||[];for(var i=0;i<attrs.length;i++){var p=attrs[i].match(/^([\\w:-]+)=[\\\"']([^\\\"']*)/);if(p&&p[1]===String(name))return __mf_decode_html(p[2]);}return undefined},each:function(fn){nodes.forEach(function(n,i){fn&&fn.call(__mf_collection([n]),i,__mf_collection([n]))});return this},map:function(fn){var arr=nodes.map(function(n,i){return fn&&fn.call(__mf_collection([n]),i,__mf_collection([n]))});return {get:function(){return arr},toArray:function(){return arr}}},get:function(i){return i==null?nodes:nodes[i]},toArray:function(){return nodes}}}\n"
                + "var cheerio={load:function(html){var root=String(html||'');var $=function(selector){return __mf_collection(__mf_find_nodes(root,selector))};$.root=function(){return __mf_collection([root])};return $;}}; cheerio.default=cheerio;\n"
                + "var dayjs=function(v){var d=v?new Date(v):new Date();return {format:function(){return d.toISOString()},unix:function(){return Math.floor(d.getTime()/1000)},valueOf:function(){return d.getTime()},toDate:function(){return d}}}; dayjs.default=dayjs;\n"
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

    private String bridge() {
        return ""
                + "function __mf_finish(ok,value,error){ __mf_native_result(JSON.stringify({ok:ok,value:value||null,error:error?String(error):''})); }\n"
                + "function __mf_call_search(query,page){ try{ var p=__mf_plugin&&__mf_plugin.search; if(!p) throw new Error('插件不支持搜索'); Promise.resolve(p.call(__mf_plugin,query,page,'music')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n"
                + "function __mf_call_media_source(itemJson,quality){ try{ var item=JSON.parse(itemJson); if(item.url){__mf_finish(true,{url:item.url},null);return;} var p=__mf_plugin&&__mf_plugin.getMediaSource; if(!p) throw new Error('插件不支持播放地址解析'); Promise.resolve(p.call(__mf_plugin,item,quality||'standard')).then(function(r){__mf_finish(true,r,null)},function(e){__mf_finish(false,null,e&&e.message||e)}); }catch(e){__mf_finish(false,null,e&&e.message||e)} }\n"
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
