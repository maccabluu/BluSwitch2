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

A64Cpu::A64Cpu(const GuestMemory& memory) : memory_(memory) {}

void A64Cpu::reset(uint64_t start) {
    x_.fill(0);
    pc_ = start;
    halted_ = false;
    steps_ = 0;
    failure_.clear();
}

bool A64Cpu::step() {
    uint32_t opcode = 0;
    if (!memory_.load32(pc_, opcode)) {
        failure_ = "guest PC outside mapped memory";
        return false;
    }
    const unsigned rd = opcode & 31u;
    const unsigned rn = (opcode >> 5) & 31u;

    // MOVZ Xd, #imm16, LSL #(16*hw)
    if ((opcode & 0xFF800000u) == 0xD2800000u) {
        const uint64_t immediate = (opcode >> 5) & 0xffffu;
        const unsigned hw = (opcode >> 21) & 3u;
        if (rd != 31) x_[rd] = immediate << (hw * 16);
        pc_ += 4;
    } else if ((opcode & 0xFF000000u) == 0x91000000u) {
        // ADD Xd, Xn, #imm12, optionally shifted left 12.
        const unsigned shift = (opcode >> 22) & 3u;
        if (shift > 1) {
            failure_ = "unsupported ADD immediate shift";
            return false;
        }
        // SP and 32-bit variants are not implemented in this interpreter.
        if (rd == 31 || rn == 31) {
            failure_ = "SP register variant is not implemented";
            return false;
        }
        const uint64_t immediate = ((opcode >> 10) & 0xfffu) << (shift ? 12u : 0u);
        x_[rd] = x_[rn] + immediate;
        pc_ += 4;
    } else if (opcode == 0xD503201Fu) {
        // NOP
        pc_ += 4;
    } else if ((opcode & 0xFFFFFC1Fu) == 0xD65F0000u) {
        // RET Xn: 0 is a test-harness return sentinel, not an OS ABI.
        if (rn == 31) {
            failure_ = "invalid return register";
            return false;
        }
        const uint64_t address = x_[rn];
        if (address == 0) halted_ = true;
        else pc_ = address;
    } else {
        std::ostringstream msg;
        msg << "unimplemented AArch64 instruction 0x" << std::hex << opcode;
        failure_ = msg.str();
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
