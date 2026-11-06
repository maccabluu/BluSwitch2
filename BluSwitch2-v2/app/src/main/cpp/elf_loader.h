// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include "engine_core.h"

namespace bluswitch2 {
struct LoadResult {
    bool ok{false};
    uint64_t entry{0};
    std::string error;
};

// Restricted static ELF64/AArch64 ET_EXEC loader for independently written tests.
// No relocations, dynamic libraries, Switch game formats, firmware, or host execution.
LoadResult loadTestElf(const std::vector<uint8_t>& image, GuestMemory& memory);
std::string elfLoaderSelfTest();
}
