# Native metadata sources

- TagLib: [v2.3.2](https://github.com/taglib/taglib/tree/v2.3.2), commit
  `deadc2990767dfbda0701e0ab35fdeea653db08f`.
  The bundled library sources retain the upstream LGPL-2.1-or-later / MPL-1.1
  dual licensing; see `taglib/COPYING.LGPL` and `taglib/COPYING.MPL`.
- utfcpp: [v4.1.1](https://github.com/nemtrif/utfcpp/tree/v4.1.1), commit
  `819011bb01628fe1aa2f1da9f2c842a48fd5680b`, under the Boost Software License 1.0;
  see `taglib/3rdparty/utfcpp/LICENSE`.
- Lyrico JNI fixes: [v1.6.0](https://github.com/Replica0110/Lyrico/tree/v1.6.0),
  commit `ad9c83f5e78561edf60cfae762556583f359a395`, Apache-2.0.

The TagLib update was merged against its v2.3 base. Existing backported fixes
and missing standard-library includes were all present in v2.3.2. Upstream
tests, examples and unused C bindings are not bundled.

Halcyon's JNI bridge retains its file-descriptor seek resets, additional format
detection and metadata compatibility. It incorporates Lyrico's checked JNI
initialization, released UTF-8 string buffers and empty audio-property fallback,
with cleanup for partial initialization and temporary JNI references.

Content detection checks that a tag object exists before inspecting properties
of an invalid file. An MP4 header without a complete `moov` box can otherwise
reach `MP4::File::properties()` with a null tag and crash the process. The fallback
for files with surviving tags is preserved.

For arm64 builds, the module defaults to its checked-in `liblyrico_taglib.so`.
After native source updates, build with `-PellaBuildNative=true -PellaAbi=arm64-v8a`
and replace that prebuilt with the freshly built release library. Updating the
sources alone does not change the packaged arm64 native code.
