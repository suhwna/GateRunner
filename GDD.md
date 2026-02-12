# Gate Runner - Game Design Document (GDD)

## 1. Game Overview
- **Title (working):** Gate Runner
- **Genre:** Vertical auto-runner + choice-based upgrades + dodge/shoot
- **Platform:** Android (Jetpack Compose)
- **Goal:** Clear 3 stages in a single run and defeat the final boss

## 2. Core Fun
- Swipe movement with constant forward motion
- Left/Right gate choices that shape your build
- Item drops that pause the game for upgrade choices
- Stage themes and difficulty scaling

## 3. Core Gameplay Loop
1. Enter stage
2. Gate choice (left/right)
3. Monster segment (1–2 enemies)
4. Gate choice
5. Monster segment repeat
6. Boss appears → defeat
7. Smooth transition to next stage
8. Stage 3 boss defeat = clear

## 4. Controls
- **Input:** Swipe left/right
- **Movement:** Auto forward + lateral movement
- **Attack:** Auto-fire only when enemies are present

## 5. Weapons
- **Types:** Multi-shot / Spread-shot / Homing / Laser
- **First weapon** obtained only at the first gate
- All later gates are **upgrade-only**

## 6. Upgrade System
### 6.1 Gate Upgrades
- Always two choices
- First gate gives weapon type
- After that, only upgrades

### 6.2 Drop Upgrades
- Monster drop chance: **30%**
- 3 unique choices per drop (no duplicates)
- Game pauses during selection

### 6.3 Rarity Distribution
- Normal 60%
- Rare 23.3%
- Advanced 10%
- Epic 3.3%
- Legendary 3.3%

### 6.4 Upgrade Types
- **Base:** Damage / Bullet Count / Attack Speed / Laser Duration
- **Special (Epic+):** Pierce / Burst
- **Extra:** Attack Range (bullet thickness / laser thickness)

### 6.5 Legendary Rules
- Base upgrades get **x2 bonus**
- Pierce/Burst stay **+1 fixed**

## 7. Meta Shop
- Accessible before stage start
- Upgrades: Damage, Attack Speed, Bullet Count, Attack Range, Pierce, Burst
- Costs increase by level
- Coins are persistent between runs

## 8. Stages & Difficulty
- **Stage 1:** Forest
- **Stage 2:** Swamp/Dark Forest
- **Stage 3:** Volcano/Fire

Difficulty scaling:
- Monster HP increases
- Monster count increases
- Boss HP increases
- Movement speed increases

## 9. Enemies & Boss
- **Monsters:** stationary, collision = game over
- **Boss:** stationary wall + projectile attacks
- Boss cannot be bypassed; must be defeated
- HP displayed above monster/boss head

## 10. Drops & Rewards
- **Coin drop ranges:**
  - Stage 1: 1–5
  - Stage 2: 1–20
  - Stage 3: 1–50
- **Item drops:** upgrade selection box

## 11. UI / UX
- Main menu: Title / Start / Exit
- HUD: Stage info (top), weapon/stats (bottom)
- Pause: Resume / Main Menu
- Game Over: Restart / Menu

## 12. Art Direction
- Fantasy arcade style aligned with concept image
- Unified style across character, monsters, boss, gates, and items
- Stage themes must differ in color palette and mood

## 13. Balance Notes (Current)
- Damage upgrade scaling by weapon type:
  - Multi 0.9 / Spread 0.8 / Homing 1.1 / Laser 0.6
- Laser hits apply every 2 frames (reduced frequency)
- Laser duration upgrades are halved
- Spread shots straighten after initial spread
- Multi-shot has a max spread width

## 14. Tech Overview
- **Engine:** Android Jetpack Compose
- **Rendering:** Canvas-based
- **State:** Compose state + mutableStateList
- **Sound:** ToneGenerator (basic SFX)
