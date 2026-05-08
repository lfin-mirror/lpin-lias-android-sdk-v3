# LPIN Android SDK Face Authenticator

## 개요

LPIN Android SDK Face Authenticator는 안드로이드 앱에서 얼굴 등록, 인증, 변경 요청 기능을 제공하는 라이브러리입니다. 현재 모듈은 단일 Activity 진입점(`FaceAuthenticator`)을 중심으로 동작하며, 네이티브 MTCNN 기반 얼굴 검출, TensorFlow 기반 특징 추출, ML Kit 기반 보조 검출 및 눈 깜빡임 라이브니스 검사를 함께 사용합니다.

이 문서는 `face` 모듈의 현재 코드 기준으로 정리되어 있으며, 실제 공개 사용 흐름은 `Intent + onActivityResult` 방식이 아니라 `Builder + Listener` 방식입니다.

## 주요 기능

### 1. 얼굴 인증 플로우
- `AUTH`: 등록된 현재 사용자 얼굴 인증
- `AUTH_SINGLE`: 단일 인증 플로우
- `REGISTER`: 신규 얼굴 등록
- `MODIFY`: 얼굴 변경 등록 요청

### 2. 얼굴 검출
- 네이티브 `facedetector` 라이브러리를 통한 MTCNN 기반 얼굴 검출
- `mtcnn.pb` 모델 사용
- 기본 검출 실패 시 ML Kit Face Detection으로 fallback 처리
- 얼굴 정렬 후 인식용 입력 크기(112x112)로 정규화

### 3. 얼굴 인식
- `model.optimized.mobile.pb` 그래프 모델 사용
- TensorFlow Android (`org.tensorflow:tensorflow-android:1.13.1`) 기반 특징 벡터 추출
- 사용자 특징 벡터와 저장된 벡터 간 코사인 유사도 비교
- 특징 벡터 길이는 코드에 하드코딩되지 않고 모델 출력 shape에서 런타임 계산

### 4. 라이브니스 검사
- ML Kit 눈 뜸 확률 기반 눈 깜빡임 감지
- 2회 이상 blink가 감지되면 live 상태로 판단

### 5. 로컬 저장소
- Room Database로 사용자 얼굴 feature 저장
- `FaceSharedPreferences`로 현재 사용자 및 변경 대기 사용자 관리
- 외부 반환용 hash는 SHA-512 기반 문자열로 생성

### 6. 사람/동물 판별 보조 기능
- ML Kit Image Labeling을 이용해 사람/동물 라벨을 함께 판별
- `FaceDetector.detectHuman(...)`에서 얼굴 수, 라벨, 판정 사유를 함께 반환

## 현재 공개 사용 방식

### FaceAuthenticator 실행

```kotlin
val builder = FaceAuthenticator.Builder(context)
    .setType(FaceAuthenticatorType.AUTH)
    .setUuid("user_uuid")
    .setTimeout(10_000L)
    .setListener(object : FaceAuthenticator.Listener {
        override fun onSuccess(faceData: FaceData?) {
            val uuid = faceData?.faceUuid
            val hash = faceData?.hashFace
            val similarityPercent = faceData?.getSimilarity()
        }

        override fun onFailure(code: String?, message: String?) {
            // 실패 처리
        }
    })

builder.run()
```

### 지원 타입

```kotlin
enum class FaceAuthenticatorType {
    AUTH,
    AUTH_SINGLE,
    REGISTER,
    MODIFY,
    FRR,
    FAR,
}
```

> 현재 `Builder.run()`에서 Activity 실행 경로로 연결되는 타입은 `AUTH`, `AUTH_SINGLE`, `REGISTER`, `MODIFY`입니다.

### 인증 임계값 조정

```kotlin
FaceAuthenticator.setThreshold(0.81)
// 또는
FaceAuthenticator.setThreshold("0.81")
```

## FaceAuthenticatorManager 사용 예시

`FaceAuthenticatorManager`는 현재 등록 사용자 조회, 변경 대기 사용자 확인, 얼굴 데이터 삭제/조회 등에 사용됩니다.

```kotlin
val manager = FaceAuthenticatorManager.Builder(context).build()

val hasUser = manager.hasUser()
val registeredUsers = manager.getUserUuid(FaceAuthenticatorManager.Type.REGISTER_FACE)
val modifyUsers = manager.getUserUuid(FaceAuthenticatorManager.Type.MODIFY_FACE)
val faceData = manager.getFaceData("user_uuid")
```

### 제공 메서드
- `hasUser()`
- `getUserUuid(Type)`
- `hasModifyUser(uuid)`
- `deleteFaceData()`
- `deleteFaceData(type)`
- `deleteFaceData(type, uuid)`
- `getFaceData(uuid)`
- `allowModifyFace(uuid)`

> 참고: `addFaceData(Type.MODIFY_FACE, ...)`는 현재 코드에서 `TODO()` 상태입니다.

## FaceDetector / FaceRecognizer 보조 API

### FaceDetector
- `detect(bitmap)`
- `isHuman(bitmap)`
- `humanFaceCount(bitmap)`
- `detectHuman(bitmap)`

