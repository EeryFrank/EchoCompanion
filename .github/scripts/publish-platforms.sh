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
readonly MR_PROJECT_DESCRIPTION="A client-side Minecraft 1.21.1 scripted/remote AI dialogue mod for Fabric and NeoForge. The mod's gameplay is inspired by Verity; it is an independent, original implementation with no Verity/ARR asset reuse."
readonly MR_INSPIRATION_EN="The mod's gameplay is inspired by Verity."
readonly MR_INSPIRATION_ZH="模组玩法受 Verity 启发。"
readonly LOGO_FILE="${GITHUB_WORKSPACE}/assets/branding/echo-companion-logo-512.png"
readonly PINNED_CHANGELOG_FILE="${GITHUB_WORKSPACE}/.github/release-notes/v0.1.0-alpha.1.md"
readonly PINNED_PROJECT_BODY_FILE="${GITHUB_WORKSPACE}/README.md"

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
[[ -s "$PINNED_PROJECT_BODY_FILE" ]] || fail "Pinned project description is missing"
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
MR_PROJECT_NEEDS_DRAFT_RESET=false
MR_PROJECT_IS_EMPTY_PLACEHOLDER=false
MR_PROJECT_REVIEW_RECOVERY=false
MR_PROJECT_PREFLIGHT_STATE="not requested"
MR_VERSIONS_JSON="$ECHO_WORK/modrinth-versions.json"

modrinth_project_summary() {
  jq -cer \
    '(.versions | type) as $versions_type |
     {project_type, license_id: .license.id, client_side, server_side, status, requested_status,
      project_versions_type: $versions_type,
      project_version_count: (if $versions_type == "array" then (.versions | length) else null end)}' \
    "$1"
}

modrinth_empty_placeholder_state() {
  jq -e \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     .project_type == "project" and
     .client_side == "unknown" and
     .server_side == "unknown" and
     .status == "draft" and
     (.requested_status == null or .requested_status == "draft" or .requested_status == "approved") and
     (.versions | type) == "array" and
     (.versions | length) == 0' \
    "$1" >/dev/null
}

modrinth_stable_empty_draft_state() {
  jq -e \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     .project_type == "project" and
     .client_side == "unknown" and
     .server_side == "unknown" and
     .status == "draft" and
     .requested_status == "draft" and
     (.versions | type) == "array" and
     (.versions | length) == 0' \
    "$1" >/dev/null
}

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
     (.versions | type) == "array" and
     (.status == "draft" or .status == "approved" or .status == "processing")' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project.json")"
    fail "Modrinth project identity or status is not safe for publishing: ${project_summary}"
  fi

  if modrinth_empty_placeholder_state "$ECHO_WORK/modrinth-project.json"; then
    MR_PROJECT_IS_EMPTY_PLACEHOLDER=true
    if modrinth_stable_empty_draft_state "$ECHO_WORK/modrinth-project.json"; then
      MR_PROJECT_PREFLIGHT_STATE="stable empty draft; ready for the first version"
    else
      MR_PROJECT_NEEDS_DRAFT_RESET=true
      MR_PROJECT_PREFLIGHT_STATE="empty draft placeholder; publish will reset the pending review request before the first version"
    fi
  elif jq -e \
    '.project_type == "mod" and
     .client_side == "required" and
     .server_side == "unsupported"' \
    "$ECHO_WORK/modrinth-project.json" >/dev/null; then
    if [[ "$(jq -er '.status' "$ECHO_WORK/modrinth-project.json")" == "processing" ]]; then
      MR_PROJECT_PREFLIGHT_STATE="review processing; exact-version recovery validation required"
    else
      MR_PROJECT_PREFLIGHT_STATE="mod metadata ready"
    fi
  else
    project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project.json")"
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

