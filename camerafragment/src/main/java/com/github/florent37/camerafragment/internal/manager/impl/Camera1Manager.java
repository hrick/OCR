package com.github.florent37.camerafragment.internal.manager.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.WindowManager;

import com.github.florent37.camerafragment.configuration.Configuration;
import com.github.florent37.camerafragment.configuration.ConfigurationProvider;
import com.github.florent37.camerafragment.internal.manager.CameraManager;
import com.github.florent37.camerafragment.internal.manager.listener.CameraCloseListener;
import com.github.florent37.camerafragment.internal.manager.listener.CameraOpenListener;
import com.github.florent37.camerafragment.internal.manager.listener.CameraPhotoListener;
import com.github.florent37.camerafragment.internal.manager.listener.CameraVideoListener;
import com.github.florent37.camerafragment.internal.ui.model.PhotoQualityOption;
import com.github.florent37.camerafragment.internal.ui.model.VideoQualityOption;
import com.github.florent37.camerafragment.internal.utils.CameraHelper;
import com.github.florent37.camerafragment.internal.utils.CameraUtil;
import com.github.florent37.camerafragment.internal.utils.Size;
import com.github.florent37.camerafragment.listeners.CameraFragmentResultListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.github.florent37.camerafragment.internal.enums.Camera.ORIGINAL_IMAGE_HEIGHT;
import static com.github.florent37.camerafragment.internal.enums.Camera.ORIGINAL_IMAGE_WIDTH;

/*
 * Created by memfis on 8/14/16.
 */
@SuppressWarnings("deprecation")
public class Camera1Manager extends BaseCameraManager<Integer, SurfaceHolder.Callback> implements Camera.ErrorCallback {

    private static final String TAG = "Camera1Manager";
    private static final int FOCUS_AREA_SIZE = 300;

    private Camera camera;
    private Surface surface;
    private TextureView bindTextureView;
    private int orientation;
    private int displayRotation = 0;
    CameraCloseListener<Integer> cameraCloseListenerLocal;
    private File outputPath;
    private CameraVideoListener videoListener;
    private CameraPhotoListener photoListener;

    private Integer futurFlashMode;

