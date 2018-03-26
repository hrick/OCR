package br.com.oxxynet.ocr;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import br.com.oxxynet.ocr.model.OCR;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity implements Detector.Processor<TextBlock> {

    private static final int REQUEST_CODE_ABRIR_CAMERA_APP = 45;
    @ViewById(R.id.list)
    ListView listView;
    @Bean
    OCRRepository ocrRepository;
    ArrayAdapter<String> adapter;
    List<OCR> ocrs;

    @AfterViews
    public void init() {
        ocrs = new ArrayList<>();
        ocrs = ocrRepository.listarOcr();
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, ocrs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                TextView text2 = (TextView) view.findViewById(android.R.id.text2);

                text1.setText(ocrs.get(position).getOcrEfetuada());
                text2.setText(String.valueOf(ocrs.get(position).getId()));
                return view;
            }
        };
        listView.setAdapter(adapter);
            RxPermissions.getInstance(this)
                    .request(android.Manifest.permission.CAMERA,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .subscribe(aBoolean -> {
                        if (!aBoolean) {
                            finish();
                        }
                    });

    }

    @OnActivityResult(REQUEST_CODE_ABRIR_CAMERA_APP)
    protected void onActivityResultAbrirCamera(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            try {
                String path = data.getStringExtra("PATH_FOTO");
                processarFoto(new File(path));

            } catch (Exception e) {
                Log.i(this.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void processarFoto(File file) {
        recortarImagem(file.getAbsolutePath());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_image:
                tirarFoto();
                break;
        }
        return true;
    }

    private void recortarImagem(String arquivoImagem) {
        File imagem = new File(arquivoImagem);
        UCrop.Options options = new UCrop.Options();
        options.useSourceImageAspectRatio();
        options.setToolbarTitle("OCR");
        options.setCompressionFormat(Bitmap.CompressFormat.PNG);
        options.setAllowedGestures(UCropActivity.ALL, UCropActivity.ALL, UCropActivity.ALL);
        options.setFreeStyleCropEnabled(false);
        options.setHideBottomControls(true);
        options.setCompressionQuality(100);
        Uri source = Uri.fromFile(imagem);
        File tempFile;
        try {
            tempFile = new File(Environment.getExternalStorageDirectory() + "/preview_crop.jpg");
            if (!tempFile.exists())
                tempFile.createNewFile();
            Uri destination = Uri.fromFile(tempFile);
            UCrop.of(source, destination)
                    .withOptions(options)
                    .withAspectRatio(4, 1)
                    .withMaxResultSize(800, 600)
                    .start(this);
        } catch (IOException e) {
            Log.i(this.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void realizarOCR(String categoryImageTempPath) {
        TextRecognizer textRecognizer = new TextRecognizer.Builder(this).build();
        textRecognizer.setProcessor(this);
        Bitmap bitmap = BitmapFactory.decodeFile(categoryImageTempPath);
//
        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        Canvas canvasResult = new Canvas();
        Paint bitmapPaint = new Paint();

        c.drawBitmap(bitmap, 0, 0, bitmapPaint);

        bitmapPaint.setColorFilter(new ColorMatrixColorFilter(createInvertedMatrix()));
        c.drawBitmap(result, 0, 0, bitmapPaint);

        bitmapPaint.setColorFilter(new ColorMatrixColorFilter(createBrightMatrix(-20)));
        c.drawBitmap(result, 0, 0, bitmapPaint);

        bitmapPaint.setColorFilter(null);
        canvasResult.drawBitmap(result, 0, 0, bitmapPaint);
        bitmap.recycle();

        Frame outputFrame = new Frame.Builder()
                .setBitmap(result)
                .build();
        textRecognizer.receiveFrame(outputFrame);
    }


    public static ColorMatrix createInvertedMatrix() {
        return new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0});
    }


    public static ColorMatrix createBrightMatrix(int fb) {
        return new ColorMatrix(new float[]{
                1, 0, 0, 0, fb,
                0, 1, 0, 0, fb,
                0, 0, 1, 0, fb,
                0, 0, 0, 1, 0});
    }


    @OnActivityResult(UCrop.REQUEST_CROP)
    protected void onActivityResultRecortarImagemOk(int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            final Uri resultUri = UCrop.getOutput(data);
            File photo = new File(resultUri.getPath());
            if (photo.exists()) {
                try {
                    realizarOCR(resultUri.getPath());
                } catch (Exception e) {
                    Log.i(this.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }

    private void tirarFoto() {
        CameraActivity_.intent(this).startForResult(REQUEST_CODE_ABRIR_CAMERA_APP);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ocr, menu);
        return true;
    }
    public String tratamentoChassi(String ocrChassi) {
        String chassiTratado = ocrChassi;
        if (ocrChassi.contains("O") || ocrChassi.contains("Q")){
            chassiTratado = ocrChassi.replace("O","0");
            chassiTratado = chassiTratado.replace("Q","0");

        }
        if (chassiTratado.contains("I")){
            chassiTratado = chassiTratado.replace("I","1");
        }
        return chassiTratado;
    }

    public String tratarOCR(String ocr) {
        String ocrTratada = (ocr.replace("null", "")).replaceAll("\\s+", "").toUpperCase();

        if (ocr.contains("%")){
            ocrTratada = ocr.replace("%","8");
        }
        if (ocr.contains("/")){
            ocrTratada = ocrTratada.replace("/","7");
        }
        if (ocr.contains("*")){
            ocrTratada = ocrTratada.replace("*","");
        }
        if (ocr.contains(".")){
            ocrTratada = ocrTratada.replace(".","");
        }
        if (ocr.contains(",")){
            ocrTratada = ocrTratada.replace(",","");
        }
        return ocrTratada;
    }


    @Override
    public void release() {

    }

    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {
        String ocrReconhecida = "";
        SparseArray<TextBlock> items = detections.getDetectedItems();
        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            ocrReconhecida += item.getValue();
        }
        if (!ocrReconhecida.isEmpty()) {
            ocrReconhecida = tratarOCR(ocrReconhecida.toUpperCase());
            ocrReconhecida = tratamentoChassi(ocrReconhecida);
        } else {
            ocrReconhecida = "Chassi não reconhecido";
        }
        OCR ocr = new OCR();
        ocr.setOcrEfetuada(ocrReconhecida);
        ocrs.add(ocr);
        ocrRepository.salvar(ocr);
        adapter.notifyDataSetChanged();
    }
}
