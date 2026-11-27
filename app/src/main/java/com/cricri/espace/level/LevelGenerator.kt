package com.cricri.espace.level

import com.cricri.espace.model.*
import java.util.Random
import kotlin.math.min

object LevelGenerator {

    private val levelPrefixes = listOf(
        "Dune d'Hossegor", "Bastion de Bayonne", "Tour de Bidache", "Phare de Biarritz",
        "Rempart d'Itxassou", "Forêt de Bénesse", "Donjon d'Orthez", "Vague de Capbreton",
        "Citadelle de Saint-Jean", "Pic d'Anie", "Courant d'Huchet", "Château de Pau",
        "Nébuleuse d'Anglet", "Anneau d'Espelette", "Quai de l'Adour", "Horizon Landifornia"
    )

    fun generate(levelNumber: Int): LevelBlueprint {
        val clamped = levelNumber.coerceIn(1, 99)
        val theme = BiomeTheme.forLevel(clamped)
        val rng = Random(clamped * 104729L + 4242L)

        val prefix = levelPrefixes[(clamped - 1) % levelPrefixes.size]
        val isBossLevel = clamped % 10 == 0 || clamped == 99
        val title = if (isBossLevel) "BOSS Niv. $clamped — $prefix" else "Niv. $clamped — $prefix"

        if (clamped <= 3) {
            return buildOpeningLevels(clamped, theme, title)
        }

        val difficulty = (clamped - 1) / 98f
        val blocks = mutableListOf<BlockBlueprint>()
        val enemies = mutableListOf<EnemyBlueprint>()

        val baseX = 18.2f
        if (isBossLevel) {
            buildBossCitadel(blocks, enemies, baseX, difficulty, rng)
        } else {
            val archetype = (clamped * 3 + rng.nextInt(4)) % 7
            when (archetype) {
                0 -> buildArmoredTwinTowers(blocks, enemies, baseX, difficulty, rng)
                1 -> buildBasqueBunkerHouse(blocks, enemies, baseX, difficulty, rng)
                2 -> buildMedievalCastleKeep(blocks, enemies, baseX, difficulty, rng)
                3 -> buildShieldedOrbitalBridge(blocks, enemies, baseX, difficulty, rng)
                4 -> buildSteppedPyramid(blocks, enemies, baseX, difficulty, rng)
                5 -> buildSuspendedCitadel(blocks, enemies, baseX, difficulty, rng)
                else -> buildPendulumVault(blocks, enemies, baseX, difficulty, rng)
            }
        }

        if (enemies.isEmpty()) {
            enemies.add(EnemyBlueprint(baseX + 2.2f, 0.8f, EnemyTier.KNIGHT_CAPTAIN))
        }

        val projectiles = buildProjectileLineup(clamped, enemies.size, isBossLevel, rng)

        val potentialBlockScore = blocks.filter { !it.isStatic }.sumOf { it.material.scoreValue }
        val potentialEnemyScore = enemies.sumOf { it.tier.scoreValue }
        val threeStarScore = ((potentialEnemyScore + potentialBlockScore * 0.55f) + 1200).toInt()

        return LevelBlueprint(
            levelNumber = clamped,
            theme = theme,
            title = title,
            projectiles = projectiles,
            blocks = blocks,
            enemies = enemies,
            threeStarScore = threeStarScore
        )
    }

