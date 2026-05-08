// Copyright 2017. Electronics and Telecommunications Research Institute. All rights reserved.
// Written by Seonho Oh @ Intelligent Security Research Group

#include "mtcnn.h"

#ifdef _WIN32 

#include <ppl.h>
using namespace concurrency;

#endif

std::vector<double> create_scale_pyramid(const int w, const int h, int minsize, double factor) {

	double minl = std::min(h, w);
	auto m = 12.0 / minsize;
	minl = minl * m;

	// create scale pyramid
	std::vector<double> scales;
	auto factor_count = 0;
	while (minl >= 12) {
		scales.push_back(m * std::pow(factor, factor_count));
		minl *= factor;
		factor_count++;
	}

	return scales;
}

void imresample(const Image& in, int width, int height, float *buffer) {
#ifndef USE_OPENCV
	std::vector<uint8_t> raw(static_cast<unsigned int>(width * height * 3));
	Image resized(reinterpret_cast<char *>(raw.data()), width, height);
	dlib::resize_image(in, resized);

	resized.inplace_trans(); // transpose
	std::copy(raw.data(), raw.data() + raw.size(), buffer);

	arma::fcube out(buffer, 3, height, width, false);
	out -= 127.5;
	out *= 0.0078125;
#else
	Image dst;
	auto size = cv::Size(width, height);
	cv::resize(in, dst, size, 0., 0., cv::INTER_AREA);

	cv::transpose(dst, dst);
	Image fdst(size, CV_32FC3);
	dst.convertTo(fdst, CV_32FC3);
	
	fdst -= cv::Scalar(127.5, 127.5, 127.5);
	fdst *= 0.0078125;
	fdst.copyTo(cv::Mat(width, height, CV_32FC3, static_cast<void*>(buffer)));
#endif
}

arma::fmat generate_boundingbox(const arma::fmat &imap, const arma::fcube &reg, double scale, double t) {
	const auto stride = 2;
	const auto cellsize = 12;

	arma::umat indices = arma::ind2sub(arma::size(imap), arma::find(imap >= t));

	if (indices.is_empty()) return arma::fmat();

	arma::fmat boundingBoxes(9, indices.n_cols);
	for (arma::uword i = 0; i < indices.n_cols; i++) {
		const auto p = indices.col(i);
		auto bb = boundingBoxes.col(i);
		bb[0] = static_cast<float>(std::floor((stride * p[1] + 1) / scale));
		bb[1] = static_cast<float>(std::floor((stride * p[0] + 1) / scale));
		bb[2] = static_cast<float>(std::floor((stride * p[1] + cellsize) / scale));
		bb[3] = static_cast<float>(std::floor((stride * p[0] + cellsize) / scale));
		bb[4] = imap.at(p[0], p[1]);
		bb[5] = reg.at(0, p[0], p[1]);
		bb[6] = reg.at(1, p[0], p[1]);
		bb[7] = reg.at(2, p[0], p[1]);
		bb[8] = reg.at(3, p[0], p[1]);
	}

	return boundingBoxes;
}

arma::uvec nms(const arma::fmat &boxes, double threshold, std::string method) {

	if (boxes.is_empty()) return arma::uvec();

	const auto x1 = boxes.row(0);
	const auto y1 = boxes.row(1);
	const auto x2 = boxes.row(2);
	const auto y2 = boxes.row(3);
	const auto s = boxes.row(4);
	const arma::fvec area = ((x2 - x1 + 1) % (y2 - y1 + 1));
	arma::uvec I = sort_index(s);

	std::vector<arma::uword> pick;
	while (!I.is_empty()) {
		auto i = I(I.n_elem - 1);
		pick.push_back(i);

		I.shed_row(I.n_elem - 1);

		std::vector<arma::uword> idx;
		I.for_each([&](arma::uword j) {
			auto xx1 = std::max(x1(i), x1(j));
			auto yy1 = std::max(y1(i), y1(j));
			auto xx2 = std::min(x2(i), x2(j));
			auto yy2 = std::min(y2(i), y2(j));
			auto w = std::max(0.f, xx2 - xx1 + 1);
			auto h = std::max(0.f, yy2 - yy1 + 1);
			auto inter = w * h;
			float o = 0.f;
			if (method == "min")
				o = inter / std::min(area(i), area(j));
			else
				o = inter / (area(i) + area(j) - inter);
			if (o <= threshold) idx.push_back(j);
		});
		I = arma::conv_to<arma::uvec>::from(idx);
	}

	return pick;
}

