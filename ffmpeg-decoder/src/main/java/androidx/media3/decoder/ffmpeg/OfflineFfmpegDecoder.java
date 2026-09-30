package androidx.media3.decoder.ffmpeg;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;

/** Independent PCM decoder for offline analysis. Never attaches to the playback audio sink. */
@UnstableApi
public final class OfflineFfmpegDecoder implements AutoCloseable {
  private final FfmpegAudioDecoder decoder;

  public OfflineFfmpegDecoder(Format format) throws FfmpegDecoderException {
    decoder = new FfmpegAudioDecoder(format, 2, 2, 64 * 1024, true);
  }

  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() throws FfmpegDecoderException {
    return decoder.dequeueInputBuffer();
  }

  public void queueInputBuffer(DecoderInputBuffer buffer) throws FfmpegDecoderException {
    decoder.queueInputBuffer(buffer);
  }

  @Nullable
  public SimpleDecoderOutputBuffer dequeueOutputBuffer() throws FfmpegDecoderException {
    return decoder.dequeueOutputBuffer();
  }

  public int getChannelCount() { return decoder.getChannelCount(); }
  public int getSampleRate() { return decoder.getSampleRate(); }
  public int getEncoding() { return decoder.getEncoding(); }

  @Override
  public void close() { decoder.release(); }
}
