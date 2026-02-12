package com.suhwan.gaterunner

import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhwan.gaterunner.ui.theme.GateRunnerTheme
import kotlinx.coroutines.delay
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GateRunnerTheme {
                GameScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GamePreview() {
    GateRunnerTheme {
        GameScreen()
    }
}

private enum class WeaponType(val label: String) {
    MULTI("멀티샷"),
    SPREAD3("부채샷"),
    HOMING("유도"),
    LASER("레이저")
}

private data class WeaponState(
    val type: WeaponType,
    val level: Int,
    val damage: Int,
    val bulletCount: Int,
    val pierce: Int,
    val burstCount: Int,
    val fireRateMs: Long,
    val laserDurationMs: Long,
    val bulletRadius: Float,
    val laserWidth: Float,
    val legendarySplash: Boolean = false,
    val legendarySuperHoming: Boolean = false,
    val legendaryShardLaser: Boolean = false
)

private enum class GateKind { WEAPON, UPGRADE }

private enum class UpgradeType(val label: String) {
    DMG("공격+"),
    COUNT("탄환+"),
    RATE("공속+"),
    LASER_TIME("지속+"),
    RANGE("범위+"),
    PIERCE("관통+"),
    BURST("연속+"),
    LEGENDARY_SPECIAL("특수+")
}

private data class Gate(
    val rect: Rect,
    val kind: GateKind,
    val weapon: WeaponType?,
    val upgrade: UpgradeType?,
    var used: Boolean = false
)

private data class GatePair(
    var left: Gate,
    var right: Gate
)

private data class Monster(
    val id: Int,
    val pos: Offset,
    val radius: Float,
    val hp: Int,
    val ranged: Boolean,
    val shotCooldownMs: Long,
    val rangedShotKind: EnemyShotKind = EnemyShotKind.SPEAR,
    val baseX: Float,
    val zigzagPhase: Float,
    val dashMs: Long,
    val dashCooldownMs: Long,
    val dodgeCooldownMs: Long
)

private data class EnemyShot(
    val pos: Offset,
    val vel: Offset,
    val radius: Float,
    val kind: EnemyShotKind
)

private enum class EnemyShotKind { SPEAR, AXE }

private data class Bullet(
    val pos: Offset,
    val vel: Offset,
    val damage: Int,
    val homing: Boolean,
    val pierceLeft: Int,
    val straightenMs: Long,
    val radius: Float,
    val homingTargetId: Int,
    val homingTargetBoss: Boolean,
    val splashRadius: Float = 0f,
    val splashDamageRatio: Float = 0f,
    val superHoming: Boolean = false
)

private data class Laser(
    var x: Float,
    var lifeMs: Long,
    val damagePerTick: Int,
    val width: Float,
    val followPlayer: Boolean = true
)

private data class ShardRay(
    val start: Offset,
    val end: Offset,
    var lifeMs: Long,
    val damagePerTick: Int,
    val width: Float
)

private data class PendingBurst(
    val fireAt: Long,
    val weapon: WeaponState,
    val x: Float
)

private data class Boss(
    val rect: Rect,
    var hp: Int
)

private enum class BossShotType { NORMAL, BOMB, SIDE_LASER }

private data class BossShot(
    var pos: Offset,
    val vel: Offset,
    val radius: Float,
    val type: BossShotType
)

private data class BossTelegraph(
    val start: Offset,
    val end: Offset,
    var delayMs: Long,
    val vel: Offset,
    val radius: Float,
    val type: BossShotType
)

private data class BossLaneLaser(
    val x: Float,
    val width: Float,
    var lifeMs: Long,
    val totalLifeMs: Long
)

private data class FloatingText(
    val text: String,
    val pos: Offset,
    val color: Color,
    var lifeMs: Long
)

private data class GateBurst(
    val pos: Offset,
    var lifeMs: Long
)

private data class SplashBurst(
    val pos: Offset,
    val radius: Float,
    var lifeMs: Long
)

private data class DeathBurst(
    val pos: Offset,
    var lifeMs: Long,
    val radius: Float
)

private data class MuzzleFlash(
    val pos: Offset,
    var lifeMs: Long
)

private data class HitSpark(
    val pos: Offset,
    var lifeMs: Long
)

private data class Particle(
    val pos: Offset,
    val vel: Offset,
    val color: Color,
    var lifeMs: Long
)

private data class Drop(
    val pos: Offset,
    val kind: DropKind
)

private enum class DropKind { UPGRADE, COIN }

private enum class Rarity(val label: String, val color: Color, val bonus: Int) {
    NORMAL("노말", Color(0xFFB8B8B8), 1),
    RARE("레어", Color(0xFF5BFF88), 2),
    ADVANCED("고급", Color(0xFF3DB7FF), 3),
    EPIC("희귀", Color(0xFFC07CFF), 4),
    LEGENDARY("전설", Color(0xFFFFC35A), 6)
}

private data class UpgradeChoice(
    val upgrade: UpgradeType,
    val rarity: Rarity
)

private data class BgTree(val pos: Offset, val size: Float)
private data class BgBush(val pos: Offset, val size: Float)
private data class BgAnimal(val pos: Offset, val size: Float)
private data class BgPebble(val pos: Offset, val size: Float)
private data class BgStick(val pos: Offset, val size: Float, val angle: Float)
private data class BgRock(val pos: Offset, val size: Float)
private data class BgFog(val pos: Offset, val size: Float)
private data class BgMushroom(val pos: Offset, val size: Float)
private data class BgLavaCrack(val pos: Offset, val size: Float, val angle: Float)
private data class BgAsh(val pos: Offset, val size: Float)
private data class BgStump(val pos: Offset, val size: Float)
private data class BgSkull(val pos: Offset, val size: Float)
private data class BgLavaPillar(val pos: Offset, val size: Float, val height: Float)

private enum class ScreenState { MENU, SHOP, GAME }

private enum class SegmentType { NORMAL, BOSS }

private data class StageTheme(
    val name: String,
    val dirtTop: Color,
    val dirtMid: Color,
    val dirtBot: Color,
    val foliageDark: Color,
    val foliageLight: Color,
    val glow: Color,
    val monsterBase: Color,
    val monsterCore: Color,
    val bossDark: Color,
    val bossMid: Color,
    val bossLight: Color
)

private data class UiPalette(
    val frame: Color = Color(0xFF2A1B12),
    val panel: Color = Color(0xFF6B3C1F),
    val accent: Color = Color(0xFFFFC35A),
    val text: Color = Color(0xFFFFE2A6),
    val glow: Color = Color(0xFFFFE2A6),
    val muted: Color = Color(0xFFB8B8B8)
)

private fun stageTheme(stageIndex: Int): StageTheme {
    return when (stageIndex % 3) {
        0 -> StageTheme(
            name = "FOREST",
            dirtTop = Color(0xFF6C4D2A),
            dirtMid = Color(0xFF8C6033),
            dirtBot = Color(0xFF5A3A1F),
            foliageDark = Color(0xFF2F4A2A),
            foliageLight = Color(0xFF4F6B3A),
            glow = Color(0xFFFFC35A),
            monsterBase = Color(0xFF2C3A1F),
            monsterCore = Color(0xFF7FBF4D),
            bossDark = Color(0xFF3C2818),
            bossMid = Color(0xFFB06B2A),
            bossLight = Color(0xFFFFC35A)
        )
        1 -> StageTheme(
            name = "SWAMP",
            dirtTop = Color(0xFF2E3A2E),
            dirtMid = Color(0xFF3C4A3A),
            dirtBot = Color(0xFF232D23),
            foliageDark = Color(0xFF1B2A1F),
            foliageLight = Color(0xFF365144),
            glow = Color(0xFF79E1B6),
            monsterBase = Color(0xFF1D2B2C),
            monsterCore = Color(0xFF4EA07A),
            bossDark = Color(0xFF1E1A24),
            bossMid = Color(0xFF4C3A5B),
            bossLight = Color(0xFF79E1B6)
        )
        else -> StageTheme(
            name = "VOLCANO",
            dirtTop = Color(0xFF3A1E1E),
            dirtMid = Color(0xFF5B2C24),
            dirtBot = Color(0xFF261414),
            foliageDark = Color(0xFF2B1B12),
            foliageLight = Color(0xFF5B3A24),
            glow = Color(0xFFFF7A3D),
            monsterBase = Color(0xFF3A1B1B),
            monsterCore = Color(0xFFE26B35),
            bossDark = Color(0xFF2B120C),
            bossMid = Color(0xFF9E3E1B),
            bossLight = Color(0xFFFF7A3D)
        )
    }
}

private data class Segment(
    val type: SegmentType,
    val length: Float
)

@Composable
private fun GameScreen() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        var screenState by remember { mutableStateOf(ScreenState.MENU) }
        var running by remember { mutableStateOf(true) }
        var stageIndex by remember { mutableStateOf(0) }
        var segmentIndex by remember { mutableStateOf(0) }
        var scrollY by remember { mutableStateOf(0f) }
        var targetScrollY by remember { mutableStateOf(0f) }
        var playerX by remember { mutableStateOf(width / 2f) }
        var targetPlayerX by remember { mutableStateOf(width / 2f) }
        val playerY = height * 0.80f
        val playerRadius = min(width, height) * 0.04025f
        val pathWidth = width * 0.62f
        val pathLeft = (width - pathWidth) / 2f
        val pathRight = pathLeft + pathWidth
        val ui = UiPalette()

        var weapon by remember { mutableStateOf<WeaponState?>(null) }
        var gatesPassed by remember { mutableStateOf(0) }
        var bossEngaged by remember { mutableStateOf(false) }
        val gatesPerStage = 5

        val gatePairs = remember { mutableStateListOf<GatePair>() }
        val monsters = remember { mutableStateListOf<Monster>() }
        var nextMonsterId by remember { mutableStateOf(0) }
        val enemyShots = remember { mutableStateListOf<EnemyShot>() }
        val bullets = remember { mutableStateListOf<Bullet>() }
        val lasers = remember { mutableStateListOf<Laser>() }
        val shardRays = remember { mutableStateListOf<ShardRay>() }
        var boss by remember { mutableStateOf<Boss?>(null) }
        val bossShots = remember { mutableStateListOf<BossShot>() }
        val bossTelegraphs = remember { mutableStateListOf<BossTelegraph>() }
        val bossLaneLasers = remember { mutableStateListOf<BossLaneLaser>() }
        val floatingTexts = remember { mutableStateListOf<FloatingText>() }
        val gateBursts = remember { mutableStateListOf<GateBurst>() }
        val splashBursts = remember { mutableStateListOf<SplashBurst>() }
        val deathBursts = remember { mutableStateListOf<DeathBurst>() }
        val muzzleFlashes = remember { mutableStateListOf<MuzzleFlash>() }
        val hitSparks = remember { mutableStateListOf<HitSpark>() }
        val particles = remember { mutableStateListOf<Particle>() }
        val maxParticles = 120
        val drops = remember { mutableStateListOf<Drop>() }
        val pendingBursts = remember { mutableStateListOf<PendingBurst>() }
        val laserAccumDamageByMonster = remember { mutableMapOf<Int, Int>() }
        val laserAccumPosByMonster = remember { mutableMapOf<Int, Offset>() }
        var laserAccumBossDamage by remember { mutableStateOf(0) }
        var laserAccumTimerMs by remember { mutableStateOf(0L) }
        var paused by remember { mutableStateOf(false) }
        var upgradeChoices by remember { mutableStateOf<List<UpgradeChoice>>(emptyList()) }
        var manualPaused by remember { mutableStateOf(false) }
        var stageTransitionMs by remember { mutableStateOf(0L) }
        var pendingStageIndex by remember { mutableStateOf(0) }
        var gameTimeMs by remember { mutableStateOf(0L) }
        var bossShotTimerMs by remember { mutableStateOf(0L) }
        var bossPatternCooldownMs by remember { mutableStateOf(420L) }
        var bossVolleyRemaining by remember { mutableStateOf(0) }
        var bossVolleyTimerMs by remember { mutableStateOf(0L) }
        var bossSweepMs by remember { mutableStateOf(0L) }
        var bossSweepX by remember { mutableStateOf(0f) }
        var bossSweepDir by remember { mutableStateOf(1f) }
        var bossPatternIndex by remember { mutableStateOf(0) }
        var bossRewardRemaining by remember { mutableStateOf(0) }
        var loopCount by remember { mutableStateOf(0) }
        var flashMs by remember { mutableStateOf(0L) }
        var coins by remember { mutableStateOf(0) }
        var shopDmgLv by remember { mutableStateOf(0) }
        var shopRateLv by remember { mutableStateOf(0) }
        var shopCountLv by remember { mutableStateOf(0) }
        var shopPierceLv by remember { mutableStateOf(0) }
        var shopBurstLv by remember { mutableStateOf(0) }
        var shopRangeLv by remember { mutableStateOf(0) }
        var shopMsg by remember { mutableStateOf("") }
        var shopMsgMs by remember { mutableStateOf(0L) }
        val bgTrees = remember { mutableStateListOf<BgTree>() }
        val bgBushes = remember { mutableStateListOf<BgBush>() }
        val bgAnimals = remember { mutableStateListOf<BgAnimal>() }
        val bgPebbles = remember { mutableStateListOf<BgPebble>() }
        val bgSticks = remember { mutableStateListOf<BgStick>() }
        val bgRocks = remember { mutableStateListOf<BgRock>() }
        val bgFog = remember { mutableStateListOf<BgFog>() }
        val bgMushrooms = remember { mutableStateListOf<BgMushroom>() }
        val bgLavaCracks = remember { mutableStateListOf<BgLavaCrack>() }
        val bgAsh = remember { mutableStateListOf<BgAsh>() }
        val bgStumps = remember { mutableStateListOf<BgStump>() }
        val bgSkulls = remember { mutableStateListOf<BgSkull>() }
        val bgLavaPillars = remember { mutableStateListOf<BgLavaPillar>() }
        var shakeMs by remember { mutableStateOf(0L) }

        val context = LocalContext.current
        val isPreview = LocalInspectionMode.current
        var toneGen by remember { mutableStateOf<ToneGenerator?>(null) }
        val prefs = remember { context.getSharedPreferences("gaterunner_prefs", android.content.Context.MODE_PRIVATE) }
        val typeface = remember { ResourcesCompat.getFont(context, R.font.galmuri9) }
        val uiPaint = remember(typeface) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 44f
                color = android.graphics.Color.WHITE
                this.typeface = typeface
            }
        }
        val uiStrokePaint = remember(typeface) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 44f
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
                this.typeface = typeface
            }
        }

        LaunchedEffect(isPreview) {
            if (!isPreview) {
                try {
                    toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
                } catch (_: Exception) {
                    toneGen = null
                }
            }
        }

        LaunchedEffect(Unit) {
            coins = prefs.getInt("coins", 0)
            shopDmgLv = prefs.getInt("shop_dmg", 0)
            shopRateLv = prefs.getInt("shop_rate", 0)
            shopCountLv = prefs.getInt("shop_count", 0)
            shopPierceLv = prefs.getInt("shop_pierce", 0)
            shopBurstLv = prefs.getInt("shop_burst", 0)
            shopRangeLv = prefs.getInt("shop_range", 0)
        }

        LaunchedEffect(coins, shopDmgLv, shopRateLv, shopCountLv, shopPierceLv, shopBurstLv, shopRangeLv) {
            prefs.edit()
                .putInt("coins", coins)
                .putInt("shop_dmg", shopDmgLv)
                .putInt("shop_rate", shopRateLv)
                .putInt("shop_count", shopCountLv)
                .putInt("shop_pierce", shopPierceLv)
                .putInt("shop_burst", shopBurstLv)
                .putInt("shop_range", shopRangeLv)
                .apply()
        }

        fun currentSegments(): List<Segment> {
            return listOf(
                Segment(SegmentType.NORMAL, height * 9.0f),
                Segment(SegmentType.BOSS, height * 2.2f)
            )
        }

        // 일반 구간의 게이트와 몬스터를 생성하는 함수
        fun spawnNormalSegmentContent(rng: Random) {
            val stage = stageIndex % 3
            val hpMult = if (loopCount >= 1) 3 else 1
            val hpBonus = (stage * 24 * 1.8f).toInt() * hpMult
            val stride = height * 1.2f
            val gateCount = gatesPerStage
            val laneCount = 2
            val laneWidth = pathWidth / laneCount
            val minMonstersPerBlock = 1
            val maxMonstersPerBlock = 2
            val segmentStartMonsterIndex = monsters.size
            val minRangedPerSegment = when (stage) {
                0 -> 2
                1 -> 4
                else -> 6
            }
            val maxRangedPerSegment = when (stage) {
                0 -> 3
                1 -> 6
                else -> 9
            }
            var rangedSpawned = 0

            val gateYs = ArrayList<Float>(gateCount)
            repeat(gateCount) { i ->
                val cx = (pathLeft + pathRight) / 2f
                val y = -scrollY - height * 0.10f - i * stride
                gateYs.add(y)
                val gateH = height * 0.08f
                val gap = 6f
                val leftRect = Rect(pathLeft, y - gateH, cx - gap, y)
                val rightRect = Rect(cx + gap, y - gateH, pathRight, y)
                val pair = if (weapon == null && i == 0) {
                    val types = WeaponType.values().toList().shuffled(rng)
                    GatePair(
                        Gate(leftRect, GateKind.WEAPON, types[0], null),
                        Gate(rightRect, GateKind.WEAPON, types[1], null)
                    )
                } else {
                    val upgrades = upgradeOptionsFor(weapon ?: defaultWeaponForUpgrades())
                    val leftUp = upgrades[rng.nextInt(upgrades.size)]
                    val rightUp = upgrades[rng.nextInt(upgrades.size)]
                    GatePair(
                        Gate(leftRect, GateKind.UPGRADE, null, leftUp),
                        Gate(rightRect, GateKind.UPGRADE, null, rightUp)
                    )
                }
                gatePairs.add(pair)
            }

            // Spawn monsters between gates with fixed interval fractions (prevents empty gaps).
            val blockFractions = floatArrayOf(0.34f, 0.70f)
            for (i in gateYs.indices) {
                val gateY = gateYs[i]
                blockFractions.forEachIndexed { blockIdx, frac ->
                    val count = if (rng.nextFloat() < 0.62f) minMonstersPerBlock else maxMonstersPerBlock
                    val usedLanes = mutableSetOf<Int>()
                    var prevLane = rng.nextInt(laneCount)
                    repeat(count) { m ->
                        val lane = when (stage) {
                            1 -> (i + m + blockIdx) % laneCount // swamp: alternating lanes
                            2 -> if (m == 0) prevLane else (laneCount - 1 - prevLane) // volcano: forced spread
                            else -> rng.nextInt(laneCount)
                        }
                        if (count == 2 && lane in usedLanes) return@repeat
                        usedLanes.add(lane)
                        prevLane = lane
                        val laneCenter = pathLeft + laneWidth * lane + laneWidth / 2f
                        val jitter = when (stage) {
                            1 -> (laneWidth * 0.12f) * (if (m == 0) -1 else 1)
                            2 -> (laneWidth * 0.16f) * (if (i % 2 == 0) 1 else -1)
                            else -> (laneWidth * 0.08f) * (rng.nextFloat() * 2f - 1f)
                        }
                        val cx = (laneCenter + jitter).coerceIn(pathLeft + laneWidth * 0.2f, pathRight - laneWidth * 0.2f)
                        val y = gateY - stride * frac - m * (height * 0.06f)
                        val r = laneWidth * 0.20f
                        val rangedChance = (0.40f + stage * 0.12f).coerceAtMost(0.80f)
                        val isRanged = (rangedSpawned < maxRangedPerSegment) && (rng.nextFloat() < rangedChance)
                        val hpValue = ((32 * 1.8f).toInt() + hpBonus) * hpMult
                        monsters.add(
                            Monster(
                                id = nextMonsterId++,
                                pos = Offset(cx, y),
                                radius = r,
                                hp = if (isRanged) (hpValue * 0.8f).toInt().coerceAtLeast(1) else hpValue,
                                ranged = isRanged,
                                shotCooldownMs = (900L + rng.nextInt(800) - stage * 120L).coerceAtLeast(420L),
                                rangedShotKind = if (isRanged && rng.nextBoolean()) EnemyShotKind.AXE else EnemyShotKind.SPEAR,
                                baseX = cx,
                                zigzagPhase = rng.nextFloat() * 6.28f,
                                dashMs = 0L,
                                dashCooldownMs = (520L + rng.nextInt(360)).toLong(),
                                dodgeCooldownMs = (460L + rng.nextInt(320)).toLong()
                            )
                        )
                        if (monsters.last().ranged) rangedSpawned += 1
                    }
                }
            }

            // Stage-based minimum ranged count guarantee for clearer difficulty scaling.
            if (rangedSpawned < minRangedPerSegment) {
                val candidates = (segmentStartMonsterIndex until monsters.size)
                    .filter { !monsters[it].ranged }
                    .shuffled(rng)
                val need = minRangedPerSegment - rangedSpawned
                val take = min(need, candidates.size)
                repeat(take) { idx ->
                    val i = candidates[idx]
                    val m = monsters[i]
                    monsters[i] = m.copy(
                        hp = (m.hp * 0.8f).toInt().coerceAtLeast(1),
                        ranged = true,
                        shotCooldownMs = (820L + rng.nextInt(620) - stage * 100L).coerceAtLeast(380L)
                    )
                }
            }
        }

        fun spawnBoss() {
            val stage = stageIndex % 3
            val hpMult = if (loopCount >= 1) 3 else 1
            val bossHp = ((340 + stage * 220) * 1.5f).toInt() * hpMult
            val bossH = height * 0.18f
            // Spawn ahead so it scrolls in naturally
            val bossY = -scrollY - height * 0.2f
            val rect = Rect(pathLeft, bossY - bossH, pathRight, bossY)
            boss = Boss(rect, hp = bossHp)
        }

        fun resetGame() {
            running = true
            stageIndex = 0
            segmentIndex = 0
            scrollY = 0f
            targetScrollY = 0f
            playerX = width / 2f
            targetPlayerX = width / 2f
            weapon = null
            gatesPassed = 0
            bossEngaged = false
            stageTransitionMs = 0L
            pendingStageIndex = 0
            manualPaused = false
            gameTimeMs = 0L
            gatePairs.clear()
            monsters.clear()
            nextMonsterId = 0
            enemyShots.clear()
            bullets.clear()
            lasers.clear()
            shardRays.clear()
            bossShots.clear()
            bossTelegraphs.clear()
            bossLaneLasers.clear()
            boss = null
            floatingTexts.clear()
            gateBursts.clear()
            splashBursts.clear()
            deathBursts.clear()
            muzzleFlashes.clear()
            hitSparks.clear()
            particles.clear()
            drops.clear()
            pendingBursts.clear()
            laserAccumDamageByMonster.clear()
            laserAccumPosByMonster.clear()
            laserAccumBossDamage = 0
            laserAccumTimerMs = 0L
            shakeMs = 0L
            flashMs = 0L
            bossShotTimerMs = 0L
            bossPatternCooldownMs = 420L
            bossVolleyRemaining = 0
            bossVolleyTimerMs = 0L
            bossSweepMs = 0L
            bossSweepX = (pathLeft + pathRight) * 0.5f
            bossSweepDir = 1f
            bossPatternIndex = 0
            bossRewardRemaining = 0
            loopCount = 0
            paused = false
            upgradeChoices = emptyList()
            bgTrees.clear()
            bgBushes.clear()
            bgAnimals.clear()
            bgPebbles.clear()
            bgSticks.clear()
            bgRocks.clear()
            bgFog.clear()
            bgMushrooms.clear()
            bgLavaCracks.clear()
            bgAsh.clear()
            bgStumps.clear()
            bgSkulls.clear()
            bgLavaPillars.clear()
            generateBackground(
                bgTrees, bgBushes, bgAnimals, bgPebbles, bgSticks, bgRocks,
                bgFog, bgMushrooms, bgLavaCracks, bgAsh,
                bgStumps, bgSkulls, bgLavaPillars,
                pathLeft, pathRight, height, stageIndex
            )
            // Spawn initial gates/monsters immediately after reset
            spawnNormalSegmentContent(Random(System.currentTimeMillis()))
        }

        fun advanceStage(nextStage: Int) {
            stageIndex = nextStage
            segmentIndex = 0
            scrollY = 0f
            targetScrollY = 0f
            gatesPassed = 0
            bossEngaged = false
            manualPaused = false
            gameTimeMs = 0L
            bossShotTimerMs = 0L
            bossPatternCooldownMs = 420L
            bossVolleyRemaining = 0
            bossVolleyTimerMs = 0L
            bossSweepMs = 0L
            bossSweepX = (pathLeft + pathRight) * 0.5f
            bossSweepDir = 1f
            bossPatternIndex = 0
            bossRewardRemaining = 0
            gatePairs.clear()
            monsters.clear()
            nextMonsterId = 0
            enemyShots.clear()
            bullets.clear()
            lasers.clear()
            shardRays.clear()
            bossShots.clear()
            bossTelegraphs.clear()
            bossLaneLasers.clear()
            boss = null
            drops.clear()
            pendingBursts.clear()
            laserAccumDamageByMonster.clear()
            laserAccumPosByMonster.clear()
            laserAccumBossDamage = 0
            laserAccumTimerMs = 0L
            floatingTexts.clear()
            gateBursts.clear()
            splashBursts.clear()
            deathBursts.clear()
            muzzleFlashes.clear()
            hitSparks.clear()
            particles.clear()
            bgTrees.clear()
            bgBushes.clear()
            bgAnimals.clear()
            bgPebbles.clear()
            bgSticks.clear()
            bgRocks.clear()
            bgFog.clear()
            bgMushrooms.clear()
            bgLavaCracks.clear()
            bgAsh.clear()
            bgStumps.clear()
            bgSkulls.clear()
            bgLavaPillars.clear()
            generateBackground(
                bgTrees, bgBushes, bgAnimals, bgPebbles, bgSticks, bgRocks,
                bgFog, bgMushrooms, bgLavaCracks, bgAsh,
                bgStumps, bgSkulls, bgLavaPillars,
                pathLeft, pathRight, height, stageIndex
            )
            spawnNormalSegmentContent(Random(System.currentTimeMillis()))
            paused = false
            upgradeChoices = emptyList()
        }

        fun applyMeta(w: WeaponState): WeaponState {
            val shopScale = 0.7f
            val laserSpecialDmg = if (w.type == WeaponType.LASER) ((shopPierceLv + shopBurstLv) * 0.8f).toInt() else 0
            val dmg = w.damage + max(1, (shopDmgLv * weaponDmgScale(w.type) * shopScale).toInt()) + laserSpecialDmg
            val count = w.bulletCount + max(0, (shopCountLv * shopScale).toInt())
            val pierce = w.pierce + max(0, (shopPierceLv * shopScale).toInt())
            val burst = w.burstCount + max(0, (shopBurstLv * shopScale).toInt())
            val range = w.bulletRadius
            val laserW = if (w.type == WeaponType.LASER) {
                w.laserWidth + shopRangeLv * 3.2f * shopScale
            } else {
                w.laserWidth
            }
            val laserSpecialRate = if (w.type == WeaponType.LASER) (shopBurstLv * 6f * shopScale).toLong() else 0L
            val rate = if (w.type == WeaponType.LASER) {
                max(420L, w.fireRateMs - (shopRateLv * 10f * shopScale).toLong() - laserSpecialRate)
            } else {
                max(180L, w.fireRateMs - (shopRateLv * 10f * shopScale).toLong())
            }
            return normalizeWeaponStats(w.copy(
                damage = dmg,
                bulletCount = count,
                pierce = pierce,
                burstCount = burst,
                fireRateMs = rate,
                bulletRadius = range,
                laserWidth = laserW
            ))
        }

        // Run-time upgrades are 60% effective via applyGate/applyUpgradeChoice scaling.

        fun shopCost(base: Int, level: Int): Int = (base * (level + 1) * (1.0 + level * 0.35)).toInt()

        val addParticles: (Offset, Color) -> Unit = { pos, color ->
            if (particles.size < maxParticles) {
                val burst = spawnBurst(pos, color)
                val room = maxParticles - particles.size
                if (burst.size <= room) {
                    particles.addAll(burst)
                } else {
                    particles.addAll(burst.take(room))
                }
            }
        }

        fun fireWeapon(w: WeaponState, x: Float) {
            when (w.type) {
                WeaponType.LASER -> {
                    lasers.add(Laser(x, w.laserDurationMs, w.damage, w.laserWidth, followPlayer = true))
                    if (w.legendaryShardLaser && Random.Default.nextFloat() < 0.20f) {
                        // Branch rays inherit upgraded laser stats (damage/width/duration).
                        val shardDmg = max(1, (w.damage * 0.55f + w.level * 0.15f).toInt())
                        val shardWidth = max(10f, w.laserWidth * 0.60f)
                        val shardLife = max(220L, (w.laserDurationMs * 0.70f).toLong())
                        repeat(6) {
                            val sx = Random.Default.nextFloat() * width
                            val sy = Random.Default.nextFloat() * playerY
                            val angleDeg = Random.Default.nextFloat() * 360f
                            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
                            val len = max(width, height) * 2.2f
                            val dx = cos(rad) * len
                            val dy = sin(rad) * len
                            shardRays.add(
                                ShardRay(
                                    start = Offset(sx - dx, sy - dy),
                                    end = Offset(sx + dx, sy + dy),
                                    lifeMs = shardLife,
                                    damagePerTick = shardDmg,
                                    width = shardWidth
                                )
                            )
                        }
                    }
                    muzzleFlashes.add(MuzzleFlash(Offset(x, playerY - playerRadius * 0.8f), 120L))
                    addParticles(Offset(x, playerY - playerRadius), Color(0xFFFFE2A6))
                }
                WeaponType.SPREAD3 -> {
                    val count = max(3, w.bulletCount)
                    val maxHalfAngle = (22f + (count - 3) * 4.2f).coerceAtMost(68f)
                    val mid = (count - 1) / 2f
                    for (i in 0 until count) {
                        val t = if (mid == 0f) 0f else (i - mid) / mid
                        val angle = t * maxHalfAngle
                        val rad = Math.toRadians(angle.toDouble()).toFloat()
                        val speed = 11f
                        val vx = sin(rad) * speed
                        val vy = -cos(rad) * speed
                        bullets.add(Bullet(
                            Offset(x, playerY),
                            Offset(vx, vy),
                            (w.damage * 0.85f).toInt().coerceAtLeast(1),
                            false,
                            w.pierce,
                            300L,
                            w.bulletRadius * 1.15f,
                            -1,
                            false,
                            splashRadius = if (w.legendarySplash) max(104f, w.bulletRadius * 19.2f) else 0f,
                            splashDamageRatio = if (w.legendarySplash) 0.55f else 0f
                        ))
                    }
                    muzzleFlashes.add(MuzzleFlash(Offset(x, playerY - playerRadius * 0.8f), 120L))
                    addParticles(Offset(x, playerY - playerRadius), Color(0xFFFFE2A6))
                }
                WeaponType.MULTI -> {
                    val count = w.bulletCount
                    val baseSpread = 14f + (count - 1) * 2f
                    val maxHalfWidth = 70f
                    val spread = if (count <= 1) 0f else min(baseSpread, (maxHalfWidth * 2f) / (count - 1))
                    for (i in 0 until count) {
                        val offset = (i - (count - 1) / 2f) * spread
                        bullets.add(Bullet(
                            Offset(x + offset, playerY),
                            Offset(0f, -14f),
                            (w.damage * 1.1f).toInt().coerceAtLeast(1),
                            false,
                            w.pierce,
                            0L,
                            w.bulletRadius * 0.9f,
                            -1,
                            false,
                            splashRadius = if (w.legendarySplash) max(96f, w.bulletRadius * 18.0f) else 0f,
                            splashDamageRatio = if (w.legendarySplash) 0.5f else 0f
                        ))
                    }
                    muzzleFlashes.add(MuzzleFlash(Offset(x, playerY - playerRadius * 0.8f), 120L))
                    addParticles(Offset(x, playerY - playerRadius), Color(0xFFFFE2A6))
                }
                WeaponType.HOMING -> {
                    val count = max(1, w.bulletCount)
                    val spread = 12f + (count - 1) * 2f
                    for (i in 0 until count) {
                        val offset = (i - (count - 1) / 2f) * spread
                        bullets.add(Bullet(
                            Offset(x + offset, playerY),
                            Offset(0f, -11f),
                            w.damage,
                            true,
                            w.pierce,
                            0L,
                            w.bulletRadius,
                            -1,
                            false,
                            superHoming = w.legendarySuperHoming
                        ))
                    }
                    muzzleFlashes.add(MuzzleFlash(Offset(x, playerY - playerRadius * 0.8f), 120L))
                    addParticles(Offset(x, playerY - playerRadius), Color(0xFFFFE2A6))
                }
            }
        }



        LaunchedEffect(width, height) {
            resetGame()
        }

        LaunchedEffect(running) {
            val rng = Random(System.currentTimeMillis())
            var fireTimerMs = 0L
            while (true) {
                if (screenState == ScreenState.GAME && running) {
                    val dt = 16L
                    if (stageTransitionMs > 0L) {
                        stageTransitionMs -= dt
                        if (stageTransitionMs <= 0L) {
                            stageTransitionMs = 0L
                        if (pendingStageIndex >= 3) {
                            loopCount = 1
                            advanceStage(0)
                        } else {
                            advanceStage(pendingStageIndex)
                        }
                        }
                        delay(16L)
                        continue
                    }
                    if (manualPaused || paused) {
                        delay(16L)
                        continue
                    }
                    gameTimeMs += dt
                    if (shopMsgMs > 0L) {
                        shopMsgMs -= dt
                        if (shopMsgMs <= 0L) {
                            shopMsgMs = 0L
                            shopMsg = ""
                        }
                    }
                    val speed = 5.2f + stageIndex * 1.2f
                    val bossScreen = boss?.rect?.shiftByScroll(scrollY)
                    val stopLine = playerY - playerRadius - 20f
                    // Always auto-scroll; boss is an obstacle wall.
                    targetScrollY += speed
                    scrollY = lerp(scrollY, targetScrollY, 0.25f)
                    playerX = lerp(playerX, targetPlayerX, 0.25f)

                    // Resolve pending burst shots
                    if (pendingBursts.isNotEmpty()) {
                        val fired = mutableListOf<Int>()
                        for (i in pendingBursts.indices) {
                            val pb = pendingBursts[i]
                            if (pb.fireAt <= gameTimeMs) {
                                fireWeapon(pb.weapon, pb.x)
                                fired.add(i)
                            }
                        }
                        fired.sortedDescending().forEach { pendingBursts.removeAt(it) }
                    }

                    // Boss projectiles + patterns
                    val currentBossForShots = boss
                    if (currentBossForShots != null) {
                        val stage = stageIndex % 3
                        val volleyInterval = if (stage == 0) 320L else if (stage == 1) 300L else 280L
                        val shotSpeed = 18.75f + stage * 2.25f
                        val bombSpeed = 8.0f + stage * 1.0f
                        val spawnY = currentBossForShots.rect.shiftByScroll(scrollY).bottom - 10f
                        val centerX = (pathLeft + pathRight) * 0.5f
                        val warnDelay = 300L
                        val laneWarnDelay = 720L

                        if (bossPatternCooldownMs > 0L) bossPatternCooldownMs -= dt
                        if (bossVolleyRemaining > 0) {
                            bossVolleyTimerMs -= dt
                            if (bossVolleyTimerMs <= 0L) {
                                bossVolleyTimerMs = volleyInterval
                                val angles = if (stage == 2) listOf(-16f, 0f, 16f) else listOf(-12f, 0f, 12f)
                                val aimX = playerX
                                val aimY = playerY
                                val dx = aimX - centerX
                                val dy = aimY - spawnY
                                val baseLen = max(1f, hypot(dx, dy))
                                val dirX = dx / baseLen
                                val dirY = dy / baseLen
                                val perpX = -dirY
                                val perpY = dirX
                                angles.forEach { a ->
                                    val rad = Math.toRadians(a.toDouble()).toFloat()
                                    val vx = (dirX * cos(rad) + perpX * sin(rad)) * shotSpeed
                                    val vy = (dirY * cos(rad) + perpY * sin(rad)) * shotSpeed
                                    val start = Offset(centerX, spawnY)
                                    val len = max(1f, hypot(vx, vy))
                                    val dir = Offset(vx / len, vy / len)
                                    val end = start + dir * (height * 1.1f)
                                    bossTelegraphs.add(BossTelegraph(start, end, warnDelay, Offset(vx, vy), 55f, BossShotType.NORMAL))
                                }
                                bossVolleyRemaining -= 1
                            }
                        }

                        val patternBusy = bossVolleyRemaining > 0 || bossTelegraphs.isNotEmpty() || bossLaneLasers.isNotEmpty()
                        if (bossPatternCooldownMs <= 0L && !patternBusy) {
                            // Stage-specific pattern sets.
                            val patternCount = when (stage) {
                                0 -> 1 // fan only
                                1 -> 2 // fan + side straight
                                else -> 3 // fan + bomb + side straight
                            }
                            val idx = bossPatternIndex % patternCount
                            when (idx) {
                                0 -> { // 3x burst fan
                                    bossVolleyRemaining = if (stage == 0) 1 else 2
                                    bossVolleyTimerMs = 0L
                                    bossPatternCooldownMs = when (stage) {
                                        0 -> 980L
                                        1 -> 900L
                                        else -> 820L
                                    }
                                }
                                1 -> { // stage2: one-side lane laser / stage3: central bomb
                                    if (stage == 1) {
                                        // Stage 2: one-side lane laser
                                        val leftX = pathLeft + pathWidth * 0.25f
                                        val rightX = pathLeft + pathWidth * 0.75f
                                        val laneX = if (rng.nextBoolean()) leftX else rightX
                                        bossTelegraphs.add(
                                            BossTelegraph(
                                                Offset(laneX, spawnY),
                                                Offset(laneX, height * 1.1f),
                                                laneWarnDelay,
                                                Offset.Zero,
                                                pathWidth * 0.25f,
                                                BossShotType.SIDE_LASER
                                            )
                                        )
                                        bossPatternCooldownMs = 850L
                                    } else {
                                        // Stage 3: central bomb
                                        val start = Offset(centerX, spawnY)
                                        val end = Offset(centerX, height * 1.1f)
                                        bossTelegraphs.add(BossTelegraph(start, end, warnDelay, Offset(0f, bombSpeed), 100f, BossShotType.BOMB))
                                        bossPatternCooldownMs = 780L
                                    }
                                }
                                2 -> { // stage3: one-side lane laser
                                    val leftX = pathLeft + pathWidth * 0.25f
                                    val rightX = pathLeft + pathWidth * 0.75f
                                    val laneX = if (rng.nextBoolean()) leftX else rightX
                                    bossTelegraphs.add(
                                        BossTelegraph(
                                            Offset(laneX, spawnY),
                                            Offset(laneX, height * 1.1f),
                                            laneWarnDelay,
                                            Offset.Zero,
                                            pathWidth * 0.25f,
                                            BossShotType.SIDE_LASER
                                        )
                                    )
                                    bossPatternCooldownMs = 780L
                                }
                            }
                            bossPatternIndex += 1
                        }
                    }
                    for (i in bossShots.indices) {
                        val s = bossShots[i]
                        bossShots[i] = s.copy(pos = s.pos + s.vel)
                    }
                    bossShots.removeAll { it.pos.y > height + 100f }

                    for (i in enemyShots.indices) {
                        val s = enemyShots[i]
                        enemyShots[i] = s.copy(pos = s.pos + s.vel)
                    }
                    enemyShots.removeAll { it.pos.y > height + 120f }
                    for (i in bossLaneLasers.indices) {
                        val l = bossLaneLasers[i]
                        bossLaneLasers[i] = l.copy(lifeMs = l.lifeMs - dt)
                    }
                    bossLaneLasers.removeAll { it.lifeMs <= 0L }

                    // Boss telegraphs -> fire
                    if (bossTelegraphs.isNotEmpty()) {
                        val fired = mutableListOf<Int>()
                        for (i in bossTelegraphs.indices) {
                            val t = bossTelegraphs[i]
                            val next = t.delayMs - dt
                            if (next <= 0L) {
                                if (t.type == BossShotType.SIDE_LASER) {
                                    val life = 480L
                                    bossLaneLasers.add(BossLaneLaser(t.start.x, t.radius * 2f, life, life))
                                } else {
                                    bossShots.add(BossShot(t.start, t.vel, t.radius, t.type))
                                }
                                fired.add(i)
                            } else {
                                bossTelegraphs[i] = t.copy(delayMs = next)
                            }
                        }
                        fired.sortedDescending().forEach { bossTelegraphs.removeAt(it) }
                    }

                    // Move bullets
                    for (i in bullets.indices) {
                        val b = bullets[i]
                        val newPos = b.pos + b.vel
                        if (!b.homing && b.straightenMs > 0L) {
                            val nextMs = b.straightenMs - dt
                            val nextVel = if (nextMs <= 0L) Offset(0f, b.vel.y) else b.vel
                            bullets[i] = b.copy(pos = newPos, vel = nextVel, straightenMs = max(0L, nextMs))
                        } else {
                            bullets[i] = b.copy(pos = newPos)
                        }
                    }
                    bullets.removeAll { it.pos.y < -200f || it.pos.y > height + 200f }

                    // Monster patterns (zigzag / dash / dodge)
                    for (i in monsters.indices) {
                        val m = monsters[i]
                        val stage = stageIndex % 3
                        var baseX = m.baseX
                        var dashMs = m.dashMs
                        var dashCd = m.dashCooldownMs
                        var dodgeCd = m.dodgeCooldownMs
                        var y = m.pos.y
                        var shotCd = m.shotCooldownMs

                        if (stage >= 1) {
                            if (dashCd > 0L) dashCd = max(0L, dashCd - dt) else if (dashMs <= 0L) {
                                dashMs = 280L
                                dashCd = 680L - stage * 110L
                            }
                        }
                        if (dashMs > 0L) {
                            dashMs = max(0L, dashMs - dt)
                            y += 11f + stage * 2.5f
                        }
                        if (stage >= 2) {
                            if (dodgeCd > 0L) dodgeCd = max(0L, dodgeCd - dt) else {
                                val step = 26f + stage * 6f
                                baseX = (baseX + if (playerX > m.pos.x) -step else step)
                                    .coerceIn(pathLeft + m.radius, pathRight - m.radius)
                                dodgeCd = 620L - stage * 80L
                            }
                        }
                        val amp = 20f + stage * 6f
                        val nx = baseX + sin((gameTimeMs * 0.009f) + m.zigzagPhase) * amp
                        val newPos = Offset(nx.coerceIn(pathLeft + m.radius, pathRight - m.radius), y)

                        if (m.ranged) {
                            shotCd = max(0L, shotCd - dt)
                            val ms = newPos.shiftByScroll(scrollY)
                            if (shotCd == 0L && ms.y < playerY - 20f && ms.y > 0f) {
                                val baseSpeed = when (stage) {
                                    0 -> 6.8f
                                    1 -> 7.8f
                                    else -> 9.0f
                                }
                                val kind = m.rangedShotKind
                                val speedShot = if (kind == EnemyShotKind.SPEAR) baseSpeed * 1.22f else baseSpeed * 0.86f
                                // Ranged monsters fire straight downward lanes (non-tracking).
                                val vx = 0f
                                val vy = speedShot
                                val radius = if (kind == EnemyShotKind.SPEAR) 9f else 16f
                                enemyShots.add(EnemyShot(ms, Offset(vx, vy), radius, kind))
                                shotCd = when (stage) {
                                    0 -> 980L
                                    1 -> 760L
                                    else -> 620L
                                }
                            }
                        }
                        monsters[i] = m.copy(
                            pos = newPos,
                            shotCooldownMs = shotCd,
                            baseX = baseX,
                            dashMs = dashMs,
                            dashCooldownMs = dashCd,
                            dodgeCooldownMs = dodgeCd
                        )
                    }

                    // Laser duration (keep attached to player)
                    for (i in lasers.indices) {
                        val l = lasers[i]
                        lasers[i] = if (l.followPlayer) {
                            l.copy(x = playerX, lifeMs = l.lifeMs - dt)
                        } else {
                            l.copy(lifeMs = l.lifeMs - dt)
                        }
                    }
                    lasers.removeAll { it.lifeMs <= 0L }
                    for (i in shardRays.indices) {
                        val r = shardRays[i]
                        shardRays[i] = r.copy(lifeMs = r.lifeMs - dt)
                    }
                    shardRays.removeAll { it.lifeMs <= 0L }

                    for (i in floatingTexts.indices) {
                        val t = floatingTexts[i]
                        floatingTexts[i] = t.copy(pos = t.pos.copy(y = t.pos.y - 0.7f), lifeMs = t.lifeMs - dt)
                    }
                    floatingTexts.removeAll { it.lifeMs <= 0L }
                    for (i in gateBursts.indices) {
                        val b = gateBursts[i]
                        gateBursts[i] = b.copy(lifeMs = b.lifeMs - dt)
                    }
                    gateBursts.removeAll { it.lifeMs <= 0L }
                    for (i in splashBursts.indices) {
                        val b = splashBursts[i]
                        splashBursts[i] = b.copy(lifeMs = b.lifeMs - dt)
                    }
                    splashBursts.removeAll { it.lifeMs <= 0L }
                    for (i in deathBursts.indices) {
                        val b = deathBursts[i]
                        deathBursts[i] = b.copy(lifeMs = b.lifeMs - dt)
                    }
                    deathBursts.removeAll { it.lifeMs <= 0L }
                    for (i in muzzleFlashes.indices) {
                        val m = muzzleFlashes[i]
                        muzzleFlashes[i] = m.copy(lifeMs = m.lifeMs - dt)
                    }
                    muzzleFlashes.removeAll { it.lifeMs <= 0L }
                    for (i in hitSparks.indices) {
                        val h = hitSparks[i]
                        hitSparks[i] = h.copy(lifeMs = h.lifeMs - dt)
                    }
                    hitSparks.removeAll { it.lifeMs <= 0L }
                    for (i in particles.indices) {
                        val p = particles[i]
                        particles[i] = p.copy(pos = p.pos + p.vel, lifeMs = p.lifeMs - dt)
                    }
                    particles.removeAll { it.lifeMs <= 0L }
                    if (shakeMs > 0L) {
                        shakeMs -= dt
                        if (shakeMs < 0L) shakeMs = 0L
                    }
                    if (flashMs > 0L) {
                        flashMs -= dt
                        if (flashMs < 0L) flashMs = 0L
                    }

                    // Auto fire
                    fireTimerMs += dt
                    val currentWeapon = weapon
                    val hasTarget = monsters.any { isOnScreen(it.pos.shiftByScroll(scrollY), height) }
                        || (boss?.rect?.shiftByScroll(scrollY)?.let { isRectOnScreen(it, height) } == true)
                    if (currentWeapon != null && hasTarget && fireTimerMs >= currentWeapon.fireRateMs) {
                        fireTimerMs = 0L
                        fireWeapon(currentWeapon, playerX)
                        if (currentWeapon.type != WeaponType.LASER && currentWeapon.burstCount > 1) {
                            val interval = 90L
                            for (i in 1 until currentWeapon.burstCount) {
                                pendingBursts.add(PendingBurst(gameTimeMs + i * interval, currentWeapon, playerX))
                            }
                        }
                    }

                    // Homing adjust (screen coords)
                    for (i in bullets.indices) {
                        val b = bullets[i]
                        if (b.homing) {
                            if (b.superHoming) {
                                val targetPos = monsters
                                    .map { it.pos.shiftByScroll(scrollY) }
                                    .filter { it.y <= playerY }
                                    .minByOrNull { hypot(it.x - b.pos.x, it.y - b.pos.y) }
                                    ?: boss?.rect?.shiftByScroll(scrollY)?.center
                                if (targetPos != null) {
                                    val dx = targetPos.x - b.pos.x
                                    val dy = targetPos.y - b.pos.y
                                    val len = max(1f, hypot(dx, dy))
                                    val vx = dx / len * 16.5f
                                    val vy = dy / len * 16.5f
                                    bullets[i] = b.copy(vel = Offset(vx, vy))
                                }
                                continue
                            }
                            val initialTarget = monsters
                                .map { m -> m.id to m.pos.shiftByScroll(scrollY) }
                                .filter { it.second.y <= playerY }
                                .minByOrNull { hypot(it.second.x - b.pos.x, it.second.y - b.pos.y) }

                            val lockedId = b.homingTargetId
                            val lockedBoss = b.homingTargetBoss

                            val targetPos = when {
                                lockedBoss -> boss?.rect?.shiftByScroll(scrollY)?.center
                                lockedId >= 0 -> monsters.firstOrNull { it.id == lockedId }?.pos?.shiftByScroll(scrollY)
                                initialTarget != null -> initialTarget.second
                                else -> boss?.rect?.shiftByScroll(scrollY)?.center
                            }

                            val nextId = if (lockedId == -1 && !lockedBoss) initialTarget?.first ?: -1 else lockedId
                            val nextBoss = if (lockedId == -1 && !lockedBoss) (initialTarget == null && boss != null) else lockedBoss

                            if (targetPos != null) {
                                val dx = targetPos.x - b.pos.x
                                val dy = targetPos.y - b.pos.y
                                val len = max(1f, hypot(dx, dy))
                                val vx = dx / len * 12.5f
                                val vy = dy / len * 12.5f
                                bullets[i] = b.copy(vel = Offset(vx, vy), homingTargetId = nextId, homingTargetBoss = nextBoss)
                            } else {
                                // No retarget if original target is gone
                                bullets[i] = b.copy(homingTargetId = nextId, homingTargetBoss = nextBoss)
                            }
                        }
                    }

                    // Bullet vs monsters (screen coords)
                    val removedMonsters = mutableListOf<Int>()
                    val removedBullets = mutableListOf<Int>()
                    for (bi in bullets.indices) {
                        val b = bullets[bi]
                        for (mi in monsters.indices) {
                            val m = monsters[mi]
                            val ms = m.pos.shiftByScroll(scrollY)
                                if (circleHit(b.pos, b.radius, ms, m.radius)) {
                                    val newHp = m.hp - b.damage
                                    if (newHp <= 0) removedMonsters.add(mi) else monsters[mi] = m.copy(hp = newHp)
                                    if (b.splashRadius > 0f) {
                                        splashBursts.add(SplashBurst(ms, b.splashRadius, 220L))
                                        val splashDamage = max(1, (b.damage * b.splashDamageRatio).toInt())
                                        for (si in monsters.indices) {
                                            if (si == mi) continue
                                            val sm = monsters[si]
                                            val ss = sm.pos.shiftByScroll(scrollY)
                                            if (hypot(ss.x - ms.x, ss.y - ms.y) <= (b.splashRadius + sm.radius)) {
                                                val hp2 = sm.hp - splashDamage
                                                if (hp2 <= 0) removedMonsters.add(si) else monsters[si] = sm.copy(hp = hp2)
                                                hitSparks.add(HitSpark(ss, 120L))
                                                floatingTexts.add(FloatingText("-$splashDamage", ss.copy(y = ss.y - 10f), Color(0xFFFFB36B), 360L))
                                                laserAccumDamageByMonster[sm.id] = (laserAccumDamageByMonster[sm.id] ?: 0) + splashDamage
                                                laserAccumPosByMonster[sm.id] = ss
                                            }
                                        }
                                    }
                                    if (b.pierceLeft > 0) {
                                        bullets[bi] = b.copy(pierceLeft = b.pierceLeft - 1)
                                    } else {
                                        removedBullets.add(bi)
                                    }
                                    hitSparks.add(HitSpark(ms, 140L))
                                    floatingTexts.add(FloatingText("-${b.damage}", ms.copy(y = ms.y - 16f), Color(0xFFFFD98A), 360L))
                                    laserAccumDamageByMonster[m.id] = (laserAccumDamageByMonster[m.id] ?: 0) + b.damage
                                    laserAccumPosByMonster[m.id] = ms
                                    addParticles(ms, Color(0xFFFFC35A))
                                    shakeMs = 120L
                                    break
                                }
                        }
                    }
                    removedMonsters.distinct().sortedDescending().forEach { idx ->
                        val m = monsters[idx]
                        val roll = rng.nextFloat()
                        if (roll < 0.176f) {
                            drops.add(Drop(m.pos, DropKind.UPGRADE))
                        } else if (roll < 0.44f) {
                            drops.add(Drop(m.pos, DropKind.COIN))
                        }
                        deathBursts.add(DeathBurst(m.pos.shiftByScroll(scrollY), 260L, 28f))
                        addParticles(m.pos.shiftByScroll(scrollY), Color(0xFFFFC35A))
                        shakeMs = 80L
                        monsters.removeAt(idx)
                    }
                    removedBullets.distinct().sortedDescending().forEach { bullets.removeAt(it) }

                    // Laser vs monsters (screen coords)
                    if (lasers.isNotEmpty()) {
                        val laserTickThisFrame = (gameTimeMs / 16L) % 2L == 0L
                        val laserTop = 0f
                        lasers.forEach { l ->
                        val laserX = l.x
                        val laserHalf = l.width * 0.5f
                        val laserDmg = max(1, (l.damagePerTick * 1.15f).toInt())
                            // Laser hits only the first monster it meets
                            var hitIndex: Int? = null
                            var hitY = Float.NEGATIVE_INFINITY
                            for (i in monsters.indices) {
                                val m = monsters[i]
                                val ms = m.pos.shiftByScroll(scrollY)
                                if (ms.y <= playerY && ms.y >= laserTop && abs(ms.x - laserX) < (m.radius + laserHalf)) {
                                    if (ms.y > hitY) {
                                        hitY = ms.y
                                        hitIndex = i
                                    }
                                }
                            }
                            if (hitIndex != null && laserTickThisFrame) {
                                val m = monsters[hitIndex!!]
                                monsters[hitIndex!!] = m.copy(hp = m.hp - laserDmg)
                                hitSparks.add(HitSpark(m.pos.shiftByScroll(scrollY), 140L))
                                floatingTexts.add(FloatingText("-$laserDmg", m.pos.shiftByScroll(scrollY).copy(y = m.pos.shiftByScroll(scrollY).y - 18f), Color(0xFFFFD98A), 340L))
                                laserAccumDamageByMonster[m.id] = (laserAccumDamageByMonster[m.id] ?: 0) + laserDmg
                                laserAccumPosByMonster[m.id] = m.pos.shiftByScroll(scrollY)
                                if (monsters[hitIndex!!].hp <= 0) {
                                    val dead = monsters[hitIndex!!]
                                    val roll = rng.nextFloat()
                                    if (roll < 0.176f) {
                                        drops.add(Drop(dead.pos, DropKind.UPGRADE))
                                    } else if (roll < 0.44f) {
                                        drops.add(Drop(dead.pos, DropKind.COIN))
                                    }
                                    deathBursts.add(DeathBurst(dead.pos.shiftByScroll(scrollY), 260L, 28f))
                                    addParticles(dead.pos.shiftByScroll(scrollY), Color(0xFFFFC35A))
                                    shakeMs = 80L
                                    monsters.removeAt(hitIndex!!)
                                }
                                addParticles(m.pos.shiftByScroll(scrollY), Color(0xFFFFC35A))
                                shakeMs = 120L
                            } else if (hitIndex == null && laserTickThisFrame) {
                                // If no monster hit, laser can hit boss
                                boss?.let { b ->
                                    val bs = b.rect.shiftByScroll(scrollY)
                                    if (bs.bottom <= playerY && bs.top >= laserTop && laserX >= bs.left - laserHalf && laserX <= bs.right + laserHalf) {
                                        boss = b.copy(hp = b.hp - laserDmg)
                                        laserAccumBossDamage += laserDmg
                                    }
                                }
                            }
                        }
                    }
                    if (shardRays.isNotEmpty()) {
                        val shardTickThisFrame = (gameTimeMs / 24L) % 2L == 0L
                        if (shardTickThisFrame) {
                            for (r in shardRays) {
                                val abx = r.end.x - r.start.x
                                val aby = r.end.y - r.start.y
                                val ab2 = max(0.0001f, abx * abx + aby * aby)
                                var bestT = Float.POSITIVE_INFINITY
                                var hitMonsterIndex: Int? = null
                                var hitBoss = false
                                var hitPos = r.end

                                for (mi in monsters.indices) {
                                    val m = monsters[mi]
                                    val ms = m.pos.shiftByScroll(scrollY)
                                    if (ms.y > playerY) continue
                                    if (!circleSegmentHit(ms, m.radius, r.start, r.end, r.width * 0.5f)) continue
                                    val t = (((ms.x - r.start.x) * abx + (ms.y - r.start.y) * aby) / ab2).coerceIn(0f, 1f)
                                    if (t < bestT) {
                                        bestT = t
                                        hitMonsterIndex = mi
                                        hitBoss = false
                                        hitPos = Offset(r.start.x + abx * t, r.start.y + aby * t)
                                    }
                                }

                                boss?.let { b ->
                                    val bs = b.rect.shiftByScroll(scrollY)
                                    val samples = listOf(
                                        bs.center,
                                        bs.topLeft,
                                        Offset(bs.right, bs.top),
                                        Offset(bs.left, bs.bottom),
                                        Offset(bs.right, bs.bottom)
                                    )
                                    for (p in samples) {
                                        if (!circleSegmentHit(p, 8f, r.start, r.end, r.width * 0.5f)) continue
                                        val t = (((p.x - r.start.x) * abx + (p.y - r.start.y) * aby) / ab2).coerceIn(0f, 1f)
                                        if (t < bestT) {
                                            bestT = t
                                            hitMonsterIndex = null
                                            hitBoss = true
                                            hitPos = Offset(r.start.x + abx * t, r.start.y + aby * t)
                                        }
                                    }
                                }

                                if (hitMonsterIndex != null) {
                                    val m = monsters[hitMonsterIndex!!]
                                    monsters[hitMonsterIndex!!] = m.copy(hp = m.hp - r.damagePerTick)
                                    laserAccumDamageByMonster[m.id] = (laserAccumDamageByMonster[m.id] ?: 0) + r.damagePerTick
                                    laserAccumPosByMonster[m.id] = m.pos.shiftByScroll(scrollY)
                                    hitSparks.add(HitSpark(hitPos, 100L))
                                } else if (hitBoss) {
                                    boss?.let { b ->
                                        boss = b.copy(hp = b.hp - r.damagePerTick)
                                        floatingTexts.add(FloatingText("-${r.damagePerTick}", hitPos.copy(y = hitPos.y - 16f), Color(0xFFFFD98A), 340L))
                                        laserAccumBossDamage += r.damagePerTick
                                        hitSparks.add(HitSpark(hitPos, 100L))
                                    }
                                }
                            }
                            monsters.indices.reversed().forEach { i ->
                                if (monsters[i].hp <= 0) {
                                    val dead = monsters[i]
                                    val roll = rng.nextFloat()
                                    if (roll < 0.176f) drops.add(Drop(dead.pos, DropKind.UPGRADE))
                                    else if (roll < 0.44f) drops.add(Drop(dead.pos, DropKind.COIN))
                                    deathBursts.add(DeathBurst(dead.pos.shiftByScroll(scrollY), 260L, 28f))
                                    addParticles(dead.pos.shiftByScroll(scrollY), Color(0xFFFFC35A))
                                    monsters.removeAt(i)
                                }
                            }
                        }
                    }

                    // Aggregate continuous-hit damage numbers for readability.
                    laserAccumTimerMs += dt
                    if (laserAccumTimerMs >= 180L) {
                        laserAccumDamageByMonster.forEach { (id, dmg) ->
                            if (dmg > 0) {
                                val pos = laserAccumPosByMonster[id]
                                    ?: monsters.firstOrNull { it.id == id }?.pos?.shiftByScroll(scrollY)
                                if (pos != null) {
                                    floatingTexts.add(
                                        FloatingText("합계 -$dmg", pos.copy(y = pos.y - 20f), Color(0xFFFF7A3D), 700L)
                                    )
                                }
                            }
                        }
                        if (laserAccumBossDamage > 0) {
                            boss?.let { b ->
                                val bs = b.rect.shiftByScroll(scrollY)
                                floatingTexts.add(
                                    FloatingText("합계 -$laserAccumBossDamage", bs.center.copy(y = bs.center.y - 28f), Color(0xFFFF7A3D), 700L)
                                )
                            }
                        }
                        laserAccumDamageByMonster.clear()
                        laserAccumPosByMonster.clear()
                        laserAccumBossDamage = 0
                        laserAccumTimerMs = 0L
                    }

                    // Boss handling
                    val currentBoss = boss
                    if (currentBoss != null) {
                        // Bullet vs boss (screen coords)
                        var bossHp = currentBoss.hp
                        val bossScreen = currentBoss.rect.shiftByScroll(scrollY)
                        for (bi in bullets.indices) {
                            val b = bullets[bi]
                            if (circleRectHit(b.pos, b.radius, bossScreen)) {
                                bossHp -= b.damage
                                removedBullets.add(bi)
                                hitSparks.add(HitSpark(bossScreen.center, 140L))
                                floatingTexts.add(FloatingText("-${b.damage}", bossScreen.center.copy(y = bossScreen.center.y - 22f), Color(0xFFFFD98A), 360L))
                                laserAccumBossDamage += b.damage
                                addParticles(bossScreen.center, Color(0xFFFFC35A))
                                shakeMs = 160L
                            }
                        }
                        removedBullets.distinct().sortedDescending().forEach { bullets.removeAt(it) }
                        boss = currentBoss.copy(hp = bossHp)

                    if (bossHp <= 0) {
                        boss = null
                        bossLaneLasers.clear()
                        bossTelegraphs.clear()
                        bossShots.clear()
                        laserAccumBossDamage = 0
                        coins += (stageIndex + 1) * 15
                        deathBursts.add(DeathBurst(bossScreen.center, 520L, 90f))
                        addParticles(bossScreen.center, Color(0xFFFFC35A))
                        shakeMs = 220L
                        flashMs = 220L
                        pendingStageIndex = stageIndex + 1
                        bossRewardRemaining = 2
                        if (weapon != null) {
                            paused = true
                            upgradeChoices = generateUpgradeChoices(weapon!!, rng)
                        } else {
                            paused = false
                            if (pendingStageIndex >= 3) {
                                loopCount = 1
                                advanceStage(0)
                            } else {
                                advanceStage(pendingStageIndex)
                            }
                        }
                    }
                    }

                    // Player collisions (monsters, boss bullets)
                    val playerPos = Offset(playerX, playerY)
                    if (monsters.any { circleHit(playerPos, playerRadius, it.pos.shiftByScroll(scrollY), it.radius) }) {
                        running = false
                    }
                    if (bossShots.any { circleHit(playerPos, playerRadius, it.pos, it.radius) }) {
                        running = false
                    }
                    if (bossLaneLasers.any {
                            val ratio = (it.lifeMs.toFloat() / max(1L, it.totalLifeMs).toFloat()).coerceIn(0f, 1f)
                            val currentWidth = it.width * (0.20f + 1.10f * ratio)
                            abs(playerPos.x - it.x) <= (currentWidth * 0.5f + playerRadius * 0.6f)
                        }) {
                        running = false
                    }
                    if (enemyShots.any { circleHit(playerPos, playerRadius, it.pos, it.radius) }) {
                        running = false
                    }
                    // Collect drops
                    val collected = mutableListOf<Int>()
                    for (i in drops.indices) {
                        val d = drops[i]
                        val ds = d.pos.shiftByScroll(scrollY)
                        if (circleHit(playerPos, playerRadius, ds, 18f)) {
                            collected.add(i)
                            when (d.kind) {
                                DropKind.UPGRADE -> {
                                    if (weapon != null) {
                                        paused = true
                                        upgradeChoices = generateUpgradeChoices(weapon!!, rng)
                                    }
                                }
                                DropKind.COIN -> {
                                    val roll = rng.nextInt(100)
                                    val amount = when (stageIndex) {
                                        0 -> 1 + rng.nextInt(5) // 1~5
                                        1 -> 1 + rng.nextInt(20) // 1~20
                                        else -> 1 + rng.nextInt(50) // 1~50
                                    }
                                    coins += amount
                                    floatingTexts.add(FloatingText("+$amount 코인", ds.copy(y = ds.y - 18f), Color(0xFFFFD36A), 700L))
                                    addParticles(ds, Color(0xFFFFD36A))
                                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                                }
                            }
                        }
                    }
                    collected.sortedDescending().forEach { drops.removeAt(it) }
                // Boss wall collision (cannot pass through)
                boss?.let { b ->
                    val bossScreen = b.rect.shiftByScroll(scrollY)
                    if (circleRectHit(playerPos, playerRadius, bossScreen)) {
                        running = false
                    }
                }

                    // Gate collisions (pair select)
                    for (i in gatePairs.indices) {
                        val pair = gatePairs[i]
                        val left = pair.left
                        val right = pair.right
                        val leftHit = !left.used && circleRectHit(playerPos, playerRadius, left.rect.shiftByScroll(scrollY))
                        val rightHit = !right.used && circleRectHit(playerPos, playerRadius, right.rect.shiftByScroll(scrollY))
                        if (leftHit || rightHit) {
                            val chosen = if (leftHit && rightHit) {
                                val dl = kotlin.math.abs(playerX - left.rect.center.x)
                                val dr = kotlin.math.abs(playerX - right.rect.center.x)
                                if (dl <= dr) left else right
                            } else if (leftHit) left else right
                            gatePairs[i] = pair.copy(left = left.copy(used = true), right = right.copy(used = true))
                            val prev = weapon
                            weapon = applyGate(weapon, chosen)?.let { if (prev == null) applyMeta(it) else it }
                            if (prev == null && weapon != null) {
                                convertRemainingGatesToUpgrades(gatePairs, weapon!!, Random(System.currentTimeMillis()))
                            }
                            floatingTexts.add(FloatingText(gateLabel(chosen), Offset(playerX, playerY - 40f), Color(0xFF7CFF7C), 700L))
                            gateBursts.add(GateBurst(chosen.rect.shiftByScroll(scrollY).center, 260L))
                            gatesPassed += 1
                            break
                        }
                    }

                    // Segment progress: after 3rd gate block, spawn boss
                    if (segmentIndex == 0 && gatesPassed >= gatesPerStage) {
                        segmentIndex = 1
                        bossEngaged = false
                        if (boss == null) {
                            spawnBoss()
                        }
                    }
                }
                delay(16L)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (screenState == ScreenState.MENU) {
                val context = LocalContext.current
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val theme = stageTheme(0)
                    val dirtTop = theme.dirtTop
                    val dirtMid = theme.dirtMid
                    val dirtBot = theme.dirtBot
                    drawRect(brush = Brush.verticalGradient(listOf(dirtTop, dirtMid, dirtBot)))
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(theme.glow.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.width * 0.9f
                        )
                    )
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .height(178.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xD9291F17), Color(0xA61A130E))
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                )
                                .border(2.dp, ui.accent.copy(alpha = 0.75f), RoundedCornerShape(26.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GATE RUNNER", color = ui.accent, fontSize = 44.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("ARCADE HUNTER", color = ui.text.copy(alpha = 0.92f), fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(26.dp))
                        val menuAction: @Composable (String, Color, () -> Unit) -> Unit = { label, color, onClick ->
                            Text(
                                label,
                                color = color,
                                fontSize = 34.sp,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .clickable { onClick() }
                            )
                        }
                        menuAction("시작", ui.accent) {
                            screenState = ScreenState.SHOP
                        }
                        menuAction("종료", ui.text.copy(alpha = 0.88f)) { (context as? android.app.Activity)?.finish() }
                    }
                }
            }
            if (screenState == ScreenState.SHOP) {
                // Background same as menu
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val theme = stageTheme(0)
                    val dirtTop = theme.dirtTop
                    val dirtMid = theme.dirtMid
                    val dirtBot = theme.dirtBot
                    drawRect(brush = Brush.verticalGradient(listOf(dirtTop, dirtMid, dirtBot)))
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(theme.glow.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.width * 0.9f
                        )
                    )
                }
                val frame = ui.frame
                val panel = ui.panel
                val accent = ui.accent
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(340.dp)
                                .height(126.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xD9291F17), Color(0xA61A130E))
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .border(2.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("상점", color = ui.accent, fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("보유 코인  $coins", color = ui.text, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (shopMsg.isNotEmpty()) {
                            Text(shopMsg, color = accent, fontSize = 17.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        val shopCard: @Composable (String, Int, Int, Int, @Composable () -> Unit, () -> Unit) -> Unit =
                            { label, level, cost, max, icon, onBuy ->
                                val shape = RoundedCornerShape(18.dp)
                                val isMax = level >= max
                                val textColor = if (isMax) ui.muted else ui.text
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 6.dp)
                                        .width(340.dp)
                                        .height(80.dp)
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                listOf(panel.copy(alpha = 0.92f), Color(0xAA24170F))
                                            ),
                                            shape = shape
                                        )
                                        .border(1.5.dp, frame.copy(alpha = 0.75f), shape)
                                        .clickable { if (!isMax) onBuy() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(ui.panel, RoundedCornerShape(10.dp))
                                                .border(2.dp, accent, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) { icon() }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, color = textColor, fontSize = 20.sp)
                                            Text(
                                                if (isMax) "Lv.$level  MAX" else "Lv.$level  ${cost}코인",
                                                color = accent,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(82.dp)
                                                .height(38.dp)
                                                .background(Color(0x8024170F), RoundedCornerShape(10.dp))
                                                .border(1.5.dp, accent, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (isMax) "완료" else "구매", color = ui.text, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }

                        val maxDmg = 10
                        val maxRate = 8
                        val maxCount = 6
                        val maxPierce = 5
                        val maxBurst = 4
                        val maxRange = 6
                        val dmgCost = shopCost(20, shopDmgLv)
                        val rateCost = shopCost(20, shopRateLv)
                        val countCost = shopCost(30, shopCountLv)
                        val pierceCost = shopCost(60, shopPierceLv)
                        val burstCost = shopCost(70, shopBurstLv)
                        val rangeCost = shopCost(20, shopRangeLv)
                        shopCard("공격력 +1", shopDmgLv, dmgCost, maxDmg, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawStar(x, y, size.minDimension * 0.6f, this)
                            }
                        }) {
                            if (coins >= dmgCost && shopDmgLv < maxDmg) {
                                coins -= dmgCost; shopDmgLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        shopCard("공격 속도 +", shopRateLv, rateCost, maxRate, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawHourglass(x, y, size.minDimension * 0.6f, 4f, this)
                            }
                        }) {
                            if (coins >= rateCost && shopRateLv < maxRate) {
                                coins -= rateCost; shopRateLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        shopCard("탄환 수 +1", shopCountLv, countCost, maxCount, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawArrowBundle(x, y, size.minDimension * 0.6f, 4f, this)
                            }
                        }) {
                            if (coins >= countCost && shopCountLv < maxCount) {
                                coins -= countCost; shopCountLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        shopCard("공격 범위 +1", shopRangeLv, rangeCost, maxRange, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawRangeIcon(x, y, size.minDimension * 0.6f, 4f, this)
                            }
                        }) {
                            if (coins >= rangeCost && shopRangeLv < maxRange) {
                                coins -= rangeCost; shopRangeLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        shopCard("관통 +1", shopPierceLv, pierceCost, maxPierce, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawPierce(x, y, size.minDimension * 0.6f, 4f, this)
                            }
                        }) {
                            if (coins >= pierceCost && shopPierceLv < maxPierce) {
                                coins -= pierceCost; shopPierceLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        shopCard("연속 발사 +1", shopBurstLv, burstCost, maxBurst, {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val x = size.width / 2f
                                val y = size.height / 2f
                                drawBurst(x, y, size.minDimension * 0.6f, 4f, this)
                            }
                        }) {
                            if (coins >= burstCost && shopBurstLv < maxBurst) {
                                coins -= burstCost; shopBurstLv += 1
                                shopMsg = "구매 완료"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            } else {
                                shopMsg = "코인 부족"
                                shopMsgMs = 1200L
                                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        val shopAction: @Composable (String, Color, () -> Unit) -> Unit = { label, color, onClick ->
                            Text(
                                label,
                                color = color,
                                fontSize = 30.sp,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable { onClick() }
                            )
                        }
                        shopAction("게임 시작", ui.accent) {
                            resetGame()
                            screenState = ScreenState.GAME
                        }
                        shopAction("뒤로", ui.text.copy(alpha = 0.9f)) { screenState = ScreenState.MENU }
                    }
                }
            }
            val pauseTopDp = 72.dp
            val pauseBlockPx = with(LocalDensity.current) { 72.dp.toPx() }
            val pauseTopPx = with(LocalDensity.current) { pauseTopDp.toPx() }
            val gameInputModifier = if (screenState == ScreenState.GAME && running && !paused && !manualPaused && stageTransitionMs == 0L) {
                Modifier.pointerInput(pauseBlockPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val size = this.size
                        if (down.position.x > size.width - pauseBlockPx && down.position.y < pauseBlockPx + pauseTopPx) {
                            return@awaitEachGesture
                        }
                        drag(down.id) { change ->
                            val dx = change.positionChange().x
                            if (dx != 0f) {
                                change.consume()
                                targetPlayerX = (targetPlayerX + dx).coerceIn(pathLeft + playerRadius, pathRight - playerRadius)
                            }
                        }
                    }
                }
            } else {
                Modifier
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(gameInputModifier)
                    .zIndex(-1f)
            ) {
                if (screenState != ScreenState.GAME) return@Canvas
                val shakeT = if (shakeMs > 0L) (shakeMs / 160f).coerceIn(0f, 1f) else 0f
                val shakeX = (sin(shakeMs.toFloat() * 0.08f) * 8f * shakeT)
                val shakeY = (cos(shakeMs.toFloat() * 0.11f) * 6f * shakeT)
                withTransform({
                    translate(shakeX, shakeY)
                }) {
                val theme = stageTheme(stageIndex)
                // Fantasy background (theme match)
                val dirtTop = theme.dirtTop
                val dirtMid = theme.dirtMid
                val dirtBot = theme.dirtBot
                val foliageDark = theme.foliageDark
                val foliageLight = theme.foliageLight
                val glowGold = theme.glow
                drawRect(brush = Brush.verticalGradient(listOf(dirtTop, dirtMid, dirtBot)))
                // Dirt path highlight (wavy edges)
                val waveAmp = 14f
                val waveFreq = 0.06f
                val pathTop = -height * 0.1f
                val pathBottom = height * 1.1f
                val pathShape = Path().apply {
                    moveTo(pathLeft, pathTop)
                    var y = pathTop
                    while (y < pathBottom) {
                        val x = pathLeft + sin((y + scrollY * 0.2f) * waveFreq) * waveAmp
                        lineTo(x, y)
                        y += 24f
                    }
                    var y2 = pathBottom
                    while (y2 > pathTop) {
                        val x = pathLeft + pathWidth + sin((y2 + scrollY * 0.2f) * waveFreq + 1.7f) * waveAmp
                        lineTo(x, y2)
                        y2 -= 24f
                    }
                    close()
                }
                drawPath(pathShape, Color(0xFFB78A4E))
                // Side foliage (soft edges)
                drawRect(
                    brush = Brush.verticalGradient(listOf(foliageDark, foliageLight, foliageDark)),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(pathLeft + waveAmp, height)
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(foliageDark, foliageLight, foliageDark)),
                    topLeft = Offset(pathLeft + pathWidth - waveAmp, 0f),
                    size = androidx.compose.ui.geometry.Size(width - (pathLeft + pathWidth) + waveAmp, height)
                )
                // Blend edges
                drawPath(pathShape, Color(0xFF7A5A33).copy(alpha = 0.35f))
                // Theme atmospherics
                when (theme.name) {
                    "SWAMP" -> {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1C2A24).copy(alpha = 0.25f),
                                    Color(0xFF2F3E33).copy(alpha = 0.18f),
                                    Color(0xFF1C2A24).copy(alpha = 0.25f)
                                )
                            )
                        )
                        val bandH = height * 0.12f
                        repeat(4) { i ->
                            val y = (i * height * 0.22f) + (scrollY * 0.12f % (height * 0.22f))
                            drawRoundRect(
                                color = theme.glow.copy(alpha = 0.12f),
                                topLeft = Offset(0f, y - bandH),
                                size = androidx.compose.ui.geometry.Size(width, bandH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f)
                            )
                        }
                    }
                    "VOLCANO" -> {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2A120C).copy(alpha = 0.2f),
                                    Color(0xFF5A2A14).copy(alpha = 0.15f),
                                    Color(0xFF2A120C).copy(alpha = 0.2f)
                                )
                            )
                        )
                        val emberStep = 180f
                        repeat(26) { i ->
                            val x = width * hash01(i, 77)
                            val y = (i * emberStep) - (scrollY % emberStep) - 200f
                            val r = 2.5f + 2.5f * hash01(i, 99)
                            if (y in -50f..height + 50f) {
                                drawCircle(theme.glow.copy(alpha = 0.6f), r, Offset(x, y))
                            }
                        }
                    }
                }
                // Draw background objects from precomputed lists
                bgTrees.forEach { t ->
                    val p = t.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        drawCircle(foliageDark, t.size, p)
                        drawCircle(foliageLight, t.size * 0.75f, Offset(p.x - t.size * 0.5f, p.y + 14f))
                        drawRect(dirtBot, Offset(p.x - 4f, p.y + t.size * 0.7f), androidx.compose.ui.geometry.Size(8f, t.size * 0.7f))
                    }
                }
                bgBushes.forEach { b ->
                    val p = b.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        drawCircle(foliageDark, b.size, Offset(p.x, p.y + 12f))
                        drawCircle(foliageLight, b.size * 0.85f, Offset(p.x + b.size * 0.7f, p.y + 20f))
                    }
                }
                bgAnimals.forEach { a ->
                    val p = a.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        val animalColor = dirtBot.copy(alpha = 0.85f)
                        drawCircle(animalColor, a.size, p)
                        drawCircle(animalColor, a.size * 0.5f, Offset(p.x - a.size * 0.7f, p.y - a.size))
                        drawCircle(animalColor, a.size * 0.5f, Offset(p.x + a.size * 0.7f, p.y - a.size))
                    }
                }
                bgPebbles.forEach { pe ->
                    val p = pe.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        drawCircle(dirtMid.copy(alpha = 0.55f), pe.size, p)
                    }
                }
                bgSticks.forEach { s ->
                    val p = s.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        val dx = cos(s.angle) * s.size
                        val dy = sin(s.angle) * s.size * 0.4f
                        drawLine(dirtBot, Offset(p.x - dx, p.y - dy), Offset(p.x + dx, p.y + dy), 3f)
                    }
                }
                bgRocks.forEach { r ->
                    val p = r.pos.shiftByScroll(scrollY)
                    if (isOnScreen(p, height)) {
                        drawCircle(dirtMid, r.size, p)
                        drawCircle(dirtBot, r.size * 0.45f, Offset(p.x - r.size * 0.3f, p.y - r.size * 0.2f))
                    }
                }
                when (theme.name) {
                    "SWAMP" -> {
                        bgFog.forEach { f ->
                            val p = f.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawRoundRect(
                                    color = theme.glow.copy(alpha = 0.14f),
                                    topLeft = Offset(p.x - f.size, p.y - f.size * 0.4f),
                                    size = androidx.compose.ui.geometry.Size(f.size * 2f, f.size * 0.8f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f)
                                )
                            }
                        }
                        bgMushrooms.forEach { m ->
                            val p = m.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawRect(theme.bossDark, Offset(p.x - m.size * 0.12f, p.y), androidx.compose.ui.geometry.Size(m.size * 0.24f, m.size * 0.6f))
                                drawCircle(theme.glow, m.size * 0.45f, Offset(p.x, p.y - m.size * 0.1f))
                                drawCircle(theme.glow.copy(alpha = 0.6f), m.size * 0.25f, Offset(p.x - m.size * 0.18f, p.y - m.size * 0.12f))
                            }
                        }
                        bgStumps.forEach { s ->
                            val p = s.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawRoundRect(
                                    color = theme.bossDark,
                                    topLeft = Offset(p.x - s.size * 0.5f, p.y - s.size * 0.2f),
                                    size = androidx.compose.ui.geometry.Size(s.size, s.size * 0.8f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawLine(
                                    theme.glow.copy(alpha = 0.4f),
                                    Offset(p.x - s.size * 0.3f, p.y - s.size * 0.1f),
                                    Offset(p.x + s.size * 0.3f, p.y - s.size * 0.1f),
                                    2.5f
                                )
                            }
                        }
                        bgSkulls.forEach { s ->
                            val p = s.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawCircle(Color(0xFFE6D8C2), s.size * 0.55f, p)
                                drawCircle(Color(0xFF2A1B12), s.size * 0.12f, Offset(p.x - s.size * 0.18f, p.y))
                                drawCircle(Color(0xFF2A1B12), s.size * 0.12f, Offset(p.x + s.size * 0.18f, p.y))
                                drawLine(Color(0xFF2A1B12), Offset(p.x - s.size * 0.12f, p.y + s.size * 0.2f), Offset(p.x + s.size * 0.12f, p.y + s.size * 0.2f), 2f)
                            }
                        }
                    }
                    "VOLCANO" -> {
                        bgLavaCracks.forEach { c ->
                            val p = c.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                val dx = cos(c.angle) * c.size
                                val dy = sin(c.angle) * c.size * 0.4f
                                drawLine(theme.bossDark, Offset(p.x - dx, p.y - dy), Offset(p.x + dx, p.y + dy), 6f)
                                drawLine(theme.glow, Offset(p.x - dx * 0.85f, p.y - dy * 0.85f), Offset(p.x + dx * 0.85f, p.y + dy * 0.85f), 2.5f)
                            }
                        }
                        bgAsh.forEach { a ->
                            val p = a.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawCircle(Color(0xFFD9C6B0).copy(alpha = 0.25f), a.size, p)
                            }
                        }
                        bgLavaPillars.forEach { lp ->
                            val p = lp.pos.shiftByScroll(scrollY)
                            if (isOnScreen(p, height)) {
                                drawRoundRect(
                                    color = theme.bossDark,
                                    topLeft = Offset(p.x - lp.size * 0.3f, p.y - lp.height),
                                    size = androidx.compose.ui.geometry.Size(lp.size * 0.6f, lp.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = theme.glow.copy(alpha = 0.7f),
                                    topLeft = Offset(p.x - lp.size * 0.18f, p.y - lp.height * 0.7f),
                                    size = androidx.compose.ui.geometry.Size(lp.size * 0.36f, lp.height * 0.7f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                )
                            }
                        }
                    }
                }

                // Gates (pair)
                gatePairs.forEach { pair ->
                    drawGateRect(pair.left, scrollY, uiPaint, this)
                    drawGateRect(pair.right, scrollY, uiPaint, this)
                }

                // Monsters (chibi minion)
                monsters.forEach { m ->
                    val ms = m.pos.shiftByScroll(scrollY)
                    val r = m.radius
                    val monsterBase = if (m.ranged) Color(0xFF3A2450) else theme.monsterBase
                    val monsterCore = if (m.ranged) Color(0xFFB57CFF) else theme.monsterCore
                    // body + head
                    drawRoundRect(
                        color = monsterBase,
                        topLeft = Offset(ms.x - r * 0.55f, ms.y - r * 0.1f),
                        size = androidx.compose.ui.geometry.Size(r * 1.1f, r * 0.9f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )
                    drawCircle(monsterCore, r * 0.65f, Offset(ms.x, ms.y - r * 0.55f))
                    // helmet rim
                    drawArc(
                        color = monsterBase,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(ms.x - r * 0.7f, ms.y - r * 1.05f),
                        size = androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.0f),
                        style = Stroke(width = 4f)
                    )
                    // eyes
                    drawCircle(Color(0xFF1A120C), r * 0.12f, Offset(ms.x - r * 0.18f, ms.y - r * 0.55f))
                    drawCircle(Color(0xFF1A120C), r * 0.12f, Offset(ms.x + r * 0.18f, ms.y - r * 0.55f))
                    drawCircle(Color(0xFFFFE2A6), r * 0.05f, Offset(ms.x - r * 0.22f, ms.y - r * 0.6f))
                    drawCircle(Color(0xFFFFE2A6), r * 0.05f, Offset(ms.x + r * 0.14f, ms.y - r * 0.6f))
                    // weapon
                    if (m.ranged) {
                        drawLine(Color(0xFF8C5BD1), Offset(ms.x - r * 0.2f, ms.y + r * 0.55f), Offset(ms.x + r * 0.55f, ms.y - r * 0.45f), 4f)
                        drawCircle(Color(0xFFFF3D3D), r * 0.16f, Offset(ms.x + r * 0.62f, ms.y - r * 0.52f))
                        drawCircle(Color(0xFFFFC35A), r * 0.08f, Offset(ms.x + r * 0.62f, ms.y - r * 0.52f))
                    } else {
                        drawLine(monsterBase, Offset(ms.x - r * 0.65f, ms.y + r * 0.15f), Offset(ms.x + r * 0.65f, ms.y - r * 0.15f), 3.5f)
                        drawCircle(monsterCore, r * 0.1f, Offset(ms.x + r * 0.7f, ms.y - r * 0.18f))
                    }
                    if (m.ranged) {
                        // hood marker for quick visual identification
                        drawArc(
                            color = Color(0xFF6E46A8),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(ms.x - r * 0.48f, ms.y - r * 1.0f),
                            size = androidx.compose.ui.geometry.Size(r * 0.96f, r * 0.8f)
                        )
                    }
                    when (theme.name) {
                        "SWAMP" -> {
                            drawCircle(theme.glow.copy(alpha = 0.6f), r * 0.16f, Offset(ms.x, ms.y + r * 0.45f))
                            drawLine(theme.glow.copy(alpha = 0.5f), Offset(ms.x, ms.y + r * 0.45f), Offset(ms.x, ms.y + r * 0.8f), 2.5f)
                            // bubbles
                            drawCircle(theme.glow.copy(alpha = 0.35f), r * 0.12f, Offset(ms.x - r * 0.5f, ms.y - r * 0.2f))
                            drawCircle(theme.glow.copy(alpha = 0.25f), r * 0.08f, Offset(ms.x + r * 0.45f, ms.y - r * 0.35f))
                        }
                        "VOLCANO" -> {
                            drawLine(theme.glow, Offset(ms.x - r * 0.4f, ms.y - r * 0.1f), Offset(ms.x + r * 0.4f, ms.y + r * 0.1f), 2.5f)
                            drawLine(theme.glow.copy(alpha = 0.8f), Offset(ms.x - r * 0.3f, ms.y + r * 0.25f), Offset(ms.x + r * 0.3f, ms.y + r * 0.35f), 2f)
                            // ember sparks
                            drawCircle(theme.glow.copy(alpha = 0.6f), r * 0.08f, Offset(ms.x - r * 0.55f, ms.y + r * 0.1f))
                            drawCircle(theme.glow.copy(alpha = 0.5f), r * 0.06f, Offset(ms.x + r * 0.55f, ms.y - r * 0.15f))
                        }
                    }
                    drawIntoCanvas { c ->
                    val fill = android.graphics.Paint(uiPaint).apply { color = ui.text.toArgb() }
                    val stroke = android.graphics.Paint(uiStrokePaint).apply { color = android.graphics.Color.BLACK }
                    drawOutlinedText(c.nativeCanvas, stroke, fill, m.hp.toString(), ms.x, ms.y - r * 1.2f, 36f, android.graphics.Paint.Align.CENTER)
                }
                }

                // Drops
                drops.forEach { d ->
                    val ds = d.pos.shiftByScroll(scrollY)
                    if (d.kind == DropKind.UPGRADE) {
                        // big chest
                        val w = 42f
                        val h = 30f
                        drawCircle(ui.accent.copy(alpha = 0.2f), 26f, ds)
                        drawRoundRect(ui.frame, Offset(ds.x - w * 0.5f, ds.y - h * 0.5f), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                        drawRoundRect(ui.panel, Offset(ds.x - w * 0.5f + 2f, ds.y - h * 0.5f + 2f), androidx.compose.ui.geometry.Size(w - 4f, h - 4f), androidx.compose.ui.geometry.CornerRadius(7f, 7f))
                        drawRect(ui.text, Offset(ds.x - w * 0.5f + 2f, ds.y - h * 0.5f + 2f), androidx.compose.ui.geometry.Size(w - 4f, 7f))
                        drawRect(ui.accent, Offset(ds.x - 5f, ds.y - h * 0.5f), androidx.compose.ui.geometry.Size(10f, h))
                        drawCircle(ui.text, 4.5f, Offset(ds.x, ds.y + 2f))
                        // small latch sparkle
                        drawCircle(ui.text, 2.5f, Offset(ds.x + w * 0.25f, ds.y - h * 0.2f))
                    } else {
                        // coin
                        val r = 12f
                        drawCircle(ui.accent.copy(alpha = 0.25f), r * 1.8f, ds)
                        drawCircle(ui.frame, r * 1.1f, ds)
                        drawCircle(ui.accent, r, ds)
                        drawCircle(ui.text, r * 0.35f, Offset(ds.x - r * 0.2f, ds.y - r * 0.2f))
                    }
                }

                // Boss shots
                bossTelegraphs.forEach { t ->
                    val pulse = 0.35f + 0.45f * abs(sin(gameTimeMs * 0.02f))
                    val warnColor = if (t.type == BossShotType.SIDE_LASER) Color(0xFFFF2E2E) else Color(0xFFFF3D3D)
                    drawLine(
                        warnColor.copy(alpha = 0.55f * pulse),
                        t.start,
                        t.end,
                        strokeWidth = t.radius * 2f
                    )
                }
                bossLaneLasers.forEach { l ->
                    val ratio = (l.lifeMs.toFloat() / max(1L, l.totalLifeMs).toFloat()).coerceIn(0f, 1f)
                    val w = l.width * (0.20f + 1.10f * ratio)
                    val a = 0.18f + 0.72f * ratio
                    val beamH = height + 60f
                    drawRect(
                        Color(0xFFFF2E2E).copy(alpha = 0.55f * a),
                        topLeft = Offset(l.x - w * 0.5f, 0f),
                        size = androidx.compose.ui.geometry.Size(w, beamH)
                    )
                    drawRect(
                        Color(0xFFFFB3B3).copy(alpha = 0.92f * a),
                        topLeft = Offset(l.x - w * 0.18f, 0f),
                        size = androidx.compose.ui.geometry.Size(w * 0.36f, beamH)
                    )
                }
                bossShots.forEach { s ->
                    val base = if (s.type == BossShotType.BOMB) Color(0xFFFF3D3D) else ui.accent
                    val inner = ui.text
                    drawCircle(base.copy(alpha = 0.85f), s.radius, s.pos)
                    drawCircle(inner, s.radius * 0.5f, s.pos)
                }
                enemyShots.forEach { s ->
                    val len = max(1f, hypot(s.vel.x, s.vel.y))
                    val dir = Offset(s.vel.x / len, s.vel.y / len)
                    val perp = Offset(-dir.y, dir.x)
                    val r = s.radius
                    when (s.kind) {
                        EnemyShotKind.SPEAR -> {
                            val tip = s.pos + dir * (r * 1.7f)
                            val neck = s.pos - dir * (r * 0.55f)
                            val tail = s.pos - dir * (r * 1.8f)
                            drawLine(Color(0xFF6B4B2A), tail, neck, max(2f, r * 0.45f))
                            drawPath(
                                path = Path().apply {
                                    moveTo(tip.x, tip.y)
                                    lineTo((neck + perp * (r * 0.85f)).x, (neck + perp * (r * 0.85f)).y)
                                    lineTo((neck - perp * (r * 0.85f)).x, (neck - perp * (r * 0.85f)).y)
                                    close()
                                },
                                color = Color(0xFFC8C8C8)
                            )
                        }
                        EnemyShotKind.AXE -> {
                            val head = s.pos + dir * (r * 0.5f)
                            val handleA = s.pos - dir * (r * 1.5f)
                            val handleB = s.pos + dir * (r * 1.2f)
                            drawLine(Color(0xFF6B4B2A), handleA, handleB, max(2f, r * 0.4f))
                            drawPath(
                                path = Path().apply {
                                    moveTo((head + perp * (r * 1.1f)).x, (head + perp * (r * 1.1f)).y)
                                    lineTo((head + dir * (r * 0.7f)).x, (head + dir * (r * 0.7f)).y)
                                    lineTo((head - perp * (r * 1.1f)).x, (head - perp * (r * 1.1f)).y)
                                    lineTo((head - dir * (r * 0.25f)).x, (head - dir * (r * 0.25f)).y)
                                    close()
                                },
                                color = Color(0xFFBDBDBD)
                            )
                        }
                    }
                }
                // Boss wall (forest gate)
                boss?.let { b ->
                    val bs = b.rect.shiftByScroll(scrollY)
                    val c = bs.center
                    val br = min(bs.width, bs.height) * 0.42f
                    // shadow + aura
                    drawOval(
                        color = Color.Black.copy(alpha = 0.32f),
                        topLeft = Offset(c.x - br * 0.95f, c.y + br * 0.72f),
                        size = androidx.compose.ui.geometry.Size(br * 1.9f, br * 0.46f)
                    )
                    drawCircle(theme.glow.copy(alpha = 0.18f), br * 1.25f, c)
                    // body/head (monster-like)
                    drawCircle(theme.bossDark, br, c)
                    drawCircle(theme.bossMid, br * 0.86f, Offset(c.x, c.y - br * 0.06f))
                    drawCircle(theme.bossLight.copy(alpha = 0.26f), br * 0.40f, Offset(c.x - br * 0.28f, c.y - br * 0.25f))
                    // horns
                    drawPath(
                        path = Path().apply {
                            moveTo(c.x - br * 0.52f, c.y - br * 0.66f)
                            lineTo(c.x - br * 1.05f, c.y - br * 1.02f)
                            lineTo(c.x - br * 0.58f, c.y - br * 0.22f)
                            close()
                        },
                        color = theme.bossDark
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(c.x + br * 0.52f, c.y - br * 0.66f)
                            lineTo(c.x + br * 1.05f, c.y - br * 1.02f)
                            lineTo(c.x + br * 0.58f, c.y - br * 0.22f)
                            close()
                        },
                        color = theme.bossDark
                    )
                    // eyes + mouth
                    val eyeY = c.y - br * 0.10f
                    drawCircle(Color(0xFFFF3D3D), br * 0.12f, Offset(c.x - br * 0.30f, eyeY))
                    drawCircle(Color(0xFFFF3D3D), br * 0.12f, Offset(c.x + br * 0.30f, eyeY))
                    drawCircle(Color(0xFFFFD0D0), br * 0.05f, Offset(c.x - br * 0.27f, eyeY - br * 0.04f))
                    drawCircle(Color(0xFFFFD0D0), br * 0.05f, Offset(c.x + br * 0.27f, eyeY - br * 0.04f))
                    drawLine(
                        Color(0xFF2A1B12),
                        Offset(c.x - br * 0.34f, c.y + br * 0.30f),
                        Offset(c.x + br * 0.34f, c.y + br * 0.30f),
                        br * 0.10f
                    )
                    drawRect(
                        Color(0xFFFFE2A6),
                        topLeft = Offset(c.x - br * 0.20f, c.y + br * 0.25f),
                        size = androidx.compose.ui.geometry.Size(br * 0.40f, br * 0.10f)
                    )
                    when (theme.name) {
                        "SWAMP" -> {
                            repeat(3) { i ->
                                val x = c.x + (i - 1) * br * 0.45f
                                drawLine(theme.glow.copy(alpha = 0.55f), Offset(x, c.y + br * 0.72f), Offset(x, c.y + br * 1.05f), 5f)
                            }
                        }
                        "VOLCANO" -> {
                            repeat(3) { i ->
                                val x = c.x + (i - 1) * br * 0.38f
                                drawLine(theme.glow.copy(alpha = 0.85f), Offset(x, c.y - br * 0.95f), Offset(x, c.y - br * 1.22f), 3.5f)
                            }
                        }
                    }
                    drawIntoCanvas { c ->
                    val fill = android.graphics.Paint(uiPaint).apply { color = ui.text.toArgb() }
                    val stroke = android.graphics.Paint(uiStrokePaint).apply { color = android.graphics.Color.BLACK }
                    drawOutlinedText(c.nativeCanvas, stroke, fill, b.hp.toString(), bs.center.x, bs.top - 14f, 64f, android.graphics.Paint.Align.CENTER)
                }
                }

                // Bullets
                bullets.forEach { b ->
                    val shaft = Color(0xFF8B552A)
                    val tip = Color(0xFFFFE2A6)
                    val glow = Color(0xFFFF7A3D)
                    val r = b.radius
                    val len = max(1f, hypot(b.vel.x, b.vel.y))
                    val dir = Offset(b.vel.x / len, b.vel.y / len)
                    val perp = Offset(-dir.y, dir.x)
                    val head = b.pos + dir * (r * 2.0f)
                    val neck = b.pos - dir * (r * 0.6f)
                    val tail = b.pos - dir * (r * 1.9f)
                    // glow
                    drawCircle(glow.copy(alpha = 0.25f), r * 1.6f, b.pos)
                    // shaft
                    drawLine(shaft, tail, neck, max(2f, r * 0.6f))
                    // arrowhead
                    drawPath(
                        path = Path().apply {
                            moveTo(head.x, head.y)
                            lineTo((neck + perp * (r * 1.0f)).x, (neck + perp * (r * 1.0f)).y)
                            lineTo((neck - perp * (r * 1.0f)).x, (neck - perp * (r * 1.0f)).y)
                            close()
                        },
                        color = tip
                    )
                    // fletching
                    val f1 = tail + perp * (r * 0.85f)
                    val f2 = tail - perp * (r * 0.85f)
                    val fTip = tail + dir * (r * 0.65f)
                    drawLine(tip.copy(alpha = 0.9f), f1, fTip, max(1.5f, r * 0.4f))
                    drawLine(tip.copy(alpha = 0.9f), f2, fTip, max(1.5f, r * 0.4f))
                }

                // Laser
                lasers.forEach { l ->
                    val w = l.width
                    val laserX = l.x
                    val laserHalf = w * 0.5f
                    var stopY = 0f
                    var hit = false
                    // Stop at first monster hit (closest to player)
                    for (i in monsters.indices) {
                        val m = monsters[i]
                        val ms = m.pos.shiftByScroll(scrollY)
                        if (ms.y <= playerY && ms.y >= 0f && abs(ms.x - laserX) < (m.radius + laserHalf)) {
                            if (!hit || ms.y > stopY) {
                                stopY = ms.y
                                hit = true
                            }
                        }
                    }
                    // If no monster, stop at boss
                    if (!hit) {
                        boss?.let { b ->
                            val bs = b.rect.shiftByScroll(scrollY)
                            if (bs.bottom <= playerY && bs.top >= 0f && laserX >= bs.left - laserHalf && laserX <= bs.right + laserHalf) {
                                stopY = bs.bottom
                                hit = true
                            }
                        }
                    }
                    val beamTop = if (hit) stopY else 0f
                    val beamHeight = (playerY - beamTop).coerceAtLeast(0f)
                    // outer glow
                    drawRect(
                        Color(0xFFFFC35A).copy(alpha = 0.35f),
                        topLeft = Offset(laserX - w * 0.5f, beamTop),
                        size = androidx.compose.ui.geometry.Size(w, beamHeight)
                    )
                    // inner beam
                    drawRect(
                        Color(0xFFFFC35A).copy(alpha = 0.6f),
                        topLeft = Offset(laserX - w * 0.28f, beamTop),
                        size = androidx.compose.ui.geometry.Size(w * 0.56f, beamHeight)
                    )
                    // core
                    drawRect(
                        Color(0xFFFFE2A6),
                        topLeft = Offset(laserX - w * 0.12f, beamTop),
                        size = androidx.compose.ui.geometry.Size(w * 0.24f, beamHeight)
                    )
                    // impact effect at stop point
                    if (hit) {
                        val impact = Offset(laserX, stopY)
                        drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.38f), w * 0.9f, impact)
                        drawCircle(Color(0xFFFFE2A6).copy(alpha = 0.95f), w * 0.26f, impact)
                        drawCircle(
                            Color(0xFFFFC35A).copy(alpha = 0.85f),
                            w * 0.55f,
                            impact,
                            style = Stroke(width = max(2f, w * 0.10f))
                        )
                    }
                    // energy burst at laser origin
                    val origin = Offset(l.x, playerY)
                    val pulse = 0.55f + 0.45f * abs(sin(gameTimeMs * 0.045f))
                    val pulseR = w * (0.95f + 0.35f * pulse)
                    drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.28f * pulse), w * 1.75f, origin)
                    drawCircle(Color(0xFFFFA84D).copy(alpha = 0.38f * pulse), pulseR, origin, style = Stroke(width = max(2f, w * 0.12f)))
                    drawCircle(Color(0xFFFFE2A6).copy(alpha = 0.96f), w * 0.42f, origin)
                    drawCircle(
                        Color(0xFFFFC35A).copy(alpha = 0.90f),
                        w * 0.92f,
                        origin,
                        style = Stroke(width = max(2f, w * 0.16f))
                    )
                    repeat(6) { i ->
                        val ang = (i / 6f) * (Math.PI * 2.0).toFloat() + gameTimeMs * 0.0025f
                        val r1 = w * 0.65f
                        val r2 = w * 1.25f
                        drawLine(
                            Color(0xFFFFD58A).copy(alpha = 0.72f * pulse),
                            start = origin + Offset(cos(ang) * r1, sin(ang) * r1),
                            end = origin + Offset(cos(ang) * r2, sin(ang) * r2),
                            strokeWidth = max(1.8f, w * 0.08f)
                        )
                    }
                }
                shardRays.forEach { r ->
                    val a = (r.lifeMs / 260f).coerceIn(0f, 1f)
                    val abx = r.end.x - r.start.x
                    val aby = r.end.y - r.start.y
                    val ab2 = max(0.0001f, abx * abx + aby * aby)
                    var bestT = Float.POSITIVE_INFINITY
                    var stop = r.end
                    for (m in monsters) {
                        val ms = m.pos.shiftByScroll(scrollY)
                        if (ms.y > playerY) continue
                        if (!circleSegmentHit(ms, m.radius, r.start, r.end, r.width * 0.5f)) continue
                        val t = (((ms.x - r.start.x) * abx + (ms.y - r.start.y) * aby) / ab2).coerceIn(0f, 1f)
                        if (t < bestT) {
                            bestT = t
                            stop = Offset(r.start.x + abx * t, r.start.y + aby * t)
                        }
                    }
                    boss?.let { b ->
                        val bs = b.rect.shiftByScroll(scrollY)
                        val samples = listOf(bs.center, bs.topLeft, Offset(bs.right, bs.top), Offset(bs.left, bs.bottom), Offset(bs.right, bs.bottom))
                        for (p in samples) {
                            if (!circleSegmentHit(p, 8f, r.start, r.end, r.width * 0.5f)) continue
                            val t = (((p.x - r.start.x) * abx + (p.y - r.start.y) * aby) / ab2).coerceIn(0f, 1f)
                            if (t < bestT) {
                                bestT = t
                                stop = Offset(r.start.x + abx * t, r.start.y + aby * t)
                            }
                        }
                    }
                    drawLine(
                        Color(0xFFFF8A3D).copy(alpha = 0.32f * a),
                        start = r.start,
                        end = stop,
                        strokeWidth = r.width
                    )
                    drawLine(
                        Color(0xFFFFE2A6).copy(alpha = 0.9f * a),
                        start = r.start,
                        end = stop,
                        strokeWidth = r.width * 0.35f
                    )
                    // start/end emphasis so direction endpoints are clear
                    drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.85f * a), r.width * 0.36f, r.start)
                    drawCircle(Color(0xFFFFE2A6).copy(alpha = 0.95f * a), r.width * 0.24f, r.start)
                    drawCircle(
                        Color(0xFFFFC35A).copy(alpha = 0.8f * a),
                        r.width * 0.75f,
                        r.start,
                        style = Stroke(width = max(2f, r.width * 0.12f))
                    )
                    drawCircle(Color(0xFFFFE2A6).copy(alpha = 0.82f * a), r.width * 0.30f, stop)
                    drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.65f * a), r.width * 0.50f, stop, style = Stroke(width = 2.2f))
                }

                // Player (chibi archer, concept-aligned)
                val c = Offset(playerX, playerY)
                val r = playerRadius
                // ground glow
                drawCircle(Color(0xFFFFC35A).copy(alpha = 0.18f), r * 1.8f, c)
                // shadow
                drawOval(
                    color = Color(0xFF2A1B12).copy(alpha = 0.35f),
                    topLeft = Offset(c.x - r * 0.9f, c.y + r * 0.8f),
                    size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 0.45f)
                )
                // body
                drawRoundRect(
                    color = Color(0xFF5FAE4D),
                    topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.1f),
                    size = androidx.compose.ui.geometry.Size(r * 1.2f, r * 0.95f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                )
                // hood
                drawCircle(Color(0xFF6FB453), r * 0.95f, Offset(c.x, c.y - r * 0.55f))
                drawCircle(Color(0xFF4D8C3A), r * 0.88f, Offset(c.x, c.y - r * 0.58f))
                // face
                drawCircle(Color(0xFFFFE2A6), r * 0.48f, Offset(c.x, c.y - r * 0.45f))
                drawCircle(Color(0xFF3A2A1A), r * 0.08f, Offset(c.x - r * 0.18f, c.y - r * 0.45f))
                drawCircle(Color(0xFF3A2A1A), r * 0.08f, Offset(c.x + r * 0.18f, c.y - r * 0.45f))
                drawLine(Color(0xFF2A1B12), Offset(c.x - r * 0.14f, c.y - r * 0.3f), Offset(c.x + r * 0.14f, c.y - r * 0.3f), 2.5f)
                // belt + pouch
                drawRect(Color(0xFF7A4B24), Offset(c.x - r * 0.6f, c.y + r * 0.45f), androidx.compose.ui.geometry.Size(r * 1.2f, r * 0.14f))
                drawRoundRect(Color(0xFF5A371F), Offset(c.x - r * 0.1f, c.y + r * 0.5f), androidx.compose.ui.geometry.Size(r * 0.4f, r * 0.25f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                // boots
                drawRoundRect(Color(0xFF3A2A1A), Offset(c.x - r * 0.55f, c.y + r * 0.8f), androidx.compose.ui.geometry.Size(r * 0.45f, r * 0.28f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                drawRoundRect(Color(0xFF3A2A1A), Offset(c.x + r * 0.1f, c.y + r * 0.8f), androidx.compose.ui.geometry.Size(r * 0.45f, r * 0.28f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                // bow + string
                drawArc(
                    color = Color(0xFF8B552A),
                    startAngle = 120f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 1.5f, c.y - r * 0.5f),
                    size = androidx.compose.ui.geometry.Size(r * 2.6f, r * 2.2f),
                    style = Stroke(width = 5f)
                )
                drawLine(
                    color = Color(0xFF2A1B12),
                    start = Offset(c.x - r * 1.12f, c.y + r * 0.1f),
                    end = Offset(c.x + r * 0.9f, c.y + r * 0.1f),
                    strokeWidth = 2.5f
                )
                // quiver + arrows
                drawRect(Color(0xFF3A2A1A), Offset(c.x + r * 0.45f, c.y - r * 1.1f), androidx.compose.ui.geometry.Size(r * 0.35f, r * 0.9f))
                drawLine(Color(0xFFE6D0A5), Offset(c.x + r * 0.62f, c.y - r * 1.15f), Offset(c.x + r * 0.62f, c.y - r * 1.45f), 3f)
                drawLine(Color(0xFFE6D0A5), Offset(c.x + r * 0.52f, c.y - r * 1.12f), Offset(c.x + r * 0.52f, c.y - r * 1.4f), 3f)

                // Floating texts
                floatingTexts.forEach { ft ->
                    drawIntoCanvas { c ->
                        val fill = android.graphics.Paint(uiPaint).apply { color = ft.color.toArgb() }
                        val stroke = android.graphics.Paint(uiStrokePaint).apply { color = android.graphics.Color.BLACK }
                    val isAccum = ft.text.startsWith("합계 ")
                    val size = if (isAccum) 48f else 38f
                    drawOutlinedText(c.nativeCanvas, stroke, fill, ft.text, ft.pos.x, ft.pos.y, size, android.graphics.Paint.Align.CENTER)
                }
            }

                // Death bursts
                deathBursts.forEach { db ->
                    val t = 1f - (db.lifeMs / 260f).coerceIn(0f, 1f)
                    val r = db.radius + t * 26f
                    val a = 1f - t
                    drawCircle(Color(0xFFFFC35A).copy(alpha = a), r, db.pos, style = Stroke(width = 4f))
                    drawCircle(Color(0xFFFFE2A6).copy(alpha = a * 0.6f), r * 0.6f, db.pos, style = Stroke(width = 2.5f))
                }

                // Gate burst rings
                gateBursts.forEach { gb ->
                    val t = 1f - (gb.lifeMs / 260f).coerceIn(0f, 1f)
                    val radius = 18f + t * 40f
                    val alpha = 1f - t
                    drawCircle(Color(0xFFFFC35A).copy(alpha = alpha), radius, gb.pos, style = Stroke(width = 4f))
                }
                splashBursts.forEach { sb ->
                    val t = 1f - (sb.lifeMs / 220f).coerceIn(0f, 1f)
                    val r = sb.radius * (0.55f + 0.75f * t)
                    val a = 1f - t
                    drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.42f * a), r, sb.pos)
                    drawCircle(Color(0xFFFFD2A6).copy(alpha = 0.95f * a), r * 0.75f, sb.pos, style = Stroke(width = 4.5f))
                }

                // Muzzle flashes
                muzzleFlashes.forEach { mf ->
                    val t = 1f - (mf.lifeMs / 120f).coerceIn(0f, 1f)
                    val r = 8f + t * 10f
                    drawCircle(Color(0xFFFFE2A6).copy(alpha = 1f - t), r, mf.pos)
                }

                // Screen flash (boss death)
                if (flashMs > 0L) {
                    val t = (flashMs / 220f).coerceIn(0f, 1f)
                    drawRect(Color.White.copy(alpha = 0.35f * t))
                }

                // Hit sparks
                hitSparks.forEach { hs ->
                    val t = 1f - (hs.lifeMs / 140f).coerceIn(0f, 1f)
                    val r = 6f + t * 10f
                    drawCircle(Color(0xFFFFC35A).copy(alpha = 1f - t), r, hs.pos)
                    drawLine(Color(0xFFFFE2A6).copy(alpha = 1f - t), hs.pos + Offset(-r, 0f), hs.pos + Offset(r, 0f), 3f)
                    drawLine(Color(0xFFFFE2A6).copy(alpha = 1f - t), hs.pos + Offset(0f, -r), hs.pos + Offset(0f, r), 3f)
                }
                // Particles
                particles.forEach { p ->
                    val a = (p.lifeMs / 220f).coerceIn(0f, 1f)
                    drawCircle(p.color.copy(alpha = a), 4f, p.pos)
                }
                }
            }

            if (screenState == ScreenState.GAME) {
                val w = weapon
                val stageThemeName = when (stageIndex % 3) {
                    0 -> "숲"
                    1 -> "늪"
                    else -> "화산"
                }
                val rateLabel = if (w == null) "-" else String.format("%.1f", 1000f / w.fireRateMs.toFloat())
                val ammoLabel = if (w == null) "-" else if (w.type == WeaponType.LASER) "${w.laserDurationMs}ms" else "${w.bulletCount}"
                val specialLabel = when {
                    w == null -> "특수 없음"
                    w.legendarySplash -> "명중폭발"
                    w.legendarySuperHoming -> "슈퍼유도"
                    w.legendaryShardLaser -> "분기레이저"
                    else -> "특수 없음"
                }
                val hudChip: @Composable (String, String, Color) -> Unit = { title, value, tint ->
                    Column(
                        modifier = Modifier
                            .background(Color(0xA015110D), RoundedCornerShape(12.dp))
                            .border(1.dp, tint.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(title, color = ui.muted, fontSize = 8.sp)
                        Text(value, color = tint, fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 96.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text("STAGE ${min(stageIndex + 1, 3)} · $stageThemeName", color = ui.accent, fontSize = 18.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        hudChip("공격력", if (w == null) "-" else "${w.damage}", Color(0xFFFFD084))
                        hudChip("탄환/지속", ammoLabel, Color(0xFF9DE7FF))
                        hudChip("공격속도", if (w == null) "-" else "$rateLabel 회/초", Color(0xFF8CFFB0))
                        hudChip("코인", "$coins", ui.accent)
                    }
                }
            }

            if (screenState == ScreenState.GAME && paused && upgradeChoices.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xD9291F17), Color(0xA61A130E))
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(1.5.dp, ui.accent.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("강화 선택", color = ui.accent, fontSize = 28.sp)
                        }
                        upgradeChoices.forEach { choice ->
                            val base = choice.rarity.color
                            val panel = base.copy(alpha = 0.16f)
                            val border = base.copy(alpha = 0.9f)
                            val glow = base.copy(alpha = 0.35f)
                            val specialBonus = 1
                            val statBonus = if (choice.rarity == Rarity.LEGENDARY) choice.rarity.bonus * 2 else choice.rarity.bonus
                            val bonusText = when (choice.upgrade) {
                                UpgradeType.DMG -> "공격력 +${statBonus}"
                                UpgradeType.COUNT -> "탄환 +${statBonus}"
                                UpgradeType.RATE -> "공속 +${statBonus}"
                                UpgradeType.LASER_TIME -> "지속 +${statBonus}"
                                UpgradeType.RANGE -> "범위 +${statBonus}"
                                UpgradeType.PIERCE -> "관통 +${specialBonus}"
                                UpgradeType.BURST -> "연속 +${specialBonus}"
                                UpgradeType.LEGENDARY_SPECIAL -> when (weapon?.type) {
                                    WeaponType.MULTI, WeaponType.SPREAD3 -> "명중 시 소형 폭발"
                                    WeaponType.HOMING -> "유도 속도+ / 무한 유도"
                                    WeaponType.LASER -> "랜덤 6갈래 분기 레이저"
                                    null -> "무기 특수 강화"
                                }
                            }
                            val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .width(340.dp)
                                    .height(110.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xCC18120D), panel, Color(0xCC18120D))
                                        ),
                                        shape = shape
                                    )
                                    .border(2.dp, border, shape)
                                    .clickable {
                                        weapon = applyUpgradeChoice(weapon, choice)
                                        val pickedLabel = if (choice.upgrade == UpgradeType.LEGENDARY_SPECIAL && weapon != null) {
                                            legendarySpecialLabel(weapon!!.type)
                                        } else {
                                            upgradeLabel(choice.upgrade)
                                        }
                                        floatingTexts.add(FloatingText("${choice.rarity.label} $pickedLabel", Offset(playerX, playerY - 60f), choice.rarity.color, 800L))
                                        if (bossRewardRemaining > 0) {
                                            bossRewardRemaining -= 1
                                            if (bossRewardRemaining > 0 && weapon != null) {
                                                upgradeChoices = generateUpgradeChoices(weapon!!, Random(System.currentTimeMillis()))
                                            } else {
                                                upgradeChoices = emptyList()
                                                paused = false
                                                if (pendingStageIndex >= 3) {
                                                    loopCount = 1
                                                    advanceStage(0)
                                                } else {
                                                    advanceStage(pendingStageIndex)
                                                }
                                            }
                                        } else {
                                            upgradeChoices = emptyList()
                                            paused = false
                                        }
                                    }
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(54.dp)) {
                                        val iconX = size.width / 2f
                                        val iconY = size.height / 2f
                                        val sizeIcon = size.minDimension * 0.7f
                                        val stroke = 4f
                                        when (choice.upgrade) {
                                            UpgradeType.DMG -> drawStar(iconX, iconY, sizeIcon, this)
                                            UpgradeType.COUNT -> drawArrowBundle(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.RATE -> drawHourglass(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.LASER_TIME -> drawHourglass(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.RANGE -> drawRangeIcon(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.PIERCE -> drawPierce(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.BURST -> drawBurst(iconX, iconY, sizeIcon, stroke, this)
                                            UpgradeType.LEGENDARY_SPECIAL -> drawStar(iconX, iconY, sizeIcon, this)
                                        }
                                    }
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(choice.rarity.label, color = border, fontSize = 16.sp)
                                        val title = if (choice.upgrade == UpgradeType.LEGENDARY_SPECIAL && weapon != null) {
                                            legendarySpecialLabel(weapon!!.type)
                                        } else {
                                            upgradeLabel(choice.upgrade)
                                        }
                                        Text(title, color = ui.text, fontSize = 24.sp)
                                        Text(bonusText, color = glow, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (screenState == ScreenState.GAME && stageTransitionMs > 0L) {
                val t = (stageTransitionMs / 1400f).coerceIn(0f, 1f)
                val alpha = 1f - t
                val nextName = if (pendingStageIndex >= 3) "2회차" else "스테이지 ${pendingStageIndex + 1}"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f * alpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xD9291F17), Color(0xA61A130E))
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(1.5.dp, ui.accent.copy(alpha = 0.75f * alpha), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(nextName, color = ui.accent.copy(alpha = alpha), fontSize = 34.sp)
                    }
                }
            }

            if (screenState == ScreenState.GAME && running && !manualPaused) {
                Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                    Box(
                        modifier = Modifier
                            .padding(top = pauseTopDp, end = 16.dp)
                            .align(Alignment.TopEnd)
                            .size(56.dp)
                            .zIndex(5f)
                            .clickable {
                                if (stageTransitionMs == 0L && running && upgradeChoices.isEmpty()) {
                                    manualPaused = !manualPaused
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(34.dp)) {
                            val barW = size.width * 0.28f
                            val barH = size.height * 0.85f
                            val gap = size.width * 0.16f
                            val x1 = (size.width - (barW * 2 + gap)) / 2f
                            val y = (size.height - barH) / 2f
                            drawRoundRect(ui.text, Offset(x1, y), androidx.compose.ui.geometry.Size(barW, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                            drawRoundRect(ui.text, Offset(x1 + barW + gap, y), androidx.compose.ui.geometry.Size(barW, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                        }
                    }
                }
            }

            if (screenState == ScreenState.GAME && manualPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xD9291F17), Color(0xA61A130E))
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(1.5.dp, ui.accent.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("일시정지", color = ui.accent, fontSize = 34.sp)
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "재개",
                            color = ui.text,
                            fontSize = 30.sp,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable { manualPaused = false }
                        )
                        Text(
                            "재시작",
                            color = ui.text,
                            fontSize = 30.sp,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable {
                                    manualPaused = false
                                    resetGame()
                                }
                        )
                        Text(
                            "메인 메뉴",
                            color = ui.text,
                            fontSize = 30.sp,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable {
                                    manualPaused = false
                                    paused = false
                                    screenState = ScreenState.MENU
                                }
                        )
                    }
                }
            }

            if (screenState == ScreenState.GAME && !running) {
                val accent = ui.accent
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xD9291F17), Color(0xA61A130E))
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(1.5.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(if (stageIndex >= 3) "클리어" else "게임 오버", color = accent, fontSize = 36.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("스테이지 ${min(stageIndex + 1, 3)}", color = ui.text, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(18.dp))
                        val actionTextButton: @Composable (String, () -> Unit) -> Unit = { label, onClick ->
                            Text(
                                label,
                                color = ui.text,
                                fontSize = 30.sp,
                                modifier = Modifier
                                    .padding(vertical = 9.dp)
                                    .clickable { onClick() }
                            )
                        }
                        actionTextButton("재시작") { resetGame() }
                        Spacer(modifier = Modifier.height(6.dp))
                        actionTextButton("메뉴") { screenState = ScreenState.MENU }
                    }
                }
            }
        }
    }
}

