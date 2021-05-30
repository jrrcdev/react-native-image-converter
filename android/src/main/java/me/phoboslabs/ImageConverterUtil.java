package me.phoboslabs;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.facebook.react.modules.network.OkHttpClientProvider;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * @author kellin.me (Lee Kyoungil) [mailto:leekyoungil@gmail.com]
 */
public class ImageConverterUtil {
    private final static String IMAGE_JPEG = "image/jpeg";
    private final static String IMAGE_PNG = "image/png";
    private final static String SCHEME_DATA = "data";
    private final static String SCHEME_HTTP = "http";
    private final static String SCHEME_HTTPS = "https";

    /**
     * Load a Bitmap from a Uri which can either be Base64 encoded or a path to a file or content scheme
     */
    @Nullable
    public static Bitmap getSourceImageByPath(final Context context, final Uri imageUri) throws Exception {
        if (imageUri == null) {
            throw new Exception("imageURI must not be null.");
        }

        Bitmap sourceImage = null;
        BitmapFactory.Options options = new BitmapFactory.Options();

        final String imageUriScheme = imageUri.getScheme();
        if (imageUriScheme == null || ContentResolver.SCHEME_CONTENT.equals(imageUriScheme) || ContentResolver.SCHEME_FILE.equals(imageUriScheme)) {
            sourceImage = loadBitmapFromFile(context, imageUri, options);
        } else if (SCHEME_DATA.equals(imageUriScheme)) {
            sourceImage = loadBitmapFromBase64(imageUri, options);
        } else if (SCHEME_HTTP.equals(imageUriScheme) || SCHEME_HTTPS.equals(imageUriScheme)) {
            sourceImage = loadBitmapFromURL(imageUri, options);
        }

        return sourceImage;
    }

    /**
     * Load a Bitmap using the {@link ContentResolver} of the current
     * {@link Context} (for real files or gallery images for example).
     *
     * Note that, when options.inJustDecodeBounds = true, we actually expect sourceImage to remain
     * as null (see https://developer.android.com/training/displaying-bitmaps/load-bitmap.html), so
     * getting null sourceImage at the completion of this method is not always worthy of an error.
     *
     * Suppress the try-with-resources warning since Android Studio 3.0 extends support to all API levels.
     * See https://developer.android.com/studio/write/java8-support.html#supported_features
     */
    @SuppressLint("NewApi")
    @Nullable
    private static Bitmap loadBitmapFromFile(final Context context, final Uri imageUri,
                                             @NonNull final BitmapFactory.Options options) throws Exception {
        Bitmap sourceImage = null;
        final ContentResolver cr = context.getContentResolver();
        try (final InputStream input = cr.openInputStream(imageUri)) {
            if (input != null) {
                sourceImage = BitmapFactory.decodeStream(input, null, options);
                input.close();
            }
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Unable to load image into Bitmap: " + e.getMessage());
        }

        return sourceImage;
    }

    /**
     * Load a Bitmap from a Base64 encoded jpg or png.
     * Format is as such:
     * png: 'data:image/png;base64,iVBORw0KGgoAA...'
     * jpg: 'data:image/jpeg;base64,/9j/4AAQSkZJ...'
     */
    @Nullable
    private static Bitmap loadBitmapFromBase64(@NonNull final Uri imageUri,
                                               @NonNull final BitmapFactory.Options options) {
        Bitmap sourceImage = null;
        final String imagePath = imageUri.getSchemeSpecificPart();
        final int commaLocation = imagePath.indexOf(',');
        if (commaLocation != -1) {
            final String mimeType = imagePath.substring(0, commaLocation).replace('\\','/').toLowerCase();
            final boolean isJpeg = mimeType.startsWith(IMAGE_JPEG);
            final boolean isPng = !isJpeg && mimeType.startsWith(IMAGE_PNG);

            if (isJpeg || isPng) {
                // base64 image. Convert to a bitmap.
                final String encodedImage = imagePath.substring(commaLocation + 1);
                final byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                sourceImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length, options);
            }
        }

