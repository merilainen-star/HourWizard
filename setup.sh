#!/bin/bash
# Numbawang setup script

CONFIG_DIR=~/.config/numbawang

if [ -f ~/.config/tuntivelho/credentials ]; then
    echo "Found old Tuntivelho credentials. Migrating to Numbawang..."
    mkdir -p "$CONFIG_DIR"
    cp ~/.config/tuntivelho/credentials "$CONFIG_DIR/credentials"
    # Convert TW_ to NW_
    sed -i 's/TW_USER/NW_USER/g' "$CONFIG_DIR/credentials"
    sed -i 's/TW_PASS/NW_PASS/g' "$CONFIG_DIR/credentials"
    sed -i 's/TW_API_URL/NW_API_URL/g' "$CONFIG_DIR/credentials"
fi
