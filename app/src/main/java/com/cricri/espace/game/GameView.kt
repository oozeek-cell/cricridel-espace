package com.cricri.espace.game

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.cricri.espace.audio.SoundEngine
import com.cricri.espace.level.LevelGenerator
import com.cricri.espace.model.*
import com.cricri.espace.render.ThemeRenderer
import com.cricri.espace.storage.ProgressManager
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.contacts.Contact
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

class GameView(context: Context) : SurfaceView(context), Runnable {

    private enum class ScreenState {
        TITLE,
        LEVEL_SELECT,
        PLAYING,
        VICTORY,
        DEFEAT
    }

    @Volatile private var screenState = ScreenState.TITLE
    @Volatile private var selectedChapter = 0

    private val progressManager = ProgressManager(context)
    private val soundEngine = SoundEngine()
    private val renderer = ThemeRenderer()
    private val rng = Random()

    private var gameThread: Thread? = null
    @Volatile private var running = false

    private var world: World? = null
    private val entities = mutableListOf<PhysicsEntity>()
    private val commandQueue = ConcurrentLinkedQueue<() -> Unit>()
    private val particles = mutableListOf<Particle>()
    private val shockwaves = mutableListOf<ShockwaveRing>()
    private val floatingScores = mutableListOf<FloatingScore>()
    private var nextEntityId = 1

    @Volatile private var currentLevelNumber = 1
    @Volatile private var currentBlueprint: LevelBlueprint? = null
    private val remainingQueue = mutableListOf<ProjectileType>()
    @Volatile private var loadedProjectile: PhysicsEntity? = null
    @Volatile private var activeProjectile: PhysicsEntity? = null

    @Volatile private var score = 0
    @Volatile private var comboCount = 0
    private var comboTimer = 0f
    @Volatile private var earnedStars = 0
    private var settleTimer = 0f
    private var flightTimer = 0f
    @Volatile private var previewTimer = 0f
    private var hitStopTimer = 0f
    private var shakeIntensity = 0f
    private var totalTime = 0f

    private val slingX = 4.6f
    private val slingY = 2.4f
    private val maxPullMeters = 2.45f
    private val launchMultiplier = 11.8f

    @Volatile private var isAiming = false
    @Volatile private var aimX = slingX
    @Volatile private var aimY = slingY