    private fun buildOpeningLevels(level: Int, theme: BiomeTheme, title: String): LevelBlueprint {
        val blocks = mutableListOf<BlockBlueprint>()
        val enemies = mutableListOf<EnemyBlueprint>()
        val baseX = 18.2f

        return when (level) {
            1 -> {
                // Tactical double-wall bunker right from Level 1
                blocks.add(BlockBlueprint(baseX - 1.1f, 1.15f, 0.42f, 2.3f, BlockMaterial.GLASS))
                blocks.add(BlockBlueprint(baseX, 1.3f, 0.48f, 2.6f, BlockMaterial.WOOD))
                blocks.add(BlockBlueprint(baseX + 2.8f, 1.3f, 0.48f, 2.6f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 1.4f, 2.82f, 3.5f, 0.44f, BlockMaterial.WOOD))
                blocks.add(BlockBlueprint(baseX + 1.4f, 3.65f, 0.45f, 1.2f, BlockMaterial.STONE))
                enemies.add(EnemyBlueprint(baseX + 1.4f, 0.6f, EnemyTier.KNIGHT_CAPTAIN))
                enemies.add(EnemyBlueprint(baseX + 4.1f, 0.55f, EnemyTier.CORSAIR_GRUNT))
                LevelBlueprint(
                    1, theme, title,
                    listOf(ProjectileType.CRICRI_BERET, ProjectileType.POMME_DE_PIN, ProjectileType.CRICRI_BERET),
                    blocks, enemies, 4200
                )
            }
            2 -> {
                // Shield pylon + stone guard wall
                blocks.add(BlockBlueprint(baseX - 1.2f, 1.4f, 0.46f, 2.8f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 0.4f, 1.35f, 0.48f, 2.7f, BlockMaterial.WOOD))
                blocks.add(BlockBlueprint(baseX + 3.2f, 1.35f, 0.48f, 2.7f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 1.8f, 2.92f, 3.5f, 0.45f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 1.8f, 3.65f, 0.72f, 0.9f, BlockMaterial.SHIELD_PYLON))
                enemies.add(EnemyBlueprint(baseX + 1.8f, 0.65f, EnemyTier.SPACE_BARON))
                enemies.add(EnemyBlueprint(baseX + 4.6f, 0.6f, EnemyTier.KNIGHT_CAPTAIN))
                LevelBlueprint(
                    2, theme, title,
                    listOf(ProjectileType.CRICRI_BERET, ProjectileType.PIMENT_ORBITAL, ProjectileType.TONNEAU_ARMAGNAC),
                    blocks, enemies, 6800
                )
            }
            else -> {
                // Two-story fortified tower with armored front & internal explosive core
                blocks.add(BlockBlueprint(baseX - 1.3f, 1.55f, 0.48f, 3.1f, BlockMaterial.METAL))
                blocks.add(BlockBlueprint(baseX + 0.3f, 1.35f, 0.48f, 2.7f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 3.3f, 1.35f, 0.48f, 2.7f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 1.8f, 2.92f, 3.7f, 0.46f, BlockMaterial.STONE))
                blocks.add(BlockBlueprint(baseX + 0.9f, 0.42f, 0.68f, 0.78f, BlockMaterial.EXPLOSIVE))
                enemies.add(EnemyBlueprint(baseX + 2.2f, 0.65f, EnemyTier.SPACE_BARON))

                val y2 = 3.15f
                blocks.add(BlockBlueprint(baseX + 0.7f, y2 + 0.95f, 0.42f, 1.9f, BlockMaterial.WOOD))
                blocks.add(BlockBlueprint(baseX + 2.9f, y2 + 0.95f, 0.42f, 1.9f, BlockMaterial.WOOD))
                blocks.add(BlockBlueprint(baseX + 1.8f, y2 + 2.1f, 2.9f, 0.42f, BlockMaterial.GLASS))
                enemies.add(EnemyBlueprint(baseX + 1.8f, y2 + 0.6f, EnemyTier.KNIGHT_CAPTAIN))
                LevelBlueprint(
                    3, theme, title,
                    listOf(
                        ProjectileType.TONNEAU_ARMAGNAC,
                        ProjectileType.CRICRI_BERET,
                        ProjectileType.PIMENT_ORBITAL,
                        ProjectileType.POMME_DE_PIN
                    ),
                    blocks, enemies, 7900
                )
            }
        }
    }

    private fun pickMaterial(difficulty: Float, rng: Random, allowMetal: Boolean = true): BlockMaterial {
        val roll = rng.nextFloat() + difficulty * 0.62f
        return when {
            roll < 0.22f -> BlockMaterial.GLASS
            roll < 0.52f -> BlockMaterial.WOOD
            roll < 0.95f || !allowMetal -> BlockMaterial.STONE
            else -> BlockMaterial.METAL
        }
    }

