/*
	ImageProcessor.hpp

	Image 처리 모듈을 의미하는 class lpin::ImageProcessor에 대한 정의가 적혀 있는 헤더 파일입니다.

	비템플릿 멤버 함수들에 대한 정의는 ImageProcessor.cpp에 있습니다.
*/

#pragma once
#define USE_HISTOGRAM_EQUALIZER

#include "common_pleaseModifyThisWhenPorting.hpp"
#include "ImageCache.hpp"

#include <queue>
#include <android/log.h>

#define LOG_TAG "jnilog"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , LOG_TAG, __VA_ARGS__)


namespace lpin
{
	class ImageProcessor
	{
	private:
		struct Comparator_Base
		{
			virtual int Process(cv::Mat img, bool isTargetImage) = 0;
			virtual cv::Mat ExportImg(int code) = 0;
			virtual double GetConfidenceRate() = 0;
		};
	public:
		template <class Detector_t, cv::DescriptorMatcher::MatcherType matcherType>
		struct Comparator_BuiltInDetector : public Comparator_Base
		{
			cv::Ptr<Detector_t> detector;
			cv::Ptr<cv::DescriptorMatcher> matcher;

			std::vector<cv::KeyPoint> keypoints_target, keypoints_toQuery;
			cv::Mat descriptors_target, descriptors_toQuery;

			cv::Mat img_target, img_toQuery, img_toQuery_aligned, img_target_masked;

			cv::Mat mask_target, mask_toQuery;

			int rows_marker = 0, cols_marker = 0;

			cv::Mat translate_matrix;

#ifdef USE_HISTOGRAM_EQUALIZER
            cv::Ptr<cv::CLAHE> clahe;
			cv::Mat img_target_equalized, img_toQuery_equalized;
#endif

            Comparator_BuiltInDetector() :
                    detector(Detector_t::create()),
                    matcher(cv::DescriptorMatcher::create(matcherType)),
#ifdef USE_HISTOGRAM_EQUALIZER
            clahe(cv::createCLAHE())
#endif
            {
                translate_matrix = cv::Mat::zeros(3, 3, CV_64F);
                translate_matrix.at<double>(0, 0) = 1;
                translate_matrix.at<double>(1, 1) = 1;
                translate_matrix.at<double>(2, 2) = 1;
            }

			int Process(cv::Mat img, bool isTargetImage) override
			{
				if ( isTargetImage )
				{
					detector->detectAndCompute(img, cv::noArray(), keypoints_target, descriptors_target);
					matcher->add(descriptors_target);

					img_target = img;

					return 1;
				}
				else
				{
					detector->detectAndCompute(img, cv::noArray(), keypoints_toQuery, descriptors_toQuery);
					matcher->add(descriptors_toQuery);

					img_toQuery = img;

					std::vector<std::vector<cv::DMatch>> knn_matches;
					matcher->knnMatch(descriptors_target, descriptors_toQuery, knn_matches, 2);

					std::vector<cv::DMatch> chosen_matches;
					for ( auto &match : knn_matches )
						if ( match[0].distance < Constants::threshold * match[1].distance )
							chosen_matches.push_back(match[0]);

					if ( chosen_matches.size() <= 4 )
						return 0;

					std::vector<cv::Point2f> mappedpoints_target;
					std::vector<cv::Point2f> mappedpoints_toQuery;
					for ( auto &match : chosen_matches )
					{
						mappedpoints_target.push_back(keypoints_target[match.queryIdx].pt);
						mappedpoints_toQuery.push_back(keypoints_toQuery[match.trainIdx].pt);
					}

					cv::Mat H = cv::findHomography(mappedpoints_target, mappedpoints_toQuery, cv::RANSAC, 1.0, cv::noArray());

					if ( H.data == 0 )
						return 0;

					cv::Mat M = H.inv();
					M /= M.at<double>(2, 2);

					cv::warpPerspective(img, img_toQuery_aligned, M, img.size());

					mask_toQuery = cv::Mat::ones(img.size(), CV_8U);

					img_target_masked = img_target.clone();

					for ( int row = 0; row < img_toQuery_aligned.rows; row++ )
					{
						for ( int column = 0; column < img_toQuery_aligned.cols; column++ )
						{
							if ( img_toQuery_aligned.at<uint8_t>(row, column) == 0 )
							{
								img_target_masked.at<uint8_t>(row, column) = 0;

								mask_toQuery.at<uint8_t>(row, column) = 0;
							}
						}
					}

					return 1;
				}
			}

			cv::Mat ExportImg(int code) override
			{
				switch ( code )
				{
				case 1:
					return img_target;
				case 2:
					return img_toQuery;
				case 3:
					return img_target_masked;
				case 4:
					return img_toQuery_aligned;
				default:
					return {};
				}
			}

			double GetConfidenceRate() override
			{
				if ( img_toQuery_aligned.empty() )
				{
					return -1.0;
				}

				cv::Mat result;

#ifdef USE_HISTOGRAM_EQUALIZER
                clahe->apply(img_target, img_target_equalized);
                clahe->apply(img_toQuery_aligned, img_toQuery_equalized);

                cv::matchTemplate(img_target_equalized, img_toQuery_equalized, result, cv::TemplateMatchModes::TM_CCORR_NORMED, mask_toQuery);
#else
                cv::matchTemplate(img_target, img_toQuery_aligned, result, cv::TemplateMatchModes::TM_CCORR_NORMED, mask_toQuery);
#endif
				return result.at<float>(0, 0);
			}
		};

		StateCode stateCode = ReadyToRetriveTargetData;
		double confidence_rate = -1.0;

	public:
		std::vector<Comparator_Base *> comparators;
	private:

		char *const buffer;

		ImageProcessor(const ImageProcessor &) = delete;
		ImageProcessor(ImageProcessor &&) = delete;
		ImageProcessor &operator =(const ImageProcessor &) = delete;
		ImageProcessor &operator =(ImageProcessor &&) = delete;
	public:
		ImageCache cache;

		ImageProcessor();
		~ImageProcessor();

		int GetStateCode();
		double Initialize(double wrapper_version);
		char *GetPtrOfBuffer();
		int SetMarker(cv::Mat img_marker, bool needConvert);
		int SetMarker(char *ptr_marker, int img_width, int img_height);
		int SetMarker(int img_width, int img_height);
		int SetMarkerData();
		int Start_RetriveTargetData();
		int End_RetriveTargetData();
		int ImportTargetData();
		int ExportTargetData();
		int Start_ProcessQuery();
		int End_ProcessQuery();
		double GetConfidenceRate();
		int ExportMostAccurateQueryImage();
		int Process(cv::Mat img, bool needConvert);
		int Process(char *ptr_img, int width, int height);
		int Process(int width, int height);
	};
}