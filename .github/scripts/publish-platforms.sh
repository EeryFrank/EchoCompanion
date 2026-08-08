#!/usr/bin/env bash

set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

readonly EXPECTED_REPOSITORY="EeryFrank/EchoCompanion"
readonly EXPECTED_ACTOR="EeryFrank"
readonly EXPECTED_TAG="v0.1.0-alpha.1"
readonly EXPECTED_RELEASE_ID="367101216"
readonly EXPECTED_TAG_COMMIT="0ab1e0be1ef7ec02d8e23194e04b72b7076c2340"
readonly GAME_VERSION="1.21.1"
readonly EXPECTED_FABRIC_FILE="echo-companion-fabric-1.21.1-0.1.0.jar"
readonly EXPECTED_FABRIC_SHA256="93b929f8dcb6544eb883592153d62b5e8eaee4c9024c036a1a0b89a2244a7421"
readonly EXPECTED_FABRIC_SIZE="73584"
readonly EXPECTED_NEOFORGE_FILE="echo-companion-neoforge-1.21.1-0.1.0.jar"
readonly EXPECTED_NEOFORGE_SHA256="76c3497fa50b2873d41ae7f9c1ec04c820ee5f464ccaeabb822a238be4936d98"
readonly EXPECTED_NEOFORGE_SIZE="74446"
readonly EXPECTED_LOGO_SHA256="188343d5f8a4977c265cf894eedbe27c4fad06db4ec1e9fa30e526952176660d"
readonly EXPECTED_LOGO_SIZE="246485"
readonly MR_API="https://api.modrinth.com/v2"
readonly CF_API="https://minecraft.curseforge.com/api"
readonly MR_USER_AGENT="EeryFrank/EchoCompanion/0.1.0 (https://github.com/EeryFrank/EchoCompanion)"
readonly LOGO_FILE="${GITHUB_WORKSPACE}/assets/branding/echo-companion-logo-512.png"
readonly PINNED_CHANGELOG_FILE="${GITHUB_WORKSPACE}/.github/release-notes/v0.1.0-alpha.1.md"

fail() {
  echo "::error::$*" >&2
  exit 1
}

require_value() {
  local name="$1"
  local value="$2"
  [[ -n "$value" ]] || fail "Required value ${name} is missing"
}

urlencode() {
  jq -rn --arg value "$1" '$value | @uri'
}

cleanup() {
  local runner_temp="${RUNNER_TEMP:-}"
  local echo_work="${ECHO_WORK:-}"
  if [[ -n "$echo_work" && -n "$runner_temp" && "$echo_work" == "$runner_temp"/echo-platform-* ]]; then
    rm -rf -- "$echo_work"
  fi
}

trap cleanup EXIT

[[ "${GITHUB_REPOSITORY:-}" == "$EXPECTED_REPOSITORY" ]] || fail "Unexpected repository"
[[ "${GITHUB_REF:-}" == "refs/heads/main" ]] || fail "Publishing must run from main"
[[ "${GITHUB_ACTOR:-}" == "$EXPECTED_ACTOR" ]] || fail "Publishing is restricted to EeryFrank"

for command_name in cp curl jq sha256sum sha512sum stat unzip; do
  command -v "$command_name" >/dev/null || fail "Missing runner command: ${command_name}"
done

require_value INPUT_MODE "${INPUT_MODE:-}"
require_value INPUT_TAG "${INPUT_TAG:-}"
require_value INPUT_PLATFORM "${INPUT_PLATFORM:-}"
require_value INPUT_CF_LOADER "${INPUT_CF_LOADER:-}"
require_value GH_API_TOKEN "${GH_API_TOKEN:-}"