describe_cf_targets() {
  jq -ncer \
    --slurpfile versions "$ECHO_WORK/curseforge-game-versions.json" \
    --slurpfile types "$ECHO_WORK/curseforge-version-types.json" \
    --argjson names '["1.21.1", "Fabric", "NeoForge", "Client"]' '
      (reduce $types[0][] as $type ({};
        .[($type.id | tostring)] = {name: $type.name, slug: $type.slug})) as $types_by_id
      | [$versions[0][]
         | select(.name as $name | $names | index($name))
         | {
             versionId: .id,
             versionName: .name,
             versionSlug: .slug,
             typeId: .gameVersionTypeID,
             type: ($types_by_id[(.gameVersionTypeID | tostring)] // null)
           }]
      | sort_by(.versionName, .typeId, .versionId)'
}

resolve_cf_type_id() {
  local slug="$1"
  local count diagnostic

  count="$(jq --arg slug "$slug" '[.[] | select(.slug == $slug) | .id] | unique | length' "$ECHO_WORK/curseforge-version-types.json")"
  if [[ "$count" != "1" ]]; then
    diagnostic="$(describe_cf_targets)"
    fail "Expected exactly one CurseForge version type with slug ${slug}, found ${count}; targets=${diagnostic}"
  fi
  jq -er --arg slug "$slug" '[.[] | select(.slug == $slug) | .id] | unique | .[0]' "$ECHO_WORK/curseforge-version-types.json"
}

resolve_cf_version_id() {
  local name="$1"
  local slug="$2"
  local type_id="$3"
  local count diagnostic

  count="$(jq --arg name "$name" --arg slug "$slug" --argjson type_id "$type_id" \
    '[.[] | select(.name == $name and .slug == $slug and .gameVersionTypeID == $type_id) | .id] | unique | length' \
    "$ECHO_WORK/curseforge-game-versions.json")"
  if [[ "$count" != "1" ]]; then
    diagnostic="$(describe_cf_targets)"
    fail "Expected exactly one CurseForge version matching ${name}/${slug} in type ${type_id}, found ${count}; targets=${diagnostic}"
  fi
  jq -er --arg name "$name" --arg slug "$slug" --argjson type_id "$type_id" \
    '[.[] | select(.name == $name and .slug == $slug and .gameVersionTypeID == $type_id) | .id] | unique | .[0]' \
    "$ECHO_WORK/curseforge-game-versions.json"
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
  jq -e '
    type == "array" and length > 0 and
    all(.[];
      (.id | type) == "number" and .id > 0 and
      (.gameVersionTypeID | type) == "number" and .gameVersionTypeID > 0 and
      (.name | type) == "string" and (.name | length) > 0 and
      (.slug | type) == "string" and (.slug | length) > 0) and
    ((map(.id) | length) == (map(.id) | unique | length))' \
    "$ECHO_WORK/curseforge-game-versions.json" >/dev/null || fail "Unexpected CurseForge game-versions response"

  curl -fsSL --retry 3 \
    -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -H "Accept: application/json" \
    "$CF_API/game/version-types" \
    -o "$ECHO_WORK/curseforge-version-types.json" || fail "CurseForge version-types endpoint is required for unambiguous publishing"
  jq -e '
    type == "array" and length > 0 and
    all(.[];
      (.id | type) == "number" and .id > 0 and
      (.name | type) == "string" and (.name | length) > 0 and
      (.slug | type) == "string" and (.slug | length) > 0) and
    ((map(.id) | length) == (map(.id) | unique | length))' \
    "$ECHO_WORK/curseforge-version-types.json" >/dev/null || fail "Unexpected CurseForge version-types response"

  CF_MC_TYPE_ID="$(resolve_cf_type_id 'minecraft-1-21')"
  CF_MODLOADER_TYPE_ID="$(resolve_cf_type_id 'modloader')"
  CF_ENVIRONMENT_TYPE_ID="$(resolve_cf_type_id 'environment')"
  jq -en \
    --argjson minecraft "$CF_MC_TYPE_ID" \
    --argjson modloader "$CF_MODLOADER_TYPE_ID" \
    --argjson environment "$CF_ENVIRONMENT_TYPE_ID" \
    '[$minecraft, $modloader, $environment] | length == (unique | length)' >/dev/null || fail "CurseForge semantic version types are not distinct"

  CF_MC_ID="$(resolve_cf_version_id "$GAME_VERSION" '1-21-1' "$CF_MC_TYPE_ID")"
  CF_FABRIC_ID="$(resolve_cf_version_id 'Fabric' 'fabric' "$CF_MODLOADER_TYPE_ID")"
  CF_NEOFORGE_ID="$(resolve_cf_version_id 'NeoForge' 'neoforge' "$CF_MODLOADER_TYPE_ID")"
  CF_CLIENT_ID="$(resolve_cf_version_id 'Client' 'client' "$CF_ENVIRONMENT_TYPE_ID")"
  jq -en \
    --argjson minecraft "$CF_MC_ID" \
    --argjson fabric "$CF_FABRIC_ID" \
    --argjson neoforge "$CF_NEOFORGE_ID" \
    --argjson client "$CF_CLIENT_ID" \
    '[$minecraft, $fabric, $neoforge, $client] | length == (unique | length)' >/dev/null || fail "CurseForge target version IDs are not distinct"
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
     (($matches[0] | has("environment") | not) or
      $matches[0].environment == "client_only") and
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

modrinth_version_set_is_exact() {
  local fabric_id="${1:-}"
  local neoforge_id="${2:-}"

  jq -e --arg fabric "$fabric_id" --arg neoforge "$neoforge_id" '
    . as $versions |
    ([$fabric, $neoforge] | map(select(length > 0))) as $expected |
    ($versions | length) == ($expected | length) and
    all($expected[]; . as $expected_id | ($versions | any(.id == $expected_id)))' \
    "$MR_VERSIONS_JSON" >/dev/null
}

modrinth_fabric_only_project_state() {
  local project_file="$1"
  local fabric_id="$2"

  jq -e --arg fabric "$fabric_id" \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     .project_type == "mod" and
     .client_side == "required" and
     .server_side == "unsupported" and
     .status == "draft" and
     (.requested_status == null or .requested_status == "draft") and
     (.versions | type) == "array" and
     (.versions | length) == 1 and
     (.versions | index($fabric)) != null' \
    "$project_file" >/dev/null
}

modrinth_after_fabric_poll_state_is_safe() {
  local project_file="$1"
  local fabric_id="$2"

  jq -e --arg fabric "$fabric_id" \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     (.project_type == "project" or .project_type == "mod") and
     ((.client_side == "unknown" and .server_side == "unknown") or
      (.client_side == "required" and .server_side == "unsupported")) and
     .status == "draft" and
     (.requested_status == null or .requested_status == "draft") and
     (.versions | type) == "array" and
     (.versions | length) <= 1 and
     all(.versions[]; . == $fabric)' \
    "$project_file" >/dev/null
}

MR_FABRIC_PREFLIGHT="not requested"
MR_NEOFORGE_PREFLIGHT="not requested"
MR_FABRIC_EXISTING_ID=""
MR_NEOFORGE_EXISTING_ID=""

if $need_modrinth; then
  [[ -f "$LOGO_FILE" ]] || fail "Repository logo file is missing"
  [[ "$(stat -c '%s' "$LOGO_FILE")" == "$EXPECTED_LOGO_SIZE" ]] || fail "Pinned Modrinth logo size mismatch"
  [[ "$(sha256sum "$LOGO_FILE" | cut -d' ' -f1)" == "$EXPECTED_LOGO_SHA256" ]] || fail "Pinned Modrinth logo SHA-256 mismatch"

  if existing_id="$(modrinth_existing_version 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512")"; then
    MR_FABRIC_EXISTING_ID="$existing_id"
    MR_FABRIC_PREFLIGHT="existing exact version ${existing_id}"
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || exit "$existing_status"
    MR_FABRIC_PREFLIGHT="ready to upload"
  fi

  if existing_id="$(modrinth_existing_version 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512")"; then
    MR_NEOFORGE_EXISTING_ID="$existing_id"
    MR_NEOFORGE_PREFLIGHT="existing exact version ${existing_id}"
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || exit "$existing_status"
    MR_NEOFORGE_PREFLIGHT="ready to upload"
  fi

  modrinth_version_set_is_exact "$MR_FABRIC_EXISTING_ID" "$MR_NEOFORGE_EXISTING_ID" ||
    fail "Modrinth project contains versions outside the two audited release targets"

  current_requested_status="$(jq -r '.requested_status // ""' "$ECHO_WORK/modrinth-project.json")"
  if ! $MR_PROJECT_IS_EMPTY_PLACEHOLDER &&
     [[ "$MR_PROJECT_STATUS" == "draft" ]] &&
     [[ -n "$current_requested_status" && "$current_requested_status" != "draft" ]] &&
     [[ -z "$MR_FABRIC_EXISTING_ID" || -z "$MR_NEOFORGE_EXISTING_ID" ]]; then
    fail "Modrinth review is already pending while an audited target version is missing; return the project to draft before publishing"
  fi

  if ! $MR_PROJECT_IS_EMPTY_PLACEHOLDER &&
     [[ "$MR_PROJECT_STATUS" == "processing" ||
        ( "$MR_PROJECT_STATUS" == "draft" && "$current_requested_status" == "approved" ) ]]; then
    [[ -n "$MR_FABRIC_EXISTING_ID" && -n "$MR_NEOFORGE_EXISTING_ID" ]] ||
      fail "Modrinth review is pending without both audited target versions"
    jq -e --arg fabric "$MR_FABRIC_EXISTING_ID" --arg neoforge "$MR_NEOFORGE_EXISTING_ID" \
      '.title == "Echo Companion" and
       .license.id == "MIT" and
       .project_type == "mod" and
       .client_side == "required" and
       .server_side == "unsupported" and
       (.status == "processing" or (.status == "draft" and .requested_status == "approved")) and
       (.versions | type) == "array" and
       (.versions | length) == 2 and
       (.versions | index($fabric)) != null and
       (.versions | index($neoforge)) != null' \
      "$ECHO_WORK/modrinth-project.json" >/dev/null ||
      fail "Modrinth pending-review state does not exactly match the two audited target versions"
    MR_PROJECT_REVIEW_RECOVERY=true
    MR_PROJECT_PREFLIGHT_STATE="review pending; both audited versions exact; Modrinth writes will be skipped"
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
    echo "- Modrinth metadata: \`$(modrinth_project_summary "$ECHO_WORK/modrinth-project.json")\`"
    echo "- Modrinth Fabric: ${MR_FABRIC_PREFLIGHT}"
    echo "- Modrinth NeoForge: ${MR_NEOFORGE_PREFLIGHT}"
  fi
  if $need_curseforge; then
    echo "- CurseForge Minecraft: \`${GAME_VERSION}\` version ID \`${CF_MC_ID}\` (type \`minecraft-1-21\`, ID \`${CF_MC_TYPE_ID}\`)"
    echo "- CurseForge loaders: Fabric \`${CF_FABRIC_ID}\`, NeoForge \`${CF_NEOFORGE_ID}\` (type \`modloader\`, ID \`${CF_MODLOADER_TYPE_ID}\`)"
    echo "- CurseForge environment: Client \`${CF_CLIENT_ID}\` (type \`environment\`, ID \`${CF_ENVIRONMENT_TYPE_ID}\`)"
    echo "- CurseForge: token and exact typed tags verified; project upload permission requires the publish request"
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

