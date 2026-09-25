#!/bin/bash
set -euo pipefail

initial_repository="$(realpath "$1")"
final_repository="$(realpath "$2")"
repository="$(realpath -m "$3")"
current_version="$4"
previous_version="${5:-}"
source_file=/etc/apt/sources.list.d/normlang.sources
keyring=/usr/share/keyrings/normlang-archive-keyring.asc

test "$(id -u)" -eq 0
test -f cli/compiler/scripts/apt-repository.mjs
test ! -e "$source_file"
test ! -e "$keyring"
test ! -e "$repository"
test "$(dpkg --print-architecture)" = amd64
! id normapt-test >/dev/null 2>&1
! dpkg-query -W -f='${Status}' normlang 2>/dev/null | grep -q 'install ok installed'
node cli/compiler/scripts/release-model.mjs "$current_version" linux-x64 >/dev/null
test -f "$final_repository/pool/main/n/normlang/normlang_${current_version}_amd64.deb"
test -f "$initial_repository/dists/stable/InRelease"
test -f "$final_repository/dists/stable/InRelease"
initial_keyring_package=("$initial_repository"/pool/main/n/normlang-archive-keyring/*.deb)
final_keyring_package=("$final_repository"/pool/main/n/normlang-archive-keyring/*.deb)
test "${#initial_keyring_package[@]}" -eq 1
test "${#final_keyring_package[@]}" -eq 1
test -f "${initial_keyring_package[0]}"
test -f "${final_keyring_package[0]}"
initial_keyring_version="$(dpkg-deb --field "${initial_keyring_package[0]}" Version)"
final_keyring_version="$(dpkg-deb --field "${final_keyring_package[0]}" Version)"
if [[ -n "$previous_version" ]]; then
  node cli/compiler/scripts/release-model.mjs "$previous_version" linux-x64 >/dev/null
  test -f "$initial_repository/pool/main/n/normlang/normlang_${previous_version}_amd64.deb"
  cmp "$initial_repository/pool/main/n/normlang/normlang_${previous_version}_amd64.deb" "$final_repository/pool/main/n/normlang/normlang_${previous_version}_amd64.deb"
  ! grep -q "^Version: $current_version$" "$initial_repository/dists/stable/main/binary-amd64/Packages"
fi

cleanup() {
  status=$?
  set +e
  if [[ -n "${evidence:-}" ]]; then
    if [[ -d /home/normapt-test/lsp-evidence ]]; then cp -aT /home/normapt-test/lsp-evidence "$evidence/lsp"; fi
    if [[ -d /home/normapt-test/worktree/build/reports/native-size ]]; then cp -aT /home/normapt-test/worktree/build/reports/native-size "$evidence/native-size"; fi
    if [[ -d /home/normapt-test/.norm ]]; then
      mkdir -p "$evidence/cache-logs"
      find /home/normapt-test/.norm -type f \( -name '*.log' -o -name '*failure*.json' -o -name '*diagnostic*.json' \) -exec cp --parents {} "$evidence/cache-logs" \;
    fi
  fi
  if [[ -n "${server_pid:-}" ]]; then kill "$server_pid" 2>/dev/null || true; fi
  package_retained=no
  if [[ "${attempted_install:-}" == yes ]] && dpkg-query -W -f='${Status}' normlang 2>/dev/null | grep -q 'install ok installed'; then
    apt-get remove -y normlang > "${evidence:-/tmp}/norm-apt-cleanup.log" 2>&1
    if dpkg-query -W -f='${Status}' normlang 2>/dev/null | grep -q 'install ok installed'; then package_retained=yes; fi
  fi
  if [[ "${attempted_keyring_install:-}" == yes ]] && dpkg-query -W -f='${Status}' normlang-archive-keyring 2>/dev/null | grep -q 'install ok installed'; then
    apt-get remove -y normlang-archive-keyring > "${evidence:-/tmp}/norm-apt-keyring-cleanup.log" 2>&1
    if dpkg-query -W -f='${Status}' normlang-archive-keyring 2>/dev/null | grep -q 'install ok installed'; then package_retained=yes; fi
  fi
  if [[ "$package_retained" == no ]]; then
    if [[ "${created_source:-}" == yes ]]; then rm -f "$source_file"; fi
    if [[ "${created_keyring:-}" == yes ]]; then rm -f "$keyring"; fi
  fi
  if [[ "${created_user:-}" == yes ]]; then userdel -r normapt-test >/dev/null 2>&1 || true; fi
  if [[ -n "${download_test:-}" ]]; then rm -rf -- "$download_test"; fi
  if [[ -n "${package_backup:-}" ]]; then rm -f -- "$package_backup"; fi
  if [[ -n "${tamper_log:-}" ]]; then rm -f -- "$tamper_log"; fi
  if [[ -n "${old_key_dir:-}" ]]; then rm -rf -- "$old_key_dir"; fi
  exit "$status"
}
trap cleanup EXIT

cp -a "$initial_repository" "$repository"

old_key_dir="$(mktemp -d)"
dpkg-deb -x "${initial_keyring_package[0]}" "$old_key_dir"
install -m 644 "$old_key_dir/usr/share/keyrings/normlang-archive-keyring.asc" "$keyring"
created_keyring=yes
if curl --fail --silent --max-time 1 "http://127.0.0.1:18765/dists/stable/InRelease" > /dev/null; then
  echo 'APT acceptance port is already in use' >&2
  exit 1
fi
python3 -m http.server 18765 --bind 127.0.0.1 --directory "$repository" > /tmp/norm-apt-http.log 2>&1 &
server_pid=$!
for attempt in {1..30}; do
  if curl --fail --silent "http://127.0.0.1:18765/dists/stable/InRelease" > /dev/null; then break; fi
  kill -0 "$server_pid"
  sleep 0.2
done
curl --fail --silent "http://127.0.0.1:18765/dists/stable/InRelease" > /dev/null
printf 'Types: deb\nURIs: http://127.0.0.1:18765\nSuites: stable\nComponents: main\nArchitectures: amd64\nSigned-By: %s\n' "$keyring" > "$source_file"
created_source=yes
source_digest="$(sha256sum "$source_file")"
apt-get update
attempted_keyring_install=yes
apt-get install -y normlang-archive-keyring
dpkg-query -S "$keyring" | grep -F 'normlang-archive-keyring:'
test "$(dpkg-query -W -f='${Version}' normlang-archive-keyring)" = "$initial_keyring_version"
cmp "$keyring" "$old_key_dir/usr/share/keyrings/normlang-archive-keyring.asc"
if [[ "${NORM_APT_TAMPER_CHECK:-}" == 1 ]]; then
  indexed_version="${previous_version:-$current_version}"
  package_file="$repository/pool/main/n/normlang/normlang_${indexed_version}_amd64.deb"
  package_backup="$(mktemp)"
  tamper_log="$(mktemp)"
  download_test="$(mktemp -d)"
  cp "$package_file" "$package_backup"
  printf 'tampered\n' >> "$package_file"
  tamper_status=0
  (cd "$download_test" && apt-get download normlang) > "$tamper_log" 2>&1 || tamper_status=$?
  cp "$package_backup" "$package_file"
  cmp "$package_file" "$initial_repository/pool/main/n/normlang/normlang_${indexed_version}_amd64.deb"
  if [[ "$tamper_status" == 0 ]] || ! grep -Eqi 'Hash Sum mismatch|File has unexpected size|Hashes of expected file' "$tamper_log"; then
    cat "$tamper_log" >&2
    echo 'APT did not reject the altered package by hash or size' >&2
    exit 1
  fi
  (cd "$download_test" && apt-get download normlang)
  cmp "$download_test/normlang_${indexed_version}_amd64.deb" "$package_file"
  rm -rf "$download_test"
  rm -f "$package_backup" "$tamper_log"
fi
useradd --create-home --shell /bin/sh normapt-test
created_user=yes
install -d -o normapt-test -g normapt-test /home/normapt-test/project
install -m 644 -o normapt-test -g normapt-test cli/compiler/scripts/fixtures/hello.norm /home/normapt-test/project/hello.norm

attempted_install=yes
apt-get install -y normlang
if [[ -n "$previous_version" ]]; then
  runuser -u normapt-test -- norm --version | grep -Fx "norm $previous_version"
  cp -R "$final_repository/." "$repository/"
  apt-get update
  apt-get upgrade -y
fi
test "$(dpkg-query -W -f='${Version}' normlang-archive-keyring)" = "$final_keyring_version"
cmp "$keyring" "$final_repository/normlang-archive-keyring.asc"
test "$(sha256sum "$source_file")" = "$source_digest"

runuser -u normapt-test -- norm --version | grep -Fx "norm $current_version"
runuser -u normapt-test -- sh -c 'cd /home/normapt-test/project && norm run hello.norm' | grep -Fx 'Hello from Norm'
if [[ "${NORM_APT_FULL_ACCEPTANCE:-}" == 1 ]]; then
  evidence="$(realpath -m "${NORM_APT_EVIDENCE_DIRECTORY:?Full acceptance requires an evidence directory}")"
  [[ "$evidence" != /home/normapt-test && "$evidence" != /home/normapt-test/* ]]
  mkdir -p "$evidence"
  install -d -o normapt-test -g normapt-test /home/normapt-test/worktree/cli/compiler
  cp -a cli/compiler/scripts /home/normapt-test/worktree/cli/compiler/
  chown -R normapt-test:normapt-test /home/normapt-test/worktree
  runuser -u normapt-test -- env JAVA_HOME=/usr/lib/normlang/runtime node /home/normapt-test/worktree/cli/compiler/scripts/verify-lsp.mjs /usr/lib/normlang /home/normapt-test/lsp-evidence /usr/bin/norm 2>&1 | tee "$evidence/lsp.log"
  apt-get install -y default-jdk-headless build-essential zlib1g-dev
  command -v javac >/dev/null
  command -v jar >/dev/null
  runuser -u normapt-test -- node /home/normapt-test/worktree/cli/compiler/scripts/verify-cli.mjs /usr/bin/norm "$current_version" 2>&1 | tee "$evidence/cli-native.log"
fi
test "$(dpkg-query -W -f='${Version}' normlang)" = "$current_version"
apt-get remove -y normlang
test ! -e /usr/bin/norm
test ! -e /usr/lib/normlang
test -f /home/normapt-test/project/hello.norm
test -e "$keyring"
