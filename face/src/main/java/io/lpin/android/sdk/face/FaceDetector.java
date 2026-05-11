package io.lpin.android.sdk.face;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import com.google.mlkit.vision.common.InputImage;
import io.lpin.android.sdk.licensing.LiasLicensedFeature;
import io.lpin.android.sdk.licensing.LiasLicenseGate;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.ImageLabel;

public class FaceDetector {
    private static final String libName = "facedetector";
    private static final String TAG = FaceDetector.class.getSimpleName();

    static {
        try {
            System.loadLibrary(libName);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load " + libName + " library");
        }
    }

    private boolean statLogging = false;
    private final TensorFlowInferenceInterface inferenceInterface;
    private long handle;

    // MLKit Face Detection for fallback
    final com.google.mlkit.vision.face.FaceDetector mlkitFaceDetector;
    private final com.google.mlkit.vision.label.ImageLabeler imageLabeler;

    private FaceDetector(Builder builder) {
        AssetManager assetManager = builder.context.getAssets();
        inferenceInterface = new TensorFlowInferenceInterface(assetManager, Builder.MODEL_FILE);
        handle = create();

        // Initialize MLKit Face Detector for fallback
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f)
                .enableTracking()
                .build();

        mlkitFaceDetector = FaceDetection.getClient(options);
        imageLabeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.45f)
                        .build());
    }

    public com.google.mlkit.vision.face.FaceDetector getMlkitFaceDetector() {
        return mlkitFaceDetector;
    }

    public native long create();

    public native void destroy(long handle);

    public native Face[] nativeDetect(long handle, final Bitmap bitmap);

    /**
     * Proposal Network를 inference 한다.
     *
     * @param floatValues 정규화된 입력 영상
     * @param width       정규화된 입력 영상의 가로 해상도
     * @param height      정규화된 입력 영상의 세로 해상도
     * @return bounding box regression 과 face probability
     */
    synchronized Object[] pnet(float[] floatValues, int width, int height) {
        final String inputName = "pnet/input";
        final String[] outputNames = {"pnet/conv4-2/BiasAdd", "pnet/prob1"};

        Trace.beginSection("pnet");

        // Copy the input data into TensorFlow.
        Trace.beginSection("fillNodeFloat");
        inferenceInterface.feed(inputName, floatValues, 1, width, height, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("runInference");
        inferenceInterface.run(outputNames, statLogging);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("readNodeFloat");

        int hs = (height - 3) / 2 - 3;
        int ws = (width - 3) / 2 - 3;
        float[] out0 = new float[hs * ws * 4];
        inferenceInterface.fetch(outputNames[0], out0);

        Trace.endSection();

        float[] out1 = new float[hs * ws * 2];
        Trace.beginSection("readNodeFloat");
        inferenceInterface.fetch(outputNames[1], out1);
        Trace.endSection();

        Trace.endSection(); // "pnet"
        return new Object[]{out0, out1};
    }

    /**
     * Refine Network를 inference 한다
     *
     * @param floatValues 정규화된 입력 영상에서 샘플링한 candidate 영상
     *                    전체 크기는 {@code num_boxes} x 24 x 24 x 3 (RGB).
     * @param num_boxes   candidate의 수.
     * @return Refine 된 bounding box regression 과 face probability의 배열
     */
    synchronized Object[] rnet(float[] floatValues, int num_boxes) {
        final String inputName = "rnet/input";
        final String[] outputNames = {"rnet/conv5-2/conv5-2", "rnet/prob1"};

        Trace.beginSection("rnet");

        // Copy the input data into TensorFlow.
        Trace.beginSection("fillNodeFloat");
        inferenceInterface.feed(inputName, floatValues, num_boxes, 24, 24, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("runInference");
        inferenceInterface.run(outputNames, statLogging);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("readNodeFloat");
        float[] out0 = new float[4 * num_boxes]; // 4 x num_boxes
        float[] out1 = new float[2 * num_boxes]; // 2 x num_boxes
        inferenceInterface.fetch(outputNames[0], out0);
        inferenceInterface.fetch(outputNames[1], out1);
        Trace.endSection();

        Trace.endSection(); // "rnet"
        return new Object[]{out0, out1};
    }

    /**
     * 얼굴 및 landmark를 검출한다.
     *
     * @param floatValues 정규화된 입력 영상에서 샘플링한 candidate 영상
     *                    전체 크기는 {@code num_boxes} x 48 x 48 x 3 (RGB).
     * @param num_boxes   candidate의 수.
     * @return Bounding box regression, landmark location, face probability의 배열
     */
    synchronized Object[] onet(float[] floatValues, int num_boxes) {
        final String inputName = "onet/input";
        final String[] outputNames = {
                "onet/conv6-2/conv6-2",
                "onet/conv6-3/conv6-3",
                "onet/prob1"};

        Trace.beginSection("onet");

        // Copy the input data into TensorFlow.
        Trace.beginSection("fillNodeFloat");
        inferenceInterface.feed(inputName, floatValues, num_boxes, 48, 48, 3);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("runInference");
        inferenceInterface.run(outputNames, statLogging);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("readNodeFloat");
        float[] out0 = new float[4 * num_boxes];
        float[] out1 = new float[10 * num_boxes];
        float[] out2 = new float[2 * num_boxes];
        inferenceInterface.fetch(outputNames[0], out0); // 4 x num_boxes
        inferenceInterface.fetch(outputNames[1], out1); // 10 x num_boxes
        inferenceInterface.fetch(outputNames[2], out2); // 2 x num_boxes
        Trace.endSection();

        Trace.endSection(); // "onet"
        return new Object[]{out0, out1, out2};
    }

    public void enableStatLogging(boolean enable) {
        statLogging = enable;
    }

    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    public List<Face> detect(final Bitmap bitmap, boolean align) {
        // First try with primary MTCNN detector
        List<Face> faces = new ArrayList<>();
        try {
            Face[] array = nativeDetect(handle, bitmap);
            if (array != null) {
                faces.addAll(Arrays.asList(array));
            }
        } catch (Throwable e) {
            Log.e(TAG, "Primary MTCNN detection crashed, attempting MLKit fallback", e);
        }
//        List<Face> faces = detectWithMLKit(bitmap);

        // If primary detection fails, try with MLKit as fallback
        if (faces.isEmpty()) {
            Log.d(TAG, "Primary MTCNN detection failed, attempting MLKit fallback");
            faces = detectWithMLKit(bitmap);
        }

        return faces;
    }

    public List<Face> detect(final Bitmap bitmap) {
        return detect(bitmap, true);
    }

    public boolean isHuman(final Bitmap bitmap) {
        return detectHuman(bitmap).isHuman();
    }

    public int humanFaceCount(final Bitmap bitmap) {
        return detectHuman(bitmap).getFaceCount();
    }

    /**
     * 사람 얼굴 판별 결과를 반환합니다.
     *
     * Face Detection으로 얼굴 후보를 찾고, Image Labeling으로 사람/동물 라벨을 확인합니다.
     * 따라서 단순히 얼굴 패턴이 잡혔다는 이유만으로 사람이라고 판정하지 않습니다.
     */
    public HumanDetectionResult detectHuman(final Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return new HumanDetectionResult(false, 0, Collections.emptyMap(), "invalid_bitmap");
        }

        List<Face> faces = detect(bitmap);
        int faceCount = faces.size();
        if (faceCount == 0) {
            return new HumanDetectionResult(false, 0, Collections.emptyMap(), "no_face_detected");
        }

        Map<String, Float> labels = detectImageLabels(bitmap);
        boolean hasHumanEvidence = hasAnyLabel(labels, HUMAN_LABELS, 0.45f);
        boolean hasAnimalEvidence = hasAnyLabel(labels, ANIMAL_LABELS, 0.50f);

        boolean isHuman = hasHumanEvidence && !hasAnimalEvidence;
        String reason;
        if (isHuman) {
            reason = "human_face_and_label_detected";
        } else if (hasAnimalEvidence && !hasHumanEvidence) {
            reason = "animal_label_detected";
        } else {
            reason = "no_human_label_detected";
        }

        return new HumanDetectionResult(isHuman, faceCount, labels, reason);
    }

    private Map<String, Float> detectImageLabels(Bitmap bitmap) {
        CompletableFuture<Map<String, Float>> future = new CompletableFuture<>();
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        imageLabeler.process(image)
                .addOnSuccessListener(labels -> {
                    Map<String, Float> result = new HashMap<>();
                    for (ImageLabel label : labels) {
                        result.put(label.getText(), label.getConfidence());
                    }
                    future.complete(result);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "MLKit image labeling failed", e);
                    future.complete(Collections.emptyMap());
                });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "MLKit image labeling timed out", e);
            return Collections.emptyMap();
        }
    }

    private static boolean hasAnyLabel(Map<String, Float> labels, Set<String> candidates, float threshold) {
        for (Map.Entry<String, Float> entry : labels.entrySet()) {
            String normalized = entry.getKey().toLowerCase(Locale.US);
            Float confidence = entry.getValue();
            if (confidence != null && confidence >= threshold && candidates.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> HUMAN_LABELS = new HashSet<>(Arrays.asList(
            "person", "people", "human", "man", "woman", "boy", "girl",
            "crowd", "selfie", "portrait", "smile", "face", "facial expression"
    ));

    private static final Set<String> ANIMAL_LABELS = new HashSet<>(Arrays.asList(
            "animal", "dog", "cat", "bird", "horse", "pet", "mammal",
            "wildlife", "fish", "reptile", "zoo", "puppy", "kitten"
    ));

    public static class HumanDetectionResult {
        private final boolean human;
        private final int faceCount;
        private final Map<String, Float> labels;
        private final String reason;

        HumanDetectionResult(boolean human, int faceCount, Map<String, Float> labels, String reason) {
            this.human = human;
            this.faceCount = faceCount;
            this.labels = labels;
            this.reason = reason;
        }

        public boolean isHuman() {
            return human;
        }

        public int getFaceCount() {
            return faceCount;
        }

        public Map<String, Float> getLabels() {
            return labels;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * MLKit을 사용한 대체 얼굴 감지
     *
     * @param bitmap 감지할 이미지
     * @return 감지된 얼굴 리스트
     */
    private List<Face> detectWithMLKit(Bitmap bitmap) {
        List<Face> faces = new ArrayList<>();
        CompletableFuture<List<Face>> future = new CompletableFuture<>();

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        mlkitFaceDetector.process(image)
                .addOnSuccessListener(mlkitFaces -> {
                    Log.d(TAG, "MLKit detected " + mlkitFaces.size() + " faces");

                    for (com.google.mlkit.vision.face.Face mlkitFace : mlkitFaces) {
                        // Convert MLKit Face to custom Face object
                        Face customFace = convertMLKitFaceToCustomFace(mlkitFace, bitmap);
                        if (customFace != null) {
                            faces.add(customFace);
                        }
                    }
                    future.complete(faces);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "MLKit face detection failed", e);
                    future.completeExceptionally(e);
                });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * MLKit Face를 커스텀 Face 객체로 변환
     *
     * @param mlkitFace MLKit에서 감지된 얼굴
     * @param bitmap    원본 이미지
     * @return 변환된 커스텀 Face 객체
     */
    private Face convertMLKitFaceToCustomFace(com.google.mlkit.vision.face.Face mlkitFace, Bitmap bitmap) {
        // Extract face region from bitmap and prepare byte/float arrays
//        extractFaceRegion(customFace, bitmap);

        // 기존 extractFaceRegion 대신 새로운 alignAndExtractFace 호출
        Bitmap alignedFaceBitmap = alignAndExtractFace(bitmap, mlkitFace);
        if (alignedFaceBitmap == null) {
            return null;
        }

        android.graphics.Rect boundingBox = mlkitFace.getBoundingBox();
        Face customFace = new Face(
                boundingBox.left,
                boundingBox.top,
                boundingBox.right,
                boundingBox.bottom
        );

        // 정렬된 비트맵을 byte[]와 float[]로 변환하여 customFace 객체에 저장
        fillFaceArraysFromBitmap(customFace, alignedFaceBitmap);

        // 더 이상 필요 없는 비트맵은 메모리 해제
        alignedFaceBitmap.recycle();

        return customFace;
    }

    // 기존 extractFaceRegion의 변환 로직을 별도 메소드로 분리
    private void fillFaceArraysFromBitmap(Face face, Bitmap faceBitmap) {
        try {
            byte[] byteArray = face.getByteArray();
            float[] floatArray = face.getFloatArray();

            int[] pixels = new int[Face.faceSize * Face.faceSize];
            faceBitmap.getPixels(pixels, 0, Face.faceSize, 0, 0, Face.faceSize, Face.faceSize);

            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                byteArray[i * 3] = (byte) r;
                byteArray[i * 3 + 1] = (byte) g;
                byteArray[i * 3 + 2] = (byte) b;

                // 사용하는 인식 모델이 요구하는 형식에 맞게 정규화해야 합니다.
                // 예: -1 ~ 1 사이라면 -> (value - 127.5f) / 128.0f
                floatArray[i * 3] = r / 255.0f;
                floatArray[i * 3 + 1] = g / 255.0f;
                floatArray[i * 3 + 2] = b / 255.0f;
            }

            face.setByteArray(byteArray);
            face.setFloatArray(floatArray);

        } catch (Exception e) {
            Log.e(TAG, "Error filling face arrays from bitmap", e);
        }
    }

    /**
     * 얼굴 영역을 추출하여 Face 객체에 byte/float 배열 설정
     *
     * @param face   대상 Face 객체
     * @param bitmap 원본 이미지
     */
    private void extractFaceRegion(Face face, Bitmap bitmap) {
        android.graphics.RectF boundingBox = face.getBoundingBox();

        // Ensure bounding box is within bitmap bounds
        int left = Math.max(0, (int) boundingBox.left);
        int top = Math.max(0, (int) boundingBox.top);
        int right = Math.min(bitmap.getWidth(), (int) boundingBox.right);
        int bottom = Math.min(bitmap.getHeight(), (int) boundingBox.bottom);

        if (left < right && top < bottom) {
            try {
                // Crop face region
                Bitmap faceBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);

                // Resize to standard face size (112x112)
                Bitmap resizedFace = Bitmap.createScaledBitmap(faceBitmap, Face.faceSize, Face.faceSize, true);

                // Convert to byte array
                byte[] byteArray = face.getByteArray();
                float[] floatArray = face.getFloatArray();

                int[] pixels = new int[Face.faceSize * Face.faceSize];
                resizedFace.getPixels(pixels, 0, Face.faceSize, 0, 0, Face.faceSize, Face.faceSize);

                for (int i = 0; i < pixels.length; i++) {
                    int pixel = pixels[i];
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;

                    // RGB 순서로 저장
                    byteArray[i * 3] = (byte) r;
                    byteArray[i * 3 + 1] = (byte) g;
                    byteArray[i * 3 + 2] = (byte) b;

                    // Float 배열은 0-1 범위로 정규화
                    floatArray[i * 3] = r / 255.0f;
                    floatArray[i * 3 + 1] = g / 255.0f;
                    floatArray[i * 3 + 2] = b / 255.0f;
                }

                face.setByteArray(byteArray);
                face.setFloatArray(floatArray);

                // Clean up
                faceBitmap.recycle();
                resizedFace.recycle();

            } catch (Exception e) {
                Log.e(TAG, "Error extracting face region", e);
            }
        }
    }

    /**
     * 두 눈의 위치를 기반으로 얼굴의 기울기 각도를 계산합니다.
     *
     * @param leftEye  왼쪽 눈 랜드마크
     * @param rightEye 오른쪽 눈 랜드마크
     * @return 회전시켜야 할 각도 (degrees)
     */
    private double getAngle(FaceLandmark leftEye, FaceLandmark rightEye) {
        if (leftEye == null || rightEye == null) {
            return 0.0; // 랜드마크 정보가 없으면 회전 없음
        }
        PointF leftEyePos = leftEye.getPosition();
        PointF rightEyePos = rightEye.getPosition();

        // 두 눈의 x, y 좌표 차이 계산
        float dx = rightEyePos.x - leftEyePos.x;
        float dy = rightEyePos.y - leftEyePos.y;

        // atan2를 사용하여 라디안 각도 계산 후, 디그리(degree)로 변환
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * 얼굴을 정렬(회전)하고, 표준화된 크기로 잘라내는 핵심 메소드
     *
     * @param originalBitmap 원본 전체 이미지
     * @param face           ML Kit에서 검출된 얼굴 객체
     * @return 정렬되고 잘라진 얼굴 비트맵 (예: 112x112 크기)
     */
    private Bitmap alignAndExtractFace(Bitmap originalBitmap, com.google.mlkit.vision.face.Face face) {
        // 1. 필요한 랜드마크 가져오기
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

        if (leftEye == null || rightEye == null) {
            // 랜드마크 없이는 정렬 불가
            Log.w(TAG, "Landmark not found..");
            return null;
        }

        // 2. 얼굴 기울기 각도 계산
        double angle = getAngle(leftEye, rightEye);

        // 3. 얼굴 중심점 계산 (회전의 기준점)
        PointF faceCenter = new PointF(
                (leftEye.getPosition().x + rightEye.getPosition().x) / 2f,
                (leftEye.getPosition().y + rightEye.getPosition().y) / 2f
        );

        // 4. 이미지 회전을 위한 Matrix 생성
        Matrix matrix = new Matrix();
        matrix.postRotate((float) angle, faceCenter.x, faceCenter.y);

        // 5. Matrix를 적용하여 원본 이미지를 회전시킨 새로운 비트맵 생성
        Bitmap rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true
        );

        // 6. 회전된 이미지에서 얼굴의 새로운 중심점 찾기
        // (회전 변환된 Matrix를 얼굴 중심점에 적용)
        float[] newCenterPoints = {faceCenter.x, faceCenter.y};
        matrix.mapPoints(newCenterPoints);
        float newCenterX = newCenterPoints[0];
        float newCenterY = newCenterPoints[1];

        // 7. 새로운 중심점을 기준으로 표준화된 얼굴 영역 잘라내기
        int cropSize = Face.faceSize; // 예: 112
        int cropX = (int) (newCenterX - (cropSize / 2f));
        int cropY = (int) (newCenterY - (cropSize / 2f));

        // (중요) 잘라낼 영역이 비트맵 경계를 벗어나지 않도록 방어 코드
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        int finalWidth = Math.min(cropSize, rotatedBitmap.getWidth() - cropX);
        int finalHeight = Math.min(cropSize, rotatedBitmap.getHeight() - cropY);

        Bitmap finalFace;
        // 최종 크기 재계산
        int safeWidth = Math.min(finalWidth, rotatedBitmap.getWidth() - cropX);
        int safeHeight = Math.min(finalHeight, rotatedBitmap.getHeight() - cropY);

        // 최소 크기 보장
        safeWidth = Math.max(1, safeWidth);
        safeHeight = Math.max(1, safeHeight);

        try {
            finalFace = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, safeWidth, safeHeight);
            // 표준 크기로 리사이즈
            if (finalFace.getWidth() != cropSize || finalFace.getHeight() != cropSize) {
                finalFace = Bitmap.createScaledBitmap(finalFace, cropSize, cropSize, true);
            }
            return finalFace;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Bitmap crop failed, returning null", e);
            return null;
        }
    }

    protected void finalize() throws Throwable {
        destroy(handle);
        inferenceInterface.close();
        // MLKit detector will be automatically cleaned up by GC
        mlkitFaceDetector.close();
    }

    public static class Builder {
        private static final String MODEL_FILE = "file:///android_asset/mtcnn.pb";
        private Context context;

        public Builder(Context context) {
            this.context = context;
        }

        public FaceDetector build() {
            LiasLicenseGate.requireFeature(context.getApplicationContext(), LiasLicensedFeature.FACE);
            return new FaceDetector(this);
        }
    }
}
