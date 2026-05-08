package io.lpin.android.sdk.face.database;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * 사용자 정보 클래스
 */

@Entity(tableName = UserDao.TABLE_NAME)
public class UserEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private byte[] features;
    private Integer featureDims;

    @Ignore
    public UserEntity() {

    }

    public UserEntity(String name, byte[] features, Integer featureDims) {
        this.name = name;
        this.features = features;
        this.featureDims = featureDims;
    }

    public UserEntity(String name, ArrayList<float[]> features, Integer featureDims) {
        this.name = name;
        this.featureDims = featureDims;
        this.features = featureToBlog(features);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFeatures(byte[] features) {
        this.features = features;
    }

    public byte[] getFeatures() {
        return this.features;
    }

    public Integer getFeatureDims() {
        return featureDims;
    }

    public void setFeatureDims(Integer featureDims) {
        this.featureDims = featureDims;
    }


    /**
     * @return Feature 데이터 반환
     */
    // public ArrayList<float[]> getFeatures() {
    //     return blobToFeatures(features);
    // }
    @SuppressLint("Assert")
    private static double dot_product(float[] a, float[] b) {
        assert (a.length == b.length);
        double product = 0;
        for (int i = 0; i < a.length; i++) {
            product += a[i] * b[i];
        }
        return product;
    }

    private static double magnitude(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double cosine_similarity(float[] a, float[] b) {
        double dotProduct = dot_product(a, b);
        double magnitudeA = magnitude(a);
        double magnitudeB = magnitude(b);

        Log.d("COSINE_DEBUG", String.format(
                "dotProduct: %.6f, magA: %.6f, magB: %.6f",
                dotProduct, magnitudeA, magnitudeB
        ));

        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        // 정확한 코사인 유사도: [-1, 1] 범위
        double cosine = dotProduct / (magnitudeA * magnitudeB);

        Log.d("COSINE_DEBUG", String.format("cosine: %.6f, result: %.6f", cosine, (cosine + 1)/2.0));

        // [0, 1] 범위로 변환
        return (cosine + 1.0) / 2.0;
    }

    /**
     * 주어진 특징 벡터와 현재 사용자의 특징 벡터 간의 유사도를 계산한다
     *
     * @param feature 특징 벡터
     * @return 주어진 특징 벡터와 현재 사용자의 특징 벡터 간의 유사도
     */
    public float similarity(float[] feature) {
        float similarity = 0;
        ArrayList<float[]> features = blobToFeatures(this.features);
        for (float[] feat : features) {
            similarity += (float)cosine_similarity(feat, feature);
        }
        return similarity / features.size(); // average distance
    }

    private byte[] featureToBlog(ArrayList<float[]> features) {
        final int length = featureDims * Float.SIZE / Byte.SIZE;
        ByteBuffer byteBuffer = ByteBuffer.allocate(features.size() * length);

        int offset = 0;
        for (float[] feature : features) {
            ByteBuffer view = byteBuffer.slice();
            view.position(offset);
            FloatBuffer floatBuffer = view.asFloatBuffer();
            floatBuffer.put(feature);
            offset += length;
        }
        return byteBuffer.array();
    }

    /**
     * Blob 데이터를 ArrayList<float[]> 형태로 변경
     *
     * @param blob
     * @return
     */
    private ArrayList<float[]> blobToFeatures(final byte[] blob) {
        ArrayList<float[]> arrayList = new ArrayList<>();
        final int length = featureDims * Float.SIZE / Byte.SIZE;
        for (int offset = 0; offset < blob.length; offset += length) {
            float[] feature = new float[featureDims];
            ByteBuffer.wrap(blob, offset, length).slice().asFloatBuffer().get(feature);
            arrayList.add(feature);
        }
        return arrayList;
    }
}