wait_for_modrinth_fabric_project_state() {
  local fabric_id="$1"
  local attempt endpoint_version_count project_file project_summary

  project_file="$ECHO_WORK/modrinth-project-after-fabric.json"
  for attempt in {1..12}; do
    curl -fsSL --retry 3 \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Accept: application/json" \
      "$MR_API/project/$mr_project_path" \
      -o "$project_file"
    refresh_modrinth_versions
    endpoint_version_count="$(jq -er 'length' "$MR_VERSIONS_JSON")"
    if ! modrinth_version_set_is_exact "$fabric_id" ""; then
      fail "Modrinth versions changed before NeoForge upload (endpoint_version_count=${endpoint_version_count}; attempt=${attempt})"
    fi
    if modrinth_fabric_only_project_state "$project_file" "$fabric_id"; then
      project_summary="$(modrinth_project_summary "$project_file")"
      echo "Modrinth Fabric-derived state verified: ${project_summary}; endpoint_version_count=${endpoint_version_count}"
      return 0
    fi
    if ! modrinth_after_fabric_poll_state_is_safe "$project_file" "$fabric_id"; then
      project_summary="$(modrinth_project_summary "$project_file")"
      fail "Modrinth project entered an unsafe state after Fabric upload: ${project_summary}; endpoint_version_count=${endpoint_version_count}; attempt=${attempt}"
    fi
    if [[ "$attempt" != "12" ]]; then
      sleep 5
    fi
  done

  project_summary="$(modrinth_project_summary "$project_file")"
  fail "Modrinth project did not derive client-only mod metadata after Fabric upload: ${project_summary}; endpoint_version_count=${endpoint_version_count}; attempts=12"
}