void rerec(arma::fmat &boxes) {
	// convert bbox to square
	auto w = (boxes.row(2) - boxes.row(0)).eval();
	auto h = (boxes.row(3) - boxes.row(1)).eval();
	auto I = arma::max(w, h).eval();
	boxes.row(0) += w * 0.5f - I * 0.5;
	boxes.row(1) += h * 0.5f - I * 0.5;
	boxes.row(2) = boxes.row(0) + I;
	boxes.row(3) = boxes.row(1) + I;
}

void pad(const arma::fmat &total_boxes, int w, int h,
	arma::ivec &x, arma::ivec &y,
	arma::ivec &dx, arma::ivec &dy,
	arma::ivec &ex, arma::ivec &ey,
	arma::ivec &edx, arma::ivec &edy,
	arma::ivec &tmpw, arma::ivec &tmph) {

	// compute the padding coordinates (pad the bounding boxes to square)
	tmpw = arma::conv_to<arma::ivec>::from(total_boxes.row(2) - total_boxes.row(0) + 1);
	tmph = arma::conv_to<arma::ivec>::from(total_boxes.row(3) - total_boxes.row(1) + 1);

	dx = arma::ones<arma::ivec>(total_boxes.n_cols).eval();
	dy = arma::ones<arma::ivec>(total_boxes.n_cols).eval();
	edx = tmpw;
	edy = tmph;

	x = arma::conv_to<arma::ivec>::from(total_boxes.row(0));
	y = arma::conv_to<arma::ivec>::from(total_boxes.row(1));
	ex = arma::conv_to<arma::ivec>::from(total_boxes.row(2));
	ey = arma::conv_to<arma::ivec>::from(total_boxes.row(3));

	arma::uvec tmp = arma::find(ex > w);
	edx(tmp) = -ex(tmp) + w + tmpw(tmp);
	ex(tmp).fill(w);

	tmp = arma::find(ey > h);
	edy(tmp) = -ey(tmp) + h + tmph(tmp);
	ey(tmp).fill(h);

	tmp = arma::find(x < 1);
	dx(tmp) = 2 - x(tmp);
	x(tmp).fill(1);

	tmp = arma::find(y < 1);
	dy(tmp) = 2 - y(tmp);
	y(tmp).fill(1);
}

void bbreg(arma::fmat &boxes, arma::fmat reg) {
	// calibrate bounding box
	auto w = (boxes.row(2) - boxes.row(0) + 1).eval();
	auto h = (boxes.row(3) - boxes.row(1) + 1).eval();
	boxes.row(0) += reg.row(0) % w;
	boxes.row(1) += reg.row(1) % h;
	boxes.row(2) += reg.row(2) % w;
	boxes.row(3) += reg.row(3) % h;
}

std::vector<float> prepare_inputs(const Image &img, const arma::fmat &total_boxes, const int size) {
#ifndef USE_OPENCV
	const int w = static_cast<int>(img.nc());
	const int h = static_cast<int>(img.nr());
#else
	const int w = img.cols;
	const int h = img.rows;
#endif
	const auto image_size = size * size * 3;
	std::vector<float> tempimg(total_boxes.n_cols * image_size);

	arma::ivec x, y, dx, dy, ex, ey, edx, edy, tmpw, tmph;
	pad(total_boxes, w, h, x, y, dx, dy, ex, ey, edx, edy, tmpw, tmph);

	const int numbox = static_cast<int>(total_boxes.n_cols);
	for (int k = 0; k < numbox; k++) {
#ifndef USE_OPENCV
		std::vector<uint8_t> tmp_buffer(tmpw[k] * tmph[k] * 3, 0); // fill zero
		Image tmp(reinterpret_cast<char *>(tmp_buffer.data()), tmpw[k], tmph[k]);
		const auto src = dlib::sub_image(img,
			dlib::rectangle(x[k] - 1, y[k] - 1, ex[k] - 1, ey[k] - 1));
		auto dst = dlib::sub_image(tmp,
			dlib::rectangle(dx[k] - 1, dy[k] - 1, edx[k] - 1, edy[k] - 1));
		dlib::copy(src, dst);
#else
		cv::Mat tmp(tmph[k], tmpw[k], CV_8UC3);
		tmp.setTo(0);
		img(cv::Rect(x[k] - 1, y[k] - 1, ex[k] - x[k] + 1, ey[k] - y[k] + 1)).copyTo(
			tmp(cv::Rect(dx[k] - 1, dy[k] - 1, edx[k] - dx[k] + 1, edy[k] - dy[k] + 1)));
#endif
		imresample(tmp, size, size, tempimg.data() + image_size * k);
	}
	return tempimg;
}
