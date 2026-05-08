package io.lpin.android.sdk.face.v1.legacy.view.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Pair;
import android.util.TypedValue;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import io.lpin.android.sdk.face.Face;
import io.lpin.android.sdk.face.v1.legacy.view.utils.BorderedText;
import io.lpin.android.sdk.face.v1.legacy.view.utils.ImageUtils;
import io.lpin.android.sdk.face.v1.legacy.view.utils.Logger;

/**
 * 더미 트래커 클래스
 * 실제로는 트래킹을 하지 않고, 단순 출력만 수행한다.
 */
public class Tracker {
    private final Logger logger = new Logger();

    private static final float TEXT_SIZE_DIP = 18;

    // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
    // the lower scored box (new or old) will be removed.
    private static final float MAX_OVERLAP = 0.2f;

    private static final float MIN_SIZE = 16.0f;

    // Allow replacement of the tracked box with new results if
    // correlation has dropped below this level.
    private static final float MARGINAL_CORRELATION = 0.75f;

    // Consider object to be lost if correlation falls below this threshold.
    private static final float MIN_CORRELATION = 0.3f;

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };

    private final Queue<Integer> availableColors = new LinkedList<Integer>();

    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

    private final List<Face> trackedObjects = new LinkedList<>();

    private final Paint boxPaint = new Paint();

    private final float textSizePx;
    private final BorderedText borderedText;

    private Matrix frameToCanvasMatrix;

    private int frameWidth;
    private int frameHeight;

    private int sensorOrientation;
    private Context context;

    public Tracker(final Context context) {
        this.context = context;
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(12.0f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);
        boxPaint.setStrokeJoin(Paint.Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);

        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Paint.Style.STROKE);

        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;
            canvas.drawRect(rect, boxPaint);
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
        }
    }

    public synchronized void updateResults(final List<Face> results) {
        trackedObjects.clear();
        trackedObjects.addAll(results);
    }

    public synchronized void draw(final Canvas canvas) {
        final float multiplier =
                Math.min(canvas.getWidth() / (float) frameHeight, canvas.getHeight() / (float) frameWidth);
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * frameHeight),
                        (int) (multiplier * frameWidth),
                        sensorOrientation,
                        false);

        canvas.scale(-1, 1, canvas.getWidth() * .5f, canvas.getHeight() * .5f);

        for (final Face face : trackedObjects) {
            final RectF trackedPos = face.getBoundingBox();
            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(Color.GREEN);
            final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
        }
    }

    private boolean initialized = false;

    public synchronized void onFrame(
            final int w,
            final int h,
            final int rowStride,
            final int sensorOrienation,
            final byte[] frame,
            final long timestamp) {
        frameWidth = w;
        frameHeight = h;
        this.sensorOrientation = sensorOrienation;
        initialized = true;
    }
}