package br.com.oxxynet.ocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.github.florent37.camerafragment.CameraFragment;
import com.github.florent37.camerafragment.CameraFragmentApi;
import com.github.florent37.camerafragment.configuration.Configuration;
import com.github.florent37.camerafragment.listeners.CameraFragmentControlsAdapter;
import com.github.florent37.camerafragment.listeners.CameraFragmentResultAdapter;
import com.github.florent37.camerafragment.listeners.CameraFragmentStateAdapter;
import com.github.florent37.camerafragment.widgets.FlashSwitchView;
import com.github.florent37.camerafragment.widgets.RecordButton;
import com.tbruyelle.rxpermissions.RxPermissions;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.Fullscreen;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.internal.Util;


@EActivity(R.layout.activity_camera)
@Fullscreen
public class CameraActivity extends AppCompatActivity {

    public static final String EXTRA_PATH_FOTO = "PATH_FOTO";
    public static final int ZOOM_VALUE = 3;
    public static final String FRAGMENT_TAG = "camera";
    private static final long REP_DELAY = 50L;

    @ViewById(R.id.flash_switch_view)
    FlashSwitchView flashSwitchView;
    @ViewById(R.id.record_button)
    RecordButton recordButton;

    Handler repeatUpdateHandler = new Handler();

    @ViewById(R.id.btnConfirmar)
    Button btnConfirmar;

    @ViewById(R.id.btnCancelar)
    Button btnCancelar;

    @ViewById(R.id.ibMaisZoom)
    ImageButton ibMaisZoom;

    @ViewById(R.id.ibMenosZoom)
    ImageButton ibMenosZoom;


    @ViewById(R.id.ivPreview)
    ImageView ivPreview;

    @ViewById(R.id.content)
    FrameLayout content;

    ImageButton ibDescricao;
    Bitmap bitmapPreview;
    @ViewById(R.id.rvPreview)
    RelativeLayout rvPreview;
    private File filePreview;
    private int zoom = 0;
    private boolean mAutoDecrement;
    private boolean mAutoIncrement;
    private boolean longClickActive;


    @ViewById(R.id.cameraLayout)
    View cameraLayout;

    private MaterialDialog materialDialogProgress;

    @Extra
    boolean mostrarBotaoLocalizacao;
    @Extra
    String idMarcaModelo;
    @Extra
    int anoFabricacao;
    @Extra
    String idOrdemServico;


