# LIAS Android SDK 라이센스 통합 가이드

## 개요

LIAS Android SDK는 **오프라인 compact license key 기반 검증 방식**을 사용합니다.
호스트 앱은 SDK 초기화 시 별도의 서버 통신 없이, `AndroidManifest.xml`의 meta-data에 포함된 compact license key를 통해 사용 권한을 검증합니다.

기본 검증 항목:

- 라이센스 서명 유효성 (Ed25519)
- 앱 패키지명 (`app_pkg_id`)
- 사용 가능 기간 (`issued_at`, `expire_at`)

검증 실패 시 SDK 기능은 즉시 차단됩니다.

---

## 제공 항목

SDK 제공자는 고객사에 아래 항목을 전달합니다.

### 1. SDK AAR 파일

예시:

- `lpin-android-sdk-face-authenticator-<version>.aar`
- `lpin-android-sdk-scanner-<version>.aar`
- `lpin-android-sdk-licensing-core-<version>.aar`
- `lpin-android-sdk-space-pixel-matching-<version>.aar`
- `lpin-android-sdk-space-authenticator-<version>.aar`

### 2. 고객사 전용 compact license key

형식:

```text
BASE64URL(payload-json).BASE64URL(signature)
```

이 키는 기본적으로 고객사 앱의 패키지명과 사용 가능 기간을 기준으로 발급됩니다. 필요하면 고급 옵션으로 서명 인증서나 허용 기능 제한을 추가할 수 있습니다.

---

## Compact license key

현재 권장 방식은 compact license key를 사용하는 것입니다.

payload를 decode하면 아래 형태의 JSON이 나옵니다.

```json
{
  "app_pkg_id": "com.example.customer",
  "issued_at": "2026-01-01T00:00:00Z",
  "expire_at": "2027-01-01T00:00:00Z"
}
```

설명:

- payload에는 항상 `app_pkg_id`, `issued_at`, `expire_at`가 포함됩니다.
- 생성 시 `--issued-at`을 생략하면 현재 UTC 시각이 자동으로 `issued_at`에 들어갑니다.
- 기본 payload에는 `app_pkg_id`, `issued_at`, `expire_at`만 포함하면 됩니다.

CLI 생성 명령:

```bash
./gradlew -q :tools:license-cli:run --args="sign-key \
  --private-key /absolute/path/to/lias_license_ed25519 \
  --app-pkg-id com.example.customer \
  --expire-at 2027-01-01T00:00:00Z"
```

`--issued-at`을 생략하면 CLI가 현재 UTC 시각을 자동으로 사용합니다. 특정 시점을 고정해야 할 때만 `--issued-at`을 명시적으로 전달하면 됩니다.

사내용 브라우저 발급 도구:

- `tools/license-cli/license-issuer.html`

이 도구는 PKCS#8 PEM 형식의 Ed25519 private key를 입력받아 브라우저에서 compact license key를 생성합니다.

---

## 고객사 앱 적용 방법

### 1. AAR 추가

전달받은 AAR 파일을 고객사 Android 프로젝트에 추가합니다.

일반적인 방식:

- `libs/` 폴더에 배치 후 `implementation files(...)`

### 2. compact license key 추가

발급받은 compact license key를 `AndroidManifest.xml` meta-data에 설정합니다.

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

### 3. 앱 패키지명 확인

라이센스는 발급 시 등록한 패키지명과 실제 앱 패키지명이 일치해야 합니다.

예시:

```text
com.example.customer
```

패키지명이 다르면 검증에 실패합니다.

### 4. 고급 옵션: 앱 서명 인증서 확인

`signing_cert_sha256`를 포함해서 발급받는 경우에만, 실제 앱 서명 인증서 SHA-256이 라이센스와 일치해야 합니다.

이 옵션을 사용할 때는 아래 항목을 확인해 주세요.

- 디버그 키 / 릴리즈 키 구분
- 최종 배포용 keystore 기준으로 발급되었는지 확인
- signing key 변경 시 라이센스 재발급 필요

---

## 사용 가능한 기능

compact license key에 `features`가 포함된 경우에만, 고객사 앱은 발급받은 기능만 사용할 수 있습니다.

예시:

- `face`
- `scanner`
- `space`
- `pixel-matching`

포함되지 않은 기능 호출 시 검증 실패가 발생합니다.

`features`가 없는 compact key는 전체 기능 사용을 허용합니다.

---

## 런타임 검증 흐름

SDK는 첫 사용 시 아래 순서로 검증을 수행합니다.

1. `AndroidManifest.xml` meta-data `io.lpin.license.KEY` 확인
2. compact key의 payload/signature 분리
3. Ed25519 서명 검증
4. 앱 패키지명 일치 여부 확인
5. 사용 가능 기간 확인 (`issued_at`, `expire_at`)

모든 검증이 통과되어야 해당 SDK 기능이 정상 동작합니다.

---

## 실패 시 동작

### Face / Space 모듈

Face 및 Space 모듈은 라이센스 오류 발생 시 public failure callback으로 실패를 반환합니다.

대표 원인:

- compact license key 누락
- 잘못된 패키지명
- 잘못된 서명 인증서
- 사용 기간 만료
- 허용되지 않은 feature 호출

### Scanner 모듈

Scanner 모듈은 초기화 시점에 라이센스 오류가 발생하면 예외가 상위로 전달될 수 있습니다.
따라서 scanner 관련 객체 생성 또는 초기화 시 예외 처리 적용을 권장합니다.

---

## 고객사 체크리스트

배포 전 아래 항목을 확인해 주세요.

- [ ] 전달받은 모든 AAR 파일이 프로젝트에 포함되었는가
- [ ] `AndroidManifest.xml`에 `io.lpin.license.KEY` meta-data가 설정되어 있는가
- [ ] 실제 앱 패키지명이 라이센스 발급 정보와 일치하는가
- [ ] 릴리즈용 keystore 변경 이력이 없는가

---

## 라이센스 재발급이 필요한 경우

아래 경우에는 라이센스 재발급이 필요합니다.

- 앱 패키지명 변경
- 앱 서명 키 변경
- 사용 기능(`features`) 변경
- 고객사/운영환경 분리 필요
- 사용 기간 연장 필요

---

## 문의 시 전달 필요 정보

라이센스 오류 발생 시 아래 정보를 함께 전달해 주세요.

- 앱 패키지명
- 앱 서명 인증서 SHA-256
- 적용한 SDK 버전
- 적용한 compact license key 사용 여부
- 어떤 기능 호출 시 실패하는지 (`face`, `scanner`, `space`, `pixel-matching`)
- 오류 메시지 / 콜백 코드 / 예외 로그

---

## 주의사항

- 권장 방식은 Manifest meta-data의 compact license key 입니다.
- private signing key는 고객사에 제공되지 않습니다.
- compact license key는 고객사 앱 전용으로 발급되며 다른 앱에서 재사용할 수 없습니다.
- 앱 서명 인증서나 기능 제한은 기본값이 아니라 필요할 때만 추가하는 고급 옵션입니다.
- 릴리즈 서명 키가 변경되면 해당 값을 라이센스에 포함한 경우 기존 라이센스는 더 이상 유효하지 않을 수 있습니다.

---

## SDK 제공자 운영 메모

SDK 제공자는 아래 절차로 라이센스를 운영합니다.

1. Ed25519 키쌍 생성
2. public key를 SDK 빌드 설정에 반영
3. SDK AAR 빌드
4. 고객사 앱 정보(package name, 기간)로 compact license key 생성
5. AAR 세트 + 고객사 전용 compact license key 배포

> private key는 외부에 절대 배포하지 않습니다.
