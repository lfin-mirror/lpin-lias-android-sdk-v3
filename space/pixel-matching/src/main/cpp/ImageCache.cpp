
#include "ImageCache.hpp"

namespace lpin
{
	ImageCache::ImageCache(char *buffer) :
		buffer(buffer),
		state(0),
		count_imgs_target(0),
		count_imgs_query(0)
	{
	}

	int ImageCache::GetState()
	{
		return state;
	}

	void ImageCache::Clear()
	{
		imgs_target.clear();
		imgs_query.clear();
		count_imgs_target = 0;
		count_imgs_query = 0;
	}
}