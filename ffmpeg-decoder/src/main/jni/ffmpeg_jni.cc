/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/log.h>
#include <jni.h>
#include <new>
#include <stdlib.h>
#include <string.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...)                               \
  extern "C" {                                                             \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(JNIEnv* env,    \
                                                           jobject thiz,   \
                                                           ##__VA_ARGS__); \
  }                                                                        \
  JNIEXPORT RETURN_TYPE                                                    \
  Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_##NAME(                \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define AUDIO_DECODER_FUNC(RETURN_TYPE, NAME, ...)               \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__);                 \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
  Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_##NAME( \
      JNIEnv* env, jobject thiz, ##__VA_ARGS__)

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// LINT.IfChange
static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java)

static jmethodID growOutputBufferMethod;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
const AVCodec* getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new decoder context for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
struct FfmpegDecoderContext {
  AVCodecContext* codecContext;
  AVPacket* packet;
  AVFrame* frame;
  SwrContext* resampleContext;
  // Input parameters the resampleContext was built for; the resampler is
  // rebuilt when the decoded frames' layout/format/rate change mid-stream.
  AVChannelLayout resampleInLayout;
  AVChannelLayout resampleOutLayout;
  AVSampleFormat resampleInFormat;
  int resampleInSampleRate;
};

FfmpegDecoderContext* createContext(JNIEnv* env, const AVCodec* codec,
                                    jbyteArray extraData,
                                    jboolean outputFloat,
                                    jint rawSampleRate,
                                    jint rawChannelCount);

struct GrowOutputBufferCallback {
  uint8_t* operator()(int requiredSize) const;