    @Override
    public void openCamera(final Integer cameraId,
                           final CameraOpenListener<Integer, SurfaceHolder.Callback> cameraOpenListener) {
        this.currentCameraId = cameraId;
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
//                        releaseCameraAndPreview();
                        camera = Camera.open(cameraId);
                        camera.setErrorCallback(Camera1Manager.this);
                        prepareCameraOutputs();
                        if (futurFlashMode != null) {
                            setFlashMode(futurFlashMode);
                            futurFlashMode = null;
                        }
                        if (cameraOpenListener != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    cameraOpenListener.onCameraOpened(cameraId, previewSize, new SurfaceHolder.Callback() {
                                        @Override
                                        public void surfaceCreated(SurfaceHolder surfaceHolder) {
                                            if (surfaceHolder.getSurface() == null) {
                                                return;
                                            }
                                            surface = surfaceHolder.getSurface();

                                            try {
                                                camera.stopPreview();
                                            } catch (Exception ignore) {
                                            }

                                            startPreview(surfaceHolder);
                                        }

                                        @Override
                                        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
                                            if (surfaceHolder.getSurface() == null) {
                                                return;
                                            }
                                            surface = surfaceHolder.getSurface();

                                            try {
                                                camera.stopPreview();
                                            } catch (Exception ignore) {
                                            }

                                            startPreview(surfaceHolder);
                                        }

                                        @Override
                                        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                                            releaseCameraAndPreview();
                                        }
                                    });
                                }
                            });
                        }
                    } catch (Exception error) {
                        Log.d(TAG, "Can't open camera: " + error.getMessage());
                        if (cameraOpenListener != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    cameraOpenListener.onCameraOpenError();
                                }
                            });
                        }
                    }
                }
            });
        } else {
            releaseCameraAndPreview();
        }
    }
    private void releaseCameraAndPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void closeCamera(final CameraCloseListener<Integer> cameraCloseListener) {
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (camera != null) {
                        camera.stopPreview();
                        camera.release();
                        camera = null;
                        if (cameraCloseListener != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    cameraCloseListener.onCameraClosed(currentCameraId);
                                }
                            });
                        }
                    }
                }
            });
        } else {
            releaseCameraAndPreview();
        }
    }


    @Override
    public void requestFocus(MotionEvent event) {
        // TODO FOCUS AREA
        if (camera != null) {
            focoManual(new Point((int) event.getX(), (int) event.getY()), null);
        }
    }

    @Override
    public void setFlashMode(@Configuration.FlashMode final int flashMode) {
        if (camera != null) {
            setFlashMode(camera, camera.getParameters(), flashMode);
        } else {
            futurFlashMode = flashMode;
        }
    }

    @Override
    public void takePhoto(File photoFile, CameraPhotoListener cameraPhotoListener, final CameraFragmentResultListener callback) {
        this.outputPath = photoFile;
        this.photoListener = cameraPhotoListener;
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (camera != null) {
                        setCameraPhotoQuality(camera);
                        if (getAutoFocus()) {
                            takePicture(callback);
                        } else {
                            focoManual(new Point(previewSize.getWidth() / 2, previewSize.getHeight() / 2), callback);
                        }
                    }
                }
            });
        }
    }

    private void takePicture(final CameraFragmentResultListener callback) {
        camera.takePicture(new Camera.ShutterCallback() {
            @Override
            public void onShutter() {
                Camera1Manager.this.onPictureStart(callback);
                Log.i(TAG, "onShutter Camera1");
            }
        }, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                Camera1Manager.this.onPictureTaken(bytes, camera, callback);
                setFlashMode(Configuration.FLASH_MODE_OFF);
            }
        });
    }

    private void onPictureStart(final CameraFragmentResultListener callback) {
        if (photoListener != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    photoListener.onPhotoStart(callback);
                }
            });
        }
    }

    private void focoManual(final Point point, @Nullable final CameraFragmentResultListener callback) {
        final int HEIGHT = ORIGINAL_IMAGE_HEIGHT;
        final int WIDTH = ORIGINAL_IMAGE_WIDTH;
        if (callback != null)
            if (photoListener != null) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        photoListener.onManualFocus(callback);
                    }
                });
            }
        Observable.just(camera)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(new Func1<Camera, Observable<Camera>>() {
                    @Override
                    public Observable<Camera> call(Camera camera) {
                        Rect rect = CameraUtil.transferCameraAreaFromOuterSize(point, new Point(WIDTH, HEIGHT), 100);
                        List<Camera.Area> areaList = Collections.singletonList(new Camera.Area(rect, 1000));
                        return areaFocusAction(areaList);

                    }
                })
                .flatMap(new Func1<Camera, Observable<Camera>>() {
                    @Override
                    public Observable<Camera> call(Camera camera) {
                        Rect rect = CameraUtil.transferCameraAreaFromOuterSize(point, new Point(WIDTH, HEIGHT), 100);
                        List<Camera.Area> areaList = Collections.singletonList(new Camera.Area(rect, 1000));
                        return areaMeterAction(areaList);

                    }
                })
                .subscribe(new Subscriber<Camera>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Camera o) {
                        if (callback != null)
                            takePicture(callback);
                    }
                });
    }

    private Observable<Camera> areaFocusAction(final List<Camera.Area> focusAreaList) {
        if (focusAreaList == null || focusAreaList.size() == 0) {
            return null;
        }
        return Observable.create(new Observable.OnSubscribe<Camera>() {
            @Override
            public void call(final Subscriber<? super Camera> subscriber) {
                if (camera.getParameters().getMaxNumFocusAreas() == 0) {
                    subscriber.onNext(camera);
                } else if (camera.getParameters().getMaxNumFocusAreas() < focusAreaList.size()) {
                    Log.i("areaMeterAction", "parameters");
                    subscriber.onNext(camera);
                } else {
                    if (!Objects.equals(camera.getParameters().getFocusMode(), Camera.Parameters.FOCUS_MODE_AUTO)) {
                        List<String> focusModes = camera.getParameters().getSupportedFocusModes();
                        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                            camera.getParameters().setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        }
                    }
                    camera.getParameters().setFocusAreas(focusAreaList);
//                    camera.setParameters(mCameraParameters);
                    camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
//                            if (success) {
                            subscriber.onNext(camera);
//                            } else {
//                                Log.i("areaMeterAction","parameters");
//                            }
                        }
                    });
                }
            }
        });
    }

    private Observable<Camera> areaMeterAction(final List<Camera.Area> meterAreaList) {
        if (meterAreaList == null || meterAreaList.size() == 0) {
            return null;
        }
        return Observable.create(new Observable.OnSubscribe<Camera>() {
            @Override
            public void call(Subscriber<? super Camera> subscriber) {
                if (camera.getParameters().getMaxNumMeteringAreas() == 0) {
                    subscriber.onNext(camera);
                } else if (camera.getParameters().getMaxNumMeteringAreas() < meterAreaList.size()) {
                    Log.i("areaMeterAction", "parameters");
                    subscriber.onNext(camera);
                } else {
                    camera.getParameters().setFocusAreas(meterAreaList);
//                    camera.setParameters(mCameraParameters);
                    subscriber.onNext(camera);
                }
            }
        });
    }


    private boolean getAutoFocus() {
        String focusMode = camera.getParameters().getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }


    @Override
    public void startVideoRecord(final File videoFile, CameraVideoListener cameraVideoListener) {
        if (isVideoRecording) return;

        this.outputPath = videoFile;
        this.videoListener = cameraVideoListener;

        if (videoListener != null)
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Tzutalin++ 2017/05. If calling release function, it should not be executed
                    if (context == null) return;

                    if (prepareVideoRecorder()) {
                        videoRecorder.start();
                        isVideoRecording = true;
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                videoListener.onVideoRecordStarted(videoSize);
                            }
                        });
                    }
                }
            });
    }

    @Override
    public void stopVideoRecord(@Nullable final CameraFragmentResultListener callback) {
        if (isVideoRecording)
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {

                    try {
                        if (videoRecorder != null) videoRecorder.stop();
                    } catch (Exception ignore) {
                        // ignore illegal state.
                        // appear in case time or file size reach limit and stop already called.
                    }

                    isVideoRecording = false;
                    releaseVideoRecorder();

                    if (videoListener != null) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                videoListener.onVideoRecordStopped(outputPath, callback);
                            }
                        });
                    }
                }
            });
    }

    @Override
    public void releaseCameraManager() {
        super.releaseCameraManager();
    }

    @Override
    public void initializeCameraManager(ConfigurationProvider configurationProvider, Context context, CameraCloseListener<Integer> cameraCloseListener) {
        super.initializeCameraManager(configurationProvider, context, cameraCloseListener);
        this.cameraCloseListenerLocal = cameraCloseListener;
        numberOfCameras = Camera.getNumberOfCameras();

        for (int i = 0; i < numberOfCameras; ++i) {
            final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                faceBackCameraId = i;
                faceBackCameraOrientation = cameraInfo.orientation;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                faceFrontCameraId = i;
                faceFrontCameraOrientation = cameraInfo.orientation;
            }
        }
    }

    @Override
    public boolean zoom(int level) {
        if (camera != null) {
            Camera.Parameters cameraParameters = camera.getParameters();
            if (!cameraParameters.isZoomSupported()) {
                return false;
            }
            int maxZoomLevel = camera.getParameters().getMaxZoom();
            if (level < 0 || level > maxZoomLevel) {
                return false;
            }
            cameraParameters.setZoom(level);
            camera.setParameters(cameraParameters);
            return true;
        } else {
            return false;
        }
    }


    @Override
    public Size getPhotoSizeForQuality(@Configuration.MediaQuality int mediaQuality) {
        return CameraHelper.getPictureSize(Size.fromList(camera.getParameters().getSupportedPictureSizes()), mediaQuality);
    }

    @Override
    protected void prepareCameraOutputs() {
        try {
            if (configurationProvider.getMediaQuality() == Configuration.MEDIA_QUALITY_AUTO) {
                camcorderProfile = CameraHelper.getCamcorderProfile(currentCameraId, configurationProvider.getVideoFileSize(), configurationProvider.getMinimumVideoDuration());
            } else
                camcorderProfile = CameraHelper.getCamcorderProfile(configurationProvider.getMediaQuality(), currentCameraId);

            final List<Size> previewSizes = Size.fromList(camera.getParameters().getSupportedPreviewSizes());
            final List<Size> pictureSizes = Size.fromList(camera.getParameters().getSupportedPictureSizes());
            List<Size> videoSizes;
            if (Build.VERSION.SDK_INT > 10)
                videoSizes = Size.fromList(camera.getParameters().getSupportedVideoSizes());
            else videoSizes = previewSizes;

            videoSize = CameraHelper.getSizeWithClosestRatio(
                    (videoSizes == null || videoSizes.isEmpty()) ? previewSizes : videoSizes,
                    camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);

//            photoSize = CameraHelper.getPictureSize(
//                    (pictureSizes == null || pictureSizes.isEmpty()) ? previewSizes : pictureSizes,
//                            Configuration.MEDIA_QUALITY_HIGHEST );

            photoSize = CameraHelper.findClosetPictureSize(pictureSizes, ORIGINAL_IMAGE_WIDTH, ORIGINAL_IMAGE_HEIGHT);


//            photoSize = CameraHelper.findClosetPictureSize(pictureSizes,ORIGINAL_IMAGE_WIDTH, ORIGINAL_IMAGE_HEIGHT);
//            // TODO
//            photoSize = new Size();
//            photoSize.setHeight(ORIGINAL_IMAGE_HEIGHT);
//            photoSize.setWidth(ORIGINAL_IMAGE_WIDTH);


            if (configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_PHOTO
                    || configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_UNSPECIFIED) {
                previewSize = CameraHelper.findClosestPreviewSize(previewSizes, new Point(ORIGINAL_IMAGE_WIDTH, ORIGINAL_IMAGE_HEIGHT));
//                previewSize = CameraHelper.getSizeWithClosestRatio(previewSizes, photoSize.getWidth(), photoSize.getHeight());
            } else {
                previewSize = CameraHelper.getSizeWithClosestRatio(previewSizes, videoSize.getWidth(), videoSize.getHeight());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while setup camera sizes.");
        }
    }

    @Override
    protected boolean prepareVideoRecorder() {
        videoRecorder = new MediaRecorder();
        try {
            camera.lock();
            camera.unlock();
            videoRecorder.setCamera(camera);

            videoRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            videoRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

            videoRecorder.setOutputFormat(camcorderProfile.fileFormat);
            videoRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
            videoRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            videoRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
            videoRecorder.setVideoEncoder(camcorderProfile.videoCodec);

            videoRecorder.setAudioEncodingBitRate(camcorderProfile.audioBitRate);
            videoRecorder.setAudioChannels(camcorderProfile.audioChannels);
            videoRecorder.setAudioSamplingRate(camcorderProfile.audioSampleRate);
            videoRecorder.setAudioEncoder(camcorderProfile.audioCodec);

            videoRecorder.setOutputFile(outputPath.toString());

            if (configurationProvider.getVideoFileSize() > 0) {
                videoRecorder.setMaxFileSize(configurationProvider.getVideoFileSize());

                videoRecorder.setOnInfoListener(this);
            }
            if (configurationProvider.getVideoDuration() > 0) {
                videoRecorder.setMaxDuration(configurationProvider.getVideoDuration());

                videoRecorder.setOnInfoListener(this);
            }

            videoRecorder.setOrientationHint(getVideoOrientation(configurationProvider.getSensorPosition()));
            videoRecorder.setPreviewDisplay(surface);

            videoRecorder.prepare();

            return true;
        } catch (IllegalStateException error) {
            Log.e(TAG, "IllegalStateException preparing MediaRecorder: " + error.getMessage());
        } catch (IOException error) {
            Log.e(TAG, "IOException preparing MediaRecorder: " + error.getMessage());
        } catch (Throwable error) {
            Log.e(TAG, "Error during preparing MediaRecorder: " + error.getMessage());
        }

        releaseVideoRecorder();
        return false;
    }

    @Override
    protected void onMaxDurationReached() {
        stopVideoRecord(null);
    }

    @Override
    protected void onMaxFileSizeReached() {
        stopVideoRecord(null);
    }

    @Override
    protected void releaseVideoRecorder() {
        super.releaseVideoRecorder();

        try {
            camera.lock(); // lock camera for later use
        } catch (Exception ignore) {
        }
    }

    @Override
    public Size getSizePreview() {
        return previewSize;
    }

    //------------------------Implementation------------------

    private void startPreview(SurfaceHolder surfaceHolder) {
        try {
            final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(currentCameraId, cameraInfo);
            int cameraRotationOffset = cameraInfo.orientation;

            final Camera.Parameters parameters = camera.getParameters();
            setAutoFocus(camera, parameters);
            setFlashMode(configurationProvider.getFlashMode());

            if (configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_PHOTO
                    || configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_UNSPECIFIED)
                turnPhotoCameraFeaturesOn(camera, parameters);
            else if (configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_PHOTO)
                turnVideoCameraFeaturesOn(camera, parameters);

            final int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break; // Natural orientation
                case Surface.ROTATION_90:
                    degrees = 90;
                    break; // Landscape left
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;// Upside down
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;// Landscape right
            }

            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                displayRotation = (cameraRotationOffset + degrees) % 360;
                displayRotation = (360 - displayRotation) % 360; // compensate
            } else {
                displayRotation = (cameraRotationOffset - degrees + 360) % 360;
            }

            this.camera.setDisplayOrientation(displayRotation);


            if (Build.VERSION.SDK_INT > 14
                    && parameters.isVideoStabilizationSupported()
                    && (configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_VIDEO
                    || configurationProvider.getMediaAction() == Configuration.MEDIA_ACTION_UNSPECIFIED)) {
                parameters.setVideoStabilization(true);
            }

            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
            parameters.setPictureSize(photoSize.getWidth(), photoSize.getHeight());

            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

        } catch (IOException error) {
            Log.d(TAG, "Error setting camera preview: " + error.getMessage());
        } catch (Exception ignore) {
            Log.d(TAG, "Error starting camera preview: " + ignore.getMessage());
        }
    }

    private void turnPhotoCameraFeaturesOn(Camera camera, Camera.Parameters parameters) {
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(parameters);
    }

    private void turnVideoCameraFeaturesOn(Camera camera, Camera.Parameters parameters) {
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        camera.setParameters(parameters);
    }

    private void setAutoFocus(Camera camera, Camera.Parameters parameters) {
        try {
            if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(parameters);
            }
        } catch (Exception ignore) {
        }
    }

    private void setFlashMode(Camera camera, Camera.Parameters parameters, @Configuration.FlashMode int flashMode) {
        try {
            switch (flashMode) {
                case Configuration.FLASH_MODE_AUTO:
                    if (parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_AUTO))
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    break;
                case Configuration.FLASH_MODE_ON:
                    if (parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH))
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    break;
                case Configuration.FLASH_MODE_OFF:
                    if (parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_OFF))
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    break;
                default:
                    if (parameters.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_AUTO))
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    break;
            }
            camera.setParameters(parameters);
        } catch (Exception ignore) {
        }
    }

    private void setCameraPhotoQuality(Camera camera) {
        final Camera.Parameters parameters = camera.getParameters();

        parameters.setPictureFormat(PixelFormat.JPEG);

        if (configurationProvider.getMediaQuality() == Configuration.MEDIA_QUALITY_LOW) {
            parameters.setJpegQuality(50);
        } else if (configurationProvider.getMediaQuality() == Configuration.MEDIA_QUALITY_MEDIUM) {
            parameters.setJpegQuality(75);
        } else if (configurationProvider.getMediaQuality() == Configuration.MEDIA_QUALITY_HIGH) {
            parameters.setJpegQuality(100);
        } else if (configurationProvider.getMediaQuality() == Configuration.MEDIA_QUALITY_HIGHEST) {
            parameters.setJpegQuality(100);
        }
        parameters.setPictureSize(photoSize.getWidth(), photoSize.getHeight());

        camera.setParameters(parameters);
    }

    @Override
    protected int getPhotoOrientation(@Configuration.SensorPosition int sensorPosition) {
        final int rotate;
        if (currentCameraId.equals(faceFrontCameraId)) {
            rotate = (360 + faceFrontCameraOrientation + configurationProvider.getDegrees()) % 360;
        } else {
            rotate = (360 + faceBackCameraOrientation - configurationProvider.getDegrees()) % 360;
        }

        if (rotate == 0) {
            orientation = ExifInterface.ORIENTATION_NORMAL;
        } else if (rotate == 90) {
            orientation = ExifInterface.ORIENTATION_ROTATE_90;
        } else if (rotate == 180) {
            orientation = ExifInterface.ORIENTATION_ROTATE_180;
        } else if (rotate == 270) {
            orientation = ExifInterface.ORIENTATION_ROTATE_270;
        } else {
            orientation = ExifInterface.ORIENTATION_NORMAL;
        }

        return orientation;

    }

    @Override
    protected int getVideoOrientation(@Configuration.SensorPosition int sensorPosition) {
        int degrees = 0;
        switch (sensorPosition) {
            case Configuration.SENSOR_POSITION_UP:
                degrees = 0;
                break; // Natural orientation
            case Configuration.SENSOR_POSITION_LEFT:
                degrees = 90;
                break; // Landscape left
            case Configuration.SENSOR_POSITION_UP_SIDE_DOWN:
                degrees = 180;
                break;// Upside down
            case Configuration.SENSOR_POSITION_RIGHT:
                degrees = 270;
                break;// Landscape right
        }

        final int rotate;
        if (currentCameraId.equals(faceFrontCameraId)) {
            rotate = (360 + faceFrontCameraOrientation + degrees) % 360;
        } else {
            rotate = (360 + faceBackCameraOrientation - degrees) % 360;
        }
        return rotate;
    }

    protected void onPictureTaken(final byte[] bytes, Camera camera, final CameraFragmentResultListener callback) {
        final File pictureFile = outputPath;
        if (pictureFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions.");
            return;
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(pictureFile);
            fileOutputStream.write(bytes);
            fileOutputStream.close();
        } catch (FileNotFoundException error) {
            Log.e(TAG, "File not found: " + error.getMessage());
        } catch (IOException error) {
            Log.e(TAG, "Error accessing file: " + error.getMessage());
        } catch (Throwable error) {
            Log.e(TAG, "Error saving file: " + error.getMessage());
        }

        try {
            final ExifInterface exif = new ExifInterface(pictureFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + getPhotoOrientation(configurationProvider.getSensorPosition()));
            exif.saveAttributes();

            if (photoListener != null) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        photoListener.onPhotoTaken(bytes, outputPath, callback);
                    }
                });
            }
        } catch (Throwable error) {
            Log.e(TAG, "Can't save exif info: " + error.getMessage());
        }
    }

    @Override
    public CharSequence[] getVideoQualityOptions() {
        final List<CharSequence> videoQualities = new ArrayList<>();

        if (configurationProvider.getMinimumVideoDuration() > 0)
            videoQualities.add(new VideoQualityOption(Configuration.MEDIA_QUALITY_AUTO, CameraHelper.getCamcorderProfile(Configuration.MEDIA_QUALITY_AUTO, getCurrentCameraId()), configurationProvider.getMinimumVideoDuration()));

        CamcorderProfile camcorderProfile = CameraHelper.getCamcorderProfile(Configuration.MEDIA_QUALITY_HIGH, getCurrentCameraId());
        double videoDuration = CameraHelper.calculateApproximateVideoDuration(camcorderProfile, configurationProvider.getVideoFileSize());
        videoQualities.add(new VideoQualityOption(Configuration.MEDIA_QUALITY_HIGH, camcorderProfile, videoDuration));

        camcorderProfile = CameraHelper.getCamcorderProfile(Configuration.MEDIA_QUALITY_MEDIUM, getCurrentCameraId());
        videoDuration = CameraHelper.calculateApproximateVideoDuration(camcorderProfile, configurationProvider.getVideoFileSize());
        videoQualities.add(new VideoQualityOption(Configuration.MEDIA_QUALITY_MEDIUM, camcorderProfile, videoDuration));

        camcorderProfile = CameraHelper.getCamcorderProfile(Configuration.MEDIA_QUALITY_LOW, getCurrentCameraId());
        videoDuration = CameraHelper.calculateApproximateVideoDuration(camcorderProfile, configurationProvider.getVideoFileSize());
        videoQualities.add(new VideoQualityOption(Configuration.MEDIA_QUALITY_LOW, camcorderProfile, videoDuration));

        final CharSequence[] array = new CharSequence[videoQualities.size()];
        videoQualities.toArray(array);

        return array;
    }

    @Override
    public CharSequence[] getPhotoQualityOptions() {
        // TODO: 24/10/2017
        final List<CharSequence> photoQualities = new ArrayList<>();
        photoQualities.add(new PhotoQualityOption(Configuration.MEDIA_QUALITY_HIGHEST, getPhotoSizeForQuality(Configuration.MEDIA_QUALITY_HIGHEST)));
        photoQualities.add(new PhotoQualityOption(Configuration.MEDIA_QUALITY_HIGH, getPhotoSizeForQuality(Configuration.MEDIA_QUALITY_HIGH)));
        photoQualities.add(new PhotoQualityOption(Configuration.MEDIA_QUALITY_MEDIUM, getPhotoSizeForQuality(Configuration.MEDIA_QUALITY_MEDIUM)));
        photoQualities.add(new PhotoQualityOption(Configuration.MEDIA_QUALITY_LOWEST, getPhotoSizeForQuality(Configuration.MEDIA_QUALITY_LOWEST)));

        final CharSequence[] array = new CharSequence[photoQualities.size()];
        photoQualities.toArray(array);

        return array;
    }

    @Override
    public void onError(int error, Camera camera) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Camera1Manager.this.camera != null) {
                    Camera1Manager.this.camera.release();
                    Camera1Manager.this.camera = null;
                }
                if (cameraCloseListenerLocal != null) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cameraCloseListenerLocal.onCameraOpen(currentCameraId);
                        }
                    });
                }

            }
        });
    }
}
