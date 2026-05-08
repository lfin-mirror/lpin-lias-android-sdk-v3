#!/usr/bin/env bash
set -euo pipefail

IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DEFAULT_CONFIGURATION="Release"
CONFIGURATION=""
CONFIGURATION_LOWER=""

MODULE_LABELS=(
  ":face"
  ":scanner"
  ":space:pixel-matching"
  ":space:authenticator"
)

MODULE_OUTPUT_DIRS=(
  "face/build/outputs/aar"
  "scanner/build/outputs/aar"
  "space/pixel-matching/build/outputs/aar"
  "space/authenticator/build/outputs/aar"
)

MODULE_GRADLE_TASK_PREFIXES=(
  ":face:assemble"
  ":scanner:assemble"
  ":space:pixel-matching:assemble"
  ":space:authenticator:assemble"
)

OPENCV_NATIVE_ROOT_RELATIVE_PATH="third_party"

GRADLE_TASKS=()
SOURCE_OUTPUT_DIRS=()
COPIED_ARTIFACTS=()

module_artifact_prefix_for_label() {
  local module_label="$1"

  case "$module_label" in
    ":face")
      printf 'lpin-android-sdk-face-authenticator'
      ;;
    ":scanner")
      printf 'lpin-android-sdk-scanner'
      ;;
    ":space:pixel-matching")
      printf 'lpin-android-sdk-space-pixel-matching'
      ;;
    ":space:authenticator")
      printf 'lpin-android-sdk-space-authenticator'
      ;;
    *)
      die "Unsupported module label for artifact prefix: $module_label"
      ;;
  esac
}

module_build_gradle_path_for_label() {
  local module_label="$1"

  case "$module_label" in
    ":face")
      printf '%s/face/build.gradle' "$REPO_ROOT"
      ;;
    ":scanner")
      printf '%s/scanner/build.gradle' "$REPO_ROOT"
      ;;
    ":space:pixel-matching")
      printf '%s/space/pixel-matching/build.gradle' "$REPO_ROOT"
      ;;
    ":space:authenticator")
      printf '%s/space/authenticator/build.gradle' "$REPO_ROOT"
      ;;
    *)
      die "Unsupported module label for build.gradle path: $module_label"
      ;;
  esac
}

module_version_for_label() {
  local module_label="$1"
  local build_gradle_path

  build_gradle_path="$(module_build_gradle_path_for_label "$module_label")"
  [[ -f "$build_gradle_path" ]] || die "Missing build.gradle for $module_label: $build_gradle_path"

  python3 - "$build_gradle_path" <<'PY'
import pathlib
import re
import sys

build_gradle_path = pathlib.Path(sys.argv[1])
content = build_gradle_path.read_text(encoding="utf-8")

parts = {}
for key in ("versionMajor", "versionMinor", "versionPatch"):
    match = re.search(rf"def\s+{key}\s*=\s*(\d+)", content)
    if not match:
        print(f"ERROR: Missing {key} in {build_gradle_path}", file=sys.stderr)
        sys.exit(1)
    parts[key] = match.group(1)

print(f"{parts['versionMajor']}.{parts['versionMinor']}.{parts['versionPatch']}")
PY
}

print_usage() {
  cat <<'EOF'
Usage: build_android_aars.sh [--help] [--configuration Release|Debug]

Configuration values are case-insensitive; release/debug are accepted.
Builds Android AARs and copies them to the repo-root AAR/ directory.
EOF
}

info() {
  printf '==> %s\n' "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

ensure_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || die "Required command not found: $command_name"
}

canonical_path() {
  python3 - "$1" <<'PY'
import os
import sys

print(os.path.realpath(sys.argv[1]))
PY
}

to_lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

ensure_path_within_directory() {
  local base_path="$1"
  local target_path="$2"
  local label="$3"

  python3 - "$base_path" "$target_path" "$label" <<'PY'
import os
import sys

base_path = os.path.realpath(sys.argv[1])
target_path = os.path.realpath(sys.argv[2])
label = sys.argv[3]

try:
    common = os.path.commonpath([base_path, target_path])
except ValueError:
    common = ""

if common != base_path:
    print(f"ERROR: {label} must stay inside {base_path}: {target_path}", file=sys.stderr)
    sys.exit(1)
PY
}

ensure_repo_root() {
  [[ -f "$REPO_ROOT/settings.gradle" ]] || die "Repo root validation failed: missing settings.gradle in $REPO_ROOT"
  [[ -f "$REPO_ROOT/build.gradle" ]] || die "Repo root validation failed: missing build.gradle in $REPO_ROOT"
  [[ -x "$REPO_ROOT/gradlew" ]] || die "Missing gradlew executable in $REPO_ROOT"

  if ! grep -q "rootProject.name = 'lpin-android-sdk'" "$REPO_ROOT/settings.gradle"; then
    die "Repo root validation failed: $REPO_ROOT/settings.gradle does not identify the lpin-android-sdk repo"
  fi
}

ensure_opencv_vendor_root() {
  local open_cv_native_root="$REPO_ROOT/$OPENCV_NATIVE_ROOT_RELATIVE_PATH"
  [[ -d "$open_cv_native_root" ]] || die "Missing vendored OpenCV native root: $open_cv_native_root"
  [[ -f "$open_cv_native_root/jni/OpenCVConfig.cmake" ]] || die "Missing OpenCVConfig.cmake under vendored OpenCV path: $open_cv_native_root/jni/OpenCVConfig.cmake"
}

