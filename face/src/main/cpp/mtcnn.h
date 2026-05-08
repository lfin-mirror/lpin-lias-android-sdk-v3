// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#ifndef __MTCNN_H__
#define __MTCNN_H__

#define ARMA_DONT_USE_WRAPPER
#define ARMA_NO_DEBUG
#define ARMA_DONT_USE_LAPACK
#define ARMA_DONT_USE_BLAS
#define ARMA_DONT_USE_ARPACK
#define ARMA_DONT_USE_SUPERLU

#include <armadillo>

#include "dlib_raw_rgb_image.h"

#ifdef USE_OPENCV
#include <opencv2/opencv.hpp>
#endif

#ifndef USE_OPENCV

typedef dlib::raw_rgb_image Image;

#else

typedef cv::Mat Image;

#endif

//namespace mtcnn {

namespace pnet {
    static std::string input = "pnet/input:0";
    static std::vector<std::string> outputs = { "pnet/conv4-2/BiasAdd:0", "pnet/prob1:0" };
};

namespace rnet {
    static std::string input = "rnet/input:0";
    static std::vector<std::string> outputs = { "rnet/conv5-2/conv5-2:0", "rnet/prob1:0" };
};

namespace onet {
    static std::string input = "onet/input:0";
    static std::vector<std::string> outputs = { "onet/conv6-2/conv6-2:0", "onet/conv6-3/conv6-3:0", "onet/prob1:0" };
};

//}

/**
 * 입력 영상에서 multi-scale 얼굴 검출 pyramid 생성을 위한 scale 값을 계산한다.
 *
 * @param w 입력 영상의 가로 해상도
 * @param h 입력 영상의 세로 해상도
 * @param minsize   얼굴의 최소 크기
 * @param factor    피라미드 생성을 위한 scale factor
 * @return  최소 얼굴 크기까지의 피라미드 생성을 위한 모든 scale 값
 */
std::vector<double> create_scale_pyramid(const int w, const int h, int minsize, double factor);

/**
 * 입력 영상을 주어진 {@code width} x {@code height}로 resampling(resize)하고, MTCNN의 입력 형식으로 정규화 한다.
 *
 * @param in 입력 영상
 * @param width resampling 가로 해상도
 * @param height resampling 세로 해상도
 * @param buffer resampling된 영상을 저장하기 위한 메모리
 */
void imresample(const Image& in, int width, int height, float *buffer);

/**
 * Bounding box regression과 probability로부터 얼굴의 bounding box의 좌표를 계산한다
 *
 * @param imap Face probability
 * @param reg   Bounding box regression
 * @param scale 원본 입력 영상 대비 scale
 * @param t Face probability이 임계값(threshold)
 * @return  얼굴의 bounding box 좌표
 */
arma::fmat generate_boundingbox(const arma::fmat &imap, const arma::fcube &reg, double scale, double t);

/**
 * Non-Maximum Suppression을 수행한다
 *
 * @param boxes Bounding box
 * @param threshold  임계값(threshold)
 * @param method 기본값은 Intersection over Union(IoU), "min"인 경우, intersecion / min of two boxes
 * @return  Bounding box의 index
 */
arma::uvec nms(const arma::fmat &boxes, double threshold, std::string method);

/**
 * 주어진 {@code boxes}가 정사각형이 되도록 한다
 */
void rerec(arma::fmat &boxes);

/**
 * Bounding box가 영상 내부를 벗어날 경우, padding을 위한 좌표를 계산한다
 */
void pad(const arma::fmat &total_boxes, int w, int h,
	arma::ivec &x, arma::ivec &y,
	arma::ivec &dx, arma::ivec &dy,
	arma::ivec &ex, arma::ivec &ey,
	arma::ivec &edx, arma::ivec &edy,
	arma::ivec &tmpw, arma::ivec &tmph);

/**
 * 주어진 bounding box {@code boxes}를 {@code reg}를 이용하여 calibration한다
 */
void bbreg(arma::fmat &boxes, arma::fmat reg);

/**
 * @brief      입력 영상에서 주어진 {@code total_boxes}의 영상을 {@code size} x {@code size}로 샘플링하고 정규화한다
 *
 * @param[in]  img          The image
 * @param[in]  total_boxes  The total boxes
 * @param[in]  size         The size
 *
 * @return     샘플링/정규화 된 영상의 벡터
 */
std::vector<float> prepare_inputs(const Image &img, const arma::fmat &total_boxes, const int size);

#endif