private fun Offset.shiftByScroll(scrollY: Float): Offset = this.copy(y = this.y + scrollY)

private fun Rect.shiftByScroll(scrollY: Float): Rect {
    return Rect(left, top + scrollY, right, bottom + scrollY)
}

private fun applyGate(current: WeaponState?, gate: Gate): WeaponState? {
    return if (gate.kind == GateKind.WEAPON && gate.weapon != null) {
        normalizeWeaponStats(when (gate.weapon) {
        WeaponType.MULTI -> WeaponState(WeaponType.MULTI, 1, 2, 2, 0, 1, 320L, 380L, 6f, 36f)
        WeaponType.SPREAD3 -> WeaponState(WeaponType.SPREAD3, 1, 2, 3, 0, 1, 320L, 380L, 6f, 36f)
        WeaponType.HOMING -> WeaponState(WeaponType.HOMING, 1, 2, 1, 0, 1, 360L, 380L, 6f, 36f)
        WeaponType.LASER -> WeaponState(WeaponType.LASER, 1, 1, 1, 0, 1, 900L, 380L, 6f, 36f)
        })
    } else if (gate.kind == GateKind.UPGRADE && current != null) {
        val nextLevel = current.level + 1
        val scale = 0.4f
        normalizeWeaponStats(when (gate.upgrade) {
            UpgradeType.DMG -> {
                val mult = weaponDmgScale(current.type)
                current.copy(level = nextLevel, damage = current.damage + max(1, (1 * scale * mult).toInt()))
            }
            UpgradeType.COUNT -> { // +1 bullet or +60ms laser time
                if (current.type == WeaponType.LASER) current.copy(level = nextLevel, laserDurationMs = current.laserDurationMs + (60L * scale).toLong())
                else current.copy(level = nextLevel, bulletCount = current.bulletCount + max(1, (1 * scale).toInt()))
            }
            UpgradeType.RATE -> {
                if (current.type == WeaponType.LASER) {
                    current.copy(level = nextLevel, fireRateMs = max(420L, current.fireRateMs - (6L * scale).toLong()))
                } else {
                    current.copy(level = nextLevel, fireRateMs = max(180L, current.fireRateMs - (10L * scale).toLong()))
                }
            }
            UpgradeType.LASER_TIME -> current.copy(level = nextLevel, laserDurationMs = current.laserDurationMs + (50L * scale).toLong())
            UpgradeType.RANGE -> {
                if (current.type == WeaponType.LASER) {
                    val addLaser = max(2f, 4f * scale)
                    current.copy(
                        level = nextLevel,
                        laserWidth = current.laserWidth + addLaser
                    )
                } else {
                    current.copy(level = nextLevel)
                }
            }
            UpgradeType.PIERCE -> current.copy(level = nextLevel, pierce = current.pierce + max(1, (1 * scale).toInt()))
            UpgradeType.BURST -> current.copy(level = nextLevel, burstCount = current.burstCount + max(1, (1 * scale).toInt()))
            UpgradeType.LEGENDARY_SPECIAL -> current.copy(level = nextLevel)
            null -> current
        })
    } else {
        current
    }
}