sync_modrinth_project_copy() {
  local body_file payload_file project_file update_http

  body_file="$ECHO_WORK/modrinth-project-body.md"
  payload_file="$ECHO_WORK/modrinth-project-copy.json"
  project_file="$ECHO_WORK/modrinth-project-after-copy.json"

  jq -jnr \
    --rawfile body "$PINNED_PROJECT_BODY_FILE" \
    --arg relative_logo 'src="assets/branding/echo-companion-logo-512.png"' \
    --arg absolute_logo 'src="https://raw.githubusercontent.com/EeryFrank/EchoCompanion/main/assets/branding/echo-companion-logo-512.png"' \
    --arg relative_security '](SECURITY.md)' \
    --arg absolute_security '](https://github.com/EeryFrank/EchoCompanion/blob/main/SECURITY.md)' \
    --arg relative_releasing '](docs/RELEASING.md' \
    --arg absolute_releasing '](https://github.com/EeryFrank/EchoCompanion/blob/main/docs/RELEASING.md' \
    --arg relative_architecture '](docs/ARCHITECTURE.md)' \
    --arg absolute_architecture '](https://github.com/EeryFrank/EchoCompanion/blob/main/docs/ARCHITECTURE.md)' \
    --arg relative_contributing '](.github/CONTRIBUTING.md)' \
    --arg absolute_contributing '](https://github.com/EeryFrank/EchoCompanion/blob/main/.github/CONTRIBUTING.md)' \
    --arg relative_license '](LICENSE)' \
    --arg absolute_license '](https://github.com/EeryFrank/EchoCompanion/blob/main/LICENSE)' '
      def literal_replace($from; $to): split($from) | join($to);
      $body
      | literal_replace($relative_logo; $absolute_logo)
      | literal_replace($relative_security; $absolute_security)
      | literal_replace($relative_releasing; $absolute_releasing)
      | literal_replace($relative_architecture; $absolute_architecture)
      | literal_replace($relative_contributing; $absolute_contributing)
      | literal_replace($relative_license; $absolute_license)' \
    > "$body_file"

  jq -n \
    --arg description "$MR_PROJECT_DESCRIPTION" \
    --rawfile body "$body_file" \
    '{description: $description, body: $body}' \
    > "$payload_file"

  update_http="$(curl -sS \
    -o "$ECHO_WORK/modrinth-project-copy-response.json" \
    -w '%{http_code}' \
    -X PATCH \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Content-Type: application/json" \
    --data-binary "@${payload_file}" \
    "$MR_API/project/$mr_project_path")"
  [[ "$update_http" == "204" ]] || fail "Modrinth project copy update failed (HTTP ${update_http})"

  curl -fsSL --retry 3 \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    "$MR_API/project/$mr_project_path" \
    -o "$project_file"
  jq -e \
    --arg description "$MR_PROJECT_DESCRIPTION" \
    --arg new_en "$MR_INSPIRATION_EN" \
    --arg new_zh "$MR_INSPIRATION_ZH" \
    --rawfile expected_body "$body_file" \
    '.title == "Echo Companion" and
     .license.id == "MIT" and
     .description == $description and
     .body == $expected_body and
     (.body | contains($new_en)) and
     (.body | contains($new_zh))' \
    "$project_file" >/dev/null || fail "Modrinth project copy did not retain the approved inspiration wording"
  if $MR_PROJECT_IS_EMPTY_PLACEHOLDER; then
    modrinth_stable_empty_draft_state "$project_file" ||
      fail "Modrinth empty project state changed during the copy update"
  else
    jq -e \
      '.project_type == "mod" and
       .client_side == "required" and
       .server_side == "unsupported" and
       ((.status == "draft" and (.requested_status == null or .requested_status == "draft")) or
        .status == "approved")' \
      "$project_file" >/dev/null || fail "Modrinth project state changed during the copy update"
  fi
  echo "Modrinth project description synchronized with the approved Verity gameplay-inspiration wording."
}

