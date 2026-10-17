#!/usr/bin/env bash

set -e

# ------------------------------------------------------------------
# Morphe-Builds — Termux one-shot builder
# Set REPO_URL to your own GitHub repository (or export MORPHE_BUILDS_REPO).
# ------------------------------------------------------------------
REPO_URL="${MORPHE_BUILDS_REPO:-https://github.com/DARKBOYJYOTI/Morphe-Builds}"
REPO_NAME="Morphe-Builds"
OUT_DIR="/sdcard/Download/Morphe-Builds"

pr() { echo -e "\033[0;32m[+] ${1}\033[0m"; }
ask() {
	local y
	for ((n = 0; n < 3; n++)); do
		pr "$1 [y/n]"
		if read -r y; then
			if [ "$y" = y ]; then
				return 0
			elif [ "$y" = n ]; then
				return 1
			fi
		fi
		pr "Asking again..."
	done
	return 1
}

pr "Ask for storage permission"
until
	yes | termux-setup-storage >/dev/null 2>&1
	ls /sdcard >/dev/null 2>&1
do sleep 1; done
if [ ! -f ~/.morphe_builds_"$(date '+%Y%m')" ]; then
	pr "Setting up environment..."
	yes "" | pkg update -y && pkg upgrade -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" && pkg install -y git curl jq openjdk-21 zip
	: >~/.morphe_builds_"$(date '+%Y%m')"
fi
mkdir -p "$OUT_DIR/"

if [ -d "$REPO_NAME" ] || [ -f config.toml ]; then
	if [ -d "$REPO_NAME" ]; then cd "$REPO_NAME"; fi
	pr "Checking for $REPO_NAME updates"
	git fetch
	if git status | grep -q 'is behind\|fatal'; then
		pr "$REPO_NAME is not synced with upstream."
		pr "Cloning $REPO_NAME. config.toml will be preserved."
		cd ..
		cp -f "$REPO_NAME/config.toml" .
		rm -rf "$REPO_NAME"
		git clone "$REPO_URL" --recurse --depth 1
		mv -f config.toml "$REPO_NAME/config.toml"
		cd "$REPO_NAME"
	fi
else
	pr "Cloning $REPO_NAME."
	git clone "$REPO_URL" --depth 1
	cd "$REPO_NAME"
	sed -i '/^enabled.*/d; /^\[.*\]/a enabled = false' config.toml
	grep -q "$REPO_NAME" ~/.gitconfig 2>/dev/null ||
		git config --global --add safe.directory ~/"$REPO_NAME"
fi

[ -f "$OUT_DIR/config.toml" ] || cp config.toml "$OUT_DIR/config.toml"

if ask "Open the config generator in your browser?"; then
	am start -a android.intent.action.VIEW -d https://j-hc.github.io/rvmm-config-gen/
fi
printf "\n"
until
	if ask "Open 'config.toml' to configure builds?\nAll apps are disabled by default, enable at least one before building"; then
		am start -a android.intent.action.VIEW -d "file://$OUT_DIR/config.toml" -t text/plain
	fi
	ask "Setup is done. Do you want to start building?"
do :; done
cp -f "$OUT_DIR/config.toml" config.toml

./build.sh

cd build
PWD=$(pwd)
for op in *; do
	[ "$op" = "*" ] && {
		pr "glob fail"
		exit 1
	}
	mv -f "${PWD}/${op}" "$OUT_DIR/${op}"
done

pr "Outputs are available in $OUT_DIR folder"
am start -a android.intent.action.VIEW -d "file://$OUT_DIR" -t resource/folder
sleep 2
am start -a android.intent.action.VIEW -d "file://$OUT_DIR" -t resource/folder
