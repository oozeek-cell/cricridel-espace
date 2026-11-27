package com.cricri.espace.render

import android.graphics.*
import com.cricri.espace.model.*
import kotlin.math.cos
import kotlin.math.sin

class ThemeRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    fun drawBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        groundScreenY: Float,
        cameraX: Float,
        theme: BiomeTheme,
        timeSec: Float
    ) {
        val skyColors = when (theme) {
            BiomeTheme.LANDIFORNIA_ORBIT -> intArrayOf(
                0xFF07031A.toInt(),
                0xFF280B46.toInt(),
                0xFFAD2831.toInt(),
                0xFFFF9E40.toInt()
            )
            BiomeTheme.BAYONNE_NEBULA -> intArrayOf(
                0xFF060B1E.toInt(),
                0xFF16213E.toInt(),
                0xFF53152A.toInt(),
                0xFFD96B43.toInt()
            )
            BiomeTheme.PIC_DANIE_FORTRESS -> intArrayOf(
                0xFF03020D.toInt(),
                0xFF120A33.toInt(),
                0xFF1F164D.toInt(),
                0xFF004D56.toInt()
            )
        }
        paint.shader = LinearGradient(
            0f, 0f, 0f, groundScreenY,
            skyColors, floatArrayOf(0f, 0.45f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, groundScreenY, paint)
        paint.shader = null

        // Volumetric Nebula Glow
        val nebX = width * 0.30f - cameraX * 0.03f
        val nebY = groundScreenY * 0.28f
        paint.shader = RadialGradient(
            nebX, nebY, height * 0.36f,
            intArrayOf(0x4400E5FF, 0x22FF1744, 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(nebX, nebY, height * 0.36f, paint)
        paint.shader = null

        // Crisp multi-depth stars + occasional shooting star
        for (i in 0 until 70) {
            val layer = (i % 3) + 1
            val sx = ((i * 211.7f - cameraX * (0.04f * layer)) % width + width) % width
            val sy = (i * 89.3f) % (groundScreenY * 0.66f)
            val twinkle = (1.2f + 1.3f * sin(timeSec * (1.8f + layer) + i)).coerceAtLeast(0.6f)
            paint.color = if (i % 7 == 0) 0xDD00E5FF.toInt() else 0xDDFFFFFF.toInt()
            canvas.drawCircle(sx, sy, twinkle, paint)
        }

        val shootCycle = (timeSec * 0.35f) % 1f
        if (shootCycle < 0.18f) {
            val p = shootCycle / 0.18f
            val stX = width * (0.85f - p * 0.45f)
            val stY = groundScreenY * (0.08f + p * 0.24f)
            strokePaint.color = 0xCC00E5FF.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawLine(stX, stY, stX + 55f, stY - 26f, strokePaint)
        }

        // Giant Ringed Synth-Sun / Gas Planet
        val sunX = width * 0.74f - cameraX * 0.045f
        val sunY = groundScreenY * 0.33f
        val sunRadius = height * 0.155f
        paint.shader = LinearGradient(
            sunX, sunY - sunRadius, sunX, sunY + sunRadius,
            if (theme == BiomeTheme.PIC_DANIE_FORTRESS) 0xFF00E5FF.toInt() else 0xFFFFEA00.toInt(),
            0xFFFF1744.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, sunRadius, paint)
        paint.shader = null

        // Synthwave scanlines across lower half of sun
        paint.color = 0x3312082E
        for (s in 1..4) {
            val ly = sunY + sunRadius * (0.14f * s)
            canvas.drawRect(sunX - sunRadius, ly, sunX + sunRadius, ly + 3.5f, paint)
        }

        strokePaint.color = 0xAA00E5FF.toInt()
        strokePaint.strokeWidth = 5.5f
        val ringRect = RectF(
            sunX - sunRadius * 1.82f,
            sunY - sunRadius * 0.28f,
            sunX + sunRadius * 1.82f,
            sunY + sunRadius * 0.28f
        )
        canvas.save()
        canvas.rotate(-15f, sunX, sunY)
        canvas.drawArc(ringRect, 12f, 156f, false, strokePaint)
        canvas.restore()

        drawParallaxLandscape(canvas, width, groundScreenY, cameraX, theme, timeSec)

        // Foreground Ground with neon grid accents
        paint.shader = LinearGradient(
            0f, groundScreenY, 0f, height,
            when (theme) {
                BiomeTheme.LANDIFORNIA_ORBIT -> intArrayOf(0xFFD89352.toInt(), 0xFF2B1914.toInt())
                BiomeTheme.BAYONNE_NEBULA -> intArrayOf(0xFF3E5C48.toInt(), 0xFF141E24.toInt())
                BiomeTheme.PIC_DANIE_FORTRESS -> intArrayOf(0xFF2C3A42.toInt(), 0xFF0A0E14.toInt())
            },
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, groundScreenY, width, height, paint)
        paint.shader = null

        strokePaint.color = when (theme) {
            BiomeTheme.LANDIFORNIA_ORBIT -> 0xFF00E5FF.toInt()
            BiomeTheme.BAYONNE_NEBULA -> 0xFFFF5252.toInt()
            BiomeTheme.PIC_DANIE_FORTRESS -> 0xFF76FF03.toInt()
        }
        strokePaint.strokeWidth = 4.5f
        canvas.drawLine(0f, groundScreenY, width, groundScreenY, strokePaint)
    }

    private fun drawParallaxLandscape(
        canvas: Canvas,
        width: Float,
        groundY: Float,
        cameraX: Float,
        theme: BiomeTheme,
        timeSec: Float
    ) {
        // Distant Pyrenees / La Rhune mountain ridge
        val mountainPath = Path()
        mountainPath.moveTo(0f, groundY)
        val step = 115f
        var x = -step
        var idx = 0
        while (x <= width + step * 2) {
            val worldIdx = idx + (cameraX * 0.14f / step).toInt()
            val offset = (cameraX * 0.14f) % step
            val peakH = 82f + ((worldIdx * 59) and 63)
            mountainPath.lineTo(x - offset, groundY - peakH)
            x += step
            idx++
        }
        mountainPath.lineTo(width + step, groundY)
        mountainPath.close()
        paint.color = when (theme) {
            BiomeTheme.LANDIFORNIA_ORBIT -> 0xFF210E38.toInt()
            BiomeTheme.BAYONNE_NEBULA -> 0xFF241832.toInt()
            BiomeTheme.PIC_DANIE_FORTRESS -> 0xFF15122E.toInt()
        }
        canvas.drawPath(mountainPath, paint)

        // Midground Basco-Landais silhouettes (etxe houses with red roofs & maritime pines)
        val treeSpacing = 165f
        val startIdx = (cameraX * 0.34f / treeSpacing).toInt() - 1
        val count = (width / treeSpacing).toInt() + 4
        for (i in 0..count) {
            val tIdx = startIdx + i
            val tx = tIdx * treeSpacing - cameraX * 0.34f
            if (tIdx % 3 == 0) {
                paint.color = 0xFF1C132D.toInt()
                canvas.drawRect(tx - 26f, groundY - 48f, tx + 26f, groundY, paint)
                val roof = Path().apply {
                    moveTo(tx - 33f, groundY - 48f)
                    lineTo(tx, groundY - 72f)
                    lineTo(tx + 33f, groundY - 48f)
                    close()
                }
                paint.color = 0xFF881326.toInt()
                canvas.drawPath(roof, paint)
                // Glowing window
                paint.color = 0xAAFFEA00.toInt()
                canvas.drawRect(tx - 12f, groundY - 32f, tx - 4f, groundY - 22f, paint)
                canvas.drawRect(tx + 4f, groundY - 32f, tx + 12f, groundY - 22f, paint)
            } else {
                paint.color = 0xFF160F24.toInt()
                canvas.drawRect(tx - 4.5f, groundY - 66f, tx + 4.5f, groundY, paint)
                canvas.drawOval(tx - 30f, groundY - 88f, tx + 30f, groundY - 56f, paint)
            }
        }

        if (theme == BiomeTheme.LANDIFORNIA_ORBIT) {
            strokePaint.color = 0x7700E5FF.toInt()
            strokePaint.strokeWidth = 3f
            val waveY = groundY - 8f + sin(timeSec * 3.2f) * 3f
            canvas.drawLine(0f, waveY, width, waveY, strokePaint)
        }
    }

    fun drawSlingshotBack(canvas: Canvas, sx: Float, sy: Float, ppm: Float) {
        strokePaint.color = 0xFF3E2723.toInt()
        strokePaint.strokeWidth = ppm * 0.24f
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(sx, sy + ppm * 1.45f, sx + ppm * 0.25f, sy - ppm * 0.28f, strokePaint)
    }

    fun drawSlingshotFront(
        canvas: Canvas,
        sx: Float,
        sy: Float,
        bandTargetX: Float?,
        bandTargetY: Float?,
        pullRatio: Float,
        ppm: Float
    ) {
        if (bandTargetX != null && bandTargetY != null) {
            val bandColor = when {
                pullRatio > 0.85f -> 0xFFFF1744.toInt()
                pullRatio > 0.45f -> 0xFFFFEA00.toInt()
                else -> 0xFF00E5FF.toInt()
            }
            strokePaint.color = bandColor
            strokePaint.strokeWidth = 8.5f
            canvas.drawLine(sx + ppm * 0.25f, sy - ppm * 0.25f, bandTargetX, bandTargetY, strokePaint)
            canvas.drawLine(sx - ppm * 0.23f, sy - ppm * 0.21f, bandTargetX, bandTargetY, strokePaint)
        }

        strokePaint.color = 0xFF795548.toInt()
        strokePaint.strokeWidth = ppm * 0.25f
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(sx, sy + ppm * 1.45f, sx, sy + ppm * 0.45f, strokePaint)
        canvas.drawLine(sx, sy + ppm * 0.45f, sx - ppm * 0.23f, sy - ppm * 0.22f, strokePaint)

        paint.color = 0xFF00E5FF.toInt()
        canvas.drawCircle(sx - ppm * 0.23f, sy - ppm * 0.22f, ppm * 0.11f, paint)
        canvas.drawCircle(sx + ppm * 0.25f, sy - ppm * 0.27f, ppm * 0.11f, paint)
    }

    fun drawBlock(
        canvas: Canvas,
        entity: PhysicsEntity,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        ppm: Float,
        timeSec: Float = 0f
    ) {
        val mat = entity.blockMaterial ?: return
        val hw = entity.halfWidth * ppm
        val hh = entity.halfHeight * ppm
        val hpRatio = (entity.hp / entity.maxHp).coerceIn(0f, 1f)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angleDeg)

        val rect = RectF(-hw, -hh, hw, hh)
        paint.color = when {
            entity.hitFlashTimer > 0f -> 0xFFFFFFFF.toInt()
            entity.isStaticPlatform -> 0xFF1C2833.toInt()
            else -> mat.fillColor
        }
        canvas.drawRoundRect(rect, 7f, 7f, paint)

        when (mat) {
            BlockMaterial.WOOD -> {
                strokePaint.color = 0xAA9E1B24.toInt()
                strokePaint.strokeWidth = 3.5f
                canvas.drawLine(-hw + 3f, -hh + 3f, hw - 3f, hh - 3f, strokePaint)
                canvas.drawLine(hw - 3f, -hh + 3f, -hw + 3f, hh - 3f, strokePaint)
            }
            BlockMaterial.GLASS -> {
                strokePaint.color = 0xCCFFFFFF.toInt()
                strokePaint.strokeWidth = 3f
                canvas.drawLine(-hw * 0.55f, -hh * 0.65f, hw * 0.35f, hh * 0.65f, strokePaint)
            }
            BlockMaterial.EXPLOSIVE -> {
                val pulse = 0.42f + 0.12f * sin(timeSec * 9f)
                paint.color = 0xFFFFEA00.toInt()
                canvas.drawCircle(0f, 0f, hw.coerceAtMost(hh) * pulse, paint)
            }
            BlockMaterial.METAL -> {
                paint.color = 0xFF00E5FF.toInt()
                canvas.drawCircle(-hw + 6f, -hh + 6f, 3f, paint)
                canvas.drawCircle(hw - 6f, -hh + 6f, 3f, paint)
                canvas.drawCircle(-hw + 6f, hh - 6f, 3f, paint)
                canvas.drawCircle(hw - 6f, hh - 6f, 3f, paint)
            }
            BlockMaterial.STONE -> {
                strokePaint.color = 0x661C2833.toInt()
                strokePaint.strokeWidth = 2.5f
                canvas.drawLine(-hw, 0f, hw, 0f, strokePaint)
                canvas.drawLine(0f, -hh, 0f, 0f, strokePaint)
            }
            BlockMaterial.SHIELD_PYLON -> {
                val coreR = hw.coerceAtMost(hh) * (0.48f + 0.14f * sin(timeSec * 7f))
                paint.color = 0xFF18FFFF.toInt()
                canvas.drawCircle(0f, 0f, coreR, paint)
            }
        }

        strokePaint.color = if (entity.isStaticPlatform) 0xFF00E5FF.toInt() else mat.borderColor
        strokePaint.strokeWidth = 3.2f
        canvas.drawRoundRect(rect, 7f, 7f, strokePaint)

        if (!entity.isStaticPlatform && hpRatio < 0.72f) {
            strokePaint.color = 0xDD111111.toInt()
            strokePaint.strokeWidth = 2.8f
            canvas.drawLine(-hw * 0.45f, -hh, 0f, 0f, strokePaint)
            if (hpRatio < 0.42f) {
                canvas.drawLine(0f, 0f, hw * 0.55f, hh, strokePaint)
                canvas.drawLine(0f, 0f, -hw * 0.65f, hh * 0.45f, strokePaint)
            }
        }

        canvas.restore()

        // Draw animated Force-Field Dome around active Shield Pylons
        if (mat == BlockMaterial.SHIELD_PYLON && !entity.isDestroyed) {
            val domeR = 4.2f * ppm
            strokePaint.color = 0x5518FFFF.toInt()
            strokePaint.strokeWidth = 3.5f
            canvas.drawCircle(cx, cy, domeR, strokePaint)
            paint.color = 0x1600E5FF.toInt()
            canvas.drawCircle(cx, cy, domeR, paint)
        }
    }

    fun drawProjectile(
        canvas: Canvas,
        type: ProjectileType,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        rPx: Float,
        showPowerAura: Boolean = false,
        timeSec: Float = 0f
    ) {
        if (showPowerAura) {
            val pulse = 1.28f + 0.22f * sin(timeSec * 9f)
            strokePaint.color = type.accentColor
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(cx, cy, rPx * pulse, strokePaint)
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angleDeg)

        when (type) {
            ProjectileType.CRICRI_BERET -> {
                paint.color = 0xFFF5F7FA.toInt()
                canvas.drawCircle(0f, 0f, rPx, paint)
                paint.color = type.accentColor
                canvas.drawRoundRect(
                    RectF(-rPx * 0.35f, -rPx * 0.35f, rPx * 0.82f, rPx * 0.34f),
                    rPx * 0.25f, rPx * 0.25f, paint
                )
                paint.color = type.primaryColor
                canvas.drawOval(RectF(-rPx * 1.08f, -rPx * 1.18f, rPx * 0.98f, -rPx * 0.42f), paint)
                strokePaint.color = 0xFF8E0000.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawLine(0f, -rPx * 1.05f, rPx * 0.16f, -rPx * 1.30f, strokePaint)
            }

            ProjectileType.PIMENT_ORBITAL -> {
                paint.color = type.primaryColor
                canvas.drawOval(RectF(-rPx * 1.18f, -rPx * 0.74f, rPx * 1.08f, rPx * 0.74f), paint)
                strokePaint.color = type.accentColor
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(0f, 0f, rPx * 0.92f, strokePaint)
                paint.color = 0xFF64DD17.toInt()
                canvas.drawRect(-rPx * 1.32f, -rPx * 0.22f, -rPx * 0.95f, rPx * 0.22f, paint)
            }

            ProjectileType.POMME_DE_PIN -> {
                paint.color = type.primaryColor
                canvas.drawCircle(0f, 0f, rPx, paint)
                strokePaint.color = type.accentColor
                strokePaint.strokeWidth = 3.8f
                canvas.drawCircle(0f, 0f, rPx * 0.65f, strokePaint)
                canvas.drawLine(-rPx * 0.72f, 0f, rPx * 0.72f, 0f, strokePaint)
                canvas.drawLine(0f, -rPx * 0.72f, 0f, rPx * 0.72f, strokePaint)
            }

            ProjectileType.TONNEAU_ARMAGNAC -> {
                paint.color = type.primaryColor
                canvas.drawRoundRect(RectF(-rPx * 0.96f, -rPx * 0.90f, rPx * 0.96f, rPx * 0.90f), 11f, 11f, paint)
                strokePaint.color = type.accentColor
                strokePaint.strokeWidth = 5.5f
                canvas.drawLine(-rPx * 0.55f, -rPx * 0.90f, -rPx * 0.55f, rPx * 0.90f, strokePaint)
                canvas.drawLine(rPx * 0.55f, -rPx * 0.90f, rPx * 0.55f, rPx * 0.90f, strokePaint)
            }
        }

        canvas.restore()
    }

    fun drawEnemy(
        canvas: Canvas,
        entity: PhysicsEntity,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        rPx: Float,
        isShielded: Boolean = false
    ) {
        val tier = entity.enemyTier ?: return

        if (isShielded) {
            strokePaint.color = 0xCC18FFFF.toInt()
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(cx, cy, rPx * 1.24f, strokePaint)
        }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angleDeg)

        paint.color = if (entity.hitFlashTimer > 0f) {
            0xFFFFFFFF.toInt()
        } else {
            when (tier) {
                EnemyTier.CORSAIR_GRUNT -> 0xFF7CB342.toInt()
                EnemyTier.KNIGHT_CAPTAIN -> 0xFF26A69A.toInt()
                EnemyTier.SPACE_BARON -> 0xFFAB47BC.toInt()
                EnemyTier.DREADNOUGHT_BOSS -> 0xFFD50000.toInt()
            }
        }
        canvas.drawCircle(0f, 0f, rPx, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(-rPx * 0.32f, -rPx * 0.12f, rPx * 0.22f, paint)
        canvas.drawCircle(rPx * 0.28f, -rPx * 0.12f, rPx * 0.22f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(-rPx * 0.36f, -rPx * 0.12f, rPx * 0.10f, paint)
        canvas.drawCircle(rPx * 0.24f, -rPx * 0.12f, rPx * 0.10f, paint)

        when (tier) {
            EnemyTier.CORSAIR_GRUNT -> {
                strokePaint.color = 0xFFB0BEC5.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawArc(RectF(-rPx, -rPx, rPx, rPx), 195f, 150f, false, strokePaint)
            }
            EnemyTier.KNIGHT_CAPTAIN -> {
                paint.color = 0xFF78909C.toInt()
                canvas.drawArc(RectF(-rPx * 1.06f, -rPx * 1.10f, rPx * 1.06f, rPx * 0.2f), 180f, 180f, true, paint)
            }
            EnemyTier.SPACE_BARON -> {
                paint.color = 0xFFFFD600.toInt()
                val crown = Path().apply {
                    moveTo(-rPx * 0.72f, -rPx * 0.75f)
                    lineTo(-rPx * 0.52f, -rPx * 1.35f)
                    lineTo(0f, -rPx * 0.92f)
                    lineTo(rPx * 0.52f, -rPx * 1.35f)
                    lineTo(rPx * 0.72f, -rPx * 0.75f)
                    close()
                }
                canvas.drawPath(crown, paint)
            }
            EnemyTier.DREADNOUGHT_BOSS -> {
                strokePaint.color = 0xFFFFEA00.toInt()
                strokePaint.strokeWidth = 6f
                canvas.drawCircle(0f, 0f, rPx * 0.95f, strokePaint)
                paint.color = 0xFFFFEA00.toInt()
                for (k in 0 until 6) {
                    val ang = Math.toRadians(k * 60.0)
                    canvas.drawCircle((cos(ang) * rPx * 0.95f).toFloat(), (sin(ang) * rPx * 0.95f).toFloat(), 5f, paint)
                }
            }
        }

        if (entity.hp < entity.maxHp) {
            val ratio = (entity.hp / entity.maxHp).coerceIn(0f, 1f)
            paint.color = 0xCC000000.toInt()
            canvas.drawRect(-rPx, -rPx * 1.50f, rPx, -rPx * 1.32f, paint)
            paint.color = 0xFFFF1744.toInt()
            canvas.drawRect(-rPx, -rPx * 1.50f, -rPx + 2f * rPx * ratio, -rPx * 1.32f, paint)
        }

        canvas.restore()
    }
}
