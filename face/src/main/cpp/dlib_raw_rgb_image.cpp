// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#include "dlib_raw_rgb_image.h"

namespace dlib {

	void copy(const const_sub_image_proxy<raw_rgb_image> &from, sub_image_proxy<raw_rgb_image> &to) {
		DLIB_ASSERT((from._nr == to._nr && from._nc == to._nc),
			"\t copying subimages..."
			<< "\n\t Image size mismatch:"
			<< "\n\t from " << from._nc << "x" << from._nr
			<< "\n\t to " << to._nc << "x" << to._nr
		);

		const uint8_t *src = reinterpret_cast<const uint8_t *>(from._data);
		uint8_t *dst = reinterpret_cast<uint8_t *>(to._data);

		for (int r = 0; r < from._nr; r++) {
			std::copy(src, src + from._nc * 3, dst);
			src += from._width_step;
			dst += to._width_step;
		}
	}

}