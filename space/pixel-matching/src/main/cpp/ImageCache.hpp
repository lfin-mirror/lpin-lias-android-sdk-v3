#pragma once

#include "common_pleaseModifyThisWhenPorting.hpp"
#include <map>

namespace lpin
{
	// 비트맵 데이터를 관리하며 액세스 기능을 제공하는 클래스입니다.
	class ImageCache
	{
	private:
		ImageCache(const ImageCache &) = delete;
		ImageCache(ImageCache &&) = delete;
		ImageCache &operator =(const ImageCache &) = delete;
		ImageCache &operator =(ImageCache &&) = delete;

		int state;

	public:
		ImageCache(char *buffer);
		~ImageCache() = default;

		// 비트맵 입/출력을 위해 사용되는 버퍼 영역입니다.
		char *const buffer;

		// 입력받은 마커 이미지입니다.
		cv::Mat img_marker;

		// Process() 호출 순번에 의해 관리되는, 입력받은 등록용 이미지들입니다.
		std::map<int, cv::Mat> imgs_target;
		int count_imgs_target;

		// Process() 호출 순번에 의해 관리되는, 입력받은 인증용 이미지들입니다.
		std::map<int, cv::Mat> imgs_query;
		int count_imgs_query;

		int GetState();

		void Clear();
	};
}