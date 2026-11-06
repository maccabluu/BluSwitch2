// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <string>
namespace bluswitch2 {
// Original 3x3 treasure hunt running under the AArch64 guest interpreter.
// The returned state is produced by AArch64 MOVZ/MOVK/LDR/STR/SUB/ADD/CBZ/B.
struct MiniGameState {
    int x{0};
    int y{0};
    int moves{0};
    int won{0};
    unsigned guestInstructions{0};
    std::string error;
};
// Direction: 0=none, 1=up, 2=down, 3=left, 4=right.
MiniGameState stepMiniGame(int x,int y,int moves,int won,int direction);
}
