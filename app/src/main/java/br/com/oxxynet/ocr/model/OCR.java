package br.com.oxxynet.ocr.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Created by henrique.pereira on 16/03/2018.
 */

@DatabaseTable(tableName = "TB_OCR")
public class OCR {
    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField(columnName = "OCR_EFETUADA")
    private String ocrEfetuada;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOcrEfetuada() {
        return ocrEfetuada;
    }

    public void setOcrEfetuada(String ocrEfetuada) {
        this.ocrEfetuada = ocrEfetuada;
    }
}