        return sourceImage;
    }

    /**
     * Load a Bitmap from an http or https URL.
     */
    @Nullable
    public static Bitmap loadBitmapFromURL(@NonNull final Uri imageUri,
                                           @NonNull final BitmapFactory.Options options) throws IOException {
        Bitmap sourceImage = null;
        URL url = null;

        try {
            url = new URL(imageUri.toString());
        } catch (MalformedURLException e) {
            throw new MalformedURLException("Unable to load image from a malformed URL: " + e.getMessage());
        }

        OkHttpClient client = OkHttpClientProvider.getOkHttpClient();
        Request request = new Request.Builder()
            .url(url)
            .build();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response.code());
      
            InputStream input = response.body().byteStream();
            sourceImage = BitmapFactory.decodeStream(input);
            input.close();
        } catch (IOException e) {
            throw new IOException("Unable to download image", e);
        }

        return sourceImage;
    }

    public static Bitmap BITMAP_RESIZER(final Bitmap bitmap, final int newWidth, final  int newHeight) {    
        Bitmap scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
    
        float ratioX = newWidth / (float) bitmap.getWidth();
        float ratioY = newHeight / (float) bitmap.getHeight();
        float middleX = newWidth / 2.0f;
        float middleY = newHeight / 2.0f;
    
        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);
    
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2, middleY - bitmap.getHeight() / 2, paint);
    
        return scaledBitmap;
    }

    public static Bitmap getImageByResize(final Bitmap image, final int width, final int height, final boolean reuseInputImage) throws Exception {
        if (image == null) {
            throw new Exception("image must not be null.");
        }
        try {
            //final int width = (int)(image.getWidth() * resizeRatio);
            //final int height = (int)(image.getHeight() * resizeRatio);
            Bitmap resultImage = Bitmap.createScaledBitmap(image, width, height, true);
            if (reuseInputImage == false) {
                image.recycle();
            }
            return resultImage;
        } catch (OutOfMemoryError ex) {
            throw ex;
        }
    }

    public static void saveImageFile(final Bitmap image, final File savePath,
                                     final Bitmap.CompressFormat compressFormat, final float imageQuality) throws Exception {
        if (image == null) {
            throw new Exception("image must not be null.");
        }

        if (savePath.createNewFile() == false) {
            throw new IOException("image file already exists.");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        image.compress(compressFormat, (int)(imageQuality * 100), outputStream);

        byte[] imageDataArray = outputStream.toByteArray();

        outputStream.flush();
        outputStream.close();

        FileOutputStream fos = new FileOutputStream(savePath);
        fos.write(imageDataArray);
        fos.flush();
        fos.close();

        image.recycle();
    }

    private static final float[] GRAYSCALE_MATRIX = new float[]{0.3f, 0.59f, 0.11f, 0, 0, 0.3f, 0.59f, 0.11f, 0, 0, 0.3f, 0.59f, 0.11f, 0, 0, 0, 0, 0, 1, 0,};

    public static Bitmap imageToGrayscale(Bitmap originSourceImage) {
        Bitmap convertedImage = Bitmap.createBitmap(originSourceImage.getWidth(), originSourceImage.getHeight(), originSourceImage.getConfig());

        Canvas canvas = new Canvas(convertedImage);
        Paint paint = new Paint();

        ColorMatrixColorFilter colorMatrixColorFilter = new ColorMatrixColorFilter(GRAYSCALE_MATRIX);
        paint.setColorFilter(colorMatrixColorFilter);
        canvas.drawBitmap(originSourceImage, 0, 0, paint);

        originSourceImage.recycle();

        return convertedImage;
    }

    public static String getBase64FromBitmap(Bitmap bitmap, final Bitmap.CompressFormat compressFormat) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, 100, byteArrayOutputStream);
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
    }
}