sync_modrinth_version_changelog() {
  local version_id="$1"
  local display_loader="$2"
  local version_path payload_file version_file update_http

  version_path="$(urlencode "$version_id")"
  payload_file="$ECHO_WORK/modrinth-${display_loader,,}-changelog.json"
  version_file="$ECHO_WORK/modrinth-${display_loader,,}-after-changelog.json"
  jq -n --rawfile changelog "$CHANGELOG_FILE" '{changelog: $changelog}' > "$payload_file"

  update_http="$(curl -sS \
    -o "$ECHO_WORK/modrinth-${display_loader,,}-changelog-response.json" \
    -w '%{http_code}' \
    -X PATCH \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Content-Type: application/json" \
    --data-binary "@${payload_file}" \
    "$MR_API/version/$version_path")"
  [[ "$update_http" == "204" ]] || fail "Modrinth ${display_loader} changelog update failed (HTTP ${update_http})"

  curl -fsSL --retry 3 \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MR_USER_AGENT}" \
    -H "Accept: application/json" \
    "$MR_API/version/$version_path" \
    -o "$version_file"
  jq -e --arg id "$version_id" --rawfile changelog "$CHANGELOG_FILE" \
    '.id == $id and .changelog == $changelog' \
    "$version_file" >/dev/null || fail "Modrinth ${display_loader} changelog verification failed"
  echo "Modrinth ${display_loader} changelog synchronized."
}

