package com.github.florent37.camerafragment.internal.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.github.florent37.camerafragment.configuration.Configuration;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static android.R.attr.bitmap;
import static android.R.attr.x;
import static com.github.florent37.camerafragment.internal.enums.Camera.ORIGINAL_IMAGE_HEIGHT;
import static com.github.florent37.camerafragment.internal.enums.Camera.ORIGINAL_IMAGE_WIDTH;

/*
 * Created by memfis on 7/6/16.
 * <p/>
 * Class with some common methods to work with camera.
 */
public final class CameraHelper {

    public final static String TAG = "CameraHelper";
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");


    private CameraHelper() {
    }

    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) ||
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean hasCamera2(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] idList = manager.getCameraIdList();
            boolean notNull = true;
            if (idList.length == 0) {
                notNull = false;
            } else {
                for (final String str : idList) {
                    if (str == null || str.trim().isEmpty()) {
                        notNull = false;
                        break;
                    }
                    final CameraCharacteristics characteristics = manager.getCameraCharacteristics(str);

                    final int supportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        notNull = false;
                        break;
                    }
                }
            }
            return notNull;
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static File generateStorageDir(Context context, @Nullable String pathToDirectory) {
        File mediaStorageDir = null;
        if (pathToDirectory != null) {
            mediaStorageDir = new File(pathToDirectory);
        } else {
            mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), context.getPackageName());
        }

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Failed to create directory.");
                return null;
            }
        }

        return mediaStorageDir;
    }

    public static File getOutputMediaFile(Context context, @Configuration.MediaAction int mediaAction, @Nullable String pathToDirectory, @Nullable String fileName) {
        final File mediaStorageDir = generateStorageDir(context, pathToDirectory);
        File mediaFile = null;

        if (mediaStorageDir != null) {
            if (fileName == null) {
                final String timeStamp = simpleDateFormat.format(new Date());
                if (mediaAction == Configuration.MEDIA_ACTION_PHOTO) {
                    fileName = "IMG_" + timeStamp;
                } else if (mediaAction == Configuration.MEDIA_ACTION_VIDEO) {
                    fileName = "VID_" + timeStamp;
                }

            }
            final String mediaStorageDirPath = mediaStorageDir.getPath();
            if (mediaAction == Configuration.MEDIA_ACTION_PHOTO) {
                mediaFile = new File(mediaStorageDirPath + File.separator + fileName + ".jpg");
            } else if (mediaAction == Configuration.MEDIA_ACTION_VIDEO) {
                mediaFile = new File(mediaStorageDirPath + File.separator + fileName + ".mp4");
            }
        }

        return mediaFile;
    }


    public static Size findClosetPictureSize(List<Size> sizeList, int width, int height) {
        ArrayList<Size> sizeListCorrectRatio = new ArrayList<>();
        if (sizeList == null || sizeList.size() <= 0) {
            return null;
        }
        for (Size size : sizeList) {
            if (CameraUtil.isCorrectRatio(size)) {
                sizeListCorrectRatio.add(size);
            }
        }
        Size bestSize = null;
        float ratio = (float) height / (float) width;
        Collections.sort(sizeListCorrectRatio, new Comparator<Size>() {
            @Override
            public int compare(Size size1, Size size2) {
                return size1.getWidth() < size2.getWidth() ? 10 : -10;
            }
        });
        for (Size size : sizeListCorrectRatio) {
            if (size.getWidth() <= width) {
                float currentRatio = (float) size.getHeight() / (float) size.getWidth();
                if (currentRatio == ratio)
                    return size;
                if (width >= size.getWidth())
                    bestSize = size;
            }
        }
        if (bestSize != null)
            return bestSize;

        return sizeListCorrectRatio.get(0);
    }

    public static Size findClosetPictureSizeCamera2(Size[] sizes, int width, int height) {
        ArrayList<Size> sizeList = new ArrayList<>();
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        for (Size size : sizes) {
            if (CameraUtil.isCorrectRatio(size)) {
                sizeList.add(size);
            }
        }
        Size bestSize = null;
        float ratio = (float) height / (float) width;
        Collections.sort(sizeList, new Comparator<Size>() {
            @Override
            public int compare(Size size1, Size size2) {
                return size1.getWidth() < size2.getWidth() ? 10 : -10;
            }
        });
        for (Size size : sizeList) {
            if (size.getWidth() <= width) {
                float currentRatio = (float) size.getHeight() / (float) size.getWidth();
                if (currentRatio == ratio)
                    return size;
                if (width >= size.getWidth())
                    bestSize = size;
            }
        }
        if (bestSize != null)
            return bestSize;

        return sizeList.get(0);
    }

    @SuppressWarnings("deprecation")
    public static Size getPictureSize(List<Size> choices, @Configuration.MediaQuality int mediaQuality) {
        if (choices == null || choices.isEmpty()) return null;
        if (choices.size() == 1) return choices.get(0);
        ArrayList<Size> sizes43 = new ArrayList<>();
        for (Size size : choices) {
            if (isValidRatio(size)) {
                sizes43.add(size);
            }
        }

        Size result = null;
        Size maxPictureSize = Collections.max(sizes43, new CompareSizesByArea2());
        Size minPictureSize = Collections.min(sizes43, new CompareSizesByArea2());

        Collections.sort(sizes43, new CompareSizesByArea2());

        if (mediaQuality == Configuration.MEDIA_QUALITY_HIGHEST) {
            result = maxPictureSize;
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOW) {
            if (sizes43.size() == 2) result = minPictureSize;
            else {
                int half = sizes43.size() / 2;
                int lowQualityIndex = (sizes43.size() - half) / 2;
                result = sizes43.get(lowQualityIndex + 1);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_HIGH) {
            if (sizes43.size() == 2) result = maxPictureSize;
            else {
                int half = sizes43.size() / 2;
                int highQualityIndex = (sizes43.size() - half) / 2;
                result = sizes43.get(sizes43.size() - highQualityIndex - 1);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_MEDIUM) {
            if (sizes43.size() == 2) result = minPictureSize;
            else {
                int mediumQualityIndex = sizes43.size() / 2;
                result = sizes43.get(mediumQualityIndex);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOWEST) {
            result = minPictureSize;
        }

        return result;
    }
//
//    @SuppressWarnings("deprecation")
//    public static Size getPictureSize(List<Size> choices, @Configuration.MediaQuality int mediaQuality) {
//        if (choices == null || choices.isEmpty()) return null;
//        if (choices.size() == 1) return choices.get(0);
//
//        Size result = null;
//        Size maxPictureSize = Collections.max(choices, new CompareSizesByArea2());
//        Size minPictureSize = Collections.min(choices, new CompareSizesByArea2());
//
//        Collections.sort(choices, new CompareSizesByArea2());
//
//        for (Size size: choices){
//            if (isValidRatio(size) && size.getHeight() < size.getWidth()){
//                if (size.getHeight() > ORIGINAL_IMAGE_HEIGHT && (result == null || size.getHeight() < result.getHeight())){
//                    result = size;
//                }
//            }
//        }
////
////        if (mediaQuality == Configuration.MEDIA_QUALITY_HIGHEST) {
////            result = maxPictureSize;
////        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOW) {
////            if (choices.size() == 2) result = minPictureSize;
////            else {
////                int half = choices.size() / 2;
////                int lowQualityIndex = (choices.size() - half) / 2;
////                result = choices.get(lowQualityIndex + 1);
////            }
////        } else if (mediaQuality == Configuration.MEDIA_QUALITY_HIGH) {
////            if (choices.size() == 2) result = maxPictureSize;
////            else {
////                int half = choices.size() / 2;
////                int highQualityIndex = (choices.size() - half) / 2;
////                result = choices.get(choices.size() - highQualityIndex - 1);
////            }
////        } else if (mediaQuality == Configuration.MEDIA_QUALITY_MEDIUM) {
////            if (choices.size() == 2) result = minPictureSize;
////            else {
////                int mediumQualityIndex = choices.size() / 2;
////                result = choices.get(mediumQualityIndex);
////            }
////        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOWEST) {
////            result = minPictureSize;
////        }
//
//        return result;
//    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Size getPictureSize(Size[] sizes, @Configuration.MediaQuality int mediaQuality) {
        if (sizes == null || sizes.length == 0) return null;

        List<Size> choices = Arrays.asList(sizes);

        if (choices.size() == 1) return choices.get(0);

        Size result = null;
        Size maxPictureSize = Collections.max(choices, new CompareSizesByArea2());
        Size minPictureSize = Collections.min(choices, new CompareSizesByArea2());

        Collections.sort(choices, new CompareSizesByArea2());

        if (mediaQuality == Configuration.MEDIA_QUALITY_HIGHEST) {
            result = maxPictureSize;
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOW) {
            if (choices.size() == 2) result = minPictureSize;
            else {
                int half = choices.size() / 2;
                int lowQualityIndex = (choices.size() - half) / 2;
                result = choices.get(lowQualityIndex + 1);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_HIGH) {
            if (choices.size() == 2) result = maxPictureSize;
            else {
                int half = choices.size() / 2;
                int highQualityIndex = (choices.size() - half) / 2;
                result = choices.get(choices.size() - highQualityIndex - 1);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_MEDIUM) {
            if (choices.size() == 2) result = minPictureSize;
            else {
                int mediumQualityIndex = choices.size() / 2;
                result = choices.get(mediumQualityIndex);
            }
        } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOWEST) {
            result = minPictureSize;
        }

        return result;
    }

    public static boolean isValidRatio(Size size) {
        float bmRatio = (float) size.getHeight() / (float) size.getWidth();
        float ratioPortrait = (float) 3 / (float) 4;
        float ratioLand = (float) 4 / (float) 3;
        return bmRatio == ratioPortrait || bmRatio == ratioLand;
    }

    @SuppressWarnings("deprecation")
    public static Size getOptimalPreviewSize(List<Size> sizes, int width, int height) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) height / width;

        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @SuppressWarnings("deprecation")
    public static Size getSizeWithClosestRatio(List<Size> sizes, int width, int height) {

        if (sizes == null) return null;

        double MIN_TOLERANCE = 100;
        double targetRatio = (double) height / width;
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Size size : sizes) {
            if (size.getWidth() == width && size.getHeight() == height)
                return size;

            double ratio = (double) size.getHeight() / size.getWidth();

            if (Math.abs(ratio - targetRatio) < MIN_TOLERANCE) {
                MIN_TOLERANCE = Math.abs(ratio - targetRatio);
                minDiff = Double.MAX_VALUE;
            } else continue;

            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public static boolean isCorrectRatio(int height, int width) {

        float sizeRatio = (float) height / (float) width;
        float ratioPortrait = (float) 3 / (float) 4;
        float ratioLand = (float) 4 / (float) 3;
        return sizeRatio == ratioPortrait || sizeRatio == ratioLand;

    }

    public static Size findClosestPreviewSize(List<Size> sizes, Point preferSize) {
        int preferX = preferSize.x;
        int preferY = preferSize.y;
        int minDiff = Integer.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < sizes.size(); i++) {
            Size size = sizes.get(i);
            int x = size.getWidth();
            int y = size.getHeight();
            if (isCorrectRatio(y, x)) {

                int diff = Math.abs(x - preferX) + Math.abs(y - preferY);
                if (diff < minDiff) {
                    minDiff = diff;
                    index = i;
                }
            }

        }

        return sizes.get(index);
    }

    public static Size findClosestPreviewSizeCamera2(Size[] sizes, Point preferSize) {
        ArrayList<Size> sizeList = new ArrayList<Size>();
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        Collections.addAll(sizeList, sizes);
        int preferX = preferSize.x;
        int preferY = preferSize.y;
        int minDiff = Integer.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < sizeList.size(); i++) {
            Size size = sizeList.get(i);
            int x = size.getWidth();
            int y = size.getHeight();

            int diff = Math.abs(x - preferX) + Math.abs(y - preferY);
            if (diff < minDiff) {
                minDiff = diff;
                index = i;
            }
        }

        return sizeList.get(index);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Size getOptimalPreviewSize(Size[] sizes, int width, int height) {

        if (sizes == null) return null;

        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) height / width;
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Size size : sizes) {
//            if (size.getWidth() == width && size.getHeight() == height)
//                return size;
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Size getSizeWithClosestRatio(Size[] sizes, int width, int height) {

        if (sizes == null) return null;

        double MIN_TOLERANCE = 100;
        double targetRatio = (double) height / width;
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for (Size size : sizes) {
//            if (size.getWidth() == width && size.getHeight() == height)
//                return size;

            double ratio = (double) size.getHeight() / size.getWidth();

            if (Math.abs(ratio - targetRatio) < MIN_TOLERANCE) {
                MIN_TOLERANCE = Math.abs(ratio - targetRatio);
                minDiff = Double.MAX_VALUE;
            } else continue;

            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea2());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return null;
        }
    }

    private static double calculateApproximateVideoSize(CamcorderProfile camcorderProfile, int seconds) {
        return ((camcorderProfile.videoBitRate / (float) 1 + camcorderProfile.audioBitRate / (float) 1) * seconds) / (float) 8;
    }

    public static double calculateApproximateVideoDuration(CamcorderProfile camcorderProfile, long maxFileSize) {
        return 8 * maxFileSize / (camcorderProfile.videoBitRate + camcorderProfile.audioBitRate);
    }

    private static long calculateMinimumRequiredBitRate(CamcorderProfile camcorderProfile, long maxFileSize, int seconds) {
        return 8 * maxFileSize / seconds - camcorderProfile.audioBitRate;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static CamcorderProfile getCamcorderProfile(String cameraId, long maximumFileSize, int minimumDurationInSeconds) {
        if (TextUtils.isEmpty(cameraId)) {
            return null;
        }
        int cameraIdInt = Integer.parseInt(cameraId);
        return getCamcorderProfile(cameraIdInt, maximumFileSize, minimumDurationInSeconds);
    }

    public static CamcorderProfile getCamcorderProfile(int currentCameraId, long maximumFileSize, int minimumDurationInSeconds) {
        if (maximumFileSize <= 0)
            return CamcorderProfile.get(currentCameraId, Configuration.MEDIA_QUALITY_HIGHEST);

        int[] qualities = new int[]{Configuration.MEDIA_QUALITY_HIGHEST,
                Configuration.MEDIA_QUALITY_HIGH, Configuration.MEDIA_QUALITY_MEDIUM,
                Configuration.MEDIA_QUALITY_LOW, Configuration.MEDIA_QUALITY_LOWEST};

        CamcorderProfile camcorderProfile;
        for (int i = 0; i < qualities.length; ++i) {
            camcorderProfile = CameraHelper.getCamcorderProfile(qualities[i], currentCameraId);
            double fileSize = CameraHelper.calculateApproximateVideoSize(camcorderProfile, minimumDurationInSeconds);

            if (fileSize > maximumFileSize) {
                long minimumRequiredBitRate = calculateMinimumRequiredBitRate(camcorderProfile, maximumFileSize, minimumDurationInSeconds);

                if (minimumRequiredBitRate >= camcorderProfile.videoBitRate / 4 && minimumRequiredBitRate <= camcorderProfile.videoBitRate) {
                    camcorderProfile.videoBitRate = (int) minimumRequiredBitRate;
                    return camcorderProfile;
                }
            } else return camcorderProfile;
        }
        return CameraHelper.getCamcorderProfile(Configuration.MEDIA_QUALITY_LOWEST, currentCameraId);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static CamcorderProfile getCamcorderProfile(@Configuration.MediaQuality int mediaQuality, String cameraId) {
        if (TextUtils.isEmpty(cameraId)) {
            return null;
        }
        int cameraIdInt = Integer.parseInt(cameraId);
        return getCamcorderProfile(mediaQuality, cameraIdInt);
    }

    public static CamcorderProfile getCamcorderProfile(@Configuration.MediaQuality int mediaQuality, int cameraId) {
        if (Build.VERSION.SDK_INT > 10) {
            if (mediaQuality == Configuration.MEDIA_QUALITY_HIGHEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_HIGH) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
                } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
                }
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_MEDIUM) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
                }
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOW) {
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                } else {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
                }
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOWEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            }
        } else {
            if (mediaQuality == Configuration.MEDIA_QUALITY_HIGHEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_HIGH) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_MEDIUM) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOW) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else if (mediaQuality == Configuration.MEDIA_QUALITY_LOWEST) {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            } else {
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static class CompareSizesByArea2 implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
