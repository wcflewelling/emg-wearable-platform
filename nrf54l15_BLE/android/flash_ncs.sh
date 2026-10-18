#!/usr/bin/env bash
# Flash the NCS-built firmware to the XIAO nRF54L15 via the attached probe.
set -euo pipefail

TOOLCHAIN_ROOT="/opt/nordic/ncs/toolchains/185bb0e3b6"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export PATH="${TOOLCHAIN_ROOT}/bin:${TOOLCHAIN_ROOT}/nrfutil/bin:${PATH}"
export NRFUTIL_HOME="${TOOLCHAIN_ROOT}/nrfutil/home"

cd "${PROJECT_DIR}"
west flash -d "${PROJECT_DIR}/build"
