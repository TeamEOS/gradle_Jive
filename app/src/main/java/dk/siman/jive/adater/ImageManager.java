/*
 * Copyright (c) 2015. Anders Nielsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.siman.jive.adater;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Stack;

import dk.siman.jive.R;
import dk.siman.jive.utils.ArtHelper;
import dk.siman.jive.utils.LogHelper;

public class ImageManager {

    private static final String TAG = LogHelper.makeLogTag(ImageManager.class);

    private HashMap<String, Bitmap> imageMap = new HashMap<String, Bitmap>();

    private File cacheDir;
    private Context mContext;
    private ImageQueue imageQueue = new ImageQueue();
    private Thread imageLoaderThread = new Thread(new ImageQueueManager());

    public ImageManager(Context context) {
        // Make background thread low priority, to avoid affecting UI performance
        //imageLoaderThread.setPriority(Thread.NORM_PRIORITY-1);

        mContext = context;

        cacheDir = context.getCacheDir();
        if(!cacheDir.exists())
            cacheDir.mkdirs();
    }

    public void displayImage(String url, String parentId, Activity activity, ImageView imageView) {
        if (!parentId.equals("__ROOT__")) {
            if (!imageMap.isEmpty()) {
                imageMap.clear();
            }
        }

        if (url.equals("dummy")) {
            imageView.setImageResource(R.drawable.ic_default_art);
        } else {
            if (imageMap.containsKey(url)) {
                imageView.setImageBitmap(imageMap.get(url));
            } else {
                queueImage(url, activity, imageView);
                imageView.setImageResource(R.drawable.ic_default_art);
            }
        }
    }

    private void queueImage(String url, Activity activity, ImageView imageView) {
        // This ImageView might have been used for other images, so we clear
        // the queue of old tasks before starting.
        imageQueue.Clean(imageView);
        ImageRef p=new ImageRef(url, imageView);

        synchronized(imageQueue.imageRefs) {
            imageQueue.imageRefs.push(p);
            imageQueue.imageRefs.notifyAll();
        }

        // Start thread if it's not started yet
        if(imageLoaderThread.getState() == Thread.State.NEW)
            imageLoaderThread.start();
    }

    private Bitmap getBitmap(String url) {
        Bitmap bitmap = null;
        String filename = String.valueOf(url.hashCode());
        File f = new File(cacheDir, filename);
        LogHelper.d(TAG, "getBitmap, url=", url, " , filename=", filename);

        Resources rsrc = mContext.getResources();
        if (rsrc == null) {
            LogHelper.d(TAG, "getBitmap.rsrc == null");
            return null;
        }

        // Is the bitmap in our cache?
        if (f.exists()) {
            bitmap = BitmapFactory.decodeFile(f.getPath());
            if (bitmap != null) {
                LogHelper.d(TAG, "Bitmap found in cache, name: ", f.getPath());
                return bitmap;
            }
        }

        // Nope, have to download it
        try {
            if (url != null) {
                bitmap = ArtHelper.getScaleBitmapIcon(mContext, Uri.parse(url));
                if (bitmap == null) {
                    return null;
                }
            } else {
                return null;
            }
            // save bitmap to cache for later
            writeFile(bitmap, f);

            return bitmap;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void writeFile(Bitmap bmp, File f) {
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 80, out);
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try { if (out != null ) out.close(); }
            catch(Exception ex) {}
        }
    }

    /** Classes **/

    private class ImageRef {
        public String url;
        public ImageView imageView;

        public ImageRef(String u, ImageView i) {
            url=u;
            imageView=i;
        }
    }

    //stores list of images to download
    private class ImageQueue {
        private final Stack<ImageRef> imageRefs =
                new Stack<ImageRef>();

        //removes all instances of this ImageView
        public void Clean(ImageView view) {
            for(int i = 0 ;i < imageRefs.size();) {
                if(imageRefs.get(i).imageView == view)
                    imageRefs.remove(i);
                else ++i;
            }
        }
    }

    private class ImageQueueManager implements Runnable {
        @Override
        public void run() {
            try {
                while(true) {
                    // Thread waits until there are images in the
                    // queue to be retrieved
                    if(imageQueue.imageRefs.size() == 0) {
                        synchronized(imageQueue.imageRefs) {
                            imageQueue.imageRefs.wait();
                        }
                    }

                    // When we have images to be loaded
                    if(imageQueue.imageRefs.size() != 0) {
                        ImageRef imageToLoad;

                        synchronized(imageQueue.imageRefs) {
                            imageToLoad = imageQueue.imageRefs.pop();
                        }

                        Bitmap bmp = getBitmap(imageToLoad.url);
                        if (bmp == null) {
                            bmp = BitmapFactory.decodeResource(mContext.getResources(),
                                    R.drawable.ic_default_art);
                        }

                        imageMap.put(imageToLoad.url, bmp);
                        Object tag = imageToLoad.imageView.getTag();

                        // Make sure we have the right view - thread safety defender
                        if(tag != null && tag.toString().equals(imageToLoad.url.toString())) {
                            BitmapDisplayer bmpDisplayer =
                                    new BitmapDisplayer(bmp, imageToLoad.imageView);

                            Activity a =
                                    (Activity)imageToLoad.imageView.getContext();

                            a.runOnUiThread(bmpDisplayer);
                        }
                    }

                    if(Thread.interrupted())
                        break;
                }
            } catch (InterruptedException e) {}
        }
    }

    //Used to display bitmap in the UI thread
    private class BitmapDisplayer implements Runnable {
        Bitmap bitmap;
        ImageView imageView;

        public BitmapDisplayer(Bitmap b, ImageView i) {
            bitmap=b;
            imageView=i;
        }

        public void run() {
            imageView.setImageBitmap(bitmap);
        }
    }
}