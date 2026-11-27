package com.cricri.espace.model

import org.jbox2d.dynamics.Body

enum class BiomeTheme(
    val title: String,
    val subtitle: String,
    val startLevel: Int,
    val endLevel: Int
) {
    LANDIFORNIA_ORBIT(
        title = "Chapitre I : Côte d'Argent Orbitale",
        subtitle = "Dunes d'Hossegor & Synth-Space",
        startLevel = 1,
        endLevel = 33
    ),
    BAYONNE_NEBULA(
        title = "Chapitre II : Citadelle de Bayonne-Nébuleuse",
        subtitle = "Remparts Féodaux & Colombages Stellaires",
        startLevel = 34,
        endLevel = 66
    ),
    PIC_DANIE_FORTRESS(
        title = "Chapitre III : Forteresse du Pic d'Anie",
        subtitle = "Bastion Cyber-Pyrénéen",
        startLevel = 67,
        endLevel = 99
    );

    companion object {
        fun forLevel(level: Int): BiomeTheme {
            return when {
                level <= 33 -> LANDIFORNIA_ORBIT
                level <= 66 -> BAYONNE_NEBULA
                else -> PIC_DANIE_FORTRESS
            }
        }
    }
}

enum class ProjectileType(
    val displayName: String,
    val powerLabel: String,
    val radius: Float,
    val density: Float,
    val restitution: Float,
    val primaryColor: Int,
    val accentColor: Int
) {
    CRICRI_BERET(
        displayName = "Cricri Béret-Laser",
        powerLabel = "TAP : Perce-Blindage Ionique",
        radius = 0.42f,
        density = 2.4f,
        restitution = 0.32f,
        primaryColor = 0xFFD91E2A.toInt(),
        accentColor = 0xFF00E5FF.toInt()
    ),
    PIMENT_ORBITAL(
        displayName = "Piment d'Espelette",
        powerLabel = "TAP : Charge Thermique Ciblée",
        radius = 0.43f,
        density = 2.5f,
        restitution = 0.22f,
        primaryColor = 0xFFFF3D00.toInt(),
        accentColor = 0xFFFFEA00.toInt()
    ),
    POMME_DE_PIN(
        displayName = "Pomme de Pin Cyber",
        powerLabel = "TAP : Escadrille 3 Drones",
        radius = 0.35f,
        density = 1.75f,
        restitution = 0.42f,
        primaryColor = 0xFF8D6E63.toInt(),
        accentColor = 0xFF76FF03.toInt()
    ),
    TONNEAU_ARMAGNAC(
        displayName = "Tonneau d'Armagnac",
        powerLabel = "TAP : Frappe Sismique",
        radius = 0.56f,
        density = 5.2f,
        restitution = 0.12f,
        primaryColor = 0xFF5D4037.toInt(),
        accentColor = 0xFFFFAB00.toInt()
    )
}

enum class BlockMaterial(
    val displayName: String,
    val maxHp: Float,
    val density: Float,
    val friction: Float,
    val restitution: Float,
    val scoreValue: Int,
    val fillColor: Int,
    val borderColor: Int
) {
    GLASS(
        displayName = "Cristal Orbital",
        maxHp = 32f,
        density = 0.9f,
        friction = 0.25f,
        restitution = 0.14f,
        scoreValue = 180,
        fillColor = 0x994DD0E1.toInt(),
        borderColor = 0xFFE0F7FA.toInt()
    ),
    WOOD(
        displayName = "Pin des Landes",
        maxHp = 78f,
        density = 1.45f,
        friction = 0.58f,
        restitution = 0.10f,
        scoreValue = 300,
        fillColor = 0xFFB87333.toInt(),
        borderColor = 0xFF5D3A1A.toInt()
    ),
    STONE(
        displayName = "Pierre de Bidache",
        maxHp = 165f,
        density = 2.9f,
        friction = 0.72f,
        restitution = 0.06f,
        scoreValue = 480,
        fillColor = 0xFF78848E.toInt(),
        borderColor = 0xFF263238.toInt()
    ),
    METAL(
        displayName = "Blindage Titane",
        maxHp = 320f,
        density = 4.5f,
        friction = 0.48f,
        restitution = 0.08f,
        scoreValue = 800,
        fillColor = 0xFF37474F.toInt(),
        borderColor = 0xFF00E5FF.toInt()
    ),
    EXPLOSIVE(
        displayName = "Baril Poudre-Plasma",
        maxHp = 26f,
        density = 1.5f,
        friction = 0.50f,
        restitution = 0.18f,
        scoreValue = 550,
        fillColor = 0xFFD50000.toInt(),
        borderColor = 0xFFFFD600.toInt()
    ),
    SHIELD_PYLON(
        displayName = "Générateur Bouclier",
        maxHp = 65f,
        density = 2.2f,
        friction = 0.55f,
        restitution = 0.10f,
        scoreValue = 900,
        fillColor = 0xFF006064.toInt(),
        borderColor = 0xFF18FFFF.toInt()
    )
}

enum class EnemyTier(
    val displayName: String,
    val radius: Float,
    val maxHp: Float,
    val scoreValue: Int
) {
    CORSAIR_GRUNT(
        displayName = "Flibustier Orbital",
        radius = 0.44f,
        maxHp = 38f,
        scoreValue = 1000
    ),
    KNIGHT_CAPTAIN(
        displayName = "Chevalier de Nébuleuse",
        radius = 0.52f,
        maxHp = 85f,
        scoreValue = 1800
    ),
    SPACE_BARON(
        displayName = "Baron Cyber-Féodal",
        radius = 0.62f,
        maxHp = 155f,
        scoreValue = 3200
    ),
    DREADNOUGHT_BOSS(
        displayName = "Seigneur Cuirassé",
        radius = 0.74f,
        maxHp = 250f,
        scoreValue = 5000
    )
}

enum class EntityKind {
    PROJECTILE,
    BLOCK,
    ENEMY,
    GROUND
}

data class PhysicsEntity(
    val id: Int,
    val kind: EntityKind,
    var body: Body? = null,
    val projectileType: ProjectileType? = null,
    val blockMaterial: BlockMaterial? = null,
    val enemyTier: EnemyTier? = null,
    val halfWidth: Float = 0.5f,
    val halfHeight: Float = 0.5f,
    val radius: Float = 0.4f,
    val isStaticPlatform: Boolean = false,
    var hp: Float = 50f,
    val maxHp: Float = 50f,
    var abilityUsed: Boolean = false,
    var isDestroyed: Boolean = false,
    var pendingDamage: Float = 0f,
    var hitFlashTimer: Float = 0f,
    val trail: MutableList<Pair<Float, Float>> = mutableListOf()
)

data class BlockBlueprint(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val material: BlockMaterial,
    val isStatic: Boolean = false
)

data class EnemyBlueprint(
    val x: Float,
    val y: Float,
    val tier: EnemyTier
)

data class LevelBlueprint(
    val levelNumber: Int,
    val theme: BiomeTheme,
    val title: String,
    val projectiles: List<ProjectileType>,
    val blocks: List<BlockBlueprint>,
    val enemies: List<EnemyBlueprint>,
    val threeStarScore: Int
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Int,
    var life: Float,
    val maxLife: Float,
    var rotation: Float = 0f,
    var vRot: Float = 0f,
    val isShard: Boolean = false
)

data class ShockwaveRing(
    val x: Float,
    val y: Float,
    val maxRadius: Float,
    val color: Int,
    var life: Float,
    val maxLife: Float
)

data class FloatingScore(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    var life: Float = 1.15f
)
