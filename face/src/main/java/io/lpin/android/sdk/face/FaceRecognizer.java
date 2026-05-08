package io.lpin.android.sdk.face;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Trace;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import io.lpin.android.sdk.face.database.UserDatabase;
import io.lpin.android.sdk.face.database.UserDao;
import io.lpin.android.sdk.face.database.UserEntity;
import kotlin.Pair;

public class FaceRecognizer {
    private static final String TAG = FaceRecognizer.class.getSimpleName();
    /**
     * 특징 벡터의 길이.
     */
    public int featureDims;
    File path;
    private String inputName;
    private String outputName;
    private int inputSize;
    private float[] outputs;
    private String[] outputNames;
    private boolean statLogging = false;
    // private UserManager userManager;
    // private ArrayList<UserEntity> users;
    private final TensorFlowInferenceInterface inferenceInterface;
    private UserDatabase database;
    private UserDao userDao;

    private FaceRecognizer(Builder builder, String name) {
        path = builder.context.getFilesDir();
        AssetManager assetManager = builder.context.getAssets();
        inferenceInterface = new TensorFlowInferenceInterface(assetManager, Builder.MODEL_FILE);

        inputName = Builder.INPUT_NAME;
        outputName = Builder.OUTPUT_NAME;

        inputSize = Builder.INPUT_SIZE;

        outputNames = new String[]{outputName};
        featureDims = (int) inferenceInterface.graph().operation(outputName).output(0).shape().size(1);
        Log.i(TAG, "Output layer size is " + featureDims);
        outputs = new float[featureDims];
        database = UserDatabase.Companion.getUserDatabase(builder.context);
        userDao = database.getUserDao();
    }

    /**
     * 유저 추가
     *
     * @param name     유저 이름 입력
     * @param features 유저 얼굴 특징점
     */
    public void insert(String name, ArrayList<float[]> features) {
        // userDao.clear();
        userDao.deleteByName(name);
        userDao.insert(new UserEntity(name, features, featureDims));
    }

    public void delete(String name) {
        userDao.deleteByName(name);
    }

    /**
     * 유저 유무 검사
     *
     * @return 유저 유무 검사 결과
     */
    public boolean isValidUser(String name) {
        String user = userDao.isValidUser(name);
        return user != null && user.equals(name);
    }

    /**
     * 유저 Feature Hash 값을 반환함
     *
     * @param name 등록된 유저 이름
     * @return
     */
    public String userFeatureHash(String name) {
        UserEntity user = userDao.findUser(name);
        if (user == null) {
            return null;
        } else {
            return hashFromFeature(user.getFeatures());
        }
    }

