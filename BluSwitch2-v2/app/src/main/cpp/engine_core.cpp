// SPDX-License-Identifier: GPL-3.0-or-later
#include "engine_core.h"
#include <sstream>

namespace bluswitch2 {
GuestMemory::GuestMemory(uint64_t base, std::size_t size)
    : base_(base), bytes_(size, 0) {}

bool GuestMemory::valid(uint64_t address, std::size_t count) const {
    if (address < base_) return false;
    const uint64_t offset = address - base_;
    return offset <= bytes_.size() && count <= bytes_.size() - offset;
}

bool GuestMemory::store32(uint64_t address, uint32_t value) {
    if (!valid(address, 4) || (address & 3u) != 0) return false;
    const auto offset = static_cast<std::size_t>(address - base_);
    for (unsigned n = 0; n < 4; ++n) {
        bytes_[offset + n] = static_cast<uint8_t>((value >> (n * 8)) & 0xffu);
    }
    return true;
}

bool GuestMemory::load32(uint64_t address, uint32_t& output) const {
    if (!valid(address, 4) || (address & 3u) != 0) return false;
    const auto offset = static_cast<std::size_t>(address - base_);
    output = 0;
    for (unsigned n = 0; n < 4; ++n) {
        output |= static_cast<uint32_t>(bytes_[offset + n]) << (n * 8);
    }
    return true;
}

bool GuestMemory::store64(uint64_t address, uint64_t value) {
    if (!valid(address, 8) || (address & 7u) != 0) return false;
    const auto offset = static_cast<std::size_t>(address - base_);
    for (unsigned i = 0; i < 8; ++i) {
        bytes_[offset + i] = static_cast<uint8_t>(value >> (8 * i));
    }
    return true;
}

bool GuestMemory::load64(uint64_t address, uint64_t& output) const {
    if (!valid(address, 8) || (address & 7u) != 0) return false;
    const auto offset = static_cast<std::size_t>(address - base_);
    output = 0;
    for (unsigned i = 0; i < 8; ++i) {
        output |= uint64_t(bytes_[offset + i]) << (8 * i);
    }
    return true;
}

A64Cpu::A64Cpu(GuestMemory& memory) : memory_(memory) {}

void A64Cpu::reset(uint64_t start) {
    x_.fill(0);
    sp_ = 0;
    pc_ = start;
    halted_ = false;
    steps_ = 0;
    failure_.clear();
}

bool A64Cpu::step() {
    uint32_t op = 0;
    if (!memory_.load32(pc_, op)) {
        failure_ = "guest PC outside mapped memory";
        return false;
    }
    const unsigned rd = op & 31u;
    const unsigned rn = (op >> 5) & 31u;
    const unsigned moveClass = op & 0xFF800000u;
    if (moveClass == 0xD2800000u || moveClass == 0xF2800000u ||
        moveClass == 0x92800000u) {
        // MOVZ, MOVK, MOVN (64-bit forms), with XZR destination discarded.
        const unsigned shift = ((op >> 21) & 3u) * 16u;
        const uint64_t imm = uint64_t((op >> 5) & 0xFFFFu) << shift;
        if (rd != 31) {
            if (moveClass == 0xD2800000u) x_[rd] = imm;
            else if (moveClass == 0x92800000u) x_[rd] = ~imm;
            else x_[rd] = (x_[rd] & ~(uint64_t(0xFFFFu) << shift)) | imm;
        }
        pc_ += 4;
    } else if ((op & 0xFF000000u) == 0x91000000u ||
               (op & 0xFF000000u) == 0xD1000000u) {
        // ADD or SUB (immediate), 64-bit. Register 31 represents SP.
        const unsigned shift = (op >> 22) & 3u;
        if (shift > 1) { failure_ = "invalid arithmetic shift"; return false; }
        const uint64_t imm = uint64_t((op >> 10) & 0xFFFu) << (shift ? 12u : 0u);
        const uint64_t lhs = (rn == 31) ? sp_ : x_[rn];
        const uint64_t val = ((op & 0xFF000000u) == 0x91000000u) ? lhs + imm : lhs - imm;
        if (rd == 31) sp_ = val; else x_[rd] = val;
        pc_ += 4;
    } else if ((op & 0xFFC00000u) == 0xF9400000u ||
               (op & 0xFFC00000u) == 0xF9000000u) {
        // 64-bit unsigned-offset LDR/STR. Enforce a bounded guest memory region.
        const uint64_t base = (rn == 31) ? sp_ : x_[rn];
        const uint64_t offset = uint64_t((op >> 10) & 0xFFFu) * 8u;
        if (base > UINT64_MAX - offset) {
            failure_ = "guest address overflow";
            return false;
        }
        const uint64_t addr = base + offset;
        if ((op & 0xFFC00000u) == 0xF9400000u) {
            uint64_t val = 0;
            if (!memory_.load64(addr, val)) { failure_ = "invalid guest read"; return false; }
            if (rd != 31) x_[rd] = val;
        } else {
            const uint64_t val = (rd == 31) ? 0 : x_[rd];
            if (!memory_.store64(addr, val)) { failure_ = "invalid guest write"; return false; }
        }
        pc_ += 4;
    } else if ((op & 0x7C000000u) == 0x14000000u) {
        // B and BL with signed 26-bit PC-relative displacement.
        int64_t offset = op & 0x03FFFFFFu;
        if (offset & (int64_t(1) << 25)) offset -= int64_t(1) << 26;
        if ((op & 0x80000000u) != 0) x_[30] = pc_ + 4;
        pc_ += static_cast<uint64_t>(offset * 4);
    } else if ((op & 0xFF000000u) == 0xB4000000u ||
               (op & 0xFF000000u) == 0xB5000000u) {
        // CBZ / CBNZ, 64-bit form.
        int64_t offset = (op >> 5) & 0x7FFFFu;
        if (offset & (int64_t(1) << 18)) offset -= int64_t(1) << 19;
        const bool isZero = (rd == 31) ? true : (x_[rd] == 0);
        const bool take = ((op & 0xFF000000u) == 0xB4000000u) ? isZero : !isZero;
        pc_ += take ? static_cast<uint64_t>(offset * 4) : 4;
    } else if (op == 0xD503201Fu) {
        pc_ += 4; // NOP
    } else if ((op & 0xFFFFFC1Fu) == 0xD65F0000u) {
        // Returning to zero is a synthetic-test sentinel, not a Switch 2 ABI.
        if (rn == 31) { failure_ = "invalid return register"; return false; }
        if (x_[rn] == 0) halted_ = true; else pc_ = x_[rn];
    } else {
        std::ostringstream message;
        message << "unimplemented AArch64 opcode 0x" << std::hex << op;
        failure_ = message.str();
        return false;
    }
    ++steps_;
    return true;
}

bool A64Cpu::run(unsigned max_steps) {
    if (max_steps == 0 || max_steps > 100000) {
        failure_ = "invalid instruction limit";
        return false;
    }
    for (unsigned i = 0; i < max_steps && !halted_; ++i) {
        if (!step()) return false;
    }
    if (!halted_) {
        failure_ = "instruction budget reached";
        return false;
    }
    return true;
}

uint64_t A64Cpu::registerValue(unsigned index) const {
    return index < x_.size() ? x_[index] : 0;
}
unsigned A64Cpu::instructions() const { return steps_; }
const std::string& A64Cpu::failure() const { return failure_; }

std::string selfTest() {
    GuestMemory mem(0x1000, 0x2000);
    // Synthetic (not extracted from any game) ARM64 program:
    // MOVZ X0,#35 ; ADD X0,X0,#7 ; RET X30
    if (!mem.store32(0x1000, 0xD2800460u)
            || !mem.store32(0x1004, 0x91001C00u)
            || !mem.store32(0x1008, 0xD65F03C0u)) {
        return "FAIL: guest memory mapping";
    }
    // Protect against an out-of-range guest write.
    if (mem.store32(0x3000, 0xFFFFFFFFu)) {
        return "FAIL: memory boundary check";
    }
    A64Cpu cpu(mem);
    cpu.reset(0x1000);
    if (!cpu.run(16)) return "FAIL: ARM64 decode: " + cpu.failure();
    if (cpu.registerValue(0) != 42 || cpu.instructions() != 3) {
        return "FAIL: wrong ARM64 arithmetic result";
    }
    return "PASS: independent AArch64 MOVZ, ADD, RET and bounds checks. "
           "3 synthetic guest instructions executed, X0 = 42. "
           "This does NOT boot a Switch 2 game or emulate its GPU, OS or firmware.";
}
}
