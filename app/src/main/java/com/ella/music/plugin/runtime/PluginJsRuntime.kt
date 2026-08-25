package com.ella.music.plugin.runtime

import androidx.annotation.Keep
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.QuickJSContext

/**
 * Runs a Lyrico-format JS plugin on Halcyon's bundled quickjs-wrapper.
 *
 * The plugin/host contract is identical to Lyrico's native runtime: the JS side talks to the
 * host through `__lyricoHostCall(name, payloadJson)` and the same `Platform` bootstrap. We add a
 * small `__invoke(name, requestJson)` dispatcher so entry functions (searchSongs/getLyrics/…)
 * return a JSON string, matching what [com.ella.music.plugin.source] expects.
 *
 * NOT thread-safe: a [PluginJsRuntime] must be created and used from a single thread (callers run
 * it on a dedicated single-thread dispatcher and apply their own timeout).
 */
@Keep
class PluginJsRuntime(
    hostApi: QuickJsHostApi = QuickJsHostApi()
) : AutoCloseable {

    private val context: QuickJSContext = QuickJSContext.create().also { ctx ->
        ctx.globalObject.setProperty("__lyricoHostCall", JSCallFunction { args ->
            val name = args.getOrNull(0) as? String ?: ""
            val payload = args.getOrNull(1) as? String ?: "{}"
            hostApi.call(name, payload)
        })
        ctx.evaluate(HOST_API_BOOTSTRAP, HOST_FILENAME)
        ctx.evaluate(INVOKE_DISPATCHER, HOST_FILENAME)
    }

    fun eval(script: String, filename: String) {
        context.evaluate(script, filename)
    }

    /** Invoke a global entry function with a JSON request, returning its JSON-stringified result. */
    fun call(functionName: String, requestJson: String): String {
        val invoke: JSFunction = context.globalObject.getJSFunction("__invoke")
        val result = invoke.call(functionName, requestJson)
        return result as? String ?: "null"
    }

    override fun close() {
        runCatching { context.destroy() }
    }

    companion object {
        private const val HOST_FILENAME = "<halcyon-host>"

        @Volatile
        private var loaderInitialized = false

        /** Loads the native quickjs library once. Safe to call repeatedly. */
        fun ensureLoaded() {
            if (loaderInitialized) return
            synchronized(this) {
                if (loaderInitialized) return
                QuickJSLoader.init()
                loaderInitialized = true
            }
        }

        private const val INVOKE_DISPATCHER = """
            (function() {
              globalThis.__invoke = function(name, requestJson) {
                var fn = globalThis[name];
                if (typeof fn !== "function") return "null";
                var result = fn(JSON.parse(requestJson || "{}"));
                return JSON.stringify(result === undefined ? null : result);
              };
            })();
        """

        // Verbatim from the Lyrico plugin host so source plugins see the same Platform surface.
        private val HOST_API_BOOTSTRAP = """
            (function() {
              function hostCall(name, payload) {
                return JSON.parse(__lyricoHostCall(name, JSON.stringify(payload || {}))).value;
              }

              function normalizeOptions(options) {
                options = options || {};
                return {
                  headers: options.headers || {},
                  contentType: options.contentType,
                  connectTimeoutMs: options.connectTimeoutMs,
                  readTimeoutMs: options.readTimeoutMs,
                  followRedirects: options.followRedirects
                };
              }

              function normalizeBodyPayload(url, body, options) {
                options = normalizeOptions(options);
                return {
                  url: String(url || ""),
                  body: body == null ? "" : String(body),
                  bodyBase64: options.bodyBase64 || "",
                  bodyBytes: options.bodyBytes || null,
                  contentType: options.contentType || "application/json; charset=utf-8",
                  headers: options.headers || {},
                  connectTimeoutMs: options.connectTimeoutMs,
                  readTimeoutMs: options.readTimeoutMs,
                  followRedirects: options.followRedirects
                };
              }

              globalThis.app = {
                getInfo: function() {
                  return hostCall("app.info", {});
                },
                getUserAgent: function() {
                  return hostCall("app.userAgent", {});
                }
              };

              globalThis.runtime = {
                getInfo: function() {
                  return hostCall("runtime.info", {});
                }
              };

              globalThis.Platform = {
                app: globalThis.app,
                runtime: globalThis.runtime,

                cache: {
                  get: function(key) {
                    return hostCall("cache.get", {
                      key: String(key || "")
                    });
                  },
                  set: function(key, value, ttlMs) {
                    return hostCall("cache.set", {
                      key: String(key || ""),
                      value: value == null ? "" : String(value),
                      ttlMs: Number(ttlMs || 0)
                    });
                  },
                  remove: function(key) {
                    return hostCall("cache.remove", {
                      key: String(key || "")
                    });
                  },
                  clear: function() {
                    return hostCall("cache.clear", {});
                  }
                },

                crypto: {
                  md5: function(text) {
                    return hostCall("crypto.md5", {
                      text: String(text || "")
                    });
                  },
                  sha256: function(text) {
                    return hostCall("crypto.sha256", {
                      text: String(text || "")
                    });
                  },
                  aesEcbPkcs5EncryptBase64: function(text, key) {
                    return hostCall("crypto.aesEcbPkcs5EncryptBase64", {
                      text: String(text || ""),
                      key: String(key || "")
                    });
                  },
                  aesEcbPkcs5EncryptHex: function(text, key) {
                    return hostCall("crypto.aesEcbPkcs5EncryptHex", {
                      text: String(text || ""),
                      key: String(key || "")
                    });
                  },
                  aesEcbPkcs5DecryptBase64ToText: function(base64, key) {
                    return hostCall("crypto.aesEcbPkcs5DecryptBase64ToText", {
                      base64: String(base64 || ""),
                      key: String(key || "")
                    });
                  }
                },

                base64: {
                  encodeText: function(text) {
                    return hostCall("base64.encodeText", {
                      text: String(text || "")
                    });
                  },
                  decodeText: function(base64) {
                    return hostCall("base64.decodeText", {
                      base64: String(base64 || "")
                    });
                  },
                  dropBytes: function(base64, count) {
                    return hostCall("base64.dropBytes", {
                      base64: String(base64 || ""),
                      count: count || 0
                    });
                  },
                  decodeBytes: function(base64) {
                    return hostCall("base64.decodeBytes", {
                      base64: String(base64 || "")
                    });
                  },
                  encodeBytes: function(bytes) {
                    return hostCall("base64.encodeBytes", {
                      bytes: Array.from(bytes || [])
                    });
                  },
                  encodeUrlText: function(text) {
                    return hostCall("base64.encodeUrlText", {
                      text: String(text || "")
                    });
                  },
                  decodeUrlText: function(base64Url) {
                    return hostCall("base64.decodeUrlText", {
                      base64Url: String(base64Url || "")
                    });
                  },
                  encodeUrlBytes: function(bytes) {
                    return hostCall("base64.encodeUrlBytes", {
                      bytes: Array.from(bytes || [])
                    });
                  },
                  decodeUrlBytes: function(base64Url) {
                    return hostCall("base64.decodeUrlBytes", {
                      base64Url: String(base64Url || "")
                    });
                  },
                  toUrl: function(base64) {
                    return hostCall("base64.toUrl", {
                      base64: String(base64 || "")
                    });
                  },
                  fromUrl: function(base64Url) {
                    return hostCall("base64.fromUrl", {
                      base64Url: String(base64Url || "")
                    });
                  }
                },

                bytes: {
                  xor: function(bytes, key) {
                    return hostCall("bytes.xor", {
                      bytes: Array.from(bytes || []),
                      key: Array.from(key || [])
                    });
                  },
                  xorBase64: function(base64, key) {
                    return hostCall("bytes.xorBase64", {
                      base64: String(base64 || ""),
                      key: Array.from(key || [])
                    });
                  }
                },

                compression: {
                  inflateBytesToText: function(bytes) {
                    return hostCall("compression.inflateBytesToText", {
                      bytes: Array.from(bytes || [])
                    });
                  },
                  inflateBase64ToText: function(base64) {
                    return hostCall("compression.inflateBase64ToText", {
                      base64: String(base64 || "")
                    });
                  }
                },

                http: {
                  getText: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.getText", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  postText: function(url, body, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.postText", {
                      url: String(url || ""),
                      body: body == null ? "" : String(body),
                      contentType: options.contentType || "application/json; charset=utf-8",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  postBytes: function(url, body, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.postBytes", {
                      url: String(url || ""),
                      body: body == null ? "" : String(body),
                      contentType: options.contentType || "application/octet-stream",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  get: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.get", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  post: function(url, body, options) {
                    return hostCall(
                      "http.post",
                      normalizeBodyPayload(url, body, options)
                    );
                  },

                  getBytes: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.getBytes", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  postBytesResponse: function(url, body, options) {
                    options = normalizeOptions(options);
                    var payload = normalizeBodyPayload(url, body, options);
                    payload.contentType = options.contentType || "application/octet-stream";
                    return hostCall("http.postBytesResponse", payload);
                  }
                },

                xml: {
                  getRootAttributes: function(xml) {
                    return hostCall("xml.getRootAttributes", {
                      xml: String(xml || "")
                    });
                  },

                  findElements: function(xml, query) {
                    return hostCall("xml.findElements", {
                      xml: String(xml || ""),
                      query: query || {}
                    });
                  },

                  replaceChildrenByAttr: function(xml, options) {
                    return hostCall("xml.replaceChildrenByAttr", {
                      xml: String(xml || ""),
                      options: options || {}
                    });
                  },

                  removeElements: function(xml, query) {
                    return hostCall("xml.removeElements", {
                      xml: String(xml || ""),
                      query: query || {}
                    });
                  }
                },

                log: {
                  debug: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.debug", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  },
                  warn: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.warn", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  },
                  error: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.error", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  }
                }
              };
            })();
        """
    }
}