### FaceRecognizer
- `insert(name, features)`
- `delete(name)`
- `isValidUser(name)`
- `userFeatureHash(name)`
- `recognize(face)`
- `recognize(faces)`

## 시스템 요구사항

### Android / Toolchain
- **minSdkVersion**: 24
- **targetSdk**: 33
- **compileSdkVersion**: 33
- **NDK**: 25.1.8937393
- **CMake 최소 버전**: 3.4.1
- **Java target**: 17
- **Kotlin JVM target**: 17
- **AndroidX / Jetifier**: enabled

### 지원 ABI
- `x86_64`
- `armeabi-v7a`
- `arm64-v8a`

> 현재 빌드 설정에는 `x86` ABI가 포함되어 있지 않습니다.

## 권한 및 Manifest

모듈 Manifest에는 다음 권한과 Activity가 선언되어 있습니다.

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.front" />

<activity
    android:name=".FaceAuthenticator"
    android:exported="false"
    android:screenOrientation="portrait" />
```

## 의존성

현재 `face/build.gradle` 기준 주요 의존성은 다음과 같습니다.

```gradle
dependencies {
    implementation 'org.tensorflow:tensorflow-android:1.13.1'
    implementation 'com.google.code.gson:gson:2.8.6'

    implementation "androidx.room:room-runtime:2.4.2"
    kapt "androidx.room:room-compiler:2.4.2"

    implementation 'androidx.core:core-ktx:1.16.0'
    implementation 'androidx.fragment:fragment-ktx:1.8.8'
    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation "androidx.constraintlayout:constraintlayout:2.2.1"

    implementation 'com.google.mlkit:face-detection:16.1.7'
    implementation 'com.google.mlkit:image-labeling:17.0.9'

    testImplementation 'junit:junit:4.13.2'
}
```

## 내부 아키텍처

### 1. FaceAuthenticator
- 모듈의 단일 Activity 진입점
- 카메라 UI와 등록/인증/변경 플로우 제어
- timeout 처리
- 결과를 `Listener`로 반환
- 허용된 패키지명만 실행할 수 있도록 `BuildConfig.ALLOW_LICENSE_PACKAGES` 검사 수행

### 2. FaceDetector
- 네이티브 MTCNN 기반 얼굴 검출
- JNI bridge 사용
- ML Kit 얼굴 검출 fallback 제공
- 사람/동물 라벨 판별 지원

### 3. FaceRecognizer
- TensorFlow 그래프 기반 특징 벡터 추출
- 사용자 feature 저장 및 유사도 비교
- hash 생성 유틸리티 제공

### 4. LivenessEyeBlink
- ML Kit 눈 확률 기반 blink 감지
- 2회 blink 기준으로 live 판정

### 5. UserDatabase / UserDao / UserEntity
- Room 기반 로컬 저장소
- 사용자 이름과 feature blob 저장
- feature blob을 다시 float 배열로 변환하여 평균 유사도 계산

## 프로젝트 구조

```sh
face/
├── build.gradle
├── gradle.properties
├── src/main/
│   ├── AndroidManifest.xml
│   ├── assets/
│   │   ├── mtcnn.pb
│   │   └── model.optimized.mobile.pb
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── mtcnn.cpp
│   │   ├── facedetector_jni.cpp
│   │   ├── dlib_raw_rgb_image.cpp
│   │   ├── imageutils_jni.cc
│   │   ├── rgb2yuv.cc
│   │   └── yuv2rgb.cc
│   └── java/io/lpin/android/sdk/face/
│       ├── FaceAuthenticator.kt
│       ├── FaceAuthenticatorManager.kt
│       ├── FaceAuthenticatorType.kt
│       ├── FaceData.kt
│       ├── FaceDetector.java
│       ├── FaceRecognizer.java
│       ├── core/
│       ├── database/
│       ├── liveness/
│       └── model/
```

## 네이티브 빌드 메모

- `src/main/cpp/CMakeLists.txt`에서 `facedetector`, `external` 두 개의 shared library를 생성합니다.
- 16KB page size linker flag를 사용합니다.
- `armeabi-v7a`에서는 NEON 관련 옵션이 적용되고, `arm64-v8a`에서는 최적화 옵션이 적용됩니다.

## 주의사항

- 현재 모듈은 **TensorFlow Lite**가 아니라 **TensorFlow Android + .pb 모델 파일** 기반입니다.
- 결과 전달 방식은 `onActivityResult`가 아니라 `FaceAuthenticator.Listener` 콜백입니다.
- 현재 저장 구조는 여러 등록 사용자를 일반화한 구조라기보다, 현재 사용자 1명과 변경 대기 사용자 목록을 함께 관리하는 형태에 가깝습니다.
- `HumanDetectionTestActivity` 소스가 있더라도 Manifest에 공개 Activity로 선언되어 있지 않으면 외부 진입점으로 간주하면 안 됩니다.

## 버전 정보
- **현재 버전**: 1.0.45
- **versionCode**: 10045
- **compileSdkVersion**: 33
- **targetSdk**: 33
- **minSdkVersion**: 24
- **NDK 버전**: 25.1.8937393
