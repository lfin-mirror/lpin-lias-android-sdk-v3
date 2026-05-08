/*
	common.cpp

	���� ������ �� �ܺ� �ڵ���� ���� �Լ��鿡 ���� ���ǰ� ���� �ִ� �ҽ� �����Դϴ�.
*/

#include "common_pleaseModifyThisWhenPorting.hpp"
#include "ImageConverter.hpp"
#include "ImageProcessor.hpp"
#include <android/log.h>

#define LOG_TAG "jnilog"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN , LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , LOG_TAG, __VA_ARGS__)

namespace lpin
{
	ImageProcessor module;

	int GetStateCode()
	{
		return module.GetStateCode();
	}

	double Initialize(double wrapper_version)
	{
		return module.Initialize(wrapper_version);
	}

	char *GetPtrOfBuffer()
	{
		return module.GetPtrOfBuffer();
	}

	int SetMarker(cv::Mat img_marker)
	{
		return module.SetMarker(img_marker, true);
	}

	int SetMarker(char *ptr_marker, int img_width, int img_height)
	{
		return module.SetMarker(ptr_marker, img_width, img_height);
	}

	int SetMarker(int img_width, int img_height)
	{
		return module.SetMarker(img_width, img_height);
	}

	int SetMarkerData()
	{
		return module.SetMarkerData();
	}


	int Start_RetriveTargetData()
	{
		return module.Start_RetriveTargetData();
	}


	int End_RetriveTargetData()
	{
		return module.End_RetriveTargetData();
	}


	int ImportTargetData()
	{
		return module.ImportTargetData();
	}


	int ExportTargetData()
	{
		return module.ExportTargetData();
	}


	int Start_ProcessQuery()
	{
		return module.Start_ProcessQuery();
	}


	int End_ProcessQuery()
	{
		return module.End_ProcessQuery();
	}


	double GetConfidenceRate()
	{
	    double result = module.GetConfidenceRate();

        return result;
	}

	int ExportMostAccurateQueryImage()
	{
		return module.ExportMostAccurateQueryImage();
	}

	int Process(cv::Mat img)
	{
		return module.Process(img, true);
	}

	int Process(char *ptr_img, int img_width, int img_height)
	{
		return module.Process(ptr_img, img_width, img_height);
	}

	int Process(int img_width, int img_height)
	{
	    int result = module.Process(img_width, img_height);

		return result;
	}
}