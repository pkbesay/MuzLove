package com.example.muzlove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Фото профиля: копия в filesDir (имя avatar_*.jpg, в настройках — строка file://...), чтение с уменьшением
 *  и поворотом по EXIF, удаление. Битый или пропавший файл — просто null (экран рисует букву). Главный поток. */
@SuppressLint("ExifInterface")       // androidx.exifinterface не подключён, а android.media читает файл с API 24
final class Avatar {

    private static String cachePath;
    private static long cacheStamp;
    private static int cacheSize;
    private static Bitmap cache;

    private Avatar() {}

    /** Копирует выбранную картинку во внутреннюю память; возвращает строку для StorageManager.setAvatarUri. */
    static String save(Context c, Uri src) throws IOException {
        File dest = new File(c.getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
        try (InputStream in = c.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("no stream");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException | RuntimeException e) {
            dest.delete();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(dest.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {      // не картинка
            dest.delete();
            throw new IOException("not an image");
        }
        return Uri.fromFile(dest).toString();
    }

    /** Файл аватара из сохранённой строки, если он лежит в filesDir; иначе null. */
    static File file(Context c, String stored) {
        if (stored == null || stored.isEmpty()) return null;
        try {
            Uri u = Uri.parse(stored);
            String scheme = u.getScheme();
            if (scheme != null && !scheme.equals("file")) return null;
            String path = u.getPath();
            if (path == null) return null;
            File f = new File(path).getCanonicalFile();
            String dir = c.getFilesDir().getCanonicalPath() + File.separator;
            return f.getPath().startsWith(dir) ? f : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Файл аватара на месте. */
    static boolean exists(Context c, String stored) {
        File f = file(c, stored);
        return f != null && f.isFile();
    }

    /** Картинка не меньше sizePx по короткой стороне (насколько позволяет исходник) или null. */
    static Bitmap load(Context c, String stored, int sizePx) {
        File f = file(c, stored);
        if (f == null || !f.isFile() || f.length() == 0) return null;
        String path = f.getPath();
        if (cache != null && path.equals(cachePath) && f.lastModified() == cacheStamp && sizePx == cacheSize)
            return cache;
        Bitmap bmp = decode(path, sizePx);
        if (bmp != null) {
            cache = bmp; cachePath = path; cacheStamp = f.lastModified(); cacheSize = sizePx;
        }
        return bmp;
    }

    private static Bitmap decode(String path, int sizePx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= sizePx) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, o);
            if (bmp == null) return null;
            int degrees = rotation(path);
            if (degrees == 0) return bmp;
            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            return rotated;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static int rotation(String path) {
        try {
            switch (new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** Удаляет файл аватара (только внутри filesDir). */
    static void delete(Context c, String stored) {
        File f = file(c, stored);
        if (f != null && f.isFile()) f.delete();
        cache = null; cachePath = null;
    }
}
