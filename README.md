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

- `AAR/lpin-android-sdk-face-authenticator-<version>.aar`
- `AAR/lpin-android-sdk-scanner-<version>.aar`
- `AAR/lpin-android-sdk-licensing-core-<version>.aar`
- `AAR/lpin-android-sdk-space-pixel-matching-<version>.aar`
- `AAR/lpin-android-sdk-space-authenticator-<version>.aar`

`AAR/lpin-android-sdk-space-authenticator-<version>.aar`는 OpenCV wrapper 클래스와 JNI 라이브러리를 직접 포함합니다.

OpenCV native SDK 원본은 `third_party`의 중립 vendor 경로에 보관되며, `:space:authenticator`와 `:space:pixel-matching`가 이 경로를 함께 참조합니다.

## 오프라인 라이센스 배포 흐름

이 저장소는 런타임 API 입력 대신 **CLI로 생성한 라이센스 파일**을 앱 `assets/`에 넣고, SDK 내부 공개키로 오프라인 검증하는 구조를 지원합니다.

### 1. Ed25519 키 생성

```bash
ssh-keygen -t ed25519 -f lias_license_ed25519 -C "lias-license"
```

라이센스 서명용 private key는 외부에 공개하지 말고, SDK 빌드에는 public key만 반영합니다.

### 2. SDK에 공개키 임베드

OpenSSH public key에서 SDK가 사용할 raw Ed25519 public key(base64url)를 추출합니다.

```bash
./gradlew -q :tools:license-cli:run --args="extract-public-key --public-key /absolute/path/to/lias_license_ed25519.pub"
```

출력된 값을 `gradle.properties`의 `lpinLicensePublicKeyBase64Url`에 넣고 AAR을 다시 빌드합니다.

```properties
lpinLicensePublicKeyId=main
lpinLicensePublicKeyBase64Url=<extract-public-key 결과>
lpinLicenseAssetFileName=lpin-lias-license.json
```

### 3. 고객사 앱용 라이센스 생성

고객사의 앱 패키지명과 서명 인증서 SHA-256, 허용 모듈 목록을 넣어 signed license를 생성합니다.

```bash
./gradlew -q :tools:license-cli:run --args="sign \
  --private-key /absolute/path/to/lias_license_ed25519 \
  --package-name com.example.customer \
  --signing-cert-sha256 AABBCCDDEEFF00112233 \
  --features face,scanner,space,pixel-matching \
  --not-before 2026-01-01T00:00:00Z \
  --not-after 2027-01-01T00:00:00Z \
  --customer ExampleCorp \
  --license-id examplecorp-prod \
  --output /tmp/lpin-lias-license.json"
```

> `signing-cert-sha256`는 콜론(`:`)이 있어도 되지만, CLI가 내부적으로 제거하고 대문자로 정규화합니다.

### 4. 고객사 앱에 라이센스 파일 포함

기본 파일명은 `lpin-lias-license.json`이며, 호스트 앱의 `assets/` 루트에 배치해야 합니다.

```text
app/
└── src/main/assets/
    └── lpin-lias-license.json
```

SDK는 첫 사용 시 이 파일을 로드하고 아래 항목을 오프라인 검증합니다.

- envelope `version`, `algorithm`, `keyId`
- payload signature (Ed25519)
- `packageName`
- `signingCertSha256`
- `notBefore`, `notAfter`
- `features`

라이센스가 없거나 검증에 실패하면 각 모듈의 public entrypoint에서 즉시 차단됩니다.
