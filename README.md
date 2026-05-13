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

이 저장소는 런타임 API 입력 대신 **CLI로 생성한 compact license key 또는 라이센스 파일**을 앱에 포함하고, SDK 내부 공개키로 오프라인 검증하는 구조를 지원합니다.

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

고객사의 앱 패키지명과 사용 기간을 기준으로 signed license를 생성합니다.

```bash
./gradlew -q :tools:license-cli:run --args="sign \
  --private-key /absolute/path/to/lias_license_ed25519 \
  --package-name com.example.customer \
  --not-before 2026-01-01T00:00:00Z \
  --not-after 2027-01-01T00:00:00Z \
  --customer ExampleCorp \
  --license-id examplecorp-prod \
  --output /tmp/lpin-lias-license.json"
```

> 필요하면 고급 옵션으로 `signing-cert-sha256`와 `features`를 추가해 특정 앱 서명키 또는 허용 기능 집합에만 묶을 수 있습니다.

### 4. 고객사 앱에 compact license key 포함 (권장)

권장 방식은 호스트 앱의 `AndroidManifest.xml`에 compact license key를 넣는 것입니다.

```xml
<application>
    <meta-data
        android:name="io.lpin.license.KEY"
        android:value="${LPIN_LICENSE_KEY}" />
</application>
```

```gradle
android {
    defaultConfig {
        manifestPlaceholders = [
            LPIN_LICENSE_KEY: "BASE64URL(payload).BASE64URL(signature)"
        ]
    }
}
```

SDK는 첫 사용 시 Manifest meta-data `io.lpin.license.KEY`를 읽고 아래 항목을 오프라인 검증합니다.

- payload signature (Ed25519)
- `app_pkg_id`
- `issued_at`
- `expire_at`

compact key가 없으면 기존 asset JSON 라이센스(`lpin-lias-license.json`)를 fallback으로 읽습니다.

라이센스가 없거나 검증에 실패하면 각 모듈의 public entrypoint에서 즉시 차단됩니다.

## Compact base64 license key

Manifest meta-data에 넣기 위한 compact license key 형식은 아래와 같습니다.

```text
BASE64URL(payload-json).BASE64URL(signature)
```

payload를 decode하면 아래 필드가 나옵니다.

```json
{
  "app_pkg_id": "com.example.customer",
  "issued_at": "2026-01-01T00:00:00Z",
  "expire_at": "2027-01-01T00:00:00Z"
}
```

생성 명령:

```bash
./gradlew -q :tools:license-cli:run --args="sign-key \
  --private-key /absolute/path/to/lias_license_ed25519 \
  --app-pkg-id com.example.customer \
  --expire-at 2027-01-01T00:00:00Z"
```

`--issued-at`을 생략하면 CLI가 현재 UTC 시각을 자동으로 넣습니다. 명시적으로 고정 시간이 필요하면 기존처럼 `--issued-at 2026-01-01T00:00:00Z`를 전달하면 됩니다.

사내용 브라우저 발급 도구도 함께 제공합니다.

- `tools/license-cli/license-issuer.html`

이 HTML은 PKCS#8 PEM 형식의 Ed25519 private key를 입력받아 브라우저에서 compact key를 생성합니다. `Issued At` 입력을 비워두면 현재 UTC 시각을 자동으로 사용합니다.

이 compact key는 verifier에서 직접 검증되며, 현재 SDK의 권장 통합 방식입니다. payload에는 항상 `app_pkg_id`, `issued_at`, `expire_at`가 들어가고, 생성 시 `issued_at`을 생략하면 현재 UTC 시각이 자동으로 채워집니다. 필요하면 고급 옵션으로 `signing_cert_sha256`와 `features`를 포함할 수 있습니다. `signing_cert_sha256`가 있으면 현재 앱 인증서와 일치해야 하며, `features`가 있으면 서명된 기능 subset만 허용됩니다. 둘 다 없으면 레거시 compact key로 간주되어 전체 기능을 허용합니다. asset JSON 라이센스는 package/cert/features 전체 검증이 필요한 레거시 fallback 경로로 유지됩니다.