    private fun pickEnemyTier(difficulty: Float, rng: Random): EnemyTier {
        val roll = rng.nextFloat() + difficulty * 0.70f
        return when {
            roll < 0.40f -> EnemyTier.CORSAIR_GRUNT
            roll < 0.90f -> EnemyTier.KNIGHT_CAPTAIN
            else -> EnemyTier.SPACE_BARON
        }
    }

    private fun buildBossCitadel(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        // Outer titanium shield wall + glass weak joint
        blocks.add(BlockBlueprint(baseX - 1.8f, 1.65f, 0.52f, 3.3f, BlockMaterial.METAL))
        blocks.add(BlockBlueprint(baseX - 1.8f, 3.85f, 0.44f, 1.1f, BlockMaterial.GLASS))

        // Main Dreadnought bunker
        val bx = baseX - 0.1f
        blocks.add(BlockBlueprint(bx, 1.55f, 0.54f, 3.1f, BlockMaterial.METAL))
        blocks.add(BlockBlueprint(bx + 3.8f, 1.55f, 0.54f, 3.1f, BlockMaterial.METAL))
        blocks.add(BlockBlueprint(bx + 1.9f, 3.35f, 4.6f, 0.52f, BlockMaterial.METAL))
        enemies.add(EnemyBlueprint(bx + 1.9f, 0.8f, EnemyTier.DREADNOUGHT_BOSS))

        // Upper shield generator + captain
        val y2 = 3.61f
        blocks.add(BlockBlueprint(bx + 0.5f, y2 + 1.05f, 0.46f, 2.1f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(bx + 3.3f, y2 + 1.05f, 0.46f, 2.1f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(bx + 1.9f, y2 + 2.32f, 3.5f, 0.45f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(bx + 1.1f, y2 + 0.48f, 0.72f, 0.9f, BlockMaterial.SHIELD_PYLON))
        enemies.add(EnemyBlueprint(bx + 2.6f, y2 + 0.6f, EnemyTier.KNIGHT_CAPTAIN))

        // Rear artillery tower
        val rx = baseX + 5.2f
        blocks.add(BlockBlueprint(rx, 1.3f, 0.48f, 2.6f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(rx + 2.4f, 1.3f, 0.48f, 2.6f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(rx + 1.2f, 2.82f, 3.0f, 0.44f, BlockMaterial.WOOD))
        blocks.add(BlockBlueprint(rx + 0.6f, 0.42f, 0.68f, 0.78f, BlockMaterial.EXPLOSIVE))
        enemies.add(EnemyBlueprint(rx + 1.7f, 0.65f, EnemyTier.SPACE_BARON))
        if (difficulty > 0.3f && rng.nextBoolean()) {
            enemies.add(EnemyBlueprint(rx + 1.2f, 3.6f, EnemyTier.KNIGHT_CAPTAIN))
        }
    }

    private fun buildArmoredTwinTowers(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        // Front barricade
        val frontMat = if (difficulty > 0.45f) BlockMaterial.METAL else BlockMaterial.STONE
        blocks.add(BlockBlueprint(baseX - 1.4f, 1.55f, 0.46f, 3.1f, frontMat))
        blocks.add(BlockBlueprint(baseX - 1.4f, 3.65f, 0.40f, 1.1f, BlockMaterial.GLASS))

        val floors = 2 + (difficulty * 1.8f).toInt().coerceAtMost(1)
        val towerOffsets = listOf(0f, 5.2f)

        for ((idx, offset) in towerOffsets.withIndex()) {
            val tx = baseX + offset
            var curY = 0f
            val mat = pickMaterial(difficulty + 0.12f, rng)
            for (f in 0 until floors) {
                val pillarH = 2.15f
                blocks.add(BlockBlueprint(tx, curY + pillarH / 2f, 0.48f, pillarH, mat))
                blocks.add(BlockBlueprint(tx + 2.5f, curY + pillarH / 2f, 0.48f, pillarH, mat))
                blocks.add(BlockBlueprint(tx + 1.25f, curY + pillarH + 0.22f, 3.15f, 0.44f, pickMaterial(difficulty, rng)))

                if (f == 0 && idx == 0 && rng.nextFloat() < 0.45f) {
                    blocks.add(BlockBlueprint(tx + 1.25f, curY + 0.46f, 0.72f, 0.88f, BlockMaterial.SHIELD_PYLON))
                } else {
                    enemies.add(EnemyBlueprint(tx + 1.25f, curY + 0.6f, pickEnemyTier(difficulty, rng)))
                }
                curY += pillarH + 0.44f
            }
            blocks.add(BlockBlueprint(tx + 1.25f, curY + 0.6f, 0.44f, 1.2f, BlockMaterial.STONE))
        }
    }

    private fun buildBasqueBunkerHouse(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        val span = 3.2f
        val h1 = 2.35f
        val wallMat = if (difficulty > 0.35f) BlockMaterial.STONE else BlockMaterial.WOOD

        for (bay in 0..1) {
            val bx = baseX + bay * (span + 0.65f)
            blocks.add(BlockBlueprint(bx, h1 / 2f, 0.48f, h1, wallMat))
            blocks.add(BlockBlueprint(bx + span, h1 / 2f, 0.48f, h1, wallMat))
            blocks.add(BlockBlueprint(bx + span / 2f, h1 + 0.23f, span + 0.65f, 0.46f, BlockMaterial.STONE))

            enemies.add(EnemyBlueprint(bx + span * 0.35f, 0.6f, pickEnemyTier(difficulty + 0.1f, rng)))
            if (bay == 1) {
                blocks.add(BlockBlueprint(bx + span * 0.74f, 0.42f, 0.68f, 0.78f, BlockMaterial.EXPLOSIVE))
            }
        }

        val upperY = h1 + 0.46f
        val upperH = 2.05f
        blocks.add(BlockBlueprint(baseX + 1.3f, upperY + upperH / 2f, 0.46f, upperH, BlockMaterial.WOOD))
        blocks.add(BlockBlueprint(baseX + 5.7f, upperY + upperH / 2f, 0.46f, upperH, BlockMaterial.WOOD))
        blocks.add(BlockBlueprint(baseX + 3.5f, upperY + upperH + 0.23f, 5.1f, 0.46f, pickMaterial(difficulty + 0.1f, rng)))
        enemies.add(EnemyBlueprint(baseX + 2.8f, upperY + 0.6f, EnemyTier.SPACE_BARON))
        blocks.add(BlockBlueprint(baseX + 4.4f, upperY + 0.48f, 0.72f, 0.88f, BlockMaterial.SHIELD_PYLON))
    }

    private fun buildMedievalCastleKeep(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        val coreMat = if (difficulty > 0.45f) BlockMaterial.METAL else BlockMaterial.STONE
        blocks.add(BlockBlueprint(baseX - 1.25f, 1.65f, 0.54f, 3.3f, coreMat))
        blocks.add(BlockBlueprint(baseX - 1.25f, 3.85f, 0.44f, 1.1f, BlockMaterial.STONE))

        val kx = baseX + 0.35f
        blocks.add(BlockBlueprint(kx, 1.45f, 0.54f, 2.9f, coreMat))
        blocks.add(BlockBlueprint(kx + 3.3f, 1.45f, 0.54f, 2.9f, coreMat))
        blocks.add(BlockBlueprint(kx + 1.65f, 3.13f, 4.0f, 0.48f, coreMat))

        enemies.add(EnemyBlueprint(kx + 1.9f, 0.68f, EnemyTier.SPACE_BARON))
        blocks.add(BlockBlueprint(kx + 0.75f, 0.42f, 0.66f, 0.76f, BlockMaterial.EXPLOSIVE))

        val y2 = 3.37f
        blocks.add(BlockBlueprint(kx + 0.5f, y2 + 0.95f, 0.46f, 1.9f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(kx + 2.8f, y2 + 0.95f, 0.46f, 1.9f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(kx + 1.65f, y2 + 2.12f, 3.0f, 0.44f, BlockMaterial.METAL))
        enemies.add(EnemyBlueprint(kx + 1.65f, y2 + 0.6f, pickEnemyTier(difficulty, rng)))

        val rx = baseX + 5.2f
        blocks.add(BlockBlueprint(rx, 1.3f, 0.48f, 2.6f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(rx + 2.2f, 1.3f, 0.48f, 2.6f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(rx + 1.1f, 2.82f, 2.8f, 0.44f, BlockMaterial.WOOD))
        enemies.add(EnemyBlueprint(rx + 1.1f, 0.6f, pickEnemyTier(difficulty, rng)))
    }

    private fun buildShieldedOrbitalBridge(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        val pillarMat = pickMaterial(difficulty + 0.15f, rng)
        for (i in 0..2) {
            val px = baseX + i * 3.15f
            blocks.add(BlockBlueprint(px, 1.4f, 0.54f, 2.8f, pillarMat))
            if (i < 2) {
                blocks.add(BlockBlueprint(px + 1.57f, 3.02f, 3.75f, 0.46f, BlockMaterial.STONE))
                enemies.add(EnemyBlueprint(px + 1.57f, 0.6f, pickEnemyTier(difficulty, rng)))
            }
        }
        val deckY = 3.25f
        blocks.add(BlockBlueprint(baseX + 1.1f, deckY + 0.9f, 0.42f, 1.8f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(baseX + 3.15f, deckY + 0.9f, 0.42f, 1.8f, BlockMaterial.WOOD))
        blocks.add(BlockBlueprint(baseX + 5.2f, deckY + 0.9f, 0.42f, 1.8f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(baseX + 3.15f, deckY + 2.02f, 4.7f, 0.44f, pickMaterial(difficulty, rng)))

        blocks.add(BlockBlueprint(baseX + 2.1f, deckY + 0.48f, 0.72f, 0.88f, BlockMaterial.SHIELD_PYLON))
        enemies.add(EnemyBlueprint(baseX + 4.1f, deckY + 0.6f, EnemyTier.SPACE_BARON))
    }

    private fun buildSteppedPyramid(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        val m1 = pickMaterial(difficulty + 0.15f, rng)
        val m2 = pickMaterial(difficulty, rng, allowMetal = false)
        for (i in 0..2) {
            val cx = baseX + i * 2.55f
            blocks.add(BlockBlueprint(cx, 1.15f, 0.48f, 2.3f, m1))
            blocks.add(BlockBlueprint(cx + 2.15f, 1.15f, 0.48f, 2.3f, m1))
            blocks.add(BlockBlueprint(cx + 1.07f, 2.5f, 2.65f, 0.42f, m2))
            if (i == 1) {
                blocks.add(BlockBlueprint(cx + 1.07f, 0.42f, 0.66f, 0.76f, BlockMaterial.EXPLOSIVE))
            } else {
                enemies.add(EnemyBlueprint(cx + 1.07f, 0.6f, pickEnemyTier(difficulty, rng)))
            }
        }
        val y2 = 2.71f
        for (i in 0..1) {
            val cx = baseX + 1.27f + i * 2.55f
            blocks.add(BlockBlueprint(cx, y2 + 0.95f, 0.44f, 1.9f, m2))
            blocks.add(BlockBlueprint(cx + 2.15f, y2 + 0.95f, 0.44f, 1.9f, m2))
            blocks.add(BlockBlueprint(cx + 1.07f, y2 + 2.12f, 2.6f, 0.42f, BlockMaterial.STONE))
            enemies.add(EnemyBlueprint(cx + 1.07f, y2 + 0.6f, pickEnemyTier(difficulty + 0.12f, rng)))
        }
        val y3 = y2 + 2.33f
        enemies.add(EnemyBlueprint(baseX + 3.6f, y3 + 0.65f, EnemyTier.SPACE_BARON))
    }

    private fun buildSuspendedCitadel(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        val platX = baseX + 2.5f
        val platY = 4.7f
        blocks.add(BlockBlueprint(platX, platY, 4.8f, 0.56f, BlockMaterial.METAL, isStatic = true))

        blocks.add(BlockBlueprint(platX - 1.6f, platY + 1.22f, 0.44f, 1.9f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(platX + 1.6f, platY + 1.22f, 0.44f, 1.9f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(platX, platY + 2.38f, 3.8f, 0.42f, BlockMaterial.METAL))
        enemies.add(EnemyBlueprint(platX, platY + 0.9f, EnemyTier.SPACE_BARON))

        val gx = baseX + 0.35f
        val gMat = if (difficulty > 0.4f) BlockMaterial.METAL else BlockMaterial.STONE
        blocks.add(BlockBlueprint(gx, 1.3f, 0.5f, 2.6f, gMat))
        blocks.add(BlockBlueprint(gx + 3.7f, 1.3f, 0.5f, 2.6f, gMat))
        blocks.add(BlockBlueprint(gx + 1.85f, 2.82f, 4.3f, 0.46f, gMat))
        enemies.add(EnemyBlueprint(gx + 1.15f, 0.6f, pickEnemyTier(difficulty, rng)))
        enemies.add(EnemyBlueprint(gx + 2.6f, 0.6f, pickEnemyTier(difficulty, rng)))
        blocks.add(BlockBlueprint(gx + 1.85f, 3.48f, 0.72f, 0.88f, BlockMaterial.SHIELD_PYLON))
    }

    private fun buildPendulumVault(
        blocks: MutableList<BlockBlueprint>,
        enemies: MutableList<EnemyBlueprint>,
        baseX: Float,
        difficulty: Float,
        rng: Random
    ) {
        // High central pillar balancing a massive stone lintel over two enemy chambers
        blocks.add(BlockBlueprint(baseX - 0.8f, 1.55f, 0.52f, 3.1f, BlockMaterial.METAL))
        blocks.add(BlockBlueprint(baseX + 2.2f, 1.55f, 0.56f, 3.1f, BlockMaterial.STONE))
        blocks.add(BlockBlueprint(baseX + 5.2f, 1.55f, 0.52f, 3.1f, BlockMaterial.METAL))
        blocks.add(BlockBlueprint(baseX + 2.2f, 3.35f, 6.7f, 0.52f, BlockMaterial.STONE))

        enemies.add(EnemyBlueprint(baseX + 0.7f, 0.65f, EnemyTier.SPACE_BARON))
        enemies.add(EnemyBlueprint(baseX + 3.7f, 0.65f, EnemyTier.KNIGHT_CAPTAIN))

        val topY = 3.61f
        blocks.add(BlockBlueprint(baseX + 0.9f, topY + 0.9f, 0.42f, 1.8f, BlockMaterial.GLASS))
        blocks.add(BlockBlueprint(baseX + 3.5f, topY + 0.9f, 0.42f, 1.8f, BlockMaterial.GLASS))
        blocks.add(BlockBlueprint(baseX + 2.2f, topY + 2.0f, 3.2f, 0.42f, BlockMaterial.WOOD))
        blocks.add(BlockBlueprint(baseX + 1.5f, topY + 0.42f, 0.66f, 0.76f, BlockMaterial.EXPLOSIVE))
        enemies.add(EnemyBlueprint(baseX + 2.8f, topY + 0.6f, pickEnemyTier(difficulty, rng)))
    }

    private fun buildProjectileLineup(
        level: Int,
        enemyCount: Int,
        isBossLevel: Boolean,
        rng: Random
    ): List<ProjectileType> {
        // Tighter, tactical projectile budget so every shot counts!
        val count = if (isBossLevel) 5 else min(5, (enemyCount + 1).coerceIn(3, 4))
        val pool = ProjectileType.values()
        val list = mutableListOf<ProjectileType>()
        list.add(pool[level % pool.size])
        for (i in 1 until count) {
            list.add(pool[(level + i * 2 + rng.nextInt(pool.size)) % pool.size])
        }
        return list
    }
}
