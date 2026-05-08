# lpin-android-sdk-v3

`lpin-android-sdk-v3`는 LIAS 구성 요소를 위한 멀티 모듈 Android SDK 저장소입니다.

## AAR 빌드

저장소 로컬 스크립트를 사용하여 모든 모듈의 AAR을 빌드합니다.

```bash
cd /Users/sonseongho/dev/lpin-android-sdk-v3
bash scripts/build_android_aars.sh
```

선택 가능한 구성:

```bash
bash scripts/build_android_aars.sh --configuration Release
bash scripts/build_android_aars.sh --configuration Debug
```

## 출력 위치

`scripts/build_android_aars.sh`를 실행하면 저장소 루트의 `AAR/` 디렉터리가 생성되거나 갱신되고, 생성된 AAR 파일이 그 위치로 복사됩니다.

출력 예시:

- `AAR/face-authenticator-release.aar`
- `AAR/scanner-release.aar`
- `AAR/pixel-matching-release.aar`
- `AAR/space-authenticator-release.aar`

`AAR/space-authenticator-release.aar`는 OpenCV wrapper 클래스와 JNI 라이브러리를 직접 포함합니다.

OpenCV native SDK 원본은 `third_party`의 중립 vendor 경로에 보관되며, `:space:authenticator`와 `:space:pixel-matching`가 이 경로를 함께 참조합니다.