  JNIEnv* env;
  jobject thiz;
  jobject decoderOutputBuffer;
};

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(FfmpegDecoderContext* decoderContext, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char* functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(FfmpegDecoderContext* decoderContext);

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOGE("JNI_OnLoad: GetEnv failed");
    return -1;
  }
  jclass clazz =
      env->FindClass("androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder");
  if (!clazz) {
    LOGE("JNI_OnLoad: FindClass failed");
    return -1;
  }
  growOutputBufferMethod =
      env->GetMethodID(clazz, "growOutputBuffer",
                       "(Landroidx/media3/decoder/"
                       "SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
  if (!growOutputBufferMethod) {
    LOGE("JNI_OnLoad: GetMethodID failed");
    return -1;
  }
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

LIBRARY_FUNC(jint, ffmpegGetInputBufferPaddingSize) {
  return (jint)AV_INPUT_BUFFER_PADDING_SIZE;
}

LIBRARY_FUNC(jboolean, ffmpegHasDecoder, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

AUDIO_DECODER_FUNC(jlong, ffmpegInitialize, jstring codecName,
                   jbyteArray extraData, jboolean outputFloat,
                   jint rawSampleRate, jint rawChannelCount) {
  const AVCodec* codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount);
}

AUDIO_DECODER_FUNC(jint, ffmpegDecode, jlong context, jobject inputData,
                   jint inputSize, jobject decoderOutputBuffer,
                   jobject outputData, jint outputSize) {
  FfmpegDecoderContext* decoderContext = (FfmpegDecoderContext*)context;
  if (!decoderContext || !decoderContext->codecContext ||
      !decoderContext->packet || !decoderContext->frame) {
    LOGE("Context must be non-NULL.");
    return AUDIO_DECODER_ERROR_OTHER;
  }
  if (!inputData || !decoderOutputBuffer || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return AUDIO_DECODER_ERROR_OTHER;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return AUDIO_DECODER_ERROR_OTHER;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return AUDIO_DECODER_ERROR_OTHER;
  }
  uint8_t* inputBuffer = (uint8_t*)env->GetDirectBufferAddress(inputData);
  uint8_t* outputBuffer = (uint8_t*)env->GetDirectBufferAddress(outputData);
  if (!inputBuffer || !outputBuffer) {
    LOGE("Input and output buffers must be direct ByteBuffers.");
    return AUDIO_DECODER_ERROR_OTHER;
  }

  AVPacket* packet = decoderContext->packet;
  av_packet_unref(packet);
  packet->data = inputBuffer;
  packet->size = inputSize;
  const int ret =
      decodePacket(decoderContext, packet, outputBuffer, outputSize,
                   GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
  av_packet_unref(packet);
  return ret;
}

uint8_t* GrowOutputBufferCallback::operator()(int requiredSize) const {
  jobject newOutputData = env->CallObjectMethod(
      thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
  if (env->ExceptionCheck()) {
    LOGE("growOutputBuffer() failed");
    env->ExceptionDescribe();
    return nullptr;
  }
  return static_cast<uint8_t*>(env->GetDirectBufferAddress(newOutputData));
}

AUDIO_DECODER_FUNC(jint, ffmpegGetChannelCount, jlong context) {
  FfmpegDecoderContext* decoderContext = (FfmpegDecoderContext*)context;
  if (!decoderContext || !decoderContext->codecContext) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return decoderContext->codecContext->ch_layout.nb_channels;
}

AUDIO_DECODER_FUNC(jint, ffmpegGetSampleRate, jlong context) {
  FfmpegDecoderContext* decoderContext = (FfmpegDecoderContext*)context;
  if (!decoderContext || !decoderContext->codecContext) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return decoderContext->codecContext->sample_rate;
}

AUDIO_DECODER_FUNC(jlong, ffmpegReset, jlong jContext, jbyteArray extraData) {
  FfmpegDecoderContext* decoderContext = (FfmpegDecoderContext*)jContext;
  if (!decoderContext || !decoderContext->codecContext) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecContext* context = decoderContext->codecContext;
  AVCodecID codecId = context->codec_id;
  if (codecId == AV_CODEC_ID_TRUEHD) {
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(decoderContext);
    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1);
  }

  avcodec_flush_buffers(context);
  av_packet_unref(decoderContext->packet);
  av_frame_unref(decoderContext->frame);
  return (jlong)decoderContext;
}

AUDIO_DECODER_FUNC(void, ffmpegRelease, jlong context) {
  if (context) {
    releaseContext((FfmpegDecoderContext*)context);
  }
}

const AVCodec* getCodecByName(JNIEnv* env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char* codecNameChars = env->GetStringUTFChars(codecName, NULL);
  const AVCodec* codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

FfmpegDecoderContext* createContext(JNIEnv* env, const AVCodec* codec,
                                    jbyteArray extraData,
                                    jboolean outputFloat,
                                    jint rawSampleRate,
                                    jint rawChannelCount) {
  FfmpegDecoderContext* decoderContext = new (std::nothrow) FfmpegDecoderContext();
  if (!decoderContext) {
    LOGE("Failed to allocate decoder wrapper context.");
    return NULL;
  }
  decoderContext->codecContext = NULL;
  decoderContext->packet = av_packet_alloc();
  decoderContext->frame = av_frame_alloc();
  decoderContext->resampleContext = NULL;
  memset(&decoderContext->resampleInLayout, 0, sizeof(AVChannelLayout));
  memset(&decoderContext->resampleOutLayout, 0, sizeof(AVChannelLayout));
  decoderContext->resampleInFormat = AV_SAMPLE_FMT_NONE;
  decoderContext->resampleInSampleRate = 0;
  if (!decoderContext->packet || !decoderContext->frame) {
    LOGE("Failed to allocate reusable packet/frame.");
    releaseContext(decoderContext);
    return NULL;
  }

  AVCodecContext* context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    releaseContext(decoderContext);
    return NULL;
  }
  decoderContext->codecContext = context;
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t*)av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(decoderContext);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte*)context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    av_channel_layout_default(&context->ch_layout, rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(decoderContext);
    return NULL;
  }
  return decoderContext;
}

