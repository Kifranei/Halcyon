/* Select the matching FFmpeg-generated Android ABI configuration. */
#ifndef AVUTIL_ANDROID_AVCONFIG_H
#define AVUTIL_ANDROID_AVCONFIG_H
#if defined(__aarch64__)
#include "avconfig-arm64-v8a.h"
#elif defined(__arm__)
#include "avconfig-armeabi-v7a.h"
#elif defined(__i386__)
#include "avconfig-x86.h"
#elif defined(__x86_64__)
#include "avconfig-x86_64.h"
#else
#error Unsupported FFmpeg Android ABI
#endif
#endif /* AVUTIL_ANDROID_AVCONFIG_H */