    @AfterViews
    public void init() {
        filePreview = new File(getTempFolder(this), "preview.jpg");
        if (!filePreview.exists())
            try {
                filePreview.createNewFile();
            } catch (IOException e) {
                showToast(e.getMessage());
                finish();
            }
        ibMaisZoom.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                mAutoIncrement = false;
            } else if ((event.getAction() == MotionEvent.ACTION_DOWN)) {
                mAutoIncrement = true;
                repeatUpdateHandler.post(new RptUpdater());
            }
            return false;
        });

        ibMenosZoom.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                mAutoDecrement = false;
            } else if ((event.getAction() == MotionEvent.ACTION_DOWN)) {
                mAutoDecrement = true;
                repeatUpdateHandler.post(new RptUpdater());
            }
            return false;
        });
        content.setOnClickListener(v -> takePicture());

        addCamera();
    }

    public void addCamera() {
        recordButton.setEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        final CameraFragment cameraFragment = CameraFragment.newInstance(new Configuration.Builder()
                .setCamera(Configuration.CAMERA_FACE_REAR).build());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, cameraFragment, FRAGMENT_TAG)
                .commitAllowingStateLoss();

        if (cameraFragment != null) {

            cameraFragment.setStateListener(new CameraFragmentStateAdapter() {

                @Override
                public void onCurrentCameraBack() {
                }

                @Override
                public void onCurrentCameraFront() {
                }

                @Override
                public void onFlashAuto() {
                    flashSwitchView.displayFlashAuto();
                }

                @Override
                public void onFlashOn() {
                    flashSwitchView.displayFlashOn();
                }

                @Override
                public void onFlashOff() {
                    flashSwitchView.displayFlashOff();
                }

                @Override
                public void onCameraSetupForPhoto() {
                }

                @Override
                public void onCameraSetupForVideo() {
                }

                @Override
                public void shouldRotateControls(int degrees) {
                    ViewCompat.setRotation(ibMaisZoom, degrees);
                    ViewCompat.setRotation(flashSwitchView, degrees);
                    ViewCompat.setRotation(ibMenosZoom, degrees);
                }

                @Override
                public void onRecordStateVideoReadyForRecord() {
                    recordButton.displayVideoRecordStateReady();
                }

                @Override
                public void onRecordStateVideoInProgress() {
                    recordButton.displayVideoRecordStateInProgress();
                }

                @Override
                public void onRecordStatePhoto() {
                    recordButton.displayPhotoState();
                }

                @Override
                public void onStopVideoRecord() {
                }

                @Override
                public void onStartVideoRecord(File outputFile) {
                }
            });

            cameraFragment.setControlsListener(new CameraFragmentControlsAdapter() {
                @Override
                public void lockControls() {
                    recordButton.setEnabled(false);
                    content.setEnabled(false);
                    flashSwitchView.setEnabled(false);
                    lockControlsLocal(false);
                }

                @Override
                public void unLockControls() {
                    recordButton.setEnabled(true);
                    content.setEnabled(true);
                    flashSwitchView.setEnabled(true);
                    lockControlsLocal(true);
                }

                @Override
                public void allowCameraSwitching(boolean allow) {
                }

                @Override
                public void allowRecord(boolean allow) {
                    recordButton.setEnabled(allow);
                    content.setEnabled(allow);
                }

                @Override
                public void setMediaActionSwitchVisible(boolean visible) {
                }
            });
        }
    }

    private void lockControlsLocal(boolean enabled) {
        ibMenosZoom.setEnabled(enabled);
        ibMaisZoom.setEnabled(enabled);
        if (enabled) {
            ibMaisZoom.setAlpha(1f);
            ibMenosZoom.setAlpha(1f);
        } else {
            ibMaisZoom.setAlpha(0.5f);
            ibMenosZoom.setAlpha(0.5f);
        }
    }


    public String getTempFolder(Context context) {
        final String path = String.format("%s/temp/", context.getFilesDir().getAbsolutePath());
        File theDir = new File(path);
        if (!theDir.exists()) {
            theDir.mkdirs();
        }
        return path;
    }

    @UiThread
    void maisZoom() {
        zoom = zoom + ZOOM_VALUE;
        actionZoom();
    }

    @UiThread
    void menosZoom() {
        zoom = zoom - ZOOM_VALUE;
        if (zoom < 0)
            zoom = zoom + ZOOM_VALUE;
        actionZoom();
    }

    private void actionZoom() {
        final CameraFragmentApi cameraFragment = getCameraFragment();
        if (cameraFragment != null) {
            if (!cameraFragment.zoom(zoom)) {
                zoom = zoom - ZOOM_VALUE;
            }
        }
    }


    @Click(R.id.flash_switch_view)
    public void onFlashSwitcClicked() {
        final CameraFragmentApi cameraFragment = getCameraFragment();
        if (cameraFragment != null) {
            cameraFragment.toggleFlashMode();
        }
    }

    @Click(R.id.btnCancelar)
    public void onCancelarPreviewClicked() {
        recycleBitmap();
        rvPreview.setVisibility(View.GONE);
        flashSwitchView.displayFlashOff();
        getCameraFragment().openCamera();
        zoom = 0;
    }

    @Click(R.id.btnConfirmar)
    public void onConfirmarPreviewClicked() {
        enviarRetornoFoto();
    }

    @Click(R.id.record_button)
    public void onRecordButtonClicked() {
        takePicture();
    }

    void takePicture() {
        recordButton.setEnabled(false);
        content.setEnabled(false);
        final CameraFragmentApi cameraFragment = getCameraFragment();
        if (cameraFragment != null) {
            cameraFragment.takePhotoOrCaptureVideo(new CameraFragmentResultAdapter() {
                                                       @Override
                                                       public void onVideoRecorded(String filePath) {
                                                       }

                                                       @Override
                                                       public void onPhotoTaken(byte[] bytes, String filePath) {
                                                           cameraFragment.closeCamera();
                                                           dismissProgress();
                                                           rvPreview.setVisibility(View.VISIBLE);
                                                           Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                                           try {
                                                               ExifInterface exif = new ExifInterface(filePath);
                                                               String orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
                                                               if (orientString != null && !orientString.equalsIgnoreCase(Integer.toString(ExifInterface.ORIENTATION_UNDEFINED))) {
                                                                   int rotationAngle = 90;
                                                                   // Rotate Bitmap
                                                                   Matrix matrix = new Matrix();
                                                                   matrix.setRotate(rotationAngle, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
                                                                   Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                                                                   if (bitmapPreview == null) {
                                                                       bitmapPreview = Bitmap.createBitmap(rotatedBitmap);
                                                                   }
                                                                   ivPreview.setImageBitmap(bitmapPreview);
                                                                   if (rotatedBitmap != null && !rotatedBitmap.isRecycled())
                                                                       rotatedBitmap.recycle();
                                                               } else {
                                                                   if (bitmapPreview == null) {
                                                                       bitmapPreview = Bitmap.createBitmap(bitmap);
                                                                   }
                                                                   ivPreview.setImageBitmap(bitmapPreview);
                                                               }
                                                           } catch (IOException e) {
                                                               showToast("Erro ao obter foto");
                                                           }

                                                       }

                                                       @Override
                                                       public void onPhotoStart() {
                                                           showProgress();
                                                       }

                                                       @Override
                                                       public void onManualFocus() {
                                                           showToast("Focando");
                                                       }
                                                   },
                    getTempFolder(this),
                    "preview_camera");
        }
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    public void gravarImagemAltaQualidade(Bitmap bitmap, File file, Bitmap.CompressFormat format) throws IOException {
        if (!file.exists())
            file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        bitmap.compress(format, 100, fos);
        fos.close();
    }


    void enviarRetornoFoto() {
        try {
            Intent intent = new Intent();
            gravarImagemAltaQualidade(bitmapPreview, filePreview, Bitmap.CompressFormat.JPEG);
            intent.putExtra(EXTRA_PATH_FOTO, filePreview.getAbsolutePath());
            setResult(RESULT_OK, intent);
            finish();
        } catch (IOException e) {
            showToast("Erro ao obter foto");
        }

    }


    @UiThread
    void showProgress() {
        materialDialogProgress = new MaterialDialog.Builder(this)
                .title("Aguarde")
                .content("Obtendo imagem")
                .progress(true, 0)
                .autoDismiss(false)
                .cancelable(false)
                .build();
        materialDialogProgress.show();
    }

    @UiThread
    void dismissProgress() {
        if (materialDialogProgress != null && materialDialogProgress.isShowing()) {
            materialDialogProgress.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recycleBitmap();
    }


    private void recycleBitmap() {
        if (bitmapPreview != null) {
            bitmapPreview.recycle();
            bitmapPreview = null;
        }
    }

    private CameraFragmentApi getCameraFragment() {
        return (CameraFragmentApi) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    private class RptUpdater implements Runnable {

        @Override
        public void run() {
            if (mAutoIncrement) {
                maisZoom();
                repeatUpdateHandler.postDelayed(new RptUpdater(), REP_DELAY);
            } else if (mAutoDecrement) {
                menosZoom();
                repeatUpdateHandler.postDelayed(new RptUpdater(), REP_DELAY);
            }
        }
    }

}