normalize_configuration() {
  local input="$1"
  local lower_input

  lower_input="$(to_lower "$input")"

  case "$lower_input" in
    release)
      CONFIGURATION="Release"
      CONFIGURATION_LOWER="release"
      ;;
    debug)
      CONFIGURATION="Debug"
      CONFIGURATION_LOWER="debug"
      ;;
    *)
      die "Unsupported configuration: $input (expected Release or Debug)"
      ;;
  esac
}

safe_prepare_output_dir() {
  local artifacts_base_dir="$1"
  local config_output_dir="$2"

  ensure_path_within_directory "$REPO_ROOT" "$artifacts_base_dir" "Artifacts base directory"
  ensure_path_within_directory "$artifacts_base_dir" "$config_output_dir" "Configuration artifact directory"

  mkdir -p "$artifacts_base_dir"
  mkdir -p "$config_output_dir"
}

build_modules() {
  local -a tasks=()
  local index

  GRADLE_TASKS=()
  for index in "${!MODULE_GRADLE_TASK_PREFIXES[@]}"; do
    tasks+=("${MODULE_GRADLE_TASK_PREFIXES[$index]}$CONFIGURATION")
    GRADLE_TASKS+=("${MODULE_GRADLE_TASK_PREFIXES[$index]}$CONFIGURATION")
  done

  info "Running Gradle tasks for $CONFIGURATION"
  (cd "$REPO_ROOT" && ./gradlew "${tasks[@]}")
}

destination_name_for_module() {
  local module_label="$1"
  local source_path="$2"
  local artifact_prefix
  local module_version

  artifact_prefix="$(module_artifact_prefix_for_label "$module_label")"
  module_version="$(module_version_for_label "$module_label")"

  if [[ "$CONFIGURATION_LOWER" == "debug" ]]; then
    printf '%s-%s-debug.aar' "$artifact_prefix" "$module_version"
    return
  fi

  printf '%s-%s.aar' "$artifact_prefix" "$module_version"
}

copy_aars() {
  local config_output_dir="$1"
  local -a missing_modules=()
  local -a aar_sources=()
  local -a aar_modules=()
  local index

  SOURCE_OUTPUT_DIRS=()
  COPIED_ARTIFACTS=()

  for index in "${!MODULE_LABELS[@]}"; do
    local module_label="${MODULE_LABELS[$index]}"
    local output_dir="$REPO_ROOT/${MODULE_OUTPUT_DIRS[$index]}"
    SOURCE_OUTPUT_DIRS+=("$output_dir")

    if [[ ! -d "$output_dir" ]]; then
      missing_modules+=("$module_label -> $output_dir (output directory missing)")
      continue
    fi

    shopt -s nullglob
    local module_aars=("$output_dir"/*.aar)
    shopt -u nullglob

    if ((${#module_aars[@]} == 0)); then
      missing_modules+=("$module_label -> $output_dir (no .aar files)")
      continue
    fi

    local aar_source
    for aar_source in "${module_aars[@]}"; do
      aar_sources+=("$aar_source")
      aar_modules+=("$module_label")
    done
  done

  if ((${#missing_modules[@]} > 0)); then
    printf 'ERROR: Missing AAR output for %d module(s):\n' "${#missing_modules[@]}" >&2
    local missing
    for missing in "${missing_modules[@]}"; do
      printf '  - %s\n' "$missing" >&2
    done
    exit 1
  fi

  shopt -s nullglob
  local stale_aar_files=("$config_output_dir"/*.aar)
  shopt -u nullglob
  if ((${#stale_aar_files[@]} > 0)); then
    rm -f "${stale_aar_files[@]}"
  fi

  local aar_source
  for index in "${!aar_sources[@]}"; do
    aar_source="${aar_sources[$index]}"
    local module_label="${aar_modules[$index]}"
    local aar_name
    aar_name="$(destination_name_for_module "$module_label" "$aar_source")"

    local destination_path="$config_output_dir/$aar_name"
    cp "$aar_source" "$destination_path"
    info "Copied $module_label -> $aar_name"
    COPIED_ARTIFACTS+=("$destination_path")

  done

}

main() {
  local configuration_input="$DEFAULT_CONFIGURATION"
  local artifacts_base_dir
  local config_output_dir

  while (($# > 0)); do
    case "$1" in
      --help|-h)
        print_usage
        exit 0
        ;;
      --configuration)
        (($# >= 2)) || die "Missing value for --configuration"
        configuration_input="$2"
        shift 2
        ;;
      *)
        print_usage >&2
        die "Unknown argument: $1"
        ;;
    esac
  done

  ensure_command python3
  normalize_configuration "$configuration_input"
  ensure_repo_root
  ensure_opencv_vendor_root

  artifacts_base_dir="$REPO_ROOT/AAR"
  config_output_dir="$artifacts_base_dir"
  safe_prepare_output_dir "$artifacts_base_dir" "$config_output_dir"

  build_modules
  copy_aars "$config_output_dir"

  info "AAR build complete"
  info "AAR files copied to $config_output_dir"
}

main "$@"