    /**
     * Feature값을 이용하여 Hash데이터 생성
     *
     * @param features
     * @return
     */
    public static String hashFromFeature(byte[] features) {
        if (features == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(features);
            StringBuilder sb = new StringBuilder();
            for (byte digit : digest) {
                sb.append(String.format("%02X", digit));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] sha512(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(str.getBytes());
            return md.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return str.getBytes();
        }
    }

    public static String sha512String(String str) {
        byte[] byteData = sha512(str);
        StringBuilder sb = new StringBuilder();
        for (byte byteDatum : byteData) {
            sb.append(Integer.toString((byteDatum & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public int getInputSize() {
        return inputSize;
    }

    public void enableStatLogging(boolean enable) {
        statLogging = enable;
    }

    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    public void writeToFile(byte[] data, String fileName) throws IOException {
        File file = new File(path, fileName);
        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();
    }

    public static void normalize(float[] arr) {
        double norm = 0;
        for (int i = 0; i < arr.length; i++)
            norm += arr[i] * arr[i];
        norm = Math.sqrt(norm);
        for (int i = 0; i < arr.length; i++)
            arr[i] /= norm;
    }

    /**
     * 주어진 얼굴 검출 결과로부터 특징 벡터를 추출한다.
     *
     * @param face 얼굴 검출 결과 객체.
     *             정규화된 얼굴의 해상도는 {@link #inputSize} x {@link #inputSize}여야 한다.
     * @return 특징 벡터.
     * 얼굴 검출에 실패하는 경우, 크기가 0인 배열을 반환한다.
     */
    public synchronized float[] getFeature(final Face face) {
        float[] floatValues = face.getFloatArray();

        if (floatValues != null) {
            Trace.beginSection("recognizeImage");

            // Copy the input data into TensorFlow.
            Trace.beginSection("fillNodeFloat");
            inferenceInterface.feed(inputName, floatValues, 1, inputSize, inputSize, 3);
            Trace.endSection();

            // Run the inference call.
            Trace.beginSection("runInference");
            inferenceInterface.run(outputNames, statLogging);
            Trace.endSection();

            // Copy the output Tensor back into the output array.
            Trace.beginSection("readNodeFloat");
            inferenceInterface.fetch(outputName, outputs);
            Trace.endSection();

            Trace.endSection(); // "recognizeImage"

            if (statLogging) {
                final Vector<String> lines = new Vector<String>();
                final String statString = getStatString();
                final String[] statLines = statString.split("\n");
                for (final String line : statLines) {
                    lines.add(line);
                }
                lines.add("");
                Log.d(TAG, lines.toString());
            }
            // normalize
            normalize(outputs);
            return outputs;
        }
        return null;
    }

    public synchronized List<float[]> getFeatures(final List<Face> faces) {
        if (faces.isEmpty())
            return null;

        final float[] floatValues = new float[faces.get(0).getFloatArray().length * faces.size()];
        int offset = 0;
        for (Face face : faces) {
            final float[] array = face.getFloatArray();
            System.arraycopy(array, 0, floatValues, offset, array.length);
            offset += array.length;
        }

        final float[] features = new float[featureDims * faces.size()];

        if (floatValues.length > 0) {
            Trace.beginSection("recognizeImage");

            // Copy the input data into TensorFlow.
            Trace.beginSection("fillNodeFloat");
            inferenceInterface.feed(inputName, floatValues, faces.size(), inputSize, inputSize, 3);
            Trace.endSection();

            // Run the inference call.
            Trace.beginSection("runInference");
            inferenceInterface.run(outputNames, statLogging);
            Trace.endSection();

            // Copy the output Tensor back into the output array.
            Trace.beginSection("readNodeFloat");
            inferenceInterface.fetch(outputName, features);
            Trace.endSection();

            Trace.endSection(); // "recognizeImage"

            if (statLogging) {
                final String statString = getStatString();
                final String[] statLines = statString.split("\n");
                final Vector<String> lines = new Vector<String>(Arrays.asList(statLines));
                lines.add("");
                Log.d(TAG, lines.toString());
            }

            // split into array
            ArrayList<float[]> outputs = new ArrayList<>();
            offset = 0;
            for (int i = 0; i < faces.size(); i++) {
                final float[] feature = new float[featureDims];
                System.arraycopy(features, offset, feature, 0, feature.length);
                // normalize
                normalize(feature);
                outputs.add(feature.clone());
                offset += feature.length;
            }
            return outputs;
        }
        return null;
    }

    private Pair<String, Float> recognize_(final float[] feature) {
        if (feature != null && feature.length == featureDims) {
            double max_similarity = 0.;
            int id = -1;
            String name = "Unknown";
            for (UserEntity user : userDao.users()) {
                double similarity = user.similarity(feature);
                if (similarity > max_similarity) {
                    max_similarity = similarity;
                    id = (int) user.getId();
                    name = user.getName();
                }
            }
            float confidence = (float) max_similarity;
            Log.i(TAG, name + ": " + confidence);
            return new Pair<>(name, confidence);
        }

        return null;
    }


    /**
     * 주어진 얼굴 검출 결과를 인식한다.
     *
     * @param face 얼굴 검출 결과 객체.
     *             정규화된 얼굴의 해상도는 {@link #inputSize} x {@link #inputSize}여야 한다.
     * @return 얼굴 인식 결과. [index] [confidence] [name]
     */
    public Pair<String, Float> recognize(final Face face) {
        final float[] feature = getFeature(face);
        return recognize_(feature);
    }

    public List<kotlin.Pair<String, Float>> recognize(final List<Face> faces) {
        if (faces == null || faces.isEmpty())
            return null;

        List<float[]> features = getFeatures(faces);
        //
        ArrayList<Pair<String, Float>> results = new ArrayList<>();
        // Feature 데이터 전체
        for (final float[] feature : features) {
            if (feature != null && feature.length == featureDims) {
                for (UserEntity entity : userDao.users()) {
                    results.add(new Pair<>(entity.getName(), entity.similarity(feature)));
                }
            }
            // results.add(recognize_(feature));
        }
        return results;
    }

    protected void finalize() throws Throwable {
        inferenceInterface.close();
    }

    public static class Builder {
        /**
         * 정규화된 얼굴 영상의 해상도.
         */
        public static final int INPUT_SIZE = 112;
        private static final String DB_NAME = "db:lpin-android-sdk-face-recognizer";
        private static final String MODEL_FILE = "file:///android_asset/model.optimized.mobile.pb";
        private static final String INPUT_NAME = "data";
        private static final String OUTPUT_NAME = "fc1/add_1";
        private Context context;
        private String name;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder(Context context, String name) {
            this.context = context;
            this.name = name;
        }

        public FaceRecognizer build() {
            String dbName;
            if (name == null)
                dbName = DB_NAME;
            else
                dbName = this.name;
            return new FaceRecognizer(this, dbName);
        }
    }
}