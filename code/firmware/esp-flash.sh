#!/usr/bin/env bash
# Compile and upload Arduino sketches to the ESP32 using arduino-cli

# CONFIG
BOARD_FQBN="esp32:esp32:esp32s3"

if ! command -v arduino-cli &> /dev/null; then
  echo "Error: arduino-cli not found.  Please install it first."
  exit 1
fi

# FUNCTIONS
usage() {
  echo "Usage: $0 {compile|upload|lsp} sketch_folder [port]"
  echo "  compile: Compile the sketch"
  echo "  upload: Upload the sketch to ESP32 (requires port)"
  echo "  lsp: Generate compile_commands.json for clangd"
  echo "Example:"
  echo "  $0 compile roadster_ctrl"
  echo "  $0 upload roadster_ctrl /dev/cu.usbmodem1101"
}

compile_sketch() {
  local sketch="$1"
  echo "Compiling sketch: $sketch for $BOARD_FQBN"
  arduino-cli compile --fqbn "$BOARD_FQBN" "$sketch" || { echo "Compile failed"; exit 1; }
  echo "Compile successful"
}

upload_sketch() {
  local sketch="$1"
  local port="$2"

  if [ -z "$port" ]; then
     echo "Error: You must specify the port for upload"
     usage
     exit 1
  fi

  echo "Uploading sketch: $sketch to port $port ..."
  arduino-cli upload -p "$port" --fqbn "$BOARD_FQBN" "$sketch" || { echo "Upload failed"; exit 1; }
  echo "Upload successful"
}

lsp_sketch() {
  local sketch="${1%/}"
  local build="$sketch/build"
  echo "Generating compile_commands.json for $sketch"
  arduino-cli compile --fqbn "$BOARD_FQBN" --only-compilation-database --build-path "$build" "$sketch" || { echo "Generate failed"; exit 1; }
  # Point entries at the real sources instead of arduino-cli's copies in the build dir
  local src build_abs
  src="$(cd "$sketch" && pwd)"
  build_abs="$(cd "$build" && pwd)"
  sed -e "s|$build_abs/sketch/\\([^\"]*\\)\\.ino\\.cpp|$src/\\1.ino|g" \
      -e "s|$build_abs/sketch/|$src/|g" \
      "$build/compile_commands.json" > "$sketch/compile_commands.json"
  echo "Wrote $sketch/compile_commands.json"
}

# MAIN
if [ $# -lt 2 ]; then
  usage
  exit 1
fi

COMMAND="$1"
SKETCH="$2"
PORT="$3"

case "$COMMAND" in 
  compile)
    compile_sketch "$SKETCH"
    ;;
  upload)
    upload_sketch "$SKETCH" "$PORT"
    ;;
  lsp)
    lsp_sketch "$SKETCH"
    ;;
  *)
    echo "Error: Unknown command '$COMMAND'"
    usage
    exit 1
    ;;
esac
