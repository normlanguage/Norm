#!/bin/bash
set -euo pipefail

initial="$(realpath "$1")"
final="$(realpath "$2")"
live="$(realpath -m "$3")"
version="$4"
previous="$5"
fingerprint="$6"
release_upgrade="${7:?Expected release upgrade yes or no}"
user=normrpm-test
port=18766
repo_override=(--setopt="normlang.baseurl=http://127.0.0.1:$port/fedora/44/x86_64")
key=RPM-GPG-KEY-normlang
repo=/etc/yum.repos.d/normlang.repo
evidence=""
if [[ "${NORM_RPM_FULL_ACCEPTANCE:-}" == 1 ]]; then
  evidence="$(realpath -m "${NORM_RPM_EVIDENCE_DIRECTORY:?Full acceptance requires evidence directory}")"
  [[ "$evidence" != "/home/$user" && "$evidence" != "/home/$user/"* ]]
  mkdir -p "$evidence"
  exec > >(tee "$evidence/acceptance.log") 2>&1
fi

test "$(id -u)" -eq 0
grep -qx 'ID=fedora' /etc/os-release
grep -qx 'VERSION_ID=44' /etc/os-release
test "$(rpm --eval '%{_arch}')" = x86_64
test ! -e "$live"
test ! -e "$repo"
! rpm -q normlang >/dev/null 2>&1
! rpm -q normlang-release >/dev/null 2>&1
! id "$user" >/dev/null 2>&1
node cli/compiler/scripts/release-model.mjs "$version" linux-x64 >/dev/null
node cli/compiler/scripts/release-model.mjs "$previous" linux-x64 >/dev/null
[[ "$fingerprint" =~ ^[A-F0-9]{40}$ ]]
test -f "$initial/$key"
test -f "$initial/normlang-release-latest.noarch.rpm"
test -f "$initial/fedora/44/x86_64/repodata/repomd.xml.asc"
test -f "$final/fedora/44/x86_64/repodata/repomd.xml.asc"
cmp "$initial/$key" "$final/$key"
initial_release="$(rpm -qp --qf '%{VERSION}-%{RELEASE}' "$initial/normlang-release-latest.noarch.rpm")"
final_release="$(rpm -qp --qf '%{VERSION}-%{RELEASE}' "$final/normlang-release-latest.noarch.rpm")"
case "$release_upgrade" in
  yes) test "$initial_release" != "$final_release" ;;
  no) test "$initial_release" = "$final_release" ;;
  *) exit 1 ;;
esac
test -f "$initial/fedora/44/x86_64/pool/normlang-$previous-1.x86_64.rpm"
test -f "$final/fedora/44/x86_64/pool/normlang-$version-1.x86_64.rpm"
cmp "$initial/fedora/44/x86_64/pool/normlang-$previous-1.x86_64.rpm" "$final/fedora/44/x86_64/pool/normlang-$previous-1.x86_64.rpm"
actual="$(gpg --show-keys --with-colons "$initial/$key" | awk -F: '$1 == "fpr" { print $10; exit }')"
test "$actual" = "$fingerprint"

cleanup() {
  status=$?
  set +e
  if [[ -n "${evidence:-}" && -d "/home/$user" ]]; then
    if [[ -d "/home/$user/lsp-evidence" ]]; then cp -aT "/home/$user/lsp-evidence" "$evidence/lsp"; fi
    if [[ -d "/home/$user/worktree/build/reports/native-size" ]]; then cp -aT "/home/$user/worktree/build/reports/native-size" "$evidence/native-size"; fi
    if [[ -d "/home/$user/.norm" ]]; then
      mkdir -p "$evidence/cache-logs"
      find "/home/$user/.norm" -type f \( -name '*.log' -o -name '*failure*.json' -o -name '*diagnostic*.json' \) -exec cp --parents {} "$evidence/cache-logs" \;
    fi
  fi
  if [[ -n "${server_pid:-}" ]]; then kill "$server_pid" 2>/dev/null || true; fi
  if [[ "${attempted_install:-}" == yes ]] && rpm -q normlang >/dev/null 2>&1; then dnf -y "${repo_override[@]}" remove normlang > "${evidence:-/tmp}/norm-rpm-cleanup.log" 2>&1; fi
  if [[ "${created_user:-}" == yes ]]; then userdel -r "$user" >/dev/null 2>&1 || true; fi
  exit "$status"
}
trap cleanup EXIT

