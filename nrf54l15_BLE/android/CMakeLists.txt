cmake_minimum_required(VERSION 3.20.0)

# ── Board default (override on CLI: -DBOARD=xiao_nrf54l15 etc.) ───────────────
# nRF54L15 DK:  nrf54l15dk/nrf54l15/cpuapp
# XIAO nRF54L15: xiao_nrf54l15/nrf54l15/cpuapp
# Adjust to your board before building.
if(NOT DEFINED ENV{BOARD})
  set(BOARD xiao_nrf54l15/nrf54l15/cpuapp CACHE STRING "")
endif()

find_package(Zephyr REQUIRED HINTS $ENV{ZEPHYR_BASE})
project(wyatt_nrf54l15)

target_sources(app PRIVATE src/main.c)