[[ "$INPUT_MODE" == "preflight" || "$INPUT_MODE" == "publish" ]] || fail "Invalid mode"
[[ "$INPUT_PLATFORM" == "both" || "$INPUT_PLATFORM" == "modrinth" || "$INPUT_PLATFORM" == "curseforge" ]] || fail "Invalid platform"
[[ "$INPUT_CF_LOADER" == "both" || "$INPUT_CF_LOADER" == "fabric" || "$INPUT_CF_LOADER" == "neoforge" ]] || fail "Invalid CurseForge loader"
[[ "$INPUT_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-alpha\.[0-9]+$ ]] || fail "Tag must follow v<version>-alpha.<test>"
[[ "$INPUT_TAG" == "$EXPECTED_TAG" ]] || fail "This audited publisher is pinned to ${EXPECTED_TAG}"

if [[ "$INPUT_MODE" == "publish" && "${INPUT_CONFIRM:-}" != "PUBLISH ${INPUT_TAG}" ]]; then
  fail "Publish confirmation must be exactly: PUBLISH ${INPUT_TAG}"
fi

ECHO_WORK="${RUNNER_TEMP}/echo-platform-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
mkdir -p "$ECHO_WORK"

readonly RELEASE_JSON="$ECHO_WORK/release.json"
readonly CHANGELOG_FILE="$ECHO_WORK/changelog.md"
readonly TAG_REF_JSON="$ECHO_WORK/tag-ref.json"

curl -fsSL --retry 3 \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GH_API_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases/tags/${INPUT_TAG}" \
  -o "$RELEASE_JSON"

jq -e --arg tag "$INPUT_TAG" --arg commit "$EXPECTED_TAG_COMMIT" --argjson release_id "$EXPECTED_RELEASE_ID" \
  '.id == $release_id and
   .tag_name == $tag and
   .target_commitish == $commit and
   .draft == false and
   .prerelease == true' \
  "$RELEASE_JSON" >/dev/null || fail "GitHub prerelease identity does not match the audited release"

encoded_tag="$(urlencode "$INPUT_TAG")"
curl -fsSL --retry 3 \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GH_API_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/${GITHUB_REPOSITORY}/git/ref/tags/${encoded_tag}" \
  -o "$TAG_REF_JSON"
jq -e --arg commit "$EXPECTED_TAG_COMMIT" \
  '.object.type == "commit" and .object.sha == $commit' \
  "$TAG_REF_JSON" >/dev/null || fail "Git tag no longer points to the audited release commit"

[[ -s "$PINNED_CHANGELOG_FILE" ]] || fail "Pinned release notes are missing"
cp -- "$PINNED_CHANGELOG_FILE" "$CHANGELOG_FILE"

select_release_asset() {
  local expected_name="$1"
  local label="$2"
  local count

  count="$(jq --arg name "$expected_name" '[.assets[] | select(.name == $name)] | length' "$RELEASE_JSON")"
  [[ "$count" == "1" ]] || fail "Expected exactly one ${label} release JAR, found ${count}"

  jq -cer --arg name "$expected_name" '.assets[] | select(.name == $name)' "$RELEASE_JSON"
}

download_release_asset() {
  local asset_json="$1"
  local label="$2"
  local known_hash="$3"
  local known_size="$4"
  local name url digest release_size output actual_hash actual_size

  name="$(jq -er '.name' <<<"$asset_json")"
  url="$(jq -er '.browser_download_url' <<<"$asset_json")"
  digest="$(jq -er '.digest' <<<"$asset_json")"
  release_size="$(jq -er '.size' <<<"$asset_json")"

  [[ "$name" != */* ]] || fail "Unsafe ${label} asset name"
  [[ "$digest" == "sha256:${known_hash}" ]] || fail "Pinned SHA-256 digest mismatch for ${label}"
  [[ "$release_size" == "$known_size" ]] || fail "Pinned size mismatch for ${label}"
  [[ "$url" == "https://github.com/${GITHUB_REPOSITORY}/releases/download/${INPUT_TAG}/${name}" ]] || fail "Unexpected ${label} download URL"

  output="$ECHO_WORK/$name"
  curl -fsSL --retry 3 "$url" -o "$output"

  actual_hash="$(sha256sum "$output" | cut -d' ' -f1)"
  actual_size="$(stat -c '%s' "$output")"
  [[ "$actual_hash" == "$known_hash" ]] || fail "SHA-256 mismatch for ${label}"
  [[ "$actual_size" == "$known_size" ]] || fail "Size mismatch for ${label}"

  printf '%s\n' "$output"
}

FABRIC_ASSET_JSON="$(select_release_asset "$EXPECTED_FABRIC_FILE" 'Fabric')"
NEOFORGE_ASSET_JSON="$(select_release_asset "$EXPECTED_NEOFORGE_FILE" 'NeoForge')"
FABRIC_PATH="$(download_release_asset "$FABRIC_ASSET_JSON" 'Fabric' "$EXPECTED_FABRIC_SHA256" "$EXPECTED_FABRIC_SIZE")"
NEOFORGE_PATH="$(download_release_asset "$NEOFORGE_ASSET_JSON" 'NeoForge' "$EXPECTED_NEOFORGE_SHA256" "$EXPECTED_NEOFORGE_SIZE")"
FABRIC_FILE="$(basename "$FABRIC_PATH")"
NEOFORGE_FILE="$(basename "$NEOFORGE_PATH")"
FABRIC_SHA512="$(sha512sum "$FABRIC_PATH" | cut -d' ' -f1)"
NEOFORGE_SHA512="$(sha512sum "$NEOFORGE_PATH" | cut -d' ' -f1)"
readonly FABRIC_ASSET_JSON NEOFORGE_ASSET_JSON FABRIC_PATH NEOFORGE_PATH
readonly FABRIC_FILE NEOFORGE_FILE FABRIC_SHA512 NEOFORGE_SHA512

unzip -Z1 "$FABRIC_PATH" > "$ECHO_WORK/fabric-entries.txt"
grep -Fxq 'fabric.mod.json' "$ECHO_WORK/fabric-entries.txt" || fail "Fabric JAR lacks fabric.mod.json"
unzip -p "$FABRIC_PATH" fabric.mod.json > "$ECHO_WORK/fabric.mod.json"
jq -e \
  '.id == "echo_companion" and .version == "0.1.0" and .environment == "client"' \
  "$ECHO_WORK/fabric.mod.json" >/dev/null || fail "Fabric metadata does not match this release"

unzip -Z1 "$NEOFORGE_PATH" > "$ECHO_WORK/neoforge-entries.txt"
grep -Fxq 'META-INF/neoforge.mods.toml' "$ECHO_WORK/neoforge-entries.txt" || fail "NeoForge JAR lacks neoforge.mods.toml"
NEOFORGE_METADATA="$(unzip -p "$NEOFORGE_PATH" META-INF/neoforge.mods.toml)"
grep -Fq 'modId="echo_companion"' <<<"$NEOFORGE_METADATA" || fail "NeoForge mod ID does not match"
grep -Fq 'version="0.1.0"' <<<"$NEOFORGE_METADATA" || fail "NeoForge version does not match"
grep -Fq 'side="CLIENT"' <<<"$NEOFORGE_METADATA" || fail "NeoForge JAR is not marked client-side"

need_modrinth=false
need_curseforge=false
if [[ "$INPUT_PLATFORM" == "both" || "$INPUT_PLATFORM" == "modrinth" ]]; then
  need_modrinth=true
fi
if [[ "$INPUT_PLATFORM" == "both" || "$INPUT_PLATFORM" == "curseforge" ]]; then
  need_curseforge=true
fi

MR_PROJECT_RESOLVED_ID=""
MR_PROJECT_SLUG=""
MR_PROJECT_STATUS=""
MR_PROJECT_NEEDS_INITIALIZATION=false
MR_PROJECT_IS_EMPTY_PLACEHOLDER=false
MR_PROJECT_PREFLIGHT_STATE="not requested"
MR_VERSIONS_JSON="$ECHO_WORK/modrinth-versions.json"

refresh_modrinth_versions() {
  local resolved_project_path
  resolved_project_path="$(urlencode "$MR_PROJECT_RESOLVED_ID")"
  curl -fsSL --retry 3 \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    "$MR_API/project/$resolved_project_path/version?include_changelog=false" \
    -o "$MR_VERSIONS_JSON"
  jq -e 'type == "array"' "$MR_VERSIONS_JSON" >/dev/null || fail "Unexpected Modrinth versions response"
}

if $need_modrinth; then
  require_value MODRINTH_TOKEN "${MODRINTH_TOKEN:-}"
  require_value MODRINTH_PROJECT_ID "${MODRINTH_PROJECT_ID:-}"

  mr_project_path="$(urlencode "$MODRINTH_PROJECT_ID")"
  curl -fsSL --retry 3 \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    "$MR_API/project/$mr_project_path" \
    -o "$ECHO_WORK/modrinth-project.json"

  if ! jq -e \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     (.status == "draft" or .status == "approved")' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    project_summary="$(jq -cer \
      '{title, project_type, license_id: .license.id, client_side, server_side, status, requested_status, version_count: ((.versions // []) | length)}' \
      "$ECHO_WORK/modrinth-project.json")"
    fail "Modrinth project identity or status is not safe for publishing: ${project_summary}"
  fi

  if jq -e \
    '.project_type == "mod" and
     .client_side == "required" and
     .server_side == "unsupported"' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    MR_PROJECT_PREFLIGHT_STATE="mod metadata ready"
  elif jq -e \
    '.project_type == "project" and
     .client_side == "unknown" and
     .server_side == "unknown" and
     .status == "draft" and
     ((.versions // []) | length) == 0' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    MR_PROJECT_NEEDS_INITIALIZATION=true
    MR_PROJECT_IS_EMPTY_PLACEHOLDER=true
    MR_PROJECT_PREFLIGHT_STATE="empty draft placeholder; publish will set client/server support before uploading"
  elif jq -e \
    '.project_type == "project" and
     .client_side == "required" and
     .server_side == "unsupported" and
     .status == "draft" and
     ((.versions // []) | length) == 0' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    MR_PROJECT_IS_EMPTY_PLACEHOLDER=true
    MR_PROJECT_PREFLIGHT_STATE="initialized empty draft; ready to upload"
  else
    project_summary="$(jq -cer \
      '{title, project_type, license_id: .license.id, client_side, server_side, status, requested_status, version_count: ((.versions // []) | length)}' \
      "$ECHO_WORK/modrinth-project.json")"
    fail "Modrinth project type or environment is not safe for publishing: ${project_summary}"
  fi

  MR_PROJECT_RESOLVED_ID="$(jq -er '.id' "$ECHO_WORK/modrinth-project.json")"
  MR_PROJECT_SLUG="$(jq -er '.slug' "$ECHO_WORK/modrinth-project.json")"
  MR_PROJECT_STATUS="$(jq -er '.status' "$ECHO_WORK/modrinth-project.json")"
  if [[ "$INPUT_MODE" == "publish" ]]; then
    require_value INPUT_MR_SLUG_CONFIRM "${INPUT_MR_SLUG_CONFIRM:-}"
    [[ "$INPUT_MR_SLUG_CONFIRM" == "$MR_PROJECT_SLUG" ]] || fail "Modrinth slug confirmation does not match the configured project"
  fi
  refresh_modrinth_versions
  if $MR_PROJECT_IS_EMPTY_PLACEHOLDER; then
    jq -e 'length == 0' "$MR_VERSIONS_JSON" >/dev/null || fail "Empty Modrinth placeholder unexpectedly has versions"
  fi
fi

CF_MC_ID=""
CF_FABRIC_ID=""
CF_NEOFORGE_ID=""
CF_CLIENT_ID=""
CF_VERSION_TYPES_AVAILABLE=false

describe_cf_candidates() {
  local name="$1"
  local candidates referenced_type_ids referenced_types

  candidates="$(jq -cer --arg name "$name" \
    '[.[] | select(.name == $name) | {id, gameVersionTypeID, name, slug}] | unique_by(.id)' \
    "$ECHO_WORK/curseforge-game-versions.json")"

  if $CF_VERSION_TYPES_AVAILABLE; then
    referenced_type_ids="$(jq -c --arg name "$name" \
      '[.[] | select(.name == $name) | .gameVersionTypeID] | unique' \
      "$ECHO_WORK/curseforge-game-versions.json")"
    referenced_types="$(jq -cer --argjson ids "$referenced_type_ids" \
      '[.[] | select(.id as $id | ($ids | index($id)) != null) | {id, name, slug}] | unique_by(.id)' \
      "$ECHO_WORK/curseforge-version-types.json")"
  else
    referenced_types='unavailable'
  fi

  printf 'candidates=%s; referencedVersionTypes=%s' "$candidates" "$referenced_types"
}

resolve_cf_id() {
  local name="$1"
  local count diagnostic
  count="$(jq --arg name "$name" '[.[] | select(.name == $name) | .id] | unique | length' "$ECHO_WORK/curseforge-game-versions.json")"
  if [[ "$count" != "1" ]]; then
    diagnostic="$(describe_cf_candidates "$name")"
    fail "Expected exactly one CurseForge game-version ID for ${name}, found ${count}; ${diagnostic}"
  fi
  jq -er --arg name "$name" '[.[] | select(.name == $name) | .id] | unique | .[0]' "$ECHO_WORK/curseforge-game-versions.json"
}

if $need_curseforge; then
  require_value CURSEFORGE_TOKEN "${CURSEFORGE_TOKEN:-}"
  require_value CURSEFORGE_PROJECT_ID "${CURSEFORGE_PROJECT_ID:-}"
  [[ "$CURSEFORGE_PROJECT_ID" =~ ^[0-9]+$ ]] || fail "CURSEFORGE_PROJECT_ID must be numeric"
  if [[ "$INPUT_MODE" == "publish" ]]; then
    require_value INPUT_CF_PROJECT_CONFIRM "${INPUT_CF_PROJECT_CONFIRM:-}"
    [[ "$INPUT_CF_PROJECT_CONFIRM" =~ ^[0-9]+$ ]] || fail "CurseForge Project ID confirmation must be numeric"
    [[ "$INPUT_CF_PROJECT_CONFIRM" == "$CURSEFORGE_PROJECT_ID" ]] || fail "CurseForge Project ID confirmation does not match the configured secret"
  fi

  curl -fsSL --retry 3 \
    -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -H "Accept: application/json" \
    "$CF_API/game/versions" \
    -o "$ECHO_WORK/curseforge-game-versions.json"
  jq -e 'type == "array"' "$ECHO_WORK/curseforge-game-versions.json" >/dev/null || fail "Unexpected CurseForge game-versions response"

  if curl -fsSL --retry 3 \
    -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -H "Accept: application/json" \
    "$CF_API/game/version-types" \
    -o "$ECHO_WORK/curseforge-version-types.json"; then
    if jq -e 'type == "array"' "$ECHO_WORK/curseforge-version-types.json" >/dev/null; then
      CF_VERSION_TYPES_AVAILABLE=true
    else
      echo "::warning::CurseForge version-types response was not an array; ambiguous candidates will include type IDs only." >&2
    fi
  else
    echo "::warning::CurseForge version-types endpoint was unavailable; ambiguous candidates will include type IDs only." >&2
  fi

  CF_MC_ID="$(resolve_cf_id "$GAME_VERSION")"
  CF_FABRIC_ID="$(resolve_cf_id 'Fabric')"
  CF_NEOFORGE_ID="$(resolve_cf_id 'NeoForge')"
  CF_CLIENT_ID="$(resolve_cf_id 'Client')"
fi

modrinth_existing_version() {
  local loader="$1"
  local filename="$2"
  local sha512="$3"
  local count display_loader expected_name

  case "$loader" in
    fabric) display_loader="Fabric" ;;
    neoforge) display_loader="NeoForge" ;;
    *) fail "Unsupported Modrinth loader: ${loader}" ;;
  esac
  expected_name="Echo Companion ${INPUT_TAG} (${display_loader})"

  count="$(jq --arg version "$INPUT_TAG" --arg loader "$loader" \
    '[.[] | select(.version_number == $version and (.loaders | index($loader)) != null)] | length' \
    "$MR_VERSIONS_JSON")"

  if [[ "$count" == "0" ]]; then
    return 10
  fi

  [[ "$count" == "1" ]] || fail "Multiple Modrinth ${loader} versions use ${INPUT_TAG}"

  jq -e --arg version "$INPUT_TAG" --arg name "$expected_name" --arg loader "$loader" --arg filename "$filename" --arg hash "$sha512" \
    '[.[] | select(.version_number == $version and (.loaders | index($loader)) != null)] as $matches |
     ($matches | length) == 1 and
     $matches[0].name == $name and
     $matches[0].version_type == "alpha" and
     $matches[0].environment == "client_only" and
     $matches[0].status == "listed" and
     ($matches[0].game_versions | length) == 1 and
     $matches[0].game_versions[0] == "1.21.1" and
     ($matches[0].loaders | length) == 1 and
     $matches[0].loaders[0] == $loader and
     ($matches[0].files | length) == 1 and
     ($matches[0].files | any(.filename == $filename and .hashes.sha512 == $hash and .primary == true))' \
    "$MR_VERSIONS_JSON" >/dev/null || fail "Existing Modrinth ${loader} version differs from the GitHub release"

  jq -er --arg version "$INPUT_TAG" --arg loader "$loader" \
    '.[] | select(.version_number == $version and (.loaders | index($loader)) != null) | .id' \
    "$MR_VERSIONS_JSON"
}

modrinth_target_is_safe() {
  local existing_status
  if modrinth_existing_version "$1" "$2" "$3" >/dev/null; then
    return 0
  else
    existing_status=$?
  fi
  [[ "$existing_status" == "10" ]] || return "$existing_status"
}

MR_FABRIC_PREFLIGHT="not requested"
MR_NEOFORGE_PREFLIGHT="not requested"

if $need_modrinth; then
  [[ -f "$LOGO_FILE" ]] || fail "Repository logo file is missing"
  [[ "$(stat -c '%s' "$LOGO_FILE")" == "$EXPECTED_LOGO_SIZE" ]] || fail "Pinned Modrinth logo size mismatch"
  [[ "$(sha256sum "$LOGO_FILE" | cut -d' ' -f1)" == "$EXPECTED_LOGO_SHA256" ]] || fail "Pinned Modrinth logo SHA-256 mismatch"

  if existing_id="$(modrinth_existing_version 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512")"; then
    MR_FABRIC_PREFLIGHT="existing exact version ${existing_id}"
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || exit "$existing_status"
    MR_FABRIC_PREFLIGHT="ready to upload"
  fi

  if existing_id="$(modrinth_existing_version 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512")"; then
    MR_NEOFORGE_PREFLIGHT="existing exact version ${existing_id}"
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || exit "$existing_status"
    MR_NEOFORGE_PREFLIGHT="ready to upload"
  fi
fi

{
  echo "## Platform publishing preflight"
  echo
  echo "- Mode: \`${INPUT_MODE}\`"
  echo "- GitHub prerelease: \`${INPUT_TAG}\`"
  echo "- Fabric: \`${FABRIC_FILE}\` (pinned SHA-256 and size verified)"
  echo "- NeoForge: \`${NEOFORGE_FILE}\` (pinned SHA-256 and size verified)"
  if $need_modrinth; then
    echo "- Modrinth: \`${MR_PROJECT_SLUG}\` (status: \`${MR_PROJECT_STATUS}\`)"
    echo "- Modrinth project state: ${MR_PROJECT_PREFLIGHT_STATE}"
    echo "- Modrinth Fabric: ${MR_FABRIC_PREFLIGHT}"
    echo "- Modrinth NeoForge: ${MR_NEOFORGE_PREFLIGHT}"
  fi
  if $need_curseforge; then
    echo "- CurseForge: token and required 1.21.1/Fabric/NeoForge/Client tags verified; project upload permission requires the publish request"
  fi
} >> "$GITHUB_STEP_SUMMARY"

if [[ "$INPUT_MODE" == "preflight" ]]; then
  echo "Preflight passed; no platform data was changed."
  exit 0
fi

upload_modrinth_version() {
  local loader="$1"
  local display_loader="$2"
  local file_path="$3"
  local filename="$4"
  local sha512="$5"
  local metadata_file response_file http_code existing_id existing_status

  if existing_id="$(modrinth_existing_version "$loader" "$filename" "$sha512")"; then
    echo "Modrinth ${display_loader} version already matches; skipping upload." >&2
    printf '%s\n' "$existing_id"
    return 0
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || return "$existing_status"
  fi

  metadata_file="$ECHO_WORK/modrinth-${loader}.json"
  response_file="$ECHO_WORK/modrinth-${loader}-response.json"

  jq -n \
    --arg name "Echo Companion ${INPUT_TAG} (${display_loader})" \
    --arg number "$INPUT_TAG" \
    --rawfile changelog "$CHANGELOG_FILE" \
    --arg project "$MR_PROJECT_RESOLVED_ID" \
    --arg loader "$loader" \
    '{
      name: $name,
      version_number: $number,
      changelog: $changelog,
      dependencies: [],
      game_versions: ["1.21.1"],
      version_type: "alpha",
      loaders: [$loader],
      featured: false,
      status: "listed",
      project_id: $project,
      file_parts: ["mod"],
      primary_file: "mod",
      environment: "client_only"
    }' > "$metadata_file"

  http_code="$(curl -sS \
    -o "$response_file" \
    -w '%{http_code}' \
    -X POST \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    -F "data=<${metadata_file};type=application/json" \
    -F "mod=@${file_path};type=application/java-archive" \
    "$MR_API/version")"

  if [[ "$http_code" != "200" ]]; then
    message="$(jq -r '.description // .error // "request failed"' "$response_file" 2>/dev/null || printf 'request failed')"
    fail "Modrinth ${display_loader} upload failed (HTTP ${http_code}): ${message}"
  fi

  jq -er '.id' "$response_file"
}

verify_modrinth_version_id() {
  local loader="$1"
  local filename="$2"
  local sha512="$3"
  local expected_id="$4"
  local attempt actual_id existing_status

  for attempt in 1 2 3; do
    refresh_modrinth_versions
    if actual_id="$(modrinth_existing_version "$loader" "$filename" "$sha512")"; then
      [[ "$actual_id" == "$expected_id" ]] || fail "Modrinth ${loader} write verification returned an unexpected version ID"
      return 0
    else
      existing_status=$?
      [[ "$existing_status" == "10" ]] || return "$existing_status"
    fi
    sleep 2
  done

  fail "Modrinth ${loader} version was not visible after upload"
}

if $need_modrinth; then
  refresh_modrinth_versions
  modrinth_target_is_safe 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512"
  modrinth_target_is_safe 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512"
  if $MR_PROJECT_IS_EMPTY_PLACEHOLDER; then
    jq -e 'length == 0' "$MR_VERSIONS_JSON" >/dev/null || fail "Modrinth placeholder gained versions after preflight; refusing to continue"
  fi

  mr_project_path="$(urlencode "$MR_PROJECT_RESOLVED_ID")"

  if $MR_PROJECT_NEEDS_INITIALIZATION; then
    initialize_http="$(curl -sS \
      -o "$ECHO_WORK/modrinth-initialize-response.json" \
      -w '%{http_code}' \
      -X PATCH \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Content-Type: application/json" \
      --data '{"client_side":"required","server_side":"unsupported"}' \
      "$MR_API/project/$mr_project_path")"
    [[ "$initialize_http" == "204" ]] || fail "Modrinth environment initialization failed (HTTP ${initialize_http})"

    curl -fsSL --retry 3 \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Accept: application/json" \
      "$MR_API/project/$mr_project_path" \
      -o "$ECHO_WORK/modrinth-project-after-initialize.json"
    jq -e \
      '.title == "Echo Companion" and
       .license.id == "MIT" and
       (.project_type == "project" or .project_type == "mod") and
       .client_side == "required" and
       .server_side == "unsupported" and
       .status == "draft" and
       ((.versions // []) | length) == 0' \
      "$ECHO_WORK/modrinth-project-after-initialize.json" >/dev/null || fail "Modrinth project did not retain the expected empty-project state after environment initialization"
    refresh_modrinth_versions
    jq -e 'length == 0' "$MR_VERSIONS_JSON" >/dev/null || fail "Modrinth placeholder gained versions during initialization"
    echo "Modrinth project environment initialized for a client-only mod."
  fi

  icon_http="$(curl -sS \
    -o "$ECHO_WORK/modrinth-icon-response.json" \
    -w '%{http_code}' \
    -X PATCH \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Content-Type: image/png" \
    --data-binary "@${LOGO_FILE}" \
    "$MR_API/project/$mr_project_path/icon?ext=png")"
  [[ "$icon_http" == "204" ]] || fail "Modrinth icon update failed (HTTP ${icon_http})"

  refresh_modrinth_versions
  fabric_version_id="$(upload_modrinth_version 'fabric' 'Fabric' "$FABRIC_PATH" "$FABRIC_FILE" "$FABRIC_SHA512")"
  echo "Modrinth Fabric version ready: ${fabric_version_id}"
  refresh_modrinth_versions
  neoforge_version_id="$(upload_modrinth_version 'neoforge' 'NeoForge' "$NEOFORGE_PATH" "$NEOFORGE_FILE" "$NEOFORGE_SHA512")"
  echo "Modrinth NeoForge version ready: ${neoforge_version_id}"

  verify_modrinth_version_id 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512" "$fabric_version_id"
  verify_modrinth_version_id 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512" "$neoforge_version_id"

  curl -fsSL --retry 3 \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    "$MR_API/project/$mr_project_path" \
    -o "$ECHO_WORK/modrinth-project-before-review.json"
  jq -e --arg fabric "$fabric_version_id" --arg neoforge "$neoforge_version_id" \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     .project_type == "mod" and
     .client_side == "required" and
     .server_side == "unsupported" and
     ((.versions // []) | index($fabric)) != null and
     ((.versions // []) | index($neoforge)) != null' \
    "$ECHO_WORK/modrinth-project-before-review.json" >/dev/null || fail "Modrinth project did not resolve to the expected mod metadata after uploads"
  current_project_status="$(jq -er '.status' "$ECHO_WORK/modrinth-project-before-review.json")"
  current_requested_status="$(jq -r '.requested_status // ""' "$ECHO_WORK/modrinth-project-before-review.json")"

  case "$current_project_status" in
    approved)
      review_result="project already approved"
      ;;
    processing)
      review_result="review already processing"
      ;;
    draft)
      if [[ "$current_requested_status" == "approved" ]]; then
        review_result="approval already requested"
      else
        submit_http="$(curl -sS \
          -o "$ECHO_WORK/modrinth-submit-response.json" \
          -w '%{http_code}' \
          -X PATCH \
          -H "Authorization: ${MODRINTH_TOKEN}" \
          -H "User-Agent: ${MR_USER_AGENT}" \
          -H "Content-Type: application/json" \
          --data '{"requested_status":"approved"}' \
          "$MR_API/project/$mr_project_path")"
        [[ "$submit_http" == "204" ]] || fail "Modrinth review submission failed (HTTP ${submit_http})"
        review_result="approval requested"
      fi
      ;;
    *)
      fail "Modrinth project entered unexpected status before review submission: ${current_project_status}"
      ;;
  esac

  {
    echo
    echo "### Modrinth upload"
    echo
    echo "- Fabric version ID: \`${fabric_version_id}\`"
    echo "- NeoForge version ID: \`${neoforge_version_id}\`"
    echo "- Review: ${review_result}"
    echo "- Project: https://modrinth.com/mod/${MR_PROJECT_SLUG}"
  } >> "$GITHUB_STEP_SUMMARY"
fi

upload_curseforge_file() {
  local loader="$1"
  local display_loader="$2"
  local loader_id="$3"
  local file_path="$4"
  local metadata_file response_file http_code

  metadata_file="$ECHO_WORK/curseforge-${loader}.json"
  response_file="$ECHO_WORK/curseforge-${loader}-response.json"

  jq -n \
    --rawfile changelog "$CHANGELOG_FILE" \
    --arg display_name "Echo Companion ${INPUT_TAG} (${display_loader})" \
    --argjson minecraft "$CF_MC_ID" \
    --argjson loader "$loader_id" \
    --argjson client "$CF_CLIENT_ID" \
    '{
      changelog: $changelog,
      changelogType: "markdown",
      displayName: $display_name,
      gameVersions: [$minecraft, $loader, $client],
      releaseType: "alpha",
      isMarkedForManualRelease: false
    }' > "$metadata_file"

  http_code="$(curl -sS \
    -o "$response_file" \
    -w '%{http_code}' \
    -X POST \
    -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -H "Accept: application/json" \
    -F "metadata=<${metadata_file};type=application/json" \
    -F "file=@${file_path};type=application/java-archive" \
    "$CF_API/projects/$CURSEFORGE_PROJECT_ID/upload-file")"

  if [[ ! "$http_code" =~ ^2[0-9][0-9]$ ]]; then
    message="$(jq -r '.errorMessage // .message // .error // "request failed"' "$response_file" 2>/dev/null || printf 'request failed')"
    fail "CurseForge ${display_loader} upload failed (HTTP ${http_code}): ${message}"
  fi

  jq -er '.id' "$response_file"
}

if $need_curseforge; then
  cf_fabric_id="not requested"
  cf_neoforge_id="not requested"

  {
    echo
    echo "### CurseForge upload"
    echo
    echo "- Recovery rule: before retrying, verify file IDs in the CurseForge Dashboard and select only a missing loader."
  } >> "$GITHUB_STEP_SUMMARY"

  if [[ "$INPUT_CF_LOADER" == "both" || "$INPUT_CF_LOADER" == "fabric" ]]; then
    cf_fabric_id="$(upload_curseforge_file 'fabric' 'Fabric' "$CF_FABRIC_ID" "$FABRIC_PATH")"
    echo "CurseForge Fabric upload succeeded: file ID ${cf_fabric_id}"
    echo "- Fabric file ID: \`${cf_fabric_id}\`" >> "$GITHUB_STEP_SUMMARY"
  else
    echo "- Fabric file: not requested" >> "$GITHUB_STEP_SUMMARY"
  fi

  if [[ "$INPUT_CF_LOADER" == "both" || "$INPUT_CF_LOADER" == "neoforge" ]]; then
    cf_neoforge_id="$(upload_curseforge_file 'neoforge' 'NeoForge' "$CF_NEOFORGE_ID" "$NEOFORGE_PATH")"
    echo "CurseForge NeoForge upload succeeded: file ID ${cf_neoforge_id}"
    echo "- NeoForge file ID: \`${cf_neoforge_id}\`" >> "$GITHUB_STEP_SUMMARY"
  else
    echo "- NeoForge file: not requested" >> "$GITHUB_STEP_SUMMARY"
  fi

  echo "- Files are subject to CurseForge moderation." >> "$GITHUB_STEP_SUMMARY"
fi

echo "Requested platform publishing operations completed."
