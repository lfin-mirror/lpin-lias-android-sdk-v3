// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#ifndef __DLIB_RAW_RGB_IMAGE_H__
#define __DLIB_RAW_RGB_IMAGE_H__

#pragma once

#ifndef DLIB_NO_GUI_SUPPORT
#define DLIB_NO_GUI_SUPPORT
#endif

#include "dlib/image_transforms.h"
#include "dlib/image_processing.h"

namespace dlib {
    class raw_rgb_image {
    public:
        friend inline long width_step(const raw_rgb_image &img);

        friend inline void *image_data(raw_rgb_image &img);

        friend inline const void *image_data(const raw_rgb_image &img);

        typedef rgb_pixel pixel_type;

        raw_rgb_image(char *ptr, long nc, long nr) : _data(ptr), _nc(nc), _nr(nr),
                                                     _width_step(nc * 3) {}

        long nr() const { return _nr; }

        long nc() const { return _nc; }

        unsigned long size() const { return static_cast<unsigned long>(nr() * nc()); }

        pixel_type *operator[](long row) { return (pixel_type *) (_data + _width_step * row); }

        const pixel_type *operator[](long row) const {
            return (const pixel_type *) (_data +
                                         _width_step *
                                         row);
        }

        void set_size(long rows, long cols) {
            DLIB_ASSERT((cols >= 0 && rows >= 0),
                        "\t raw_rgb_image::set_size(long rows, long cols)"
                                << "\n\t The images can't have negative rows or columns."
                                << "\n\t cols: " << cols
                                << "\n\t rows: " << rows
            );
        }

        void clear() { set_size(0, 0); }

        void inplace_trans() {
            if (_nr == _nc) { // square
                for (int r = 0; r < _nr - 1; r++) {
                    for (int c = r + 1; c < _nc; c++) {
                        auto src = &_data[(r * _nc + c) * 3];
                        auto dst = &_data[(c * _nr + r) * 3];
                        std::swap_ranges(src, src + 3, dst);
                    }
                }
            } else {
                unsigned long last = size();
                std::vector<bool> visited(last);

                unsigned long cycle = 0;
                unsigned long mn1 = visited.size() - 1;
                while (++cycle != last) {
                    long loc = cycle;
                    if (visited[loc])
                        continue;
                    do {
                        loc = loc == mn1 ? mn1 : (_nr * loc) % mn1;
                        auto src = &_data[loc * 3];
                        std::swap_ranges(src, src + 3, &_data[cycle * 3]);
                        visited[loc] = true;
                    } while (loc != cycle);
                }
            }

            std::swap(_nc, _nr);
        }

    private:
        char *_data;
        long _width_step;
        long _nr;
        long _nc;
    };

    template<>
    struct image_traits<raw_rgb_image> {
        typedef rgb_pixel pixel_type;
    };

    inline long width_step(const raw_rgb_image &img) {
        return img._width_step;
    }

    inline void *image_data(raw_rgb_image &img) {
        return img._data;
    }

    inline const void *image_data(const raw_rgb_image &img) {
        return img._data;
    }

	void copy(const const_sub_image_proxy<raw_rgb_image> &from, sub_image_proxy<raw_rgb_image> &to);
}

#endif // __DLIB_RAW_RGB_IMAGE_H__