cp -a "$initial" "$live"
if curl --fail --silent --max-time 1 "http://127.0.0.1:$port/fedora/44/x86_64/repodata/repomd.xml" >/dev/null; then
  echo 'RPM acceptance port is already in use' >&2
  exit 1
fi
python3 -m http.server "$port" --bind 127.0.0.1 --directory "$live" >"${evidence:-/tmp}/norm-rpm-http.log" 2>&1 &
server_pid=$!
for attempt in {1..30}; do
  if curl --fail --silent "http://127.0.0.1:$port/fedora/44/x86_64/repodata/repomd.xml" >/dev/null; then break; fi
  kill -0 "$server_pid"
  sleep 0.2
done
curl --fail --silent "http://127.0.0.1:$port/fedora/44/x86_64/repodata/repomd.xml" >/dev/null

rpmkeys --import "$live/$key"
rpmkeys --define '_pkgverify_level all' --checksig "$live/normlang-release-latest.noarch.rpm"
dnf -y --setopt=localpkg_gpgcheck=1 install "$live/normlang-release-latest.noarch.rpm"
test "$(rpm -q --qf '%{VERSION}-%{RELEASE}' normlang-release)" = "$initial_release"
test -f "$repo"
grep -Fx 'baseurl=https://normlanguage.github.io/rpm/fedora/44/$basearch' "$repo"
grep -Fx 'gpgcheck=1' "$repo"
grep -Fx 'repo_gpgcheck=1' "$repo"
useradd --create-home --shell /bin/bash "$user"
created_user=yes
install -d -o "$user" -g "$user" "/home/$user/project"
install -m 644 -o "$user" -g "$user" cli/compiler/scripts/fixtures/hello.norm "/home/$user/project/hello.norm"
project_hash="$(sha256sum "/home/$user/project/hello.norm" | cut -d' ' -f1)"

attempted_install=yes
dnf -y "${repo_override[@]}" install normlang
runuser -u "$user" -- norm --version | grep -Fx "norm $previous"
cp -a "$final/." "$live/"
dnf clean metadata
dnf -y "${repo_override[@]}" upgrade normlang
runuser -u "$user" -- norm --version | grep -Fx "norm $version"
dnf -y "${repo_override[@]}" upgrade normlang-release
test "$(rpm -q --qf '%{VERSION}-%{RELEASE}' normlang-release)" = "$final_release"
test -f "$repo"
test -f /etc/pki/rpm-gpg/RPM-GPG-KEY-normlang
runuser -u "$user" -- bash -c "cd /home/$user/project && norm run hello.norm" | grep -Fx 'Hello from Norm'

if [[ "${NORM_RPM_FULL_ACCEPTANCE:-}" == 1 ]]; then
  install -d -o "$user" -g "$user" "/home/$user/worktree/cli/compiler"
  cp -a cli/compiler/scripts "/home/$user/worktree/cli/compiler/"
  chown -R "$user:$user" "/home/$user/worktree"
  runuser -u "$user" -- env JAVA_HOME=/usr/lib/normlang/runtime node "/home/$user/worktree/cli/compiler/scripts/verify-lsp.mjs" /usr/lib/normlang "/home/$user/lsp-evidence" /usr/bin/norm 2>&1 | tee "$evidence/lsp.log"
  dnf -y "${repo_override[@]}" install java-25-openjdk-devel gcc zlib-devel
  command -v javac >/dev/null
  command -v jar >/dev/null
  runuser -u "$user" -- node "/home/$user/worktree/cli/compiler/scripts/verify-cli.mjs" /usr/bin/norm "$version" 2>&1 | tee "$evidence/cli-native.log"
fi

test "$(rpm -q --qf '%{VERSION}' normlang)" = "$version"
dnf -y "${repo_override[@]}" remove normlang
test ! -e /usr/bin/norm
test ! -e /usr/lib/normlang
rpm -q normlang-release
test -f "$repo"
test -f /etc/pki/rpm-gpg/RPM-GPG-KEY-normlang
test "$(sha256sum "/home/$user/project/hello.norm" | cut -d' ' -f1)" = "$project_hash"
