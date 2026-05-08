// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#ifndef __FACEDETECTOR_IMPL_TF_ANDROID_H__
#define __FACEDETECTOR_IMPL_TF_ANDROID_H__

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include "mtcnn.h"

#define TAG "DETECTOR"

#define MX2TF

// for compatibility
void CHECK_EQ(int x, int y, const char *func) {
    if (x != y)
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s failed! ret = %d", func, x);
}

typedef struct {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
    uint8_t alpha;
} RGBA;

void adjust(arma::vec4 &bbox, arma::mat &points, int width, int height, double &angle,
            arma::vec2 &center) {
    auto left_eye = points.row(0);
    auto right_eye = points.row(1);
    auto nose = points.row(2);

    auto diff = (right_eye - left_eye).eval();
    angle = std::atan2(diff(1), diff(0));
    arma::vec2 ec = (left_eye + right_eye) / 2.;

    arma::vec2 v = {-diff(1), diff(0)};
    v = arma::normalise(v);

    center = v * arma::dot(v, (nose - ec)) * 2 / 3. + ec;

    // update bounding box
    arma::vec2 offset = center - (bbox.rows(0, 1) + bbox.rows(2, 3)) / 2.;
    bbox.rows(0, 1) += offset;
    bbox.rows(2, 3) += offset;

    // make square shape
    auto size = ((bbox.rows(2, 3) - bbox.rows(0, 1)) / 2.).eval();
    auto margin = size(1) - size(0);
    bbox(0) -= margin;
    bbox(2) += margin;

    bbox(0) = std::max(static_cast<int>(bbox(0)), 0);
    bbox(1) = std::max(static_cast<int>(bbox(1)), 0);
    bbox(2) = std::min(static_cast<int>(bbox(2)), width);
    bbox(3) = std::min(static_cast<int>(bbox(3)), height);
}

/**
* @brief      주어진 입력 영상과 검출된 얼굴의 위치, landmark를 이용하여 정규화된 얼굴 영상을 얻는다.
*
* @param[in]  img      The image
* @param[in]  bbox     The bounding box
* @param[in]  points   The landmark points
* @param[out] buffer   정규화된 얼굴 영상
*
*/
void align(const Image &img, arma::vec4 &bbox, arma::mat &points, uint8_t *buffer, int face_size) {

#ifndef USE_OPENCV

#ifndef MX2TF
    std::vector<dlib::dpoint> src, dst = {
        { 40.3928 + 16,  68.9284 },
        { 87.3757 + 16,  68.6685 },
        { 64.0336 + 16,  95.6488 },
        { 44.7324 + 16, 123.1540 },
        { 83.6399 + 16, 122.9388 } };
#else
    std::vector<dlib::dpoint> src, dst = {
            {30.2946 + 8.0, 51.6963},
            {65.5318 + 8.0, 51.5014},
            {48.0252 + 8.0, 71.7366},
            {33.5493 + 8.0, 92.3655},
            {62.7299 + 8.0, 92.2041}};
#endif

    for (int i = 0; i < points.n_rows; i++)
        src.push_back(dlib::dpoint(points.at(i, 0), points.at(i, 1)));

    dlib::raw_rgb_image aligned(reinterpret_cast<char *>(buffer), face_size, face_size);
    auto Hinv = dlib::find_similarity_transform(dst, src);
    dlib::transform_image(img, aligned, dlib::interpolate_bilinear(), Hinv);
#else
    double angle;
    arma::vec2 center;
    adjust(bbox, points, img.cols, img.rows, angle, center);

    auto deg = angle * 180. / CV_PI;
    auto H = cv::getRotationMatrix2D(cv::Point2f(static_cast<float>(center(0)),
        static_cast<float>(center(1))), deg, 1.);

    cv::Mat rotated, resized(face_size, face_size, CV_8UC3, buffer);
    cv::warpAffine(img, rotated, H, img.size());
    cv::resize(rotated(cv::Rect(cv::Point(static_cast<int>(bbox(0)),
        static_cast<int>(bbox(1))),
        cv::Point(static_cast<int>(bbox(2)),
            static_cast<int>(bbox(3))))),
        resized, cv::Size(face_size, face_size), 0., 0., CV_INTER_AREA);
#endif
}