    @Volatile private var basePpm = 44f
    @Volatile private var zoomScale = 1.0f
    @Volatile private var cameraX = 0f
    private var lastTouchX = 0f
    @Volatile private var isPanning = false
    private var lastHapticBucket = -1

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomScale = (zoomScale * detector.scaleFactor).coerceIn(0.65f, 1.35f)
            return true
        }
    })

    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    @Volatile private var activeButtonRects: Map<String, RectF> = emptyMap()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                resume()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                basePpm = (height / 11.5f).coerceAtLeast(28f)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                pause()
            }
        })
    }

    fun handleBackNavigation(): Boolean {
        return when (screenState) {
            ScreenState.PLAYING, ScreenState.VICTORY, ScreenState.DEFEAT -> {
                selectedChapter = (currentLevelNumber - 1) / 33
                screenState = ScreenState.LEVEL_SELECT
                true
            }
            ScreenState.LEVEL_SELECT -> {
                screenState = ScreenState.TITLE
                true
            }
            ScreenState.TITLE -> false
        }
    }

    fun resume() {
        if (running) return
        running = true
        gameThread = Thread(this, "CricriGameLoop").apply { start() }
    }

    fun pause() {
        running = false
        try {
            gameThread?.join(500)
        } catch (_: InterruptedException) {
        }
        gameThread = null
    }

    fun release() {
        pause()
        soundEngine.release()
    }

    override fun run() {
        var lastNs = System.nanoTime()
        while (running) {
            val nowNs = System.nanoTime()
            val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNs = nowNs

            update(dt)
            renderFrame()
        }
    }

    private fun enqueueStartLevel(levelNum: Int) {
        val target = levelNum.coerceIn(1, 99)
        currentLevelNumber = target
        screenState = ScreenState.PLAYING
        commandQueue.offer { startLevelInternal(target) }
    }

    private fun startLevelInternal(levelNum: Int) {
        currentLevelNumber = levelNum.coerceIn(1, 99)
        val bp = LevelGenerator.generate(currentLevelNumber)
        currentBlueprint = bp

        synchronized(entities) {
            entities.clear()
            particles.clear()
            shockwaves.clear()
            floatingScores.clear()
            nextEntityId = 1

            val w = World(Vec2(0f, -16.8f))
            w.setContactListener(createContactListener())
            world = w

            val groundDef = BodyDef().apply {
                this.type = BodyType.STATIC
                position.set(25f, -1.0f)
            }
            val groundBody = w.createBody(groundDef)
            val groundShape = PolygonShape().apply { setAsBox(60f, 1.0f) }
            val groundEntity = PhysicsEntity(
                id = nextEntityId++,
                kind = EntityKind.GROUND,
                body = groundBody,
                hp = 99999f,
                maxHp = 99999f
            )
            groundBody.createFixture(groundShape, 0f).userData = groundEntity

            for (b in bp.blocks) {
                spawnBlock(w, b)
            }

            for (e in bp.enemies) {
                spawnEnemy(w, e)
            }

            remainingQueue.clear()
            remainingQueue.addAll(bp.projectiles)
            loadedProjectile = null
            activeProjectile = null
            score = 0
            comboCount = 0
            comboTimer = 0f
            earnedStars = 0
            settleTimer = 0f
            flightTimer = 0f
            previewTimer = 0.85f
            hitStopTimer = 0f
            shakeIntensity = 0f
            isAiming = false
            aimX = slingX
            aimY = slingY
            zoomScale = 0.88f
            cameraX = 8.5f * (basePpm * zoomScale)

            loadNextProjectile()
        }
    }

    private fun spawnBlock(w: World, b: BlockBlueprint) {
        val def = BodyDef().apply {
            this.type = if (b.isStatic) BodyType.STATIC else BodyType.DYNAMIC
            position.set(b.x, b.y)
            linearDamping = 0.08f
            angularDamping = 0.14f
        }
        val body = w.createBody(def)
        val shape = PolygonShape().apply { setAsBox(b.width / 2f, b.height / 2f) }
        val entity = PhysicsEntity(
            id = nextEntityId++,
            kind = EntityKind.BLOCK,
            body = body,
            blockMaterial = b.material,
            halfWidth = b.width / 2f,
            halfHeight = b.height / 2f,
            isStaticPlatform = b.isStatic,
            hp = if (b.isStatic) 99999f else b.material.maxHp,
            maxHp = if (b.isStatic) 99999f else b.material.maxHp
        )
        val fixDef = FixtureDef().apply {
            this.shape = shape
            density = if (b.isStatic) 0f else b.material.density
            friction = b.material.friction
            restitution = b.material.restitution
            userData = entity
        }
        body.createFixture(fixDef)
        entities.add(entity)
    }

    private fun spawnEnemy(w: World, e: EnemyBlueprint) {
        val def = BodyDef().apply {
            this.type = BodyType.DYNAMIC
            position.set(e.x, e.y)
            linearDamping = 0.16f
            angularDamping = 0.28f
        }
        val body = w.createBody(def)
        val shape = CircleShape().apply { m_radius = e.tier.radius }
        val entity = PhysicsEntity(
            id = nextEntityId++,
            kind = EntityKind.ENEMY,
            body = body,
            enemyTier = e.tier,
            radius = e.tier.radius,
            hp = e.tier.maxHp,
            maxHp = e.tier.maxHp
        )
        val fixDef = FixtureDef().apply {
            this.shape = shape
            density = 1.8f
            friction = 0.52f
            restitution = 0.18f
            userData = entity
        }
        body.createFixture(fixDef)
        entities.add(entity)
    }

    private fun loadNextProjectile() {
        val w = world ?: return
        if (remainingQueue.isEmpty()) {
            loadedProjectile = null
            return
        }
        val nextType = remainingQueue.removeAt(0)
        val def = BodyDef().apply {
            this.type = BodyType.KINEMATIC
            position.set(slingX, slingY)
        }
        val body = w.createBody(def)
        val shape = CircleShape().apply { m_radius = nextType.radius }
        val entity = PhysicsEntity(
            id = nextEntityId++,
            kind = EntityKind.PROJECTILE,
            body = body,
            projectileType = nextType,
            radius = nextType.radius,
            hp = 999f,
            maxHp = 999f
        )
        val fixDef = FixtureDef().apply {
            this.shape = shape
            density = nextType.density
            friction = 0.45f
            restitution = nextType.restitution
            userData = entity
        }
        body.createFixture(fixDef)
        entities.add(entity)
        loadedProjectile = entity
        aimX = slingX
        aimY = slingY
    }

    private fun launchLoadedProjectileInternal(snapAimX: Float, snapAimY: Float) {
        synchronized(entities) {
            val proj = loadedProjectile ?: return
            val body = proj.body ?: return
            val dx = slingX - snapAimX
            val dy = slingY - snapAimY
            val dist = hypot(dx, dy)
            if (dist < 0.22f) {
                aimX = slingX
                aimY = slingY
                body.setTransform(Vec2(slingX, slingY), 0f)
                return
            }

            body.type = BodyType.DYNAMIC
            body.isBullet = true
            body.linearVelocity = Vec2(dx * launchMultiplier, dy * launchMultiplier)
            body.angularVelocity = -5.0f

            activeProjectile = proj
            loadedProjectile = null
            settleTimer = 0f
            flightTimer = 0f
            previewTimer = 0f
            soundEngine.playLaunch()
        }
    }

    private fun triggerProjectileAbilityInternal() {
        synchronized(entities) {
            val proj = activeProjectile ?: return
            if (proj.abilityUsed || proj.isDestroyed) return
            val body = proj.body ?: return
            val type = proj.projectileType ?: return
            val w = world ?: return

            proj.abilityUsed = true
            val pos = body.position
            val vel = body.linearVelocity

            when (type) {
                ProjectileType.CRICRI_BERET -> {
                    val speed = hypot(vel.x, vel.y).coerceAtLeast(12f)
                    val dirX = if (vel.x >= 0f) 1f else -1f
                    body.linearVelocity = Vec2(dirX * speed * 2.05f, vel.y * 0.20f)
                    shockwaves.add(ShockwaveRing(pos.x, pos.y, 2.2f, 0xFF00E5FF.toInt(), 0.28f, 0.28f))
                    spawnBurst(pos.x, pos.y, 0xFF00E5FF.toInt(), 20)
                    soundEngine.playAbilityDash()
                }

                ProjectileType.PIMENT_ORBITAL -> {
                    triggerExplosion(pos.x, pos.y, radius = 2.75f, maxDamage = 135f, impulseForce = 22f)
                    shockwaves.add(ShockwaveRing(pos.x, pos.y, 3.0f, 0xFFFF3D00.toInt(), 0.36f, 0.36f))
                    spawnBurst(pos.x, pos.y, 0xFFFF3D00.toInt(), 32)
                    shakeIntensity = 18f
                    hitStopTimer = 0.025f
                    soundEngine.playExplosion()
                }

                ProjectileType.POMME_DE_PIN -> {
                    soundEngine.playAbilitySplit()
                    spawnBurst(pos.x, pos.y, 0xFF76FF03.toInt(), 16)
                    val offsets = listOf(0.45f to 4.5f, -0.45f to -4.1f)
                    for ((dyOff, vyAdd) in offsets) {
                        val def = BodyDef().apply {
                            this.type = BodyType.DYNAMIC
                            position.set(pos.x, pos.y + dyOff)
                            bullet = true
                        }
                        val b = w.createBody(def)
                        val shape = CircleShape().apply { m_radius = type.radius * 0.86f }
                        val clone = PhysicsEntity(
                            id = nextEntityId++,
                            kind = EntityKind.PROJECTILE,
                            body = b,
                            projectileType = type,
                            radius = type.radius * 0.86f,
                            hp = 999f,
                            maxHp = 999f,
                            abilityUsed = true
                        )
                        b.createFixture(FixtureDef().apply {
                            this.shape = shape
                            density = type.density
                            friction = 0.4f
                            restitution = type.restitution
                            userData = clone
                        })
                        b.linearVelocity = Vec2(vel.x * 1.12f, vel.y + vyAdd)
                        entities.add(clone)
                    }
                }

                ProjectileType.TONNEAU_ARMAGNAC -> {
                    body.linearVelocity = Vec2(vel.x * 0.12f, -35f)
                    shockwaves.add(ShockwaveRing(pos.x, pos.y, 2.4f, 0xFFFFAB00.toInt(), 0.30f, 0.30f))
                    spawnBurst(pos.x, pos.y, 0xFFFFAB00.toInt(), 20)
                    shakeIntensity = 14f
                    soundEngine.playAbilityDrop()
                }
            }
        }
    }

    private fun triggerExplosion(cx: Float, cy: Float, radius: Float, maxDamage: Float, impulseForce: Float) {
        for (e in entities) {
            if (e.isDestroyed || e.isStaticPlatform) continue
            val b = e.body ?: continue
            val dx = b.position.x - cx
            val dy = b.position.y - cy
            val dist = hypot(dx, dy)
            if (dist < radius) {
                val falloff = (1f - dist / radius).coerceIn(0.15f, 1f)
                if (e.kind == EntityKind.BLOCK || e.kind == EntityKind.ENEMY) {
                    e.pendingDamage += maxDamage * falloff
                }
                val nx = if (dist > 0.01f) dx / dist else 0f
                val ny = if (dist > 0.01f) dy / dist else 1f
                b.isAwake = true
                val curVel = b.linearVelocity
                val invMass = if (b.mass > 0f) (1f / b.mass).coerceAtMost(1.15f) else 0.45f
                b.linearVelocity = Vec2(
                    curVel.x + nx * impulseForce * falloff * invMass,
                    curVel.y + (ny + 0.32f) * impulseForce * falloff * invMass
                )
            }
        }
    }

    private fun isEnemyProtectedByShield(enemy: PhysicsEntity): Boolean {
        val eb = enemy.body ?: return false
        for (other in entities) {
            if (other.isDestroyed || other.blockMaterial != BlockMaterial.SHIELD_PYLON) continue
            val sb = other.body ?: continue
            if (hypot(eb.position.x - sb.position.x, eb.position.y - sb.position.y) <= 4.2f) {
                return true
            }
        }
        return false
    }

    private fun createContactListener(): ContactListener {
        return object : ContactListener {
            override fun beginContact(contact: Contact) {}
            override fun endContact(contact: Contact) {}
            override fun preSolve(contact: Contact, oldManifold: Manifold) {}

            override fun postSolve(contact: Contact, impulse: ContactImpulse) {
                var maxImp = 0f
                for (imp in impulse.normalImpulses) {
                    if (imp > maxImp) maxImp = imp
                }
                if (maxImp < 3.1f) return

                val eA = contact.fixtureA.userData as? PhysicsEntity
                val eB = contact.fixtureB.userData as? PhysicsEntity

                val baseDamage = (maxImp - 2.6f) * 2.55f
                applyImpactDamage(eA, eB, baseDamage)
                applyImpactDamage(eB, eA, baseDamage)
            }
        }
    }

    private fun applyImpactDamage(target: PhysicsEntity?, hitter: PhysicsEntity?, baseDamage: Float) {
        if (target == null || target.isStaticPlatform || target.isDestroyed) return
        if (target.kind != EntityKind.BLOCK && target.kind != EntityKind.ENEMY) return

        var mult = 1.0f
        val pType = hitter?.projectileType
        if (pType != null) {
            mult = when (pType) {
                ProjectileType.CRICRI_BERET -> if (hitter.abilityUsed && target.blockMaterial == BlockMaterial.METAL) 2.1f else 1.2f
                ProjectileType.TONNEAU_ARMAGNAC -> if (target.blockMaterial == BlockMaterial.STONE || target.blockMaterial == BlockMaterial.METAL) 1.75f else 1.15f
                ProjectileType.POMME_DE_PIN -> if (target.blockMaterial == BlockMaterial.GLASS || target.blockMaterial == BlockMaterial.WOOD) 1.85f else 0.75f
                ProjectileType.PIMENT_ORBITAL -> 1.1f
            }
        }
        target.pendingDamage += baseDamage * mult
    }

    private fun update(dt: Float) {
        totalTime += dt
        if (shakeIntensity > 0f) {
            shakeIntensity = (shakeIntensity - dt * 52f).coerceAtLeast(0f)
        }
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) comboCount = 0
        }

        while (true) {
            val cmd = commandQueue.poll() ?: break
            cmd.invoke()
        }

        if (screenState != ScreenState.PLAYING) return

        if (hitStopTimer > 0f) {
            hitStopTimer -= dt
            return
        }

        synchronized(entities) {
            val w = world ?: return

            if (isAiming) {
                loadedProjectile?.body?.setTransform(Vec2(aimX, aimY), 0f)
            }

            w.step(1f / 60f, 8, 3)

            val iter = entities.iterator()
            val explosionsToRun = mutableListOf<Pair<Float, Float>>()

            while (iter.hasNext()) {
                val e = iter.next()
                val b = e.body ?: continue

                if (e.hitFlashTimer > 0f) {
                    e.hitFlashTimer = (e.hitFlashTimer - dt).coerceAtLeast(0f)
                }

                if (e.kind == EntityKind.PROJECTILE && b.type == BodyType.DYNAMIC) {
                    if (e.trail.size > 28) e.trail.removeAt(0)
                    e.trail.add(b.position.x to b.position.y)
                }

                if (e.pendingDamage > 0f && !e.isDestroyed) {
                    val actualDamage = if (e.kind == EntityKind.ENEMY && isEnemyProtectedByShield(e)) {
                        e.pendingDamage * 0.22f
                    } else {
                        e.pendingDamage
                    }
                    e.hp -= actualDamage
                    e.pendingDamage = 0f
                    e.hitFlashTimer = 0.09f

                    if (e.hp <= 0f) {
                        e.isDestroyed = true
                        val pos = b.position
                        if (e.kind == EntityKind.BLOCK) {
                            val mat = e.blockMaterial ?: BlockMaterial.WOOD
                            addScore(pos.x, pos.y, mat.scoreValue, mat.borderColor)
                            spawnBurst(pos.x, pos.y, mat.fillColor, 16, isShard = true)
                            if (mat == BlockMaterial.EXPLOSIVE) {
                                explosionsToRun.add(pos.x to pos.y)
                            } else if (mat == BlockMaterial.SHIELD_PYLON) {
                                shockwaves.add(ShockwaveRing(pos.x, pos.y, 4.2f, 0xFF18FFFF.toInt(), 0.42f, 0.42f))
                                soundEngine.playExplosion()
                            } else {
                                soundEngine.playBlockBreak(mat == BlockMaterial.GLASS)
                            }
                        } else if (e.kind == EntityKind.ENEMY) {
                            val tier = e.enemyTier ?: EnemyTier.CORSAIR_GRUNT
                            addScore(pos.x, pos.y, tier.scoreValue, 0xFFFFEA00.toInt())
                            spawnBurst(pos.x, pos.y, 0xFF76FF03.toInt(), 24)
                            shockwaves.add(ShockwaveRing(pos.x, pos.y, 1.8f, 0xFF76FF03.toInt(), 0.30f, 0.30f))
                            soundEngine.playEnemyDefeat()
                        }
                    }
                }

                if (b.position.x < -8f || b.position.x > 46f || b.position.y < -4f) {
                    e.isDestroyed = true
                }
            }

            for ((ex, ey) in explosionsToRun) {
                triggerExplosion(ex, ey, radius = 3.1f, maxDamage = 130f, impulseForce = 24f)
                shockwaves.add(ShockwaveRing(ex, ey, 3.2f, 0xFFFF3D00.toInt(), 0.38f, 0.38f))
                spawnBurst(ex, ey, 0xFFFF3D00.toInt(), 30)
                shakeIntensity = 20f
                hitStopTimer = 0.025f
                soundEngine.playExplosion()
            }

            val removeIter = entities.iterator()
            while (removeIter.hasNext()) {
                val e = removeIter.next()
                if (e.isDestroyed) {
                    e.body?.let { w.destroyBody(it) }
                    e.body = null
                    if (activeProjectile?.id == e.id) {
                        activeProjectile = null
                    }
                    removeIter.remove()
                }
            }

            val pIter = particles.iterator()
            while (pIter.hasNext()) {
                val p = pIter.next()
                p.life -= dt
                if (p.life <= 0f) {
                    pIter.remove()
                } else {
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy -= 13f * dt
                    p.rotation += p.vRot * dt
                }
            }

            val sIter = shockwaves.iterator()
            while (sIter.hasNext()) {
                val s = sIter.next()
                s.life -= dt
                if (s.life <= 0f) sIter.remove()
            }

            val fIter = floatingScores.iterator()
            while (fIter.hasNext()) {
                val f = fIter.next()
                f.life -= dt
                if (f.life <= 0f) {
                    fIter.remove()
                } else {
                    f.y += 1.3f * dt
                }
            }

            if (previewTimer > 0f) {
                previewTimer -= dt
            }

            if (!isPanning) {
                val targetCamMeters = when {
                    isAiming -> 0f
                    activeProjectile != null -> ((activeProjectile!!.body?.position?.x ?: slingX) - 6.5f).coerceIn(0f, 15f)
                    previewTimer > 0f -> 8.5f
                    else -> 0f
                }
                val ppm = basePpm * zoomScale
                val targetCamPx = targetCamMeters * ppm
                val lerpSpeed = if (isAiming) 12f else 4.5f
                cameraX += (targetCamPx - cameraX) * (dt * lerpSpeed).coerceIn(0f, 1f)
            }

            val remainingEnemies = entities.count { it.kind == EntityKind.ENEMY && !it.isDestroyed }
            val activeBody = activeProjectile?.body
            val activeSpeed = if (activeBody != null) hypot(activeBody.linearVelocity.x, activeBody.linearVelocity.y) else 0f

            if (activeProjectile != null) {
                flightTimer += dt
                if (activeSpeed < 0.45f || flightTimer > 5.5f) {
                    settleTimer += dt
                    if (settleTimer > 1.05f || flightTimer > 5.5f) {
                        activeProjectile = null
                        settleTimer = 0f
                        flightTimer = 0f
                    }
                } else {
                    settleTimer = 0f
                }
            } else if (loadedProjectile == null && remainingQueue.isNotEmpty() && remainingEnemies > 0) {
                settleTimer += dt
                if (settleTimer > 0.45f) {
                    loadNextProjectile()
                    settleTimer = 0f
                }
            }

            if (remainingEnemies == 0) {
                settleTimer += dt
                if (settleTimer > 0.9f) {
                    completeLevelVictory()
                }
            } else if (activeProjectile == null && loadedProjectile == null && remainingQueue.isEmpty()) {
                settleTimer += dt
                if (settleTimer > 1.8f) {
                    screenState = ScreenState.DEFEAT
                }
            }
        }
    }

    private fun completeLevelVictory() {
        val unusedBonus = (remainingQueue.size + if (loadedProjectile != null) 1 else 0) * 1200
        score += unusedBonus
        val target = currentBlueprint?.threeStarScore ?: 5000
        earnedStars = when {
            score >= target -> 3
            score >= (target * 0.65f).toInt() -> 2
            else -> 1
        }
        progressManager.recordLevelWin(currentLevelNumber, score, earnedStars)
        soundEngine.playVictory()
        screenState = ScreenState.VICTORY
    }

    private fun addScore(wx: Float, wy: Float, points: Int, color: Int) {
        comboCount++
        comboTimer = 1.4f
        val mult = comboCount.coerceIn(1, 5)
        val totalPts = points * mult
        score += totalPts
        val label = if (mult > 1) "+$totalPts (x$mult)" else "+$totalPts"
        floatingScores.add(FloatingScore(wx, wy + 0.45f, label, color))
    }

    private fun spawnBurst(wx: Float, wy: Float, color: Int, count: Int, isShard: Boolean = false) {
        for (i in 0 until count) {
            val angle = rng.nextFloat() * 2f * PI.toFloat()
            val speed = 1.8f + rng.nextFloat() * 7.2f
            particles.add(
                Particle(
                    x = wx,
                    y = wy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed + 2.5f,
                    size = 0.08f + rng.nextFloat() * 0.14f,
                    color = color,
                    life = 0.45f + rng.nextFloat() * 0.45f,
                    maxLife = 0.9f,
                    vRot = (rng.nextFloat() - 0.5f) * 420f,
                    isShard = isShard
                )
            )
        }
    }

    private fun renderFrame() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        val frameButtons = LinkedHashMap<String, RectF>()
        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            when (screenState) {
                ScreenState.TITLE -> drawTitleScreen(canvas, w, h, frameButtons)
                ScreenState.LEVEL_SELECT -> drawLevelSelectScreen(canvas, w, h, frameButtons)
                ScreenState.PLAYING -> drawGameplayScreen(canvas, w, h, frameButtons)
                ScreenState.VICTORY -> {
                    drawGameplayScreen(canvas, w, h, frameButtons)
                    drawEndOverlay(canvas, w, h, isVictory = true, frameButtons)
                }
                ScreenState.DEFEAT -> {
                    drawGameplayScreen(canvas, w, h, frameButtons)
                    drawEndOverlay(canvas, w, h, isVictory = false, frameButtons)
                }
            }
            activeButtonRects = frameButtons
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawTitleScreen(canvas: Canvas, w: Float, h: Float, frameButtons: MutableMap<String, RectF>) {
        val groundY = h * 0.84f
        renderer.drawBackground(canvas, w, h, groundY, totalTime * 32f, BiomeTheme.LANDIFORNIA_ORBIT, totalTime)

        val floatY = groundY * 0.44f + sin(totalTime * 2.8f) * 15f
        renderer.drawProjectile(canvas, ProjectileType.CRICRI_BERET, w * 0.20f, floatY, sin(totalTime * 2.2f) * 9f, h * 0.115f, true, totalTime)
        renderer.drawProjectile(canvas, ProjectileType.PIMENT_ORBITAL, w * 0.80f, floatY + 12f, -14f, h * 0.085f, true, totalTime)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = h * 0.11f
        textPaint.color = 0xFFFFEA00.toInt()
        textPaint.setShadowLayer(18f, 0f, 5f, 0xFFD50000.toInt())
        canvas.drawText("CRICRI DE L'ESPACE", w * 0.5f, h * 0.27f, textPaint)
        textPaint.clearShadowLayer()

        textPaint.textSize = h * 0.036f
        textPaint.color = 0xFF00E5FF.toInt()
        canvas.drawText("ÉDITION ARCADE HARDCORE • LANDIFORNIA • SPACE OPÉRA", w * 0.5f, h * 0.35f, textPaint)

        val totalStars = progressManager.getTotalStars()
        val unlocked = progressManager.getUnlockedLevel()
        textPaint.textSize = h * 0.036f
        textPaint.color = Color.WHITE
        canvas.drawText("Progression : Niveau $unlocked / 99   •   ★ $totalStars / 297", w * 0.5f, h * 0.43f, textPaint)

        val playRect = RectF(w * 0.33f, h * 0.51f, w * 0.67f, h * 0.64f)
        drawGlassButton(canvas, playRect, "JOUER (NIV. $unlocked)", 0xFFD91E2A.toInt(), 0xFFFFEA00.toInt())
        frameButtons["PLAY_CURRENT"] = playRect

        val levelsRect = RectF(w * 0.33f, h * 0.67f, w * 0.67f, h * 0.79f)
        drawGlassButton(canvas, levelsRect, "CHOISIR PARMI LES 99 NIVEAUX", 0xFF1A237E.toInt(), 0xFF00E5FF.toInt())
        frameButtons["OPEN_LEVELS"] = levelsRect
    }

    private fun drawLevelSelectScreen(canvas: Canvas, w: Float, h: Float, frameButtons: MutableMap<String, RectF>) {
        val chapters = BiomeTheme.values()
        val activeChapter = chapters[selectedChapter.coerceIn(0, 2)]
        renderer.drawBackground(canvas, w, h, h * 0.92f, 0f, activeChapter, totalTime)

        val backRect = RectF(w * 0.03f, h * 0.04f, w * 0.15f, h * 0.13f)
        drawGlassButton(canvas, backRect, "← ACCUEIL", 0xFF263238.toInt(), 0xFF00E5FF.toInt(), textSizeRatio = 0.34f)
        frameButtons["BACK_TITLE"] = backRect

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = h * 0.048f
        textPaint.color = 0xFFFFEA00.toInt()
        canvas.drawText(activeChapter.title, w * 0.52f, h * 0.095f, textPaint)

        val tabWidth = w * 0.27f
        val tabTop = h * 0.15f
        val tabBottom = h * 0.23f
        val tabLabels = listOf("I. Landifornia (1-33)", "II. Bayonne-Nébuleuse (34-66)", "III. Pic d'Anie (67-99)")
        for (i in 0..2) {
            val left = w * 0.07f + i * (tabWidth + w * 0.025f)
            val r = RectF(left, tabTop, left + tabWidth, tabBottom)
            val isSelected = selectedChapter == i
            drawGlassButton(
                canvas, r, tabLabels[i],
                if (isSelected) 0xFFD91E2A.toInt() else 0xAA101835.toInt(),
                if (isSelected) 0xFFFFEA00.toInt() else 0xFF00E5FF.toInt(),
                textSizeRatio = 0.34f
            )
            frameButtons["TAB_$i"] = r
        }

        val cols = 11
        val rows = 3
        val gridLeft = w * 0.05f
        val gridTop = h * 0.27f
        val gridW = w * 0.90f
        val gridH = h * 0.66f
        val gapX = 10f
        val gapY = 12f
        val cellW = (gridW - gapX * (cols - 1)) / cols
        val cellH = (gridH - gapY * (rows - 1)) / rows
        val unlockedMax = progressManager.getUnlockedLevel()

        for (idx in 0 until 33) {
            val lvl = activeChapter.startLevel + idx
            val col = idx % cols
            val row = idx / cols
            val l = gridLeft + col * (cellW + gapX)
            val t = gridTop + row * (cellH + gapY)
            val rect = RectF(l, t, l + cellW, t + cellH)

            val isUnlocked = lvl <= unlockedMax
            val isBoss = lvl % 10 == 0 || lvl == 99
            val stars = progressManager.getStars(lvl)

            uiPaint.color = when {
                !isUnlocked -> 0x8811111A.toInt()
                isBoss -> 0xCC4A1025.toInt()
                else -> 0xCC162447.toInt()
            }
            canvas.drawRoundRect(rect, 12f, 12f, uiPaint)

            uiPaint.style = Paint.Style.STROKE
            uiPaint.strokeWidth = if (isBoss) 4f else 3f
            uiPaint.color = when {
                !isUnlocked -> 0x5578909C.toInt()
                isBoss -> 0xFFFFEA00.toInt()
                else -> 0xFF00E5FF.toInt()
            }
            canvas.drawRoundRect(rect, 12f, 12f, uiPaint)
            uiPaint.style = Paint.Style.FILL

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = cellH * 0.34f
            textPaint.color = if (isUnlocked) Color.WHITE else 0xFF78909C.toInt()
            canvas.drawText(if (isUnlocked) "$lvl" else "🔒", rect.centerX(), rect.centerY() - cellH * 0.04f, textPaint)

            if (isUnlocked) {
                textPaint.textSize = cellH * 0.22f
                textPaint.color = 0xFFFFD600.toInt()
                val starStr = "★".repeat(stars) + "☆".repeat(3 - stars)
                canvas.drawText(starStr, rect.centerX(), rect.bottom - cellH * 0.14f, textPaint)
                frameButtons["LVL_$lvl"] = rect
            }
        }
    }

    private fun drawGameplayScreen(canvas: Canvas, w: Float, h: Float, frameButtons: MutableMap<String, RectF>) {
        val bp = currentBlueprint ?: return
        val ppm = basePpm * zoomScale
        val groundY = h * 0.85f

        val shakeX = if (shakeIntensity > 0f) (rng.nextFloat() - 0.5f) * shakeIntensity else 0f
        val shakeY = if (shakeIntensity > 0f) (rng.nextFloat() - 0.5f) * shakeIntensity else 0f

        canvas.save()
        canvas.translate(shakeX, shakeY)

        fun toScreenX(wx: Float): Float = wx * ppm - cameraX
        fun toScreenY(wy: Float): Float = groundY - wy * ppm

        renderer.drawBackground(canvas, w, h, groundY, cameraX, bp.theme, totalTime)

        val sx = toScreenX(slingX)
        val sy = toScreenY(slingY)
        renderer.drawSlingshotBack(canvas, sx, sy, ppm)

        // Pulsing touch-zone halo around slingshot when ready to aim
        if (!isAiming && loadedProjectile != null) {
            val haloR = ppm * (1.35f + 0.18f * sin(totalTime * 5f))
            uiPaint.style = Paint.Style.STROKE
            uiPaint.strokeWidth = 3f
            uiPaint.color = 0x6600E5FF.toInt()
            canvas.drawCircle(sx, sy, haloR, uiPaint)
            uiPaint.style = Paint.Style.FILL
        }

        if (isAiming && loadedProjectile != null) {
            val dx = slingX - aimX
            val dy = slingY - aimY
            val vx0 = dx * launchMultiplier
            val vy0 = dy * launchMultiplier
            val g = -16.8f
            uiPaint.color = 0xEE00E5FF.toInt()
            for (step in 1..22) {
                val t = step * 0.085f
                val px = slingX + vx0 * t
                val py = slingY + vy0 * t + 0.5f * g * t * t
                if (py < 0f) break
                val dotRadius = (7.5f - step * 0.22f).coerceAtLeast(2.5f)
                canvas.drawCircle(toScreenX(px), toScreenY(py), dotRadius, uiPaint)
            }
        }

        synchronized(entities) {
            for (e in entities) {
                val b = e.body ?: continue
                val cx = toScreenX(b.position.x)
                val cy = toScreenY(b.position.y)
                val deg = -Math.toDegrees(b.angle.toDouble()).toFloat()

                when (e.kind) {
                    EntityKind.BLOCK -> renderer.drawBlock(canvas, e, cx, cy, deg, ppm, totalTime)
                    EntityKind.ENEMY -> renderer.drawEnemy(
                        canvas, e, cx, cy, deg, e.radius * ppm, isEnemyProtectedByShield(e)
                    )
                    EntityKind.PROJECTILE -> {
                        if (e.trail.size > 1) {
                            uiPaint.color = e.projectileType?.accentColor ?: 0x88FFFFFF.toInt()
                            for ((idx, pt) in e.trail.withIndex()) {
                                val alpha = ((idx.toFloat() / e.trail.size) * 200).toInt()
                                uiPaint.alpha = alpha
                                canvas.drawCircle(toScreenX(pt.first), toScreenY(pt.second), 5f, uiPaint)
                            }
                            uiPaint.alpha = 255
                        }
                        val isReadyForPower = (activeProjectile?.id == e.id && !e.abilityUsed)
                        e.projectileType?.let {
                            renderer.drawProjectile(canvas, it, cx, cy, deg, e.radius * ppm, isReadyForPower, totalTime)
                        }
                    }
                    EntityKind.GROUND -> {}
                }
            }

            val loadedBody = loadedProjectile?.body
            val bandX = if (loadedBody != null) toScreenX(loadedBody.position.x) else null
            val bandY = if (loadedBody != null) toScreenY(loadedBody.position.y) else null
            val pullRatio = (hypot(slingX - aimX, slingY - aimY) / maxPullMeters).coerceIn(0f, 1f)
            renderer.drawSlingshotFront(canvas, sx, sy, bandX, bandY, pullRatio, ppm)

            for (sw in shockwaves) {
                val prog = 1f - (sw.life / sw.maxLife).coerceIn(0f, 1f)
                uiPaint.style = Paint.Style.STROKE
                uiPaint.strokeWidth = 6f * (1f - prog)
                uiPaint.color = sw.color
                uiPaint.alpha = ((1f - prog) * 220).toInt()
                canvas.drawCircle(toScreenX(sw.x), toScreenY(sw.y), sw.maxRadius * prog * ppm, uiPaint)
            }
            uiPaint.style = Paint.Style.FILL
            uiPaint.alpha = 255

            for (p in particles) {
                uiPaint.color = p.color
                uiPaint.alpha = ((p.life / p.maxLife).coerceIn(0f, 1f) * 255).toInt()
                val px = toScreenX(p.x)
                val py = toScreenY(p.y)
                val r = p.size * ppm
                if (p.isShard) {
                    canvas.drawRect(px - r, py - r * 0.6f, px + r, py + r * 0.6f, uiPaint)
                } else {
                    canvas.drawCircle(px, py, r, uiPaint)
                }
            }
            uiPaint.alpha = 255

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = h * 0.038f
            for (f in floatingScores) {
                textPaint.color = f.color
                canvas.drawText(f.text, toScreenX(f.x), toScreenY(f.y), textPaint)
            }

            for ((idx, qType) in remainingQueue.withIndex()) {
                val qx = toScreenX(slingX - 1.3f - idx * 0.95f)
                val qy = groundY - qType.radius * ppm
                renderer.drawProjectile(canvas, qType, qx, qy, 0f, qType.radius * ppm * 0.85f)
            }
        }

        canvas.restore()
        drawHud(canvas, w, h, bp, frameButtons)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float, bp: LevelBlueprint, frameButtons: MutableMap<String, RectF>) {
        val menuRect = RectF(w * 0.02f, h * 0.03f, w * 0.11f, h * 0.11f)
        drawGlassButton(canvas, menuRect, "☰", 0xAA162447.toInt(), 0xFF00E5FF.toInt(), textSizeRatio = 0.52f)
        frameButtons["HUD_MENU"] = menuRect

        val restartRect = RectF(w * 0.125f, h * 0.03f, w * 0.215f, h * 0.11f)
        drawGlassButton(canvas, restartRect, "↻", 0xAA162447.toInt(), 0xFFFFEA00.toInt(), textSizeRatio = 0.56f)
        frameButtons["HUD_RESTART"] = restartRect

        val muteRect = RectF(w * 0.23f, h * 0.03f, w * 0.31f, h * 0.11f)
        drawGlassButton(
            canvas, muteRect,
            if (soundEngine.isMuted) "🔇" else "🔊",
            0xAA162447.toInt(), 0xFF00E5FF.toInt(), textSizeRatio = 0.46f
        )
        frameButtons["HUD_MUTE"] = muteRect

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.036f
        textPaint.color = Color.WHITE
        canvas.drawText(bp.title, w * 0.53f, h * 0.068f, textPaint)

        val currentHero = activeProjectile?.projectileType ?: loadedProjectile?.projectileType
        if (currentHero != null) {
            textPaint.textSize = h * 0.029f
            textPaint.color = 0xFF00E5FF.toInt()
            canvas.drawText("${currentHero.displayName}  •  ${currentHero.powerLabel}", w * 0.53f, h * 0.105f, textPaint)
        }

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = h * 0.042f
        textPaint.color = 0xFFFFEA00.toInt()
        canvas.drawText("SCORE : $score", w * 0.975f, h * 0.072f, textPaint)

        textPaint.textSize = h * 0.027f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("Objectif 3★ : ${bp.threeStarScore}", w * 0.975f, h * 0.108f, textPaint)
    }

    private fun drawEndOverlay(canvas: Canvas, w: Float, h: Float, isVictory: Boolean, frameButtons: MutableMap<String, RectF>) {
        uiPaint.color = 0xCC060917.toInt()
        canvas.drawRect(0f, 0f, w, h, uiPaint)

        val card = RectF(w * 0.23f, h * 0.17f, w * 0.77f, h * 0.83f)
        uiPaint.color = 0xF0131C3A.toInt()
        canvas.drawRoundRect(card, 28f, 28f, uiPaint)

        uiPaint.style = Paint.Style.STROKE
        uiPaint.strokeWidth = 5f
        uiPaint.color = if (isVictory) 0xFFFFEA00.toInt() else 0xFFFF1744.toInt()
        canvas.drawRoundRect(card, 28f, 28f, uiPaint)
        uiPaint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = h * 0.072f
        textPaint.color = if (isVictory) 0xFFFFEA00.toInt() else 0xFFFF5252.toInt()
        canvas.drawText(
            if (isVictory) "FORTERESSE VAINCUE !" else "ÉCHEC DE LA MISSION",
            w * 0.5f, h * 0.31f, textPaint
        )

        if (isVictory) {
            textPaint.textSize = h * 0.088f
            textPaint.color = 0xFFFFD600.toInt()
            val starText = "★".repeat(earnedStars) + "☆".repeat(3 - earnedStars)
            canvas.drawText(starText, w * 0.5f, h * 0.44f, textPaint)
        }

        textPaint.textSize = h * 0.045f
        textPaint.color = Color.WHITE
        canvas.drawText("Score Final : $score", w * 0.5f, h * 0.54f, textPaint)

        val retryRect = RectF(w * 0.27f, h * 0.63f, w * 0.48f, h * 0.75f)
        drawGlassButton(canvas, retryRect, "REJOUER", 0xFF263238.toInt(), 0xFF00E5FF.toInt(), textSizeRatio = 0.34f)
        frameButtons["END_RETRY"] = retryRect

        if (isVictory && currentLevelNumber < 99) {
            val nextRect = RectF(w * 0.52f, h * 0.63f, w * 0.73f, h * 0.75f)
            drawGlassButton(canvas, nextRect, "NIVEAU ${currentLevelNumber + 1} →", 0xFFD91E2A.toInt(), 0xFFFFEA00.toInt(), textSizeRatio = 0.34f)
            frameButtons["END_NEXT"] = nextRect
        } else {
            val levelsRect = RectF(w * 0.52f, h * 0.63f, w * 0.73f, h * 0.75f)
            drawGlassButton(canvas, levelsRect, "NIVEAUX", 0xFF1A237E.toInt(), 0xFF00E5FF.toInt(), textSizeRatio = 0.34f)
            frameButtons["END_LEVELS"] = levelsRect
        }
    }

    private fun drawGlassButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        fillColor: Int,
        borderColor: Int,
        textSizeRatio: Float = 0.38f
    ) {
        uiPaint.color = fillColor
        canvas.drawRoundRect(rect, 18f, 18f, uiPaint)

        uiPaint.style = Paint.Style.STROKE
        uiPaint.strokeWidth = 3.5f
        uiPaint.color = borderColor
        canvas.drawRoundRect(rect, 18f, 18f, uiPaint)
        uiPaint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = rect.height() * textSizeRatio
        textPaint.color = Color.WHITE
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val x = event.x
        val y = event.y

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val buttonsSnapshot = activeButtonRects
            val pad = 22f
            for ((key, rect) in buttonsSnapshot) {
                if (x >= rect.left - pad && x <= rect.right + pad &&
                    y >= rect.top - pad && y <= rect.bottom + pad
                ) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    handleButtonAction(key)
                    return true
                }
            }
        }

        if (screenState != ScreenState.PLAYING) return true

        val wPx = width.toFloat().coerceAtLeast(1f)
        val hPx = height.toFloat().coerceAtLeast(1f)
        val ppm = basePpm * zoomScale
        val groundY = hPx * 0.85f
        val wx = (x + cameraX) / ppm
        val wy = (groundY - y) / ppm

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val active = activeProjectile
                if (active != null && !active.abilityUsed) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    commandQueue.offer { triggerProjectileAbilityInternal() }
                    return true
                }

                val loaded = loadedProjectile
                val nearSlingWorld = hypot(wx - slingX, wy - slingY) < 4.5f
                val onLeftLaunchZone = x < wPx * 0.42f
                if (loaded != null && (nearSlingWorld || onLeftLaunchZone)) {
                    isAiming = true
                    isPanning = false
                    previewTimer = 0f
                    lastHapticBucket = -1
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    updateAimCoordinatesLockFree(wx, wy)
                } else {
                    isPanning = true
                    previewTimer = 0f
                    lastTouchX = x
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val active = activeProjectile
                if (active != null && !active.abilityUsed) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    commandQueue.offer { triggerProjectileAbilityInternal() }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAiming) {
                    val effectiveWx = x / ppm
                    updateAimCoordinatesLockFree(effectiveWx, wy)
                } else if (isPanning && !scaleDetector.isInProgress) {
                    val dx = x - lastTouchX
                    lastTouchX = x
                    cameraX = (cameraX - dx).coerceIn(0f, 16f * ppm)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isAiming) {
                    isAiming = false
                    val snapX = aimX
                    val snapY = aimY
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    commandQueue.offer { launchLoadedProjectileInternal(snapX, snapY) }
                }
                isPanning = false
            }
        }

        return true
    }

    private fun updateAimCoordinatesLockFree(wx: Float, wy: Float) {
        var dx = wx - slingX
        var dy = wy - slingY
        val dist = hypot(dx, dy)
        if (dist > maxPullMeters) {
            dx = dx / dist * maxPullMeters
            dy = dy / dist * maxPullMeters
        }
        aimX = slingX + dx
        aimY = (slingY + dy).coerceAtLeast(0.45f)

        val bucket = ((dist.coerceAtMost(maxPullMeters) / maxPullMeters) * 4f).toInt()
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun handleButtonAction(key: String) {
        when {
            key == "PLAY_CURRENT" -> enqueueStartLevel(progressManager.getUnlockedLevel())
            key == "OPEN_LEVELS" -> {
                selectedChapter = (progressManager.getUnlockedLevel() - 1) / 33
                screenState = ScreenState.LEVEL_SELECT
            }
            key == "BACK_TITLE" -> screenState = ScreenState.TITLE
            key.startsWith("TAB_") -> selectedChapter = key.removePrefix("TAB_").toIntOrNull() ?: 0
            key.startsWith("LVL_") -> {
                val lvl = key.removePrefix("LVL_").toIntOrNull() ?: 1
                enqueueStartLevel(lvl)
            }
            key == "HUD_MENU" || key == "END_LEVELS" -> {
                selectedChapter = (currentLevelNumber - 1) / 33
                screenState = ScreenState.LEVEL_SELECT
            }
            key == "HUD_RESTART" || key == "END_RETRY" -> enqueueStartLevel(currentLevelNumber)
            key == "HUD_MUTE" -> soundEngine.isMuted = !soundEngine.isMuted
            key == "END_NEXT" -> enqueueStartLevel(currentLevelNumber + 1)
        }
    }
}