if $need_modrinth; then
  if $MR_PROJECT_REVIEW_RECOVERY; then
    mr_project_path="$(urlencode "$MR_PROJECT_RESOLVED_ID")"
    fabric_version_id="$MR_FABRIC_EXISTING_ID"
    neoforge_version_id="$MR_NEOFORGE_EXISTING_ID"

    verify_modrinth_version_id 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512" "$fabric_version_id"
    verify_modrinth_version_id 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512" "$neoforge_version_id"
    refresh_modrinth_versions
    modrinth_version_set_is_exact "$fabric_version_id" "$neoforge_version_id" ||
      fail "Modrinth review recovery version set changed after preflight"

    curl -fsSL --retry 3 \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Accept: application/json" \
      "$MR_API/project/$mr_project_path" \
      -o "$ECHO_WORK/modrinth-project-review-recovery.json"
    jq -e --arg fabric "$fabric_version_id" --arg neoforge "$neoforge_version_id" \
      '.title == "Echo Companion" and
       .license.id == "MIT" and
       .project_type == "mod" and
       .client_side == "required" and
       .server_side == "unsupported" and
       (.status == "processing" or .status == "approved" or
        (.status == "draft" and .requested_status == "approved")) and
       (.versions | type) == "array" and
       (.versions | length) == 2 and
       (.versions | index($fabric)) != null and
       (.versions | index($neoforge)) != null' \
      "$ECHO_WORK/modrinth-project-review-recovery.json" >/dev/null ||
      fail "Modrinth review recovery state changed unexpectedly"
    current_project_status="$(jq -er '.status' "$ECHO_WORK/modrinth-project-review-recovery.json")"
    if [[ "$current_project_status" == "approved" ]]; then
      review_result="project approved; Modrinth writes skipped during recovery"
    elif [[ "$current_project_status" == "processing" ]]; then
      review_result="review processing; Modrinth writes skipped during recovery"
    else
      review_result="approval already requested; Modrinth writes skipped during recovery"
    fi
    project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project-review-recovery.json")"
    echo "Modrinth review recovery verified without writes: ${project_summary}"
  else
  refresh_modrinth_versions
  modrinth_target_is_safe 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512"
  modrinth_target_is_safe 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512"
  modrinth_version_set_is_exact "$MR_FABRIC_EXISTING_ID" "$MR_NEOFORGE_EXISTING_ID" ||
    fail "Modrinth version set changed after preflight; refusing to write"
  if $MR_PROJECT_IS_EMPTY_PLACEHOLDER; then
    jq -e 'length == 0' "$MR_VERSIONS_JSON" >/dev/null || fail "Modrinth placeholder gained versions after preflight; refusing to continue"
  fi

  mr_project_path="$(urlencode "$MR_PROJECT_RESOLVED_ID")"

  if $MR_PROJECT_NEEDS_DRAFT_RESET; then
    draft_reset_http="$(curl -sS \
      -o "$ECHO_WORK/modrinth-draft-reset-response.json" \
      -w '%{http_code}' \
      -X PATCH \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Content-Type: application/json" \
      --data '{"requested_status":"draft"}' \
      "$MR_API/project/$mr_project_path")"
    [[ "$draft_reset_http" == "204" ]] || fail "Modrinth draft review reset failed (HTTP ${draft_reset_http})"

    stable_draft_visible=false
    for attempt in {1..12}; do
      curl -fsSL --retry 3 \
        -H "Authorization: ${MODRINTH_TOKEN}" \
        -H "User-Agent: ${MR_USER_AGENT}" \
        -H "Accept: application/json" \
        "$MR_API/project/$mr_project_path" \
        -o "$ECHO_WORK/modrinth-project-after-draft-reset.json"
      refresh_modrinth_versions
      endpoint_version_count="$(jq -er 'length' "$MR_VERSIONS_JSON")"
      if [[ "$endpoint_version_count" != "0" ]]; then
        fail "Modrinth placeholder gained versions during the draft review reset (endpoint_version_count=${endpoint_version_count})"
      fi
      if modrinth_stable_empty_draft_state "$ECHO_WORK/modrinth-project-after-draft-reset.json"; then
        stable_draft_visible=true
        break
      fi
      if ! modrinth_empty_placeholder_state "$ECHO_WORK/modrinth-project-after-draft-reset.json"; then
        project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project-after-draft-reset.json")"
        fail "Modrinth project entered an unsafe state while resetting review: ${project_summary}; endpoint_version_count=${endpoint_version_count}; attempt=${attempt}"
      fi
      if [[ "$attempt" != "12" ]]; then
        sleep 5
      fi
    done
    if ! $stable_draft_visible; then
      project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project-after-draft-reset.json")"
      fail "Modrinth project did not reach the expected stable empty-draft state: ${project_summary}; endpoint_version_count=${endpoint_version_count}; attempts=12"
    fi
    project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project-after-draft-reset.json")"
    echo "Modrinth project review request reset before the first version: ${project_summary}; endpoint_version_count=${endpoint_version_count}"
  fi

  sync_modrinth_project_copy
  refresh_modrinth_versions
  modrinth_version_set_is_exact "$MR_FABRIC_EXISTING_ID" "$MR_NEOFORGE_EXISTING_ID" ||
    fail "Modrinth version set changed during the project copy update; refusing to continue"

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
  modrinth_version_set_is_exact "$MR_FABRIC_EXISTING_ID" "$MR_NEOFORGE_EXISTING_ID" ||
    fail "Modrinth version set changed after the icon update; refusing to upload"
  if $MR_PROJECT_IS_EMPTY_PLACEHOLDER; then
    curl -fsSL --retry 3 \
      -H "Authorization: ${MODRINTH_TOKEN}" \
      -H "User-Agent: ${MR_USER_AGENT}" \
      -H "Accept: application/json" \
      "$MR_API/project/$mr_project_path" \
      -o "$ECHO_WORK/modrinth-project-before-first-version.json"
    endpoint_version_count="$(jq -er 'length' "$MR_VERSIONS_JSON")"
    if ! modrinth_stable_empty_draft_state "$ECHO_WORK/modrinth-project-before-first-version.json" || [[ "$endpoint_version_count" != "0" ]]; then
      project_summary="$(modrinth_project_summary "$ECHO_WORK/modrinth-project-before-first-version.json")"
      fail "Modrinth placeholder changed before the first version upload: ${project_summary}; endpoint_version_count=${endpoint_version_count}"
    fi
  fi
  fabric_version_id="$(upload_modrinth_version 'fabric' 'Fabric' "$FABRIC_PATH" "$FABRIC_FILE" "$FABRIC_SHA512")"
  echo "Modrinth Fabric version ready: ${fabric_version_id}"
  verify_modrinth_version_id 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512" "$fabric_version_id"
  sync_modrinth_version_changelog "$fabric_version_id" 'Fabric'
  verify_modrinth_version_id 'fabric' "$FABRIC_FILE" "$FABRIC_SHA512" "$fabric_version_id"
  refresh_modrinth_versions
  if neoforge_version_id="$(modrinth_existing_version 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512")"; then
    echo "Modrinth NeoForge version already matches; skipping upload."
  else
    existing_status=$?
    [[ "$existing_status" == "10" ]] || exit "$existing_status"
    wait_for_modrinth_fabric_project_state "$fabric_version_id"
    neoforge_version_id="$(upload_modrinth_version 'neoforge' 'NeoForge' "$NEOFORGE_PATH" "$NEOFORGE_FILE" "$NEOFORGE_SHA512")"
  fi
  echo "Modrinth NeoForge version ready: ${neoforge_version_id}"

  verify_modrinth_version_id 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512" "$neoforge_version_id"
  sync_modrinth_version_changelog "$neoforge_version_id" 'NeoForge'
  verify_modrinth_version_id 'neoforge' "$NEOFORGE_FILE" "$NEOFORGE_SHA512" "$neoforge_version_id"
  refresh_modrinth_versions
  modrinth_version_set_is_exact "$fabric_version_id" "$neoforge_version_id" ||
    fail "Modrinth version set differs from the two audited uploads"

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
     (.versions | type) == "array" and
     (.versions | length) == 2 and
     (.versions | index($fabric)) != null and
     (.versions | index($neoforge)) != null' \
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
  fi

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