/**
 * @brief      Multitask Cascaded Convolutional Networks(MTCNN)을 이용하여 얼굴과 landmark를 동시에 검출한다
 *
 * @see <a href="https://kpzhang93.github.io/MTCNN_face_detection_alignment/">K. Zhang, Z. Zhang, Z. Li and Y. Qiao, "Joint Face Detection andAlignment Using Multitask
 * Cascaded Convolutional Networks," in IEEE Signal Processing Letters, vol. 23, no. 10,
 * pp. 1499-1503, Oct. 2016.</a>
 */
class FaceDetectorImplMTCNN {
public:
    FaceDetectorImplMTCNN() :
            minsize(48),
            threshold{0.6, 0.7, 0.7},
            factor(0.709) {
    }

    bool warm_up(JNIEnv *env, jobject thiz) {
        jclass cls = env->GetObjectClass(thiz);
        jmethodID pnet_func = env->GetMethodID(cls, "pnet", "([FII)[Ljava/lang/Object;");
        jfloatArray pnet_inputs = env->NewFloatArray(48 * 48 * 3);
        jobjectArray outputs = (jobjectArray) env->CallObjectMethod(thiz, pnet_func, pnet_inputs,
                                                                    48, 48);

        jmethodID rnet_func = env->GetMethodID(cls, "rnet", "([FI)[Ljava/lang/Object;");
        jfloatArray rnet_inputs = env->NewFloatArray(24 * 24 * 3);
        outputs = (jobjectArray) env->CallObjectMethod(thiz, rnet_func, rnet_inputs, 1);

        jmethodID onet_func = env->GetMethodID(cls, "onet", "([FI)[Ljava/lang/Object;");
        jfloatArray onet_inputs = env->NewFloatArray(48 * 48 * 3);
        outputs = (jobjectArray) env->CallObjectMethod(thiz, onet_func, onet_inputs, 1);

        return true;
    }

    /**
    * @brief      주어진 영상에서 얼굴과 landmark를 동시에 검출한다
    *
    * @param[in]  img          The image
    * @param      boundingBoxes  The bounding boxes
    * @param      landmarks    The landmarks
    * @return     얼굴 검출 성공 여부
    */
    inline bool detect(JNIEnv *env, jobject thiz,
                       const Image &img, arma::fmat &boundingBoxes, arma::fmat &landmarks) {
        // resize
        float scale = 0.5f;
        int width = static_cast<int>(img.nc() * scale),
                height = static_cast<int>(img.nr() * scale);
        std::vector<uint8_t> raw(width * height * 3);
        Image resized(reinterpret_cast<char *>(raw.data()), width, height);
        dlib::resize_image(img, resized);

        bool found = pnet(env, thiz, resized, boundingBoxes) &&
                     rnet(env, thiz, resized, boundingBoxes) &&
                     onet(env, thiz, resized, boundingBoxes, landmarks);
        boundingBoxes /= scale;
        landmarks /= scale;
        return found;
    }

protected:
    bool pnet(JNIEnv *env, jobject thiz,
              const Image &img, arma::fmat &total_boxes) {

        jclass cls = env->GetObjectClass(thiz);
        jmethodID pnet_func = env->GetMethodID(cls, "pnet", "([FII)[Ljava/lang/Object;");

#ifndef USE_OPENCV
        const int width = static_cast<int>(img.nc());
        const int height = static_cast<int>(img.nr());
#else
        const int width = img.cols;
        const int height = img.rows;
#endif

        auto scales = create_scale_pyramid(width, height, minsize, factor);

        std::for_each(scales.begin(), scales.end(), [&](const double scale) {
            const auto hs = static_cast<int>(std::ceil(height * scale));
            const auto ws = static_cast<int>(std::ceil(width * scale));

            std::vector<float> im_data(hs * ws * 3);
            imresample(img, hs, ws, im_data.data()); // transpose and normalize

            jfloatArray tempimg = env->NewFloatArray(im_data.size());
            env->SetFloatArrayRegion(tempimg, 0, im_data.size(), im_data.data());

            // infer pnet
            jobjectArray outputs = (jobjectArray) env->CallObjectMethod(thiz, pnet_func, tempimg,
                                                                        ws,
                                                                        hs);

            jfloatArray out0_ = (jfloatArray) env->GetObjectArrayElement(outputs, 0);
            jfloatArray out1_ = (jfloatArray) env->GetObjectArrayElement(outputs, 1);
            float *out0_ptr = env->GetFloatArrayElements(out0_, nullptr);
            float *out1_ptr = env->GetFloatArrayElements(out1_, nullptr);

            auto hs_ = (hs - 3) / 2 - 3;
            auto ws_ = (ws - 3) / 2 - 3;
            arma::fcube reg(out0_ptr, 4, hs_, ws_);
            arma::fcube tmp(out1_ptr, 2, hs_, ws_);

            auto imap = tmp(arma::span(1), arma::span::all, arma::span::all);
            auto boxes = generate_boundingbox(imap, reg, scale, threshold[0]);

            env->ReleaseFloatArrayElements(out0_, out0_ptr, JNI_ABORT);
            env->ReleaseFloatArrayElements(out1_, out1_ptr, JNI_ABORT);

            env->DeleteLocalRef(out0_);
            env->DeleteLocalRef(out1_);
            env->DeleteLocalRef(outputs);

            // intra-scale nms
            auto pick = nms(boxes, 0.5, "union");
            if (!boxes.is_empty() && !pick.is_empty()) {
                arma::fvec temp = arma::join_rows(total_boxes, boxes.cols(pick));
                total_boxes = temp;
            }
        });

        if (!total_boxes.is_empty()) {
            // inter-scale nms
            auto pick = nms(total_boxes, 0.7, "union");
            arma::fvec temp = total_boxes.cols(pick);
            arma::fvec regw = (temp.row(2) - temp.row(0)).eval();
            arma::fvec regh = (temp.row(3) - temp.row(1)).eval();
            temp.row(0) += temp.row(5) % regw;
            temp.row(1) += temp.row(6) % regh;
            temp.row(2) += temp.row(7) % regw;
            temp.row(3) += temp.row(8) % regh;
            total_boxes = temp.rows(0, 4);
            rerec(total_boxes);
            total_boxes.rows(0, 3) = arma::floor(total_boxes.rows(0, 3));

            __android_log_print(ANDROID_LOG_INFO, TAG, "PNET: %d", total_boxes.n_cols);
        }

        return !total_boxes.is_empty();
    }