int decodePacket(FfmpegDecoderContext* decoderContext, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer) {
  AVCodecContext* context = decoderContext->codecContext;
  AVFrame* frame = decoderContext->frame;
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return transformError(result);
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  while (true) {
    av_frame_unref(frame);
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_unref(frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return transformError(result);
    }

    // Resample output. Input parameters come from the frame itself: for files
    // whose channel layout the container leaves unspecified, the codec context
    // can lag behind what the decoder actually produces.
    AVSampleFormat sampleFormat = (AVSampleFormat)frame->format;
    int sampleRate = frame->sample_rate;
    int sampleCount = frame->nb_samples;
    // The output layout is what ffmpegGetChannelCount reports to the Java side;
    // fall back to the frame layout when the codec context knows none.
    const AVChannelLayout* outLayout =
        context->ch_layout.nb_channels > 0 ? &context->ch_layout
                                           : &frame->ch_layout;
    int channelCount = outLayout->nb_channels;
    SwrContext* resampleContext = decoderContext->resampleContext;
    bool rebuildResampler =
        !resampleContext ||
        av_channel_layout_compare(&decoderContext->resampleInLayout,
                                  &frame->ch_layout) != 0 ||
        av_channel_layout_compare(&decoderContext->resampleOutLayout,
                                  outLayout) != 0 ||
        decoderContext->resampleInFormat != sampleFormat ||
        decoderContext->resampleInSampleRate != sampleRate;
    if (rebuildResampler) {
      if (resampleContext) {
        swr_free(&decoderContext->resampleContext);
        resampleContext = NULL;
      }
      result =
          swr_alloc_set_opts2(&resampleContext,             // ps
                              outLayout,                    // out_ch_layout
                              context->request_sample_fmt,  // out_sample_fmt
                              sampleRate,                   // out_sample_rate
                              &frame->ch_layout,            // in_ch_layout
                              sampleFormat,                 // in_sample_fmt
                              sampleRate,                   // in_sample_rate
                              0,                            // log_offset
                              NULL                          // log_ctx
      );
      if (result < 0) {
        logError("swr_alloc_set_opts2", result);
        swr_free(&resampleContext);
        av_frame_unref(frame);
        return transformError(result);
      }
      result = swr_init(resampleContext);
      if (result < 0) {
        logError("swr_init", result);
        swr_free(&resampleContext);
        av_frame_unref(frame);
        return transformError(result);
      }
      decoderContext->resampleContext = resampleContext;
      av_channel_layout_uninit(&decoderContext->resampleInLayout);
      av_channel_layout_uninit(&decoderContext->resampleOutLayout);
      av_channel_layout_copy(&decoderContext->resampleInLayout,
                             &frame->ch_layout);
      av_channel_layout_copy(&decoderContext->resampleOutLayout, outLayout);
      decoderContext->resampleInFormat = sampleFormat;
      decoderContext->resampleInSampleRate = sampleRate;
    }

    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = swr_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGD(
          "Output buffer size (%d) too small for output data (%d), "
          "reallocating buffer.",
          outputSize, outSize + bufferOutSize);
      outputSize = outSize + bufferOutSize;
      outputBuffer = growBuffer(outputSize);
      if (!outputBuffer) {
        LOGE("Failed to reallocate output buffer.");
        av_frame_unref(frame);
        return AUDIO_DECODER_ERROR_OTHER;
      }
    }
    // frame->data only stores 8 plane pointers; channels 9+ live in
    // extended_data (which also covers the <=8 case), so it must be used for
    // inputs with more than 8 channels.
    result = swr_convert(resampleContext, &outputBuffer, outSamples,
                         (const uint8_t**)frame->extended_data,
                         frame->nb_samples);
    av_frame_unref(frame);
    if (result < 0) {
      logError("swr_convert", result);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    int available = swr_get_out_samples(resampleContext, 0);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    int bytesWritten = outSampleSize * channelCount * result;
    outputBuffer += bytesWritten;
    outSize += bytesWritten;
  }
  return outSize;
}

int transformError(int errorNumber) {
  return errorNumber == AVERROR_INVALIDDATA ? AUDIO_DECODER_ERROR_INVALID_DATA
                                            : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char* functionName, int errorNumber) {
  char* buffer = (char*)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(FfmpegDecoderContext* decoderContext) {
  if (!decoderContext) {
    return;
  }
  if (decoderContext->resampleContext) {
    swr_free(&decoderContext->resampleContext);
  }
  av_channel_layout_uninit(&decoderContext->resampleInLayout);
  av_channel_layout_uninit(&decoderContext->resampleOutLayout);
  if (decoderContext->frame) {
    av_frame_free(&decoderContext->frame);
  }
  if (decoderContext->packet) {
    av_packet_free(&decoderContext->packet);
  }
  if (decoderContext->codecContext) {
    avcodec_free_context(&decoderContext->codecContext);
  }
  delete decoderContext;
}
