package com.github.florent37.camerafragment;

import android.support.annotation.Nullable;
import android.view.MotionEvent;

import com.github.florent37.camerafragment.internal.utils.Size;
import com.github.florent37.camerafragment.listeners.CameraFragmentControlsListener;
import com.github.florent37.camerafragment.listeners.CameraFragmentResultListener;
import com.github.florent37.camerafragment.listeners.CameraFragmentStateListener;
import com.github.florent37.camerafragment.listeners.CameraFragmentVideoRecordTextListener;

/*
 * Created by florentchampigny on 16/01/2017.
 */

public interface CameraFragmentApi {

    void takePhotoOrCaptureVideo(CameraFragmentResultListener resultListener, @Nullable String directoryPath, @Nullable String fileName);

    void openSettingDialog();

    Size getPreviewSize();

    void switchCameraTypeFrontBack();

    void switchActionPhotoVideo();

    void toggleFlashMode();

    boolean zoom(int level);

    void closeCamera();

    void setStateListener(CameraFragmentStateListener cameraFragmentStateListener);

    void setTextListener(CameraFragmentVideoRecordTextListener cameraFragmentVideoRecordTextListener);

    void setControlsListener(CameraFragmentControlsListener cameraFragmentControlsListener);

    void setResultListener(CameraFragmentResultListener cameraFragmentResultListener);

    void requestFocus(MotionEvent event);

    void openCamera();
}
