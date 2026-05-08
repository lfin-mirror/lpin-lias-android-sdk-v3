# gradle 디렉토리

이 디렉토리는 `lpin-android-sdk-v3`의 **Gradle Wrapper 관련 파일**을 보관하는 용도입니다.

## 왜 필요한가

이 저장소는 시스템에 따로 설치된 `gradle` 명령을 직접 쓰지 않고, 루트의 `./gradlew` 스크립트를 통해 빌드합니다.
이때 `gradle/` 아래 파일들이 실제로 어떤 Gradle 버전을 사용할지 결정하고, 필요한 실행 파일을 내려받아 부트스트랩하는 역할을 합니다.

즉:

- `gradlew` = 실행 진입점
- `gradle/wrapper/gradle-wrapper.jar` = Gradle Wrapper 실행 코드
- `gradle/wrapper/gradle-wrapper.properties` = 사용할 Gradle 배포본 정보

## 현재 이 저장소에서의 역할

이 저장소의 `gradle/wrapper/gradle-wrapper.properties`는 Gradle 배포본을 다음과 같이 고정합니다.

- `gradle-8.9-bin.zip`

그래서 개발자 로컬 환경에 어떤 Gradle이 설치되어 있든, 이 저장소는 같은 Gradle 버전 기준으로 빌드되도록 맞춰집니다.

## 왜 루트가 아니라 `gradle/` 아래 두는가

`gradlew`는 루트에 두고, 나머지 Wrapper 파일은 `gradle/` 아래 두는 구조가 Gradle의 표준 관례입니다.
이렇게 하면:

1. 루트 디렉토리가 덜 복잡해지고
2. Wrapper 관련 파일을 한 곳에서 관리할 수 있고
3. Gradle 도구와의 호환성이 좋아집니다.

## 이 저장소에서 실제 사용되는 곳

저장소의 Android AAR 빌드 스크립트인 `scripts/build_android_aars.sh`는 루트의 `./gradlew`를 사용해 모듈 빌드를 수행합니다.
즉, 이 디렉토리는 단순 보관용이 아니라 실제 빌드 동작에 직접 연결된 디렉토리입니다.
