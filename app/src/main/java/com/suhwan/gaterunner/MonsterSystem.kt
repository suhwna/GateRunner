package com.suhwan.gaterunner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.random.Random

// 원거리 몬스터 투사체 타입(시각/판정용)
enum class EnemyShotKind { SPEAR, AXE, MAGIC_BALL }

// 몬스터 역할군(근접/원거리/엘리트)
enum class MonsterRole { RUSHER, SKIRMISHER, BRUISER, THROWER_SPEAR, THROWER_AXE, CASTER, ELITE_MINI }

// 런타임 몬스터 상태 데이터
data class Monster(
    val id: Int,
    val pos: Offset,
    val radius: Float,
    val spriteRawIndex: Int,
    val role: MonsterRole,
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

// 적 원거리 투사체 데이터
data class EnemyShot(
    val pos: Offset,
    val vel: Offset,
    val radius: Float,
    val kind: EnemyShotKind
)

// 보스 엔티티 데이터(현재 HP, 사용할 스프라이트 인덱스 포함)
data class Boss(
    val rect: Rect,
    var hp: Int,
    val spriteRawIndex: Int = -1
)

// 몬스터 밸런스/라벨 매핑/스프라이트 선택 규칙 집합
object MonsterBalance {
    // 스테이지별 근접 역할 가중치 선택
    fun pickRoleForStage(stage: Int, rng: Random): MonsterRole {
        val roll = rng.nextFloat()
        return when (stage) {
            0 -> when {
                roll < 0.70f -> MonsterRole.RUSHER
                roll < 0.90f -> MonsterRole.SKIRMISHER
                else -> MonsterRole.BRUISER
            }
            1 -> when {
                roll < 0.25f -> MonsterRole.RUSHER
                roll < 0.65f -> MonsterRole.SKIRMISHER
                else -> MonsterRole.BRUISER
            }
            else -> when {
                roll < 0.20f -> MonsterRole.RUSHER
                roll < 0.55f -> MonsterRole.SKIRMISHER
                else -> MonsterRole.BRUISER
            }
        }
    }

    // 스테이지별 원거리 역할 가중치 선택
    fun pickRangedRoleForStage(stage: Int, rng: Random): MonsterRole {
        val roll = rng.nextFloat()
        return when (stage) {
            0 -> if (roll < 0.70f) MonsterRole.THROWER_SPEAR else MonsterRole.THROWER_AXE
            1 -> if (roll < 0.55f) MonsterRole.THROWER_AXE else MonsterRole.CASTER
            else -> if (roll < 0.40f) MonsterRole.THROWER_SPEAR else MonsterRole.CASTER
        }
    }

    // 역할별 기본 HP 계산(스테이지, 루프 배수 반영)
    fun hpForRole(role: MonsterRole, stage: Int, hpMult: Int): Int {
        val hp = when (role) {
            MonsterRole.RUSHER -> 34 + stage * 20
            MonsterRole.SKIRMISHER -> 30 + stage * 18
            MonsterRole.BRUISER -> 62 + stage * 32
            MonsterRole.THROWER_SPEAR -> ((42 + stage * 24) * 0.85f).toInt()
            MonsterRole.THROWER_AXE -> ((46 + stage * 26) * 0.9f).toInt()
            MonsterRole.CASTER -> ((40 + stage * 22) * 0.8f).toInt()
            MonsterRole.ELITE_MINI -> (86 + stage * 42) * 2
        }
        return max(1, hp * hpMult)
    }

    // 역할별 발사 투사체 종류 매핑
    fun shotKindForRole(role: MonsterRole): EnemyShotKind {
        return when (role) {
            MonsterRole.CASTER -> EnemyShotKind.MAGIC_BALL
            MonsterRole.THROWER_AXE -> EnemyShotKind.AXE
            else -> EnemyShotKind.SPEAR
        }
    }

    // 원거리 역할 여부 판정
    fun isRangedRole(role: MonsterRole): Boolean {
        return role == MonsterRole.THROWER_SPEAR || role == MonsterRole.THROWER_AXE || role == MonsterRole.CASTER
    }

    // 스테이지별 보스 전용 라벨 고정값
    fun bossLabelForStage(stage: Int): String {
        return when (stage % 3) {
            0 -> "B2"
            1 -> "I3"
            else -> "L1"
        }
    }

    // 라벨 -> 스프라이트 인덱스 조회
    fun monsterSpriteIndexByLabel(spriteLabels: List<String>, label: String): Int {
        return spriteLabels.indexOfFirst { it.equals(label, ignoreCase = true) }
    }

    // 스테이지/역할에 맞는 몬스터 스프라이트 인덱스 선택
    // - 보스 예약 라벨(B2/I3/L1)은 일반 몬스터에서 제외
    // - 후보가 없으면 예약 라벨 제외 풀에서 폴백 선택
    fun pickMonsterRawSpriteIndex(
        stage: Int,
        role: MonsterRole,
        rng: Random,
        spriteLabels: List<String>
    ): Int {
        if (spriteLabels.isEmpty()) return -1
        val reservedBossLabel = bossLabelForStage(stage)
        val byLabel = spriteLabels.mapIndexed { i, label -> i to label }.toMap()

        fun exact(label: String): Int? = byLabel.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key
        fun byLabels(labels: List<String>): List<Int> = labels.mapNotNull { exact(it) }
        fun pick(candidates: List<Int>): Int {
            val filtered = candidates.filter { idx ->
                !spriteLabels[idx].equals(reservedBossLabel, ignoreCase = true)
            }
            if (filtered.isNotEmpty()) return filtered[rng.nextInt(filtered.size)]
            val fallback = spriteLabels.indices.filter { idx ->
                !spriteLabels[idx].equals(reservedBossLabel, ignoreCase = true)
            }
            return if (fallback.isNotEmpty()) fallback[rng.nextInt(fallback.size)] else rng.nextInt(spriteLabels.size)
        }

        // 스테이지 1(숲) 라벨 풀
        val s1Melee = listOf("A3", "A4", "A5")
        val s1Spear = listOf("A2")
        val s1Axe = listOf("B2")
        val s1Caster = listOf("C2")

        // 스테이지 2(늪) 라벨 풀
        val s2Rusher = listOf("C1", "C2", "C3")
        val s2Skirm = listOf("C4", "C5", "D2")
        val s2Bruiser = listOf("D3", "D4", "E1")
        val s2Spear = listOf("C6", "D1")
        val s2Axe = listOf("D5", "E2")
        val s2Caster = listOf("D6", "E3")

        // 스테이지 3(화산) 라벨 풀
        val s3Rusher = listOf("E2", "E3", "E4")
        val s3Skirm = listOf("E5", "F1", "F2")
        val s3Bruiser = listOf("F3", "F4", "G1")
        val s3Spear = listOf("E7", "F5")
        val s3Axe = listOf("F6", "G2")
        val s3Caster = listOf("G3", "G4")

        return when (stage % 3) {
            0 -> when (role) {
                MonsterRole.THROWER_SPEAR -> pick(byLabels(s1Spear))
                MonsterRole.THROWER_AXE -> pick(byLabels(s1Axe.ifEmpty { s1Spear }))
                MonsterRole.CASTER -> pick(byLabels(s1Caster.ifEmpty { s1Spear }))
                MonsterRole.RUSHER, MonsterRole.SKIRMISHER, MonsterRole.BRUISER -> pick(byLabels(s1Melee))
                MonsterRole.ELITE_MINI -> pick(byLabels(listOf("F1", "F2", "F3")))
            }
            1 -> when (role) {
                MonsterRole.RUSHER -> pick(byLabels(s2Rusher))
                MonsterRole.SKIRMISHER -> pick(byLabels(s2Skirm))
                MonsterRole.BRUISER -> pick(byLabels(s2Bruiser))
                MonsterRole.THROWER_SPEAR -> pick(byLabels(s2Spear))
                MonsterRole.THROWER_AXE -> pick(byLabels(s2Axe))
                MonsterRole.CASTER -> pick(byLabels(s2Caster))
                MonsterRole.ELITE_MINI -> pick(byLabels(listOf("H1", "H2", "H3")))
            }
            else -> when (role) {
                MonsterRole.RUSHER -> pick(byLabels(s3Rusher))
                MonsterRole.SKIRMISHER -> pick(byLabels(s3Skirm))
                MonsterRole.BRUISER -> pick(byLabels(s3Bruiser))
                MonsterRole.THROWER_SPEAR -> pick(byLabels(s3Spear))
                MonsterRole.THROWER_AXE -> pick(byLabels(s3Axe))
                MonsterRole.CASTER -> pick(byLabels(s3Caster))
                MonsterRole.ELITE_MINI -> pick(byLabels(listOf("I1", "I2", "I3")))
            }
        }
    }
}
