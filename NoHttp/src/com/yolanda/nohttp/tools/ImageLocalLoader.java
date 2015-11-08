/*
 * Copyright © YOLANDA. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yolanda.nohttp.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.yolanda.nohttp.Logger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

/**
 * Created in Nov 4, 2015 3:07:29 PM
 * 
 * @author YOLANDA;
 */
public class ImageLocalLoader {

	/**
	 * Update bitmap for view
	 */
	private static final int UPDATE_UI = 0x112;
	/**
	 * Single lock
	 */
	private static final Object SINGLE_OBJECT = new Object();
	/**
	 * Single module
	 */
	private static ImageLocalLoader mInstance;
	/**
	 * Default gray image
	 */
	private Drawable mDefaultDrawable;
	/**
	 * Image cache
	 */
	private LruCache<String, Bitmap> mLruCache;
	/**
	 * Thread pool
	 */
	private ExecutorService mExecutorService;
	/**
	 * Update poster
	 */
	private Handler mPosterHandler;

	/**
	 * Get single object
	 */
	public static ImageLocalLoader getInstance() {
		synchronized (SINGLE_OBJECT) {
			if (mInstance == null) {
				mInstance = new ImageLocalLoader();
			}
		}
		return mInstance;
	}

	private ImageLocalLoader() {
		mDefaultDrawable = new ColorDrawable(Color.GRAY);
		mExecutorService = Executors.newSingleThreadExecutor();
		mPosterHandler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == UPDATE_UI) {
					ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
					ImageView imageView = holder.imageView;
					Bitmap bm = holder.bitmap;
					String path = holder.imagePath;
					if (path.equals(imageView.getTag())) {
						imageView.setImageBitmap(bm);
					}
				}
			}
		};

		int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 8);
		mLruCache = new LruCache<String, Bitmap>(maxMemory) {
			@SuppressLint("NewApi")
			@Override
			protected int sizeOf(String key, Bitmap value) {
				if (VERSION.SDK_INT >= 19)
					return value.getByteCount();
				return value.getRowBytes() * value.getHeight();
			};
		};
	}

	/**
	 * Deposit in the province read images, width is high, the greater the picture clearer, but also the memory
	 * 
	 * @param imagePath Pictures in the path of the memory card
	 * @param maxWidth The highest limit value target width
	 * @param maxHeight The highest limit value target height
	 * @return
	 */
	public Bitmap readImage(String imagePath, int maxWidth, int maxHeight) {
		File imageFile = new File(imagePath);
		if (imageFile.exists()) {
			try {
				BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(imageFile));
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(inputStream, null, options);
				inputStream.close();
				int i = 0;
				while (true) {
					if ((options.outWidth >> i <= maxWidth) && (options.outHeight >> i <= maxHeight)) {
						inputStream = new BufferedInputStream(new FileInputStream(new File(imagePath)));
						options.inSampleSize = (int) Math.pow(2.0, i);
						options.inJustDecodeBounds = false;
						Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
						inputStream.close();
						return bitmap;
					}
					i += 1;
				}
			} catch (IOException e) {
				Logger.e("This path does not exist" + imagePath, e);
			}
		}
		return null;
	}

	/**
	 * According to the ImageView obtains appropriate width and height of compression
	 */
	public void measureSize(ImageView imageView, int[] viewSizes) {
		final DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
		final LayoutParams params = imageView.getLayoutParams();
		// 测量宽
		int width = params.width == LayoutParams.WRAP_CONTENT ? 0 : imageView.getWidth(); // Get actual image width
		if (width <= 0)
			width = params.width; // Get layout width parameter
		if (width <= 0)
			width = displayMetrics.widthPixels;
		// 测量高
		int height = params.height == LayoutParams.WRAP_CONTENT ? 0 : imageView.getHeight(); // Get actual image height
		if (height <= 0)
			height = params.height; // Get layout height parameter
		if (height <= 0)
			height = displayMetrics.heightPixels;
		viewSizes[0] = width;
		viewSizes[1] = height;
	}

	/**
	 * Set the default image, resId from drawable. Is displayed when loading or loading failure
	 */
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	public void setDefaultImage(Context context, int resId) {
		if (VERSION.SDK_INT >= 21)
			mDefaultDrawable = context.getDrawable(resId);
		else
			mDefaultDrawable = context.getResources().getDrawable(resId);
	}

	/**
	 * Set the default image, resId from drawable. Is displayed when loading or loading failure
	 */
	public void setDefaultImageColor(int color) {
		mDefaultDrawable = new ColorDrawable(color);
	}

	/**
	 * Load image from local SDCard
	 */
	public void loadImage(ImageView imageView, String imagePath) {
		loadImage(imageView, imagePath, 0, 0);
	}

	/**
	 * According to the specified width high loading pictures, wide high, the greater the picture clearer, more memory
	 * 
	 * @param imageView ImageView
	 * @param imagePath Path from local SDCard
	 * @param width Target width
	 * @param height Target height
	 */
	public void loadImage(ImageView imageView, String imagePath, int width, int height) {
		imageView.setTag(imagePath);
		Bitmap bitmap = getImageFromCache(imagePath + width + height);
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
		} else {
			imageView.setImageDrawable(mDefaultDrawable);
			mExecutorService.execute(new TaskThread(imageView, imagePath, width, height));
		}
	}

	/**
	 * Read the images from the cache
	 */
	private Bitmap getImageFromCache(String key) {
		return mLruCache.get(key);
	}

	/**
	 * Add images to the cache
	 */
	private void addImageToCache(String key, Bitmap bitmap) {
		if (getImageFromCache(key) == null && bitmap != null)
			mLruCache.put(key, bitmap);
	}

	private class TaskThread implements Runnable {
		private ImageView mImageView;
		private String mImagePath;
		private int width;
		private int height;

		TaskThread(ImageView imageView, String imagePath, int width, int height) {
			this.mImagePath = imagePath;
			this.mImageView = imageView;
			this.width = width;
			this.height = height;
		}

		@Override
		public void run() {
			Bitmap bitmap = null;
			if (width != 0 && height != 0)
				bitmap = readImage(mImagePath, width, height);
			else {
				int[] viewSizes = new int[2];
				measureSize(mImageView, viewSizes);
				bitmap = readImage(mImagePath, viewSizes[0], viewSizes[1]);
			}
			addImageToCache(mImagePath + width + height, bitmap);
			ImgBeanHolder holder = new ImgBeanHolder();
			holder.bitmap = getImageFromCache(mImagePath + width + height);
			holder.imageView = mImageView;
			holder.imagePath = mImagePath;
			mPosterHandler.obtainMessage(UPDATE_UI, holder).sendToTarget();	
		}
	};

	private class ImgBeanHolder {
		Bitmap bitmap;
		ImageView imageView;
		String imagePath;
	}
}
