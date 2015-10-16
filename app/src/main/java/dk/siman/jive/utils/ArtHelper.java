/*
 * Copyright (c) 2015. Anders Nielsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package dk.siman.jive.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;

public class ArtHelper {
    private static final String TAG = LogHelper.makeLogTag(ArtHelper.class);

    // Resolution reasonable for carrying around as an icon (generally in
    // MediaDescription.getIconBitmap). This should not be bigger than necessary, because
    // the MediaDescription object should be lightweight. If you set it too high and try to
    // serialize the MediaDescription, you may get FAILED BINDER TRANSACTION errors.
    private static final int MAX_ART_WIDTH = 800;  // pixels
    private static final int MAX_ART_HEIGHT = 480;  // pixels
    private static final int MAX_ART_WIDTH_ICON = 170;  // pixels, 128 was default
    private static final int MAX_ART_HEIGHT_ICON = 170;  // pixels, 128 was default

    public static Bitmap getAlbumArt(Context context, Uri uri) {
        Bitmap bm = null;
        if (uri == null) {
            return null;
        }
        try {
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                bm = BitmapFactory.decodeFileDescriptor(fd);
            }
        } catch (FileNotFoundException e) {
            //LogHelper.i(TAG, "Song does not contain album art");
        } catch (NullPointerException e) {
            //LogHelper.i(TAG, "NullPointerException: " + e);
        }

        return bm;
    }

    public static boolean checkAlbumArt(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (pfd != null) {
                return true;
            }
        } catch (FileNotFoundException e) {
            //LogHelper.i(TAG, "Song does not contain album art");
        } catch (NullPointerException e) {
            //LogHelper.i(TAG, "NullPointerException: " + e);
        }

        return false;
    }

    public static Bitmap getScaleBitmap(Context context, Uri uri) {

        if (uri == null) {
            return null;
        }
        Bitmap bm = null;
        try {
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                bm = BitmapFactory.decodeFileDescriptor(fd);
            }
        } catch (FileNotFoundException e) {
            //LogHelper.i(TAG, "Song does not contain album art");
        } catch (NullPointerException e) {
            //LogHelper.i(TAG, "NullPointerException: " + e);
        }

        if (bm == null)
            return null;

        double scaleFactor = Math.min(
                ((double) MAX_ART_WIDTH)/bm.getWidth(), ((double) MAX_ART_HEIGHT)/bm.getHeight());
        return Bitmap.createScaledBitmap(bm,
                (int) (bm.getWidth() * scaleFactor), (int) (bm.getHeight() * scaleFactor), false);
    }

    public static Bitmap getScaleBitmapIcon(Context context, Uri uri) {

        if (uri == null) {
            return null;
        }
        Bitmap bm = null;
        try {
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                bm = BitmapFactory.decodeFileDescriptor(fd);
            }
        } catch (FileNotFoundException e) {
            //LogHelper.i(TAG, "Song does not contain album art");
        } catch (NullPointerException e) {
            //LogHelper.i(TAG, "NullPointerException: " + e);
        }

        if (bm == null)
            return null;

        double scaleFactor = Math.min(
                ((double) MAX_ART_WIDTH_ICON)/bm.getWidth(), ((double) MAX_ART_HEIGHT_ICON)/bm.getHeight());
        return Bitmap.createScaledBitmap(bm,
                (int) (bm.getWidth() * scaleFactor), (int) (bm.getHeight() * scaleFactor), false);
    }

    public static Bitmap scaleBitmapIcon(Bitmap src) {
        double scaleFactor = Math.min(
                ((double) MAX_ART_WIDTH_ICON)/src.getWidth(), ((double) MAX_ART_HEIGHT_ICON)/src.getHeight());
        return Bitmap.createScaledBitmap(src,
                (int) (src.getWidth() * scaleFactor), (int) (src.getHeight() * scaleFactor), false);
    }

    public static Bitmap scaleBitmap(Bitmap src) {
        double scaleFactor = Math.min(
                ((double) MAX_ART_WIDTH)/src.getWidth(), ((double) MAX_ART_HEIGHT)/src.getHeight());
        return Bitmap.createScaledBitmap(src,
                (int) (src.getWidth() * scaleFactor), (int) (src.getHeight() * scaleFactor), false);
    }
}