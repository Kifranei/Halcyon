package androidx.media3.session;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.legacy.MediaMetadataCompat;
import androidx.media3.session.legacy.MediaSessionCompat;

/** Adds the legacy ART bitmap omitted by Media3's platform metadata conversion. */
public final class LegacyArtworkCompat {
    private static final int ARTWORK_SIZE_PX = 500;
    private static Bitmap cachedSource;
    private static Bitmap cachedArtwork;

    private LegacyArtworkCompat() {}

    public static synchronized void clear() {
        cachedSource = null;
        cachedArtwork = null;
    }

    public static void publish(
            MediaSession session,
            MediaMetadata metadata,
            String mediaId,
            long durationMs,
            Bitmap source
    ) {
        MediaSessionCompat sessionCompat = session
                .getImpl()
                .getMediaSessionLegacyStub()
                .getSessionCompat();
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId);
        if (metadata.title != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title);
        }
        if (metadata.displayTitle != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, metadata.displayTitle);
        }
        if (metadata.subtitle != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, metadata.subtitle);
        }
        if (metadata.description != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, metadata.description);
        }
        if (metadata.artist != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.artist);
        }
        if (metadata.albumTitle != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.albumTitle);
        }
        if (metadata.albumArtist != null) {
            builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, metadata.albumArtist);
        }
        if (metadata.recordingYear != null) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, metadata.recordingYear);
        }
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        Bundle extras = metadata.extras;
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value == null || value instanceof CharSequence) {
                    builder.putText(key, (CharSequence) value);
                } else if (value instanceof Number) {
                    builder.putLong(key, ((Number) value).longValue());
                }
            }
        }
        Bitmap artwork = centerCrop(source);
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork);
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork);
        sessionCompat.setMetadata(builder.build());
    }

    private static synchronized Bitmap centerCrop(Bitmap source) {
        if (source == cachedSource && cachedArtwork != null && !cachedArtwork.isRecycled()) {
            return cachedArtwork;
        }
        if (source.getWidth() == ARTWORK_SIZE_PX && source.getHeight() == ARTWORK_SIZE_PX) {
            cachedSource = source;
            cachedArtwork = source;
            return source;
        }
        Bitmap result = Bitmap.createBitmap(
                ARTWORK_SIZE_PX,
                ARTWORK_SIZE_PX,
                Bitmap.Config.ARGB_8888
        );
        float scale = Math.max(
                ARTWORK_SIZE_PX / (float) source.getWidth(),
                ARTWORK_SIZE_PX / (float) source.getHeight()
        );
        float dx = (ARTWORK_SIZE_PX - source.getWidth() * scale) * 0.5f;
        float dy = (ARTWORK_SIZE_PX - source.getHeight() * scale) * 0.5f;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, matrix, paint);
        canvas.setBitmap(null);
        cachedSource = source;
        cachedArtwork = result;
        return result;
    }
}
