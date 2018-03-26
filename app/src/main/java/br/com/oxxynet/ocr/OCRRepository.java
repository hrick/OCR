package br.com.oxxynet.ocr;

import com.j256.ormlite.dao.Dao;

import org.androidannotations.annotations.EBean;
import org.androidannotations.ormlite.annotations.OrmLiteDao;

import java.sql.SQLException;
import java.util.List;

import br.com.oxxynet.ocr.model.OCR;

import static android.R.attr.id;

@EBean
public class OCRRepository {

    @OrmLiteDao(helper = DatabaseHelper.class)
    Dao<OCR, Integer> ocrDao;


    public List<OCR> listarOcr() {
        try {
            return ocrDao.queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void salvar(OCR OCR) {
        try {
            ocrDao.createOrUpdate(OCR);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}
