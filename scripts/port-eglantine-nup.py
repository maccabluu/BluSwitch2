#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Port the Eglantine #512 NUP update-executable fix to Eden SX2A Android v0.3.2.

Upstream: https://github.com/Djihads80/Eglantine/pull/512
Android has a different NUP API; this is an adapted port, not a copied patch.
Fail on unknown upstream code instead of silently building an unreviewed change.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: port-eglantine-nup.py <source directory>")
root = Path(sys.argv[1]).resolve() / "src/core"

def patch(name, old, new, desc):
    file = root / name
    source = file.read_text()
    if source.count(old) != 1:
        raise SystemExit(f"{desc}: expected exactly one known upstream anchor, found {source.count(old)}")
    file.write_text(source.replace(old, new, 1))

# Existing Android detection uses main.npdm next to a loose exefs/main.
# For NUP it sees the container instead, and incorrectly chooses Switch 1 clocks/RAM.
old = """// Switch 2 titles boot as a loose ExeFS, with main.npdm next to main.
bool IsSwitch2Program(const FileSys::VirtualFile& file) {
    const auto dir = file != nullptr ? file->GetContainingDirectory() : nullptr;
    const auto npdm = dir != nullptr ? dir->GetFile("main.npdm") : nullptr;
    FileSys::ProgramMetadata metadata;
    return npdm != nullptr && metadata.Load(npdm) == Loader::ResultStatus::Success &&
           metadata.IsSwitch2();
}"""
new = """// NUP platform selection follows the executable selected by the NUP loader.
// Eglantine #512: never let an empty base/exefs hide a valid update/exefs.
bool IsSwitch2Program(const FileSys::VirtualFile& file) {
    if (file == nullptr) {
        return false;
    }
    FileSys::VirtualFile npdm;
    if (Loader::AppLoader_Nup::IdentifyType(file) == Loader::FileType::NUP) {
        FileSys::NUP container{file};
        if (container.GetStatus() != Loader::ResultStatus::Success) {
            return false;
        }
        const FileSys::NupPackageInfo* executable = nullptr;
        const auto updates = container.GetUpdatePackages();
        for (auto it = updates.rbegin(); it != updates.rend(); ++it) {
            const auto* candidate = *it;
            if (candidate->exefs_dir != nullptr &&
                candidate->exefs_dir->GetFile("main") != nullptr) {
                executable = candidate;
                break;
            }
        }
        if (executable == nullptr) {
            const auto* base = container.GetBasePackage();
            if (base != nullptr && base->exefs_dir != nullptr &&
                base->exefs_dir->GetFile("main") != nullptr) {
                executable = base;
            }
        }
        npdm = executable != nullptr && executable->exefs_dir != nullptr
                   ? executable->exefs_dir->GetFile("main.npdm")
                   : nullptr;
    } else {
        const auto dir = file->GetContainingDirectory();
        npdm = dir != nullptr ? dir->GetFile("main.npdm") : nullptr;
    }
    FileSys::ProgramMetadata metadata;
    return npdm != nullptr && metadata.Load(npdm) == Loader::ResultStatus::Success &&
           metadata.IsSwitch2();
}"""
patch("core.cpp", old, new, "Platform detection")
patch("core.cpp", '#include "core/file_sys/patch_manager.h"',
      '#include "core/file_sys/patch_manager.h"\n#include "core/file_sys/nup.h"\n#include "core/loader/nup.h"',
      "NUP detection includes")

# The original NUP runtime only loads base/exefs, even when it has no executable.
old = """    const auto* base = nup->GetBasePackage();
    if (base == nullptr) {
        return;
    }

    title_id = base->title_id;
    title_name = base->title_name;
    publisher = base->publisher;
    display_version = base->display_version;
    icon_data = base->icon_data;
    nacp_data = base->nacp_data;

    // Build RomFS from the package's romfs directory tree
    if (base->romfs_dir) {
        romfs = FileSys::CreateRomFS(base->romfs_dir);
    }

    // Create secondary loader from exefs directory
    if (base->exefs_dir) {
        secondary_loader = std::make_unique<AppLoader_DeconstructedRomDirectory>(
            base->exefs_dir, false);
    }"""
new = """    const auto* base = nup->GetBasePackage();
    // Updates are sorted oldest-first; select the newest with a real main.
    const FileSys::NupPackageInfo* executable = nullptr;
    const auto updates = nup->GetUpdatePackages();
    for (auto it = updates.rbegin(); it != updates.rend(); ++it) {
        const auto* candidate = *it;
        if (candidate->exefs_dir != nullptr &&
            candidate->exefs_dir->GetFile("main") != nullptr) {
            executable = candidate;
            break;
        }
    }
    if (executable == nullptr && base != nullptr && base->exefs_dir != nullptr &&
        base->exefs_dir->GetFile("main") != nullptr) {
        executable = base;
    }
    if (executable == nullptr) {
        return;
    }

    // Use the base for identity/artwork; use the chosen update for executable.
    const auto* info = base != nullptr ? base : executable;
    title_id = info->title_id;
    title_name = info->title_name;
    publisher = info->publisher;
    display_version = executable->display_version.empty()
                          ? info->display_version : executable->display_version;
    icon_data = info->icon_data;
    nacp_data = info->nacp_data;

    if (executable->romfs_dir != nullptr) {
        romfs = FileSys::CreateRomFS(executable->romfs_dir);
    } else if (base != nullptr && base->romfs_dir != nullptr) {
        romfs = FileSys::CreateRomFS(base->romfs_dir);
    }
    secondary_loader = std::make_unique<AppLoader_DeconstructedRomDirectory>(
        executable->exefs_dir, false);"""
patch("loader/nup.cpp", old, new, "NUP runtime executable")

# Library scanner must allow update-only executable packages too.
old = """    // NUP containers are bootable if they have a base package with an exefs
    if (type == FileType::NUP) {
        FileSys::NUP nup{file};
        if (nup.GetStatus() != ResultStatus::Success) {
            return false;
        }
        const auto* base = nup.GetBasePackage();
        return base != nullptr && base->exefs_dir != nullptr;
    }"""
new = """    // NUP updates sometimes contain the only executable. Ignore empty base/exefs.
    if (type == FileType::NUP) {
        FileSys::NUP nup{file};
        if (nup.GetStatus() != ResultStatus::Success) {
            return false;
        }
        const auto updates = nup.GetUpdatePackages();
        for (auto it = updates.rbegin(); it != updates.rend(); ++it) {
            const auto* candidate = *it;
            if (candidate->exefs_dir != nullptr &&
                candidate->exefs_dir->GetFile("main") != nullptr) {
                return true;
            }
        }
        const auto* base = nup.GetBasePackage();
        return base != nullptr && base->exefs_dir != nullptr &&
               base->exefs_dir->GetFile("main") != nullptr;
    }"""
patch("loader/loader.cpp", old, new, "NUP scanner")
print("Applied Android-specific Eglantine #512 NUP update loader and platform detection")
