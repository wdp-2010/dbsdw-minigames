#!/usr/bin/env bash
# Downloads the pinned server and plugin jars for the throwaway e2e servers, checks each
# sha256, and prepares tools/e2e/servers/{survival,minigames,folia}/. Never touches live.
set -euo pipefail

cd "$(dirname "$0")"
JARS=servers/_jars
mkdir -p "$JARS"

# name | url | sha256
PINNED=(
  "paper.jar|https://fill-data.papermc.io/v1/objects/a7116085a8ba88cf00771d5e61a2925007942092650811da7327dc23243f0cec/paper-26.3-255.jar|a7116085a8ba88cf00771d5e61a2925007942092650811da7327dc23243f0cec"
  "folia.jar|https://fill-data.papermc.io/v1/objects/e128879088ba73aa99e218fa6d51fdba3ef2fa3e3c4bc63b834145ac03a243b4/folia-26.3-21.jar|e128879088ba73aa99e218fa6d51fdba3ef2fa3e3c4bc63b834145ac03a243b4"
  "ViaVersion-5.12.2.jar|https://github.com/ViaVersion/ViaVersion/releases/download/5.12.2/ViaVersion-5.12.2.jar|24e4876fefe73e08a4c87cd6c457ee0916e62dd69656631caf41e26f909d504b"
  "ViaBackwards-5.12.2.jar|https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.2/ViaBackwards-5.12.2.jar|9a148a80ea22074f4bd1626cdbe254a4cd998bf44e7ceaff4e285a1b6b35da42"
  "AuthMe-6.0.1-Paper.jar|https://github.com/AuthMe/AuthMeReloaded/releases/download/6.0.1/AuthMe-6.0.1-Paper.jar|7704335e9e73a634d9d926344f77897f4c74f78f82453a59f5aa0f8d2722450a"
  "AuthMe-6.0.1-Folia.jar|https://github.com/AuthMe/AuthMeReloaded/releases/download/6.0.1/AuthMe-6.0.1-Folia.jar|7c5f57fc0ae0f8a522a4d6695c6a54618761c94c859758b7375e5b2ea49eec65"
)

for entry in "${PINNED[@]}"; do
  IFS='|' read -r name url sha <<<"$entry"
  if [[ ! -f "$JARS/$name" ]] || ! echo "$sha  $JARS/$name" | sha256sum -c --status; then
    curl -fsSL -o "$JARS/$name.part" "$url"
    echo "$sha  $JARS/$name.part" | sha256sum -c --status || { echo "sha256 mismatch: $name" >&2; rm -f "$JARS/$name.part"; exit 1; }
    mv "$JARS/$name.part" "$JARS/$name"
  fi
  echo "ok  $sha  $name"
done

prepare() { # <server dir> <authme jar or empty>
  local dir="servers/$1"
  mkdir -p "$dir/plugins"
  echo "eula=true" >"$dir/eula.txt"
  [[ -f "$dir/server.properties" ]] || cat >"$dir/server.properties" <<PROPS
online-mode=false
accepts-transfers=true
white-list=false
difficulty=peaceful
allow-flight=true
enforce-secure-profile=false
server-port=25565
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
motd=dbsdw e2e $1
PROPS
  cp "$JARS"/ViaVersion-5.12.2.jar "$JARS"/ViaBackwards-5.12.2.jar "$dir/plugins/"
  [[ -z "$2" ]] || cp "$JARS/$2" "$dir/plugins/"
}
prepare survival AuthMe-6.0.1-Paper.jar
prepare minigames ""
prepare folia AuthMe-6.0.1-Folia.jar
echo "servers prepared in $(pwd)/servers"
