#pragma once

#include "common_pleaseModifyThisWhenPorting.hpp"

namespace lpin
{
	// Image 포맷을 조율하기 위한 함수들을 정의하는 클래스입니다.
	class ImageConverter
	{
	private:
		~ImageConverter() = delete;

	public:
		// Color space 및 크기 조정
		static cv::Mat Convert(cv::Mat img);

		// Matrix화 + Color space 및 크기 조정
		static cv::Mat Convert(char *ptr, int img_width, int img_height);
	};
}