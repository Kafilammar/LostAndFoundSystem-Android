package com.example.lostandfoundsystem;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ضغط الصورة وتخزينها كنص Base64 صغير (أقل بكثير من حد 1MB لمستند Firestore)،
 * وتحميلها في الواجهات مع ذاكرة مؤقتة ومنع ظهور صورة خاطئة داخل القوائم.
 */
public final class ImageBase64Utils {
    private static final int MAX_BASE64_CHARS = 300_000;
    private static final int INITIAL_MAX_DIMENSION = 900;
    private static final int MIN_MAX_DIMENSION = 480;
    private static final int INITIAL_QUALITY = 72;
    private static final int MIN_QUALITY = 40;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ImageBase64Utils() {}

    public interface EncodeCallback {
        void onSuccess(String base64);
        void onError(Exception error);
    }

    /** الضغط في الخلفية، والنتيجة تُعاد على الخيط الرئيسي. */
    public static void encodeAsync(Context context, Uri uri, EncodeCallback callback) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                String result = encode(app.getContentResolver(), uri);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e));
            }
        });
    }

    public static String encode(ContentResolver resolver, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("لم يتم اختيار صورة");

        Bitmap bitmap = decodeScaled(resolver, uri, INITIAL_MAX_DIMENSION);
        int quality = INITIAL_QUALITY;
        try {
            while (true) {
                String encoded = compress(bitmap, quality);
                if (encoded.length() <= MAX_BASE64_CHARS) return encoded;

                if (quality > MIN_QUALITY) {
                    quality -= 8;
                    continue;
                }
                int currentMax = Math.max(bitmap.getWidth(), bitmap.getHeight());
                int nextMax = Math.max(MIN_MAX_DIMENSION, currentMax - 120);
                if (nextMax >= currentMax) {
                    throw new IllegalArgumentException("الصورة كبيرة جدًا. اختر صورة أصغر.");
                }
                Bitmap smaller = scaleDown(bitmap, nextMax);
                if (smaller != bitmap) bitmap.recycle();
                bitmap = smaller;
                quality = INITIAL_QUALITY;
            }
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static Bitmap decodeScaled(ContentResolver resolver, Uri uri, int maxDimension) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("تعذر فتح الصورة");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("ملف الصورة غير صالح");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);

        Bitmap bitmap;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("تعذر فتح الصورة");
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IllegalArgumentException("تعذر قراءة الصورة");

        bitmap = rotateByExif(resolver, uri, bitmap);
        return scaleDown(bitmap, maxDimension);
    }

    /** تصحيح اتجاه صور الكاميرا (كانت تظهر مقلوبة أو مائلة). */
    private static Bitmap rotateByExif(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return bitmap;
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int deg = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (deg == 0) return bitmap;
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception ignored) {
            return bitmap;
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        int larger = Math.max(width, height);
        while (larger / (sample * 2) >= maxDimension) sample *= 2;
        return Math.max(1, sample);
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int larger = Math.max(width, height);
        if (larger <= maxDimension) return bitmap;
        float scale = maxDimension / (float) larger;
        return Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    private static String compress(Bitmap bitmap, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private static String cacheKey(String value) {
        return value.length() + ":" + value.hashCode();
    }

    private static void showPlaceholder(ImageView target, int placeholderRes) {
        target.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        target.setImageResource(placeholderRes);
    }

    /** عرض صورة Base64 داخل ImageView، مع صورة بديلة عند عدم وجودها. */
    public static void loadInto(ImageView target, String base64, int placeholderRes) {
        final String value = base64 == null ? "" : base64.trim();
        target.setTag(R.id.tag_image_key, value);
        if (value.isEmpty()) {
            showPlaceholder(target, placeholderRes);
            return;
        }
        final String key = cacheKey(value);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setScaleType(ImageView.ScaleType.CENTER_CROP);
            target.setImageBitmap(cached);
            return;
        }
        showPlaceholder(target, placeholderRes);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] bytes = Base64.decode(value, Base64.DEFAULT);
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {
            }
            final Bitmap result = bitmap;
            if (result != null) CACHE.put(key, result);
            MAIN.post(() -> {
                if (result != null && value.equals(target.getTag(R.id.tag_image_key))) {
                    target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    target.setImageBitmap(result);
                }
            });
        });
    }

    /** عرض صورة مختارة من الجهاز (قبل الرفع). */
    public static void showLocal(ImageView target, Uri uri) {
        target.setTag(R.id.tag_image_key, uri.toString());
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        target.setImageURI(null);
        target.setImageURI(uri);
    }
}
