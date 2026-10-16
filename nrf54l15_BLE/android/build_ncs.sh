#!/usr/bin/env bash
# Build this project using nRF Connect SDK v3.2.4 (SoftDevice Controller)
# instead of upstream Zephyr v4.4.99 (which had a broken BLE SW-split controller
# on nRF54L15 — caused bt_enable() crashes).
set -euo pipefail

NCS_VERSION="v3.2.4"
NCS_ROOT="/opt/nordic/ncs/${NCS_VERSION}"
TOOLCHAIN_ROOT="/opt/nordic/ncs/toolchains/185bb0e3b6"
PROJECT_DIR="/Users/wyattflew7/android"
BOARD="xiao_nrf54l15/nrf54l15/cpuapp"

export PATH="${TOOLCHAIN_ROOT}/bin:${TOOLCHAIN_ROOT}/usr/bin:${TOOLCHAIN_ROOT}/usr/local/bin:${TOOLCHAIN_ROOT}/opt/bin:${TOOLCHAIN_ROOT}/opt/nanopb/generator-bin:${TOOLCHAIN_ROOT}/nrfutil/bin:${TOOLCHAIN_ROOT}/opt/zephyr-sdk/arm-zephyr-eabi/bin:${TOOLCHAIN_ROOT}/opt/zephyr-sdk/riscv64-zephyr-elf/bin:${PATH}"
export ZEPHYR_BASE="${NCS_ROOT}/zephyr"
export ZEPHYR_TOOLCHAIN_VARIANT="zephyr"
export ZEPHYR_SDK_INSTALL_DIR="${TOOLCHAIN_ROOT}/opt/zephyr-sdk"
export NRFUTIL_HOME="${TOOLCHAIN_ROOT}/nrfutil/home"
# Make sure west uses the NCS workspace (which has all modules)
export WEST_CONFIG="${NCS_ROOT}/.west/config"

echo "=== Building with NCS ${NCS_VERSION} ==="
echo "ZEPHYR_BASE=${ZEPHYR_BASE}"
echo "BOARD=${BOARD}"

cd "${NCS_ROOT}"
west build \
  -p always \
  -b "${BOARD}" \
  -d "${PROJECT_DIR}/build" \
  "${PROJECT_DIR}"

echo ""
echo "=== Build complete. To flash: ==="
echo "  ${0%/*}/flash_ncs.sh"