private fun weaponColor(type: WeaponType): Color {
    return when (type) {
        WeaponType.MULTI -> Color(0xFF3B8DFF)
        WeaponType.SPREAD3 -> Color(0xFFFFC35A)
        WeaponType.HOMING -> Color(0xFF76C26A)
        WeaponType.LASER -> Color(0xFFFF7A3D)
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun weaponDmgScale(type: WeaponType): Float {
    return when (type) {
        WeaponType.MULTI -> 0.9f
        WeaponType.SPREAD3 -> 0.8f
        WeaponType.HOMING -> 1.1f
        WeaponType.LASER -> 0.6f
    }
}

private fun normalizeWeaponStats(w: WeaponState): WeaponState {
    val maxDamage = if (w.type == WeaponType.LASER) 16 else 24
    val maxCount = if (w.type == WeaponType.LASER) 1 else 9
    val minRate = if (w.type == WeaponType.LASER) 560L else 240L
    val maxLaserDuration = 1200L
    val maxPierce = 4
    val maxBurst = 3
    val maxBulletRadius = 16f
    val maxLaserWidth = 72f
    return w.copy(
        damage = w.damage.coerceAtMost(maxDamage),
        bulletCount = w.bulletCount.coerceAtMost(maxCount),
        fireRateMs = w.fireRateMs.coerceAtLeast(minRate),
        laserDurationMs = w.laserDurationMs.coerceAtMost(maxLaserDuration),
        pierce = w.pierce.coerceAtMost(maxPierce),
        burstCount = w.burstCount.coerceAtMost(maxBurst),
        bulletRadius = w.bulletRadius.coerceAtMost(maxBulletRadius),
        laserWidth = w.laserWidth.coerceAtMost(maxLaserWidth)
    )
}

private fun upgradeOptionsFor(weapon: WeaponState): List<UpgradeType> {
    return if (weapon.type == WeaponType.LASER) {
        listOf(UpgradeType.DMG, UpgradeType.RATE, UpgradeType.LASER_TIME, UpgradeType.RANGE)
    } else {
        listOf(UpgradeType.DMG, UpgradeType.COUNT, UpgradeType.RATE)
    }
}

private fun upgradeOptionsForChoice(weapon: WeaponState, rarity: Rarity): List<UpgradeType> {
    val base = upgradeOptionsFor(weapon)
    val specials = if (weapon.type == WeaponType.LASER) {
        emptyList()
    } else {
        listOf(UpgradeType.PIERCE, UpgradeType.BURST)
    }
    val hasLegendarySpecial = weapon.legendarySplash || weapon.legendarySuperHoming || weapon.legendaryShardLaser
    return if (rarity == Rarity.LEGENDARY) {
        if (hasLegendarySpecial) base else listOf(UpgradeType.LEGENDARY_SPECIAL) + base
    } else if (rarity == Rarity.EPIC) {
        base + specials
    } else {
        base
    }
}

private fun defaultWeaponForUpgrades(): WeaponState {
    return WeaponState(WeaponType.MULTI, 1, 2, 2, 0, 1, 320L, 380L, 6f, 36f)
}

private fun convertRemainingGatesToUpgrades(
    gatePairs: MutableList<GatePair>,
    weapon: WeaponState,
    rng: Random
) {
    val upgrades = upgradeOptionsFor(weapon)
    for (i in gatePairs.indices) {
        val pair = gatePairs[i]
        val left = pair.left
        val right = pair.right
        if (left.used || right.used) continue
        val leftUp = upgrades[rng.nextInt(upgrades.size)]
        val rightUp = upgrades[rng.nextInt(upgrades.size)]
        gatePairs[i] = pair.copy(
            left = left.copy(kind = GateKind.UPGRADE, weapon = null, upgrade = leftUp),
            right = right.copy(kind = GateKind.UPGRADE, weapon = null, upgrade = rightUp)
        )
    }
}

private fun drawGateRect(
    gate: Gate,
    scrollY: Float,
    paint: android.graphics.Paint,
    scope: androidx.compose.ui.graphics.drawscope.DrawScope
) {
    val rect = gate.rect.shiftByScroll(scrollY)
    if (gate.used) return
    val color = when (gate.kind) {
        GateKind.WEAPON -> weaponColor(gate.weapon!!)
        GateKind.UPGRADE -> Color(0xFFB0562F)
    }
    with(scope) {
        // Gate frame + inner fill (arcade, concept style)
        val border = 7f
        val frame = Color(0xFF2A1B12)
        val inner = color
        val panel = Color(0xFF7A4B24)
        drawRoundRect(frame, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f))
        drawRoundRect(panel, rect.topLeft + Offset(4f, 4f), rect.size.copy(width = rect.size.width - 8f, height = rect.size.height - 8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        drawRoundRect(inner, rect.topLeft + Offset(border, border), rect.size.copy(width = rect.size.width - border * 2, height = rect.size.height - border * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
        // Portal ring + glow
        val ringR = rect.height * 0.34f
        val ringCenter = Offset(rect.center.x, rect.center.y - rect.height * 0.1f)
        drawCircle(inner.copy(alpha = 0.25f), ringR * 1.25f, ringCenter)
        drawCircle(Color(0xFFE6D0A5), ringR * 1.05f, ringCenter, style = Stroke(width = 6f))
        drawCircle(Color(0xFF9C6B38), ringR, ringCenter, style = Stroke(width = 4f))
        drawCircle(inner.copy(alpha = 0.65f), ringR * 0.7f, ringCenter)
        // bottom emblem plate
        val plateW = rect.width * 0.62f
        val plateH = rect.height * 0.18f
        val plateTop = rect.bottom - plateH - rect.height * 0.08f
        drawRoundRect(
            color = frame,
            topLeft = Offset(rect.center.x - plateW * 0.5f, plateTop),
            size = androidx.compose.ui.geometry.Size(plateW, plateH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = Color(0xFFD8B48A),
            topLeft = Offset(rect.center.x - plateW * 0.5f + 3f, plateTop + 3f),
            size = androidx.compose.ui.geometry.Size(plateW - 6f, plateH - 6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        drawIntoCanvas { c ->
                    val label = when (gate.kind) {
                        GateKind.WEAPON -> weaponLabel(gate.weapon!!)
                        GateKind.UPGRADE -> upgradeLabel(gate.upgrade!!)
                    }
            drawOutlinedText(c.nativeCanvas, android.graphics.Paint(paint).apply {
                this.style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = 6f
                this.color = android.graphics.Color.BLACK
            }, paint, label, rect.center.x, rect.center.y + rect.height * 0.28f, 38f, android.graphics.Paint.Align.CENTER)
        }
        drawGateIcon(gate, rect, this)
    }
}

private fun gateLabel(gate: Gate): String {
    return when (gate.kind) {
        GateKind.WEAPON -> weaponLabel(gate.weapon!!)
        GateKind.UPGRADE -> upgradeLabel(gate.upgrade!!)
    }
}

private fun weaponLabel(type: WeaponType): String {
    return when (type) {
        WeaponType.MULTI -> "+멀티샷"
        WeaponType.SPREAD3 -> "+부채샷"
        WeaponType.HOMING -> "+유도"
        WeaponType.LASER -> "+레이저"
    }
}

private fun upgradeLabel(type: UpgradeType): String {
    return when (type) {
        UpgradeType.DMG -> "공격력"
        UpgradeType.COUNT -> "탄환 수"
        UpgradeType.RATE -> "공격 속도"
        UpgradeType.LASER_TIME -> "레이저 지속시간"
        UpgradeType.RANGE -> "공격 범위"
        UpgradeType.PIERCE -> "관통"
        UpgradeType.BURST -> "연속 발사"
        UpgradeType.LEGENDARY_SPECIAL -> "전설 특수"
    }
}

private fun legendarySpecialLabel(weaponType: WeaponType): String {
    return when (weaponType) {
        WeaponType.MULTI, WeaponType.SPREAD3 -> "명중 폭발"
        WeaponType.HOMING -> "슈퍼 유도"
        WeaponType.LASER -> "분기 레이저"
    }
}

private fun generateUpgradeChoices(weapon: WeaponState, rng: Random): List<UpgradeChoice> {
    val chosen = mutableSetOf<UpgradeType>()
    val result = mutableListOf<UpgradeChoice>()
    val hasLegendarySpecial = weapon.legendarySplash || weapon.legendarySuperHoming || weapon.legendaryShardLaser
    var safety = 0
    while (result.size < 3 && safety < 200) {
        safety++
        val rarity = rollRarity(rng)
        val upgrades = upgradeOptionsForChoice(weapon, rarity)
        val pick = if (rarity == Rarity.LEGENDARY && !hasLegendarySpecial && UpgradeType.LEGENDARY_SPECIAL !in chosen) {
            UpgradeType.LEGENDARY_SPECIAL
        } else {
            upgrades.shuffled(rng).firstOrNull { it !in chosen } ?: continue
        }
        chosen.add(pick)
        result.add(UpgradeChoice(pick, rarity))
    }
    return result
}

private fun rollRarity(rng: Random): Rarity {
    val roll = rng.nextInt(30) + 1
    return when {
        roll <= 18 -> Rarity.NORMAL
        roll <= 25 -> Rarity.RARE
        roll <= 28 -> Rarity.ADVANCED
        roll == 29 -> Rarity.EPIC
        else -> Rarity.LEGENDARY
    }
}

private fun applyUpgradeChoice(current: WeaponState?, choice: UpgradeChoice): WeaponState? {
    val w = current ?: return current
    val specialBonus = 1
    val statBonus = if (choice.rarity == Rarity.LEGENDARY) choice.rarity.bonus * 2 else choice.rarity.bonus
    val mult = if (choice.upgrade == UpgradeType.PIERCE || choice.upgrade == UpgradeType.BURST) specialBonus else statBonus
    val scale = 0.35f
    return normalizeWeaponStats(when (choice.upgrade) {
        UpgradeType.DMG -> {
            val dmgScale = weaponDmgScale(w.type)
            w.copy(level = w.level + 1, damage = w.damage + max(1, (mult * scale * dmgScale).toInt()))
        }
        UpgradeType.COUNT -> {
            if (w.type == WeaponType.LASER) w.copy(level = w.level + 1, laserDurationMs = w.laserDurationMs + (60L * mult * scale).toLong())
            else w.copy(level = w.level + 1, bulletCount = w.bulletCount + max(1, (mult * scale).toInt()))
        }
        UpgradeType.RATE -> {
            if (w.type == WeaponType.LASER) {
                w.copy(level = w.level + 1, fireRateMs = max(420L, w.fireRateMs - (6L * mult * scale).toLong()))
            } else {
                w.copy(level = w.level + 1, fireRateMs = max(180L, w.fireRateMs - (10L * mult * scale).toLong()))
            }
        }
        UpgradeType.LASER_TIME -> w.copy(level = w.level + 1, laserDurationMs = w.laserDurationMs + (50L * mult * scale).toLong())
        UpgradeType.RANGE -> {
            if (w.type == WeaponType.LASER) {
                val addLaser = max(2f, 4f * mult * scale)
                w.copy(
                    level = w.level + 1,
                    laserWidth = w.laserWidth + addLaser
                )
            } else {
                w.copy(level = w.level + 1)
            }
        }
        UpgradeType.PIERCE -> w.copy(level = w.level + 1, pierce = w.pierce + max(1, (mult * scale).toInt()))
        UpgradeType.BURST -> w.copy(level = w.level + 1, burstCount = w.burstCount + max(1, (mult * scale).toInt()))
        UpgradeType.LEGENDARY_SPECIAL -> {
            when (w.type) {
                WeaponType.MULTI, WeaponType.SPREAD3 -> {
                    if (w.legendarySplash) w.copy(level = w.level + 1, damage = w.damage + 1)
                    else w.copy(level = w.level + 1, legendarySplash = true)
                }
                WeaponType.HOMING -> {
                    if (w.legendarySuperHoming) w.copy(level = w.level + 1, damage = w.damage + 1)
                    else w.copy(level = w.level + 1, legendarySuperHoming = true)
                }
                WeaponType.LASER -> {
                    if (w.legendaryShardLaser) w.copy(level = w.level + 1, laserWidth = w.laserWidth + 2f)
                    else w.copy(level = w.level + 1, legendaryShardLaser = true)
                }
            }
        }
    })
}

private fun drawGateIcon(gate: Gate, rect: Rect, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val iconY = rect.center.y - rect.height * 0.12f
    val iconX = rect.center.x
    val size = rect.height * 0.36f
    val stroke = 5f
    with(scope) {
        when (gate.kind) {
            GateKind.WEAPON -> {
                when (gate.weapon!!) {
                    WeaponType.MULTI -> drawArrowBundle(iconX, iconY, size, stroke, this)
                    WeaponType.SPREAD3 -> drawSpread(iconX, iconY, size, stroke, this)
                    WeaponType.HOMING -> drawTarget(iconX, iconY, size, stroke, this)
                    WeaponType.LASER -> drawBeam(iconX, iconY, size, stroke, this)
                }
            }
            GateKind.UPGRADE -> {
                when (gate.upgrade!!) {
                    UpgradeType.DMG -> drawStar(iconX, iconY, size, this)
                    UpgradeType.COUNT -> drawArrowBundle(iconX, iconY, size, stroke, this)
                    UpgradeType.RATE -> drawHourglass(iconX, iconY, size, stroke, this)
                    UpgradeType.LASER_TIME -> drawHourglass(iconX, iconY, size, stroke, this)
                    UpgradeType.RANGE -> drawRangeIcon(iconX, iconY, size, stroke, this)
                    UpgradeType.PIERCE -> drawPierce(iconX, iconY, size, stroke, this)
                    UpgradeType.BURST -> drawBurst(iconX, iconY, size, stroke, this)
                    UpgradeType.LEGENDARY_SPECIAL -> drawStar(iconX, iconY, size, this)
                }
            }
        }
    }
}

private fun drawRangeIcon(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    scope.drawCircle(icon, size * 0.35f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
    scope.drawCircle(icon, size * 0.18f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.8f))
}

private fun drawArrowBundle(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    val offsets = listOf(-size * 0.18f, 0f, size * 0.18f)
    offsets.forEach { dx ->
        scope.drawLine(icon, Offset(x + dx, y + size * 0.35f), Offset(x + dx, y - size * 0.3f), stroke)
        scope.drawLine(icon, Offset(x + dx, y - size * 0.3f), Offset(x + dx - size * 0.08f, y - size * 0.15f), stroke)
        scope.drawLine(icon, Offset(x + dx, y - size * 0.3f), Offset(x + dx + size * 0.08f, y - size * 0.15f), stroke)
    }
}

private fun drawSpread(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    scope.drawLine(icon, Offset(x, y + size * 0.35f), Offset(x, y - size * 0.3f), stroke)
    scope.drawLine(icon, Offset(x - size * 0.22f, y + size * 0.3f), Offset(x - size * 0.45f, y - size * 0.1f), stroke)
    scope.drawLine(icon, Offset(x + size * 0.22f, y + size * 0.3f), Offset(x + size * 0.45f, y - size * 0.1f), stroke)
}

private fun drawTarget(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    scope.drawCircle(icon, size * 0.35f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
    scope.drawCircle(icon, size * 0.12f, Offset(x, y))
}

private fun drawBeam(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    scope.drawRect(icon, Offset(x - size * 0.12f, y - size * 0.35f), androidx.compose.ui.geometry.Size(size * 0.24f, size * 0.7f))
}

private fun drawHourglass(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val icon = Color(0xFFFFE2A6)
    scope.drawLine(icon, Offset(x - size * 0.28f, y - size * 0.3f), Offset(x + size * 0.28f, y - size * 0.3f), stroke)
    scope.drawLine(icon, Offset(x - size * 0.28f, y + size * 0.3f), Offset(x + size * 0.28f, y + size * 0.3f), stroke)
    scope.drawLine(icon, Offset(x - size * 0.28f, y - size * 0.3f), Offset(x + size * 0.28f, y + size * 0.3f), stroke)
    scope.drawLine(icon, Offset(x + size * 0.28f, y - size * 0.3f), Offset(x - size * 0.28f, y + size * 0.3f), stroke)
}

private fun drawPierce(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    scope.drawLine(Color.White, Offset(x - size * 0.4f, y), Offset(x + size * 0.4f, y), stroke)
    scope.drawLine(Color.White, Offset(x + size * 0.4f, y), Offset(x + size * 0.28f, y - size * 0.14f), stroke)
    scope.drawLine(Color.White, Offset(x + size * 0.4f, y), Offset(x + size * 0.28f, y + size * 0.14f), stroke)
    scope.drawLine(Color.White, Offset(x - size * 0.1f, y - size * 0.35f), Offset(x - size * 0.1f, y + size * 0.35f), stroke)
}

private fun drawBurst(x: Float, y: Float, size: Float, stroke: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val dy = size * 0.18f
    scope.drawLine(Color.White, Offset(x - size * 0.35f, y - dy), Offset(x + size * 0.35f, y - dy), stroke)
    scope.drawLine(Color.White, Offset(x - size * 0.4f, y), Offset(x + size * 0.4f, y), stroke)
    scope.drawLine(Color.White, Offset(x - size * 0.35f, y + dy), Offset(x + size * 0.35f, y + dy), stroke)
}

private fun drawStar(x: Float, y: Float, size: Float, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    scope.drawLine(Color.White, Offset(x - size * 0.25f, y), Offset(x + size * 0.25f, y), 4f)
    scope.drawLine(Color.White, Offset(x, y - size * 0.25f), Offset(x, y + size * 0.25f), 4f)
    scope.drawLine(Color.White, Offset(x - size * 0.18f, y - size * 0.18f), Offset(x + size * 0.18f, y + size * 0.18f), 4f)
    scope.drawLine(Color.White, Offset(x - size * 0.18f, y + size * 0.18f), Offset(x + size * 0.18f, y - size * 0.18f), 4f)
}

private fun circleHit(a: Offset, ar: Float, b: Offset, br: Float): Boolean {
    return hypot(a.x - b.x, a.y - b.y) <= ar + br
}

private fun circleRectHit(center: Offset, radius: Float, rect: Rect): Boolean {
    val closestX = center.x.coerceIn(rect.left, rect.right)
    val closestY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - closestX
    val dy = center.y - closestY
    return dx * dx + dy * dy <= radius * radius
}

private fun circleSegmentHit(center: Offset, radius: Float, a: Offset, b: Offset, segHalfWidth: Float): Boolean {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val apx = center.x - a.x
    val apy = center.y - a.y
    val ab2 = abx * abx + aby * aby
    val t = if (ab2 <= 0.0001f) 0f else ((apx * abx + apy * aby) / ab2).coerceIn(0f, 1f)
    val cx = a.x + abx * t
    val cy = a.y + aby * t
    val dx = center.x - cx
    val dy = center.y - cy
    val rr = radius + segHalfWidth
    return dx * dx + dy * dy <= rr * rr
}

private fun drawOutlinedText(
    canvas: android.graphics.Canvas,
    stroke: android.graphics.Paint,
    fill: android.graphics.Paint,
    text: String,
    x: Float,
    y: Float,
    size: Float,
    align: android.graphics.Paint.Align
) {
    stroke.textSize = size
    fill.textSize = size
    stroke.textAlign = align
    fill.textAlign = align
    canvas.drawText(text, x, y, stroke)
    canvas.drawText(text, x, y, fill)
}

private fun spawnBurst(pos: Offset, color: Color): List<Particle> {
    val list = ArrayList<Particle>(6)
    val count = 6
    for (i in 0 until count) {
        val angle = (i * (360f / count)) * (Math.PI / 180f)
        val speed = 1.5f + (i % 3) * 0.6f
        val vx = (cos(angle) * speed).toFloat()
        val vy = (sin(angle) * speed).toFloat()
        list.add(Particle(pos, Offset(vx, vy), color, 220L))
    }
    return list
}

private fun isOnScreen(pos: Offset, height: Float): Boolean {
    return pos.y >= -80f && pos.y <= height + 80f
}

private fun isRectOnScreen(rect: Rect, height: Float): Boolean {
    return rect.bottom >= -80f && rect.top <= height + 80f
}

private fun hash01(i: Int, seed: Int): Float {
    val v = (i * 374761393 + seed * 668265263) xor (i shl 13)
    val n = (v * (v * v * 15731 + 789221) + 1376312589) and 0x7fffffff
    return (n % 1000) / 1000f
}

private fun generateBackground(
    trees: MutableList<BgTree>,
    bushes: MutableList<BgBush>,
    animals: MutableList<BgAnimal>,
    pebbles: MutableList<BgPebble>,
    sticks: MutableList<BgStick>,
    rocks: MutableList<BgRock>,
    fogs: MutableList<BgFog>,
    mushrooms: MutableList<BgMushroom>,
    lavaCracks: MutableList<BgLavaCrack>,
    ash: MutableList<BgAsh>,
    stumps: MutableList<BgStump>,
    skulls: MutableList<BgSkull>,
    lavaPillars: MutableList<BgLavaPillar>,
    pathLeft: Float,
    pathRight: Float,
    height: Float,
    stageIndex: Int
) {
    val theme = stageTheme(stageIndex)
    val pathWidth = pathRight - pathLeft
    val worldLen = height * 20f
    val rng = Random(1337)

    // Trees
    var y = -height
    while (y > -worldLen) {
        val treeChance = when (theme.name) {
            "SWAMP" -> 0.55f
            "VOLCANO" -> 0.75f
            else -> 0.35f
        }
        if (rng.nextFloat() > treeChance) {
            val size = 20f + rng.nextFloat() * 28f
            val leftX = pathLeft * (0.2f + rng.nextFloat() * 0.6f)
            trees.add(BgTree(Offset(leftX, y), size))
        }
        if (rng.nextFloat() > (treeChance + 0.1f).coerceAtMost(0.9f)) {
            val size = 22f + rng.nextFloat() * 28f
            val rightX = pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.4f)
            trees.add(BgTree(Offset(rightX, y + 26f), size))
        }
        y -= 220f + rng.nextFloat() * 120f
    }

    // Bushes
    y = -height
    while (y > -worldLen) {
        val bushChance = when (theme.name) {
            "SWAMP" -> 0.25f
            "VOLCANO" -> 0.65f
            else -> 0.4f
        }
        if (rng.nextFloat() > bushChance) {
            val size = 12f + rng.nextFloat() * 10f
            val leftX = pathLeft * (0.25f + rng.nextFloat() * 0.6f)
            bushes.add(BgBush(Offset(leftX, y), size))
        }
        if (rng.nextFloat() > (bushChance + 0.1f).coerceAtMost(0.9f)) {
            val size = 12f + rng.nextFloat() * 10f
            val rightX = pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.5f)
            bushes.add(BgBush(Offset(rightX, y + 18f), size))
        }
        y -= 260f + rng.nextFloat() * 160f
    }

    // Animals
    y = -height
    while (y > -worldLen) {
        val animalChance = when (theme.name) {
            "SWAMP" -> 0.8f
            "VOLCANO" -> 0.9f
            else -> 0.7f
        }
        if (rng.nextFloat() > animalChance) {
            val size = 6f + rng.nextFloat() * 3f
            val leftSide = rng.nextBoolean()
            val x = if (leftSide) {
                pathLeft * (0.3f + rng.nextFloat() * 0.5f)
            } else {
                pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.4f)
            }
            animals.add(BgAnimal(Offset(x, y), size))
        }
        y -= 320f + rng.nextFloat() * 200f
    }

    // Pebbles
    y = -height
    while (y > -worldLen) {
        val count = when (theme.name) {
            "VOLCANO" -> 3 + rng.nextInt(4)
            else -> 2 + rng.nextInt(3)
        }
        repeat(count) {
            val size = 3.5f + rng.nextFloat() * 4f
            val x = pathLeft + 24f + rng.nextFloat() * (pathWidth - 48f)
            pebbles.add(BgPebble(Offset(x, y), size))
        }
        y -= 160f + rng.nextFloat() * 120f
    }

    // Sticks
    y = -height
    while (y > -worldLen) {
        if (rng.nextFloat() > 0.4f) {
            val size = 16f + rng.nextFloat() * 12f
            val x = pathLeft + 30f + rng.nextFloat() * (pathWidth - 60f)
            val angle = (rng.nextFloat() * 0.6f - 0.3f)
            sticks.add(BgStick(Offset(x, y), size, angle))
        }
        y -= 220f + rng.nextFloat() * 160f
    }

    // Rocks
    y = -height
    while (y > -worldLen) {
        val rockChance = when (theme.name) {
            "VOLCANO" -> 0.2f
            "SWAMP" -> 0.5f
            else -> 0.35f
        }
        if (rng.nextFloat() > rockChance) {
            val size = 6f + rng.nextFloat() * 8f
            val x = pathLeft + 28f + rng.nextFloat() * (pathWidth - 56f)
            rocks.add(BgRock(Offset(x, y), size))
        }
        y -= 240f + rng.nextFloat() * 160f
    }

    // Theme-specific objects
    when (theme.name) {
        "SWAMP" -> {
            // Fog patches on path
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.45f) {
                    val size = 60f + rng.nextFloat() * 80f
                    val x = pathLeft + 40f + rng.nextFloat() * (pathWidth - 80f)
                    fogs.add(BgFog(Offset(x, y), size))
                }
                y -= 200f + rng.nextFloat() * 180f
            }
            // Mushrooms on sides
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.55f) {
                    val size = 8f + rng.nextFloat() * 6f
                    val leftSide = rng.nextBoolean()
                    val x = if (leftSide) {
                        pathLeft * (0.25f + rng.nextFloat() * 0.6f)
                    } else {
                        pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.5f)
                    }
                    mushrooms.add(BgMushroom(Offset(x, y), size))
                }
                y -= 240f + rng.nextFloat() * 160f
            }
            // Dead stumps
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.55f) {
                    val size = 10f + rng.nextFloat() * 12f
                    val leftSide = rng.nextBoolean()
                    val x = if (leftSide) {
                        pathLeft * (0.2f + rng.nextFloat() * 0.6f)
                    } else {
                        pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.4f)
                    }
                    stumps.add(BgStump(Offset(x, y), size))
                }
                y -= 260f + rng.nextFloat() * 180f
            }
            // Skulls
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.7f) {
                    val size = 6f + rng.nextFloat() * 6f
                    val x = pathLeft + 30f + rng.nextFloat() * (pathWidth - 60f)
                    skulls.add(BgSkull(Offset(x, y), size))
                }
                y -= 300f + rng.nextFloat() * 200f
            }
        }
        "VOLCANO" -> {
            // Lava cracks on path
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.35f) {
                    val size = 18f + rng.nextFloat() * 30f
                    val x = pathLeft + 36f + rng.nextFloat() * (pathWidth - 72f)
                    val angle = rng.nextFloat() * 1.2f - 0.6f
                    lavaCracks.add(BgLavaCrack(Offset(x, y), size, angle))
                }
                y -= 220f + rng.nextFloat() * 150f
            }
            // Ash particles (slow drifting)
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.4f) {
                    val size = 2.5f + rng.nextFloat() * 2.5f
                    val x = rng.nextFloat() * (pathRight + (pathRight - pathLeft) * 0.5f)
                    ash.add(BgAsh(Offset(x, y), size))
                }
                y -= 120f + rng.nextFloat() * 120f
            }
            // Lava pillars on sides
            y = -height
            while (y > -worldLen) {
                if (rng.nextFloat() > 0.55f) {
                    val size = 16f + rng.nextFloat() * 18f
                    val heightP = 28f + rng.nextFloat() * 40f
                    val leftSide = rng.nextBoolean()
                    val x = if (leftSide) {
                        pathLeft * (0.2f + rng.nextFloat() * 0.6f)
                    } else {
                        pathRight + (rng.nextFloat() * (pathRight - pathLeft) * 0.4f)
                    }
                    lavaPillars.add(BgLavaPillar(Offset(x, y), size, heightP))
                }
                y -= 260f + rng.nextFloat() * 190f
            }
        }
    }
}