    bool rnet(JNIEnv *env, jobject thiz,
              const Image &img, arma::fmat &total_boxes) {

        jclass cls = env->GetObjectClass(thiz);
        jmethodID rnet_func = env->GetMethodID(cls, "rnet", "([FI)[Ljava/lang/Object;");

        const int numbox = static_cast<int>(total_boxes.n_cols);
        std::vector<float> tempimg = prepare_inputs(img, total_boxes, 24);

        jfloatArray inputs = env->NewFloatArray(tempimg.size());
        env->SetFloatArrayRegion(inputs, 0, tempimg.size(), tempimg.data());

        // call rnet
        jobjectArray outputs = (jobjectArray) env->CallObjectMethod(thiz, rnet_func, inputs,
                                                                    numbox);

        jfloatArray out0_ = (jfloatArray) env->GetObjectArrayElement(outputs, 0);
        jfloatArray out1_ = (jfloatArray) env->GetObjectArrayElement(outputs, 1);
        float *out0_ptr = env->GetFloatArrayElements(out0_, nullptr);
        float *out1_ptr = env->GetFloatArrayElements(out1_, nullptr);

        arma::fmat reg(out0_ptr, 4, numbox);
        arma::fmat tmp(out1_ptr, 2, numbox);

        arma::frowvec score = tmp.row(1);
        arma::uvec ipass = arma::find(score > threshold[1]);
        arma::fmat temp = total_boxes.cols(ipass);
        total_boxes = temp.rows(0, 4);
        total_boxes.row(4) = score(ipass);

        arma::fmat mv = reg.cols(ipass);
        if (!total_boxes.is_empty()) {
            auto pick = nms(total_boxes, 0.7, "union");
            temp = total_boxes.cols(pick);
            total_boxes = temp;
            bbreg(total_boxes, mv.cols(pick));
            rerec(total_boxes);
            // LOG(INFO) << "RNET: " << total_boxes.size();
            __android_log_print(ANDROID_LOG_INFO, TAG, "RNET: %d", (int) total_boxes.n_cols);
        }

        env->ReleaseFloatArrayElements(out0_, out0_ptr, JNI_ABORT);
        env->ReleaseFloatArrayElements(out1_, out1_ptr, JNI_ABORT);

        env->DeleteLocalRef(out0_);
        env->DeleteLocalRef(out1_);
        env->DeleteLocalRef(outputs);

        return !total_boxes.is_empty();
    }

    bool onet(JNIEnv *env, jobject thiz,
              const Image &img, arma::fmat &total_boxes, arma::fmat &landmarks) {

        jclass cls = env->GetObjectClass(thiz);
        jmethodID onet_func = env->GetMethodID(cls, "onet", "([FI)[Ljava/lang/Object;");

        total_boxes.rows(0, 3) = arma::floor(total_boxes.rows(0, 3));

        const int numbox = static_cast<int>(total_boxes.n_cols);
        std::vector<float> tempimg = prepare_inputs(img, total_boxes, 48);

        jfloatArray inputs = env->NewFloatArray(tempimg.size());
        env->SetFloatArrayRegion(inputs, 0, tempimg.size(), tempimg.data());

        // call onet
        jobjectArray outputs = (jobjectArray) env->CallObjectMethod(thiz, onet_func, inputs,
                                                                    numbox);

        jfloatArray out0_ = (jfloatArray) env->GetObjectArrayElement(outputs, 0);
        jfloatArray out1_ = (jfloatArray) env->GetObjectArrayElement(outputs, 1);
        jfloatArray out2_ = (jfloatArray) env->GetObjectArrayElement(outputs, 2);
        float *out0_ptr = env->GetFloatArrayElements(out0_, nullptr);
        float *out1_ptr = env->GetFloatArrayElements(out1_, nullptr);
        float *out2_ptr = env->GetFloatArrayElements(out2_, nullptr);

        arma::fmat out0(out0_ptr, 4, numbox);
        arma::fmat out1(out1_ptr, 10, numbox);
        arma::fmat out2(out2_ptr, 2, numbox);

        arma::frowvec score = out2.row(1);

        arma::uvec ipass = arma::find(score > threshold[2]);
        arma::fmat points = out1.cols(ipass);
        arma::fmat temp = total_boxes.cols(ipass);
        total_boxes = temp.rows(0, 4);
        total_boxes.row(4) = score(ipass);

        arma::fmat mv = out0.cols(ipass);

        // VS 2015 bug - auto return type deduction fail
        //auto w = total_boxes.row(2) - total_boxes.row(0) + 1;
        //auto h = total_boxes.row(3) - total_boxes.row(1) + 1;
        arma::fvec w = total_boxes.row(2) - total_boxes.row(0) + 1;
        arma::fvec h = total_boxes.row(3) - total_boxes.row(1) + 1;
        points.rows(0, 4).each_row() %= w;
        points.rows(0, 4).each_row() += total_boxes.row(0) - 1;
        points.rows(5, 9).each_row() %= h;
        points.rows(5, 9).each_row() += total_boxes.row(1) - 1;

        if (!total_boxes.is_empty()) {
            bbreg(total_boxes, mv);
            auto pick = nms(total_boxes, 0.7, "min");

            temp = total_boxes.cols(pick);
            total_boxes = temp.rows(0, 3);

            landmarks = points.cols(pick);
            // LOG(INFO) << "ONET: " << total_boxes.size();
            __android_log_print(ANDROID_LOG_INFO, TAG, "ONET: %d", total_boxes.n_cols);
        }

        env->ReleaseFloatArrayElements(out0_, out0_ptr, JNI_ABORT);
        env->ReleaseFloatArrayElements(out1_, out1_ptr, JNI_ABORT);
        env->ReleaseFloatArrayElements(out2_, out2_ptr, JNI_ABORT);

        env->DeleteLocalRef(out0_);
        env->DeleteLocalRef(out1_);
        env->DeleteLocalRef(out2_);
        env->DeleteLocalRef(outputs);

        return !total_boxes.is_empty();
    }

private:
    int minsize; // minimum size of face (default 20)
    double threshold[3];  // three steps's threshold
    double factor; // scale factor
};

#endif