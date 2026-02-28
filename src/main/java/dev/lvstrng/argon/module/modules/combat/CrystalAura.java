package dev.lvstrng.argon.module.modules.combat;

import com.google.common.collect.Lists;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.*;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto crystal: places and breaks crystals for maximum damage.
 * Uses rotations, damage calculation, and packet-level control.
 */
public final class CrystalAura extends Module implements PlayerTickListener, PacketSendListener, PacketReceiveListener, MovementPacketListener, GameRenderListener {

	// Target
	private final NumberSetting targetRange = new NumberSetting(EncryptedString.of("Target Range"), 1, 15, 12, 0.5);
	private final BooleanSetting players = new BooleanSetting(EncryptedString.of("Players"), true);
	private final BooleanSetting monsters = new BooleanSetting(EncryptedString.of("Monsters"), false);
	private final BooleanSetting animals = new BooleanSetting(EncryptedString.of("Animals"), false);

	// Break
	private final NumberSetting breakSpeed = new NumberSetting(EncryptedString.of("Break Speed"), 0.1, 20, 15, 0.5);
	private final NumberSetting breakRange = new NumberSetting(EncryptedString.of("Break Range"), 1, 6, 4, 0.1);
	private final NumberSetting breakWallRange = new NumberSetting(EncryptedString.of("Break Wall Range"), 1, 6, 4, 0.1);
	private final NumberSetting maxYOffset = new NumberSetting(EncryptedString.of("Max Y Offset"), 1, 10, 5, 0.5);
	private final NumberSetting ticksExisted = new NumberSetting(EncryptedString.of("Ticks Existed"), 0, 10, 0, 1);
	private final BooleanSetting raytrace = new BooleanSetting(EncryptedString.of("Raytrace"), true);
	private final BooleanSetting swing = new BooleanSetting(EncryptedString.of("Swing"), true);

	// Place
	private final BooleanSetting place = new BooleanSetting(EncryptedString.of("Place"), true);
	private final NumberSetting placeSpeed = new NumberSetting(EncryptedString.of("Place Speed"), 0.1, 20, 15, 0.5);
	private final NumberSetting placeRange = new NumberSetting(EncryptedString.of("Place Range"), 1, 6, 4, 0.1);
	private final NumberSetting placeWallRange = new NumberSetting(EncryptedString.of("Place Wall Range"), 1, 6, 4, 0.1);
	private final BooleanSetting strictDirection = new BooleanSetting(EncryptedString.of("Strict Direction"), false);

	// Damage
	private final NumberSetting minDamage = new NumberSetting(EncryptedString.of("Min Damage"), 1, 10, 3.5, 0.1);
	private final NumberSetting maxSelfDamage = new NumberSetting(EncryptedString.of("Max Self Damage"), 1, 20, 8, 0.5);
	private final BooleanSetting safety = new BooleanSetting(EncryptedString.of("Safety"), true);
	private final BooleanSetting antiWeakness = new BooleanSetting(EncryptedString.of("Anti-Weakness"), true);

	// Swap
	private final ModeSetting<SwapMode> swapMode = new ModeSetting<>(EncryptedString.of("Swap"), SwapMode.Silent, SwapMode.class);
	private final ModeSetting<SwapMode> antiWeaknessSwap = new ModeSetting<>(EncryptedString.of("AntiWeakness Swap"), SwapMode.Silent, SwapMode.class);

	// Rotate
	private final BooleanSetting rotate = new BooleanSetting(EncryptedString.of("Rotate"), false);
	private final NumberSetting yawStep = new NumberSetting(EncryptedString.of("Yaw Step"), 1, 180, 180, 1);

	// Render
	private final BooleanSetting render = new BooleanSetting(EncryptedString.of("Render"), true);
	private final BooleanSetting disableOnDeath = new BooleanSetting(EncryptedString.of("Disable On Death"), false);

	public enum SwapMode { Off, Normal, Silent }

	private DamageData<EndCrystalEntity> attackCrystal;
	private DamageData<BlockPos> placeCrystal;
	private BlockPos renderPos;
	private double renderDamage;
	private Vec3d crystalRotation;
	private boolean attackRotate;
	private boolean rotated;
	private float[] silentRotations;
	private final Map<Integer, Long> attackPackets = new ConcurrentHashMap<>();
	private final Map<BlockPos, Long> placePackets = new ConcurrentHashMap<>();
	private final Deque<Long> attackLatency = new ArrayDeque<>();
	private static final int MAX_LATENCY_QUEUE = 20;
	private long lastPlaceTime;
	private long lastAttackTime;
	private int predictId;

	public CrystalAura() {
		super(EncryptedString.of("Crystal Aura"),
				EncryptedString.of("Places and breaks crystals for max damage"),
				-1,
				Category.COMBAT);
		addSettings(targetRange, players, monsters, animals,
				breakSpeed, breakRange, breakWallRange, maxYOffset, ticksExisted, raytrace, swing,
				place, placeSpeed, placeRange, placeWallRange, strictDirection,
				minDamage, maxSelfDamage, safety, antiWeakness,
				swapMode, antiWeaknessSwap, rotate, yawStep, render, disableOnDeath);
	}

	@Override
	public void onEnable() {
		eventManager.add(PlayerTickListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		if (rotate.getValue()) {
			eventManager.add(MovementPacketListener.class, Argon.INSTANCE.rotatorManager);
		}
		attackCrystal = null;
		placeCrystal = null;
		renderPos = null;
		attackPackets.clear();
		placePackets.clear();
		attackLatency.clear();
		lastPlaceTime = 0;
		lastAttackTime = 0;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PlayerTickListener.class, this);
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(MovementPacketListener.class, Argon.INSTANCE.rotatorManager);
		Argon.INSTANCE.rotatorManager.disable();
		renderPos = null;
		attackCrystal = null;
		placeCrystal = null;
		super.onDisable();
	}

	@Override
	public void onPlayerTick() {
		if (mc.player == null || mc.world == null || mc.player.isSpectator()) return;

		renderPos = null;
		List<Entity> entities = Lists.newArrayList(mc.world.getEntities());
		List<BlockPos> placeBlocks = getSphere(placeRange.getValue());

		if (place.getValue()) {
			placeCrystal = calculatePlaceCrystal(placeBlocks, entities);
		} else {
			placeCrystal = null;
		}
		attackCrystal = calculateAttackCrystal(entities);

		if (attackCrystal == null && placeCrystal != null) {
			EndCrystalEntity atPos = getCrystalAt(placeCrystal.getDamageData());
			if (atPos != null) {
				double selfDmg = DamageUtils.crystalDamage(mc.player, crystalVec(placeCrystal.getDamageData()));
				if (!safety.getValue() || selfDmg < maxSelfDamage.getValue()) {
					attackCrystal = new DamageData<>(atPos, placeCrystal.getAttackTarget(), placeCrystal.getDamage(), selfDmg, atPos.getBlockPos().down(), false);
				}
			}
		}

		float breakDelay = getBreakDelay();
		attackRotate = attackCrystal != null && (System.currentTimeMillis() - lastAttackTime) >= breakDelay;

		if (attackCrystal != null) {
			crystalRotation = attackCrystal.getDamageData().getPos();
		} else if (placeCrystal != null) {
			crystalRotation = placeCrystal.getDamageData().toCenterPos().add(0, 0.5, 0);
		}

		if (rotate.getValue() && crystalRotation != null && (placeCrystal == null || isHoldingCrystal())) {
			Rotation targetRot = RotationUtils.getDirection(mc.player, crystalRotation);
			float[] rots = new float[] { (float) targetRot.yaw(), (float) targetRot.pitch() };
			if (yawStep.getValue() < 180) {
				float serverYaw = (float) Argon.INSTANCE.rotatorManager.getServerRotation().yaw();
				float diff = MathHelper.wrapDegrees(serverYaw - rots[0]);
				float step = yawStep.getValueFloat();
				if (Math.abs(diff) > step) {
					rots[0] = serverYaw + (diff > 0 ? -step : step);
					rotated = false;
				} else {
					rotated = true;
					crystalRotation = null;
				}
			} else {
				rotated = true;
				crystalRotation = null;
			}
			Argon.INSTANCE.rotatorManager.enable();
			Argon.INSTANCE.rotatorManager.setRotation(rots[0], rots[1]);
		} else {
			silentRotations = null;
		}

		if (Argon.INSTANCE.rotatorManager.isEnabled() && !rotated && rotate.getValue()) return;

		Hand hand = getCrystalHand();
		if (attackCrystal != null && attackRotate) {
			attackCrystal(attackCrystal.getDamageData(), hand);
			lastAttackTime = System.currentTimeMillis();
		}

		boolean placeRotate = (System.currentTimeMillis() - lastPlaceTime) >= (1000.0 - placeSpeed.getValue() * 50.0);
		if (placeCrystal != null) {
			renderPos = placeCrystal.getDamageData();
			renderDamage = placeCrystal.getDamage();
			if (placeRotate) {
				placeCrystal(placeCrystal.getDamageData(), hand);
				lastPlaceTime = System.currentTimeMillis();
			}
		}
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.player == null) return;
		if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
			// track slot changes for swap delay if needed
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (mc.player == null || mc.world == null) return;
		Packet<?> p = event.packet;
		if (p instanceof ExplosionS2CPacket packet) {
			for (Entity e : Lists.newArrayList(mc.world.getEntities())) {
				if (e instanceof EndCrystalEntity && e.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) < 144) {
					mc.execute(() -> mc.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED));
					Long t = attackPackets.remove(e.getId());
					if (t != null) {
						attackLatency.add(System.currentTimeMillis() - t);
						while (attackLatency.size() > MAX_LATENCY_QUEUE) attackLatency.poll();
					}
				}
			}
		}
		if (p instanceof PlaySoundS2CPacket packet) {
			if (packet.getSound().value() == SoundEvents.ENTITY_GENERIC_EXPLODE.value() && packet.getCategory() == SoundCategory.BLOCKS) {
				for (Entity e : Lists.newArrayList(mc.world.getEntities())) {
					if (e instanceof EndCrystalEntity && e.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) < 144) {
						mc.execute(() -> mc.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED));
						Long t = attackPackets.remove(e.getId());
						if (t != null) {
							attackLatency.add(System.currentTimeMillis() - t);
							while (attackLatency.size() > MAX_LATENCY_QUEUE) attackLatency.poll();
						}
					}
				}
			}
		}
		if (p instanceof EntitiesDestroyS2CPacket packet) {
			for (int id : packet.getEntityIds()) {
				Long t = attackPackets.remove(id);
				if (t != null) {
					attackLatency.add(System.currentTimeMillis() - t);
					while (attackLatency.size() > MAX_LATENCY_QUEUE) attackLatency.poll();
				}
			}
		}
		if (p instanceof EntitySpawnS2CPacket packet) {
			if (packet.getEntityType() == net.minecraft.entity.EntityType.END_CRYSTAL) {
				if (packet.getEntityId() > predictId) predictId = packet.getEntityId();
			}
		}
	}

	@Override
	public void onSendMovementPackets() {
		// RotatorManager handles rotation; we only set it from onPlayerTick
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (!render.getValue() || renderPos == null || mc.gameRenderer.getCamera() == null) return;
		MatrixStack ms = event.matrices;
		net.minecraft.client.render.Camera cam = mc.gameRenderer.getCamera();
		Vec3d camPos = cam.getPos();
		ms.push();
		ms.translate(-camPos.x, -camPos.y, -camPos.z);
		float r = 0.4f, g = 0.8f, b = 1f, a = 0.35f;
		RenderUtils.renderFilledBox(ms, (float) renderPos.getX(), (float) renderPos.getY(), (float) renderPos.getZ(),
				(float) (renderPos.getX() + 1), (float) (renderPos.getY() + 2), (float) (renderPos.getZ() + 1),
				new Color((int)(r*255), (int)(g*255), (int)(b*255), (int)(a*255)));
		ms.pop();
	}

	private float getBreakDelay() {
		if (attackLatency.isEmpty()) return 0;
		long sum = 0;
		for (Long l : attackLatency) sum += l;
		return (float) (sum / attackLatency.size());
	}

	private void attackCrystal(EndCrystalEntity entity, Hand hand) {
		StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
		StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);
		if (weakness != null && (strength == null || weakness.getAmplifier() > strength.getAmplifier()) && antiWeakness.getValue()) {
			int slot = -1;
			for (int i = 0; i < 9; i++) {
				ItemStack stack = mc.player.getInventory().getStack(i);
				if (!stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof PickaxeItem)) {
					slot = i;
					break;
				}
			}
			if (slot != -1 && antiWeaknessSwap.getMode() != SwapMode.Off) {
				if (antiWeaknessSwap.getMode() == SwapMode.Silent) {
					mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot + 36, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
				} else {
					InventoryUtils.setInvSlot(slot);
				}
				attackInternal(entity.getId(), Hand.MAIN_HAND);
				if (antiWeaknessSwap.getMode() == SwapMode.Silent) {
					mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot + 36, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
				}
				attackPackets.put(entity.getId(), System.currentTimeMillis());
				return;
			}
		}
		attackInternal(entity.getId(), hand != null ? hand : Hand.MAIN_HAND);
	}

	private void attackInternal(int crystalId, Hand hand) {
		EndCrystalEntity fake = new EndCrystalEntity(net.minecraft.entity.EntityType.END_CRYSTAL, mc.world);
		fake.setId(crystalId);
		mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(fake, mc.player.isSneaking()));
		if (swing.getValue()) mc.player.swingHand(hand);
		else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
		attackPackets.put(crystalId, System.currentTimeMillis());
	}

	private void placeCrystal(BlockPos pos, Hand hand) {
		if (hand == null) return;
		Direction side = getPlaceDirection(pos);
		BlockHitResult result = new BlockHitResult(pos.toCenterPos(), side, pos, false);
		if (swapMode.getMode() != SwapMode.Off && hand != Hand.OFF_HAND && getCrystalHand() == null) {
			int crystalSlot = getCrystalSlot();
			if (crystalSlot != -1) {
				if (swapMode.getMode() == SwapMode.Silent) {
					mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, crystalSlot + 36, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
				} else {
					InventoryUtils.setInvSlot(crystalSlot);
				}
				mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, 0));
				if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
				else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
				placePackets.put(pos, System.currentTimeMillis());
				if (swapMode.getMode() == SwapMode.Silent) {
					mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, crystalSlot + 36, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
				}
				return;
			}
		}
		if (isHoldingCrystal()) {
			mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, result, 0));
			if (swing.getValue()) mc.player.swingHand(hand);
			else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
			placePackets.put(pos, System.currentTimeMillis());
		}
	}

	private Direction getPlaceDirection(BlockPos blockPos) {
		int x = blockPos.getX(), y = blockPos.getY(), z = blockPos.getZ();
		if (strictDirection.getValue() && mc.player.getY() < blockPos.getY()) {
			BlockHitResult r = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), new Vec3d(x + 0.5, y + 0.5, z + 0.5), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
			if (r != null && r.getType() == HitResult.Type.BLOCK) return r.getSide();
		}
		return Direction.UP;
	}

	private DamageData<EndCrystalEntity> calculateAttackCrystal(List<Entity> entities) {
		DamageData<EndCrystalEntity> best = null;
		for (Entity crystal : entities) {
			if (!(crystal instanceof EndCrystalEntity ce) || !crystal.isAlive()) continue;
			if (crystal.age < ticksExisted.getValueInt()) continue;
			if (attackRangeCheck(ce)) continue;
			double selfDamage = DamageUtils.crystalDamage(mc.player, crystal.getPos());
			if (safety.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 0.5) continue;
			if (selfDamage > maxSelfDamage.getValue()) continue;
			for (Entity entity : entities) {
				if (entity == null || !entity.isAlive() || entity == mc.player || !isValidTarget(entity)) continue;
				if (entity instanceof PlayerEntity pe && Argon.INSTANCE.getFriendManager().isFriend(pe)) continue;
				if (mc.player.squaredDistanceTo(entity) > targetRange.getValue() * targetRange.getValue()) continue;
				double damage = entity instanceof net.minecraft.entity.LivingEntity le ? DamageUtils.crystalDamage(le, crystal.getPos()) : 0;
				if (damage < minDamage.getValue()) continue;
				if (best == null || damage > best.getDamage()) {
					best = new DamageData<>(ce, entity, damage, selfDamage, ce.getBlockPos().down(), false);
				}
			}
		}
		return best;
	}

	private DamageData<BlockPos> calculatePlaceCrystal(List<BlockPos> blocks, List<Entity> entities) {
		DamageData<BlockPos> best = null;
		for (BlockPos pos : blocks) {
			if (!canPlaceCrystal(pos) || placeRangeCheck(pos)) continue;
			Vec3d crystalVec = crystalVec(pos);
			double selfDamage = DamageUtils.crystalDamage(mc.player, crystalVec);
			if (safety.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 0.5) continue;
			if (selfDamage > maxSelfDamage.getValue()) continue;
			for (Entity entity : entities) {
				if (entity == null || !entity.isAlive() || entity == mc.player || !isValidTarget(entity)) continue;
				if (entity instanceof PlayerEntity pe && Argon.INSTANCE.getFriendManager().isFriend(pe)) continue;
				if (pos.getSquaredDistance(entity.getPos()) > 144) continue;
				if (mc.player.squaredDistanceTo(entity) > targetRange.getValue() * targetRange.getValue()) continue;
				double damage = entity instanceof net.minecraft.entity.LivingEntity le ? DamageUtils.crystalDamage(le, crystalVec) : 0;
				if (damage < minDamage.getValue()) continue;
				if (best == null || damage > best.getDamage()) {
					best = new DamageData<>(pos, entity, damage, selfDamage, false);
				}
			}
		}
		return best;
	}

	private boolean attackRangeCheck(EndCrystalEntity entity) {
		return attackRangeCheck(entity.getPos());
	}

	private boolean attackRangeCheck(Vec3d pos) {
		double br = breakRange.getValue();
		double bwr = breakWallRange.getValue();
		Vec3d eye = mc.player.getEyePos();
		double dist = eye.squaredDistanceTo(pos);
		if (dist > br * br) return true;
		if (Math.abs(pos.getY() - mc.player.getY()) > maxYOffset.getValue()) return true;
		if (raytrace.getValue()) {
			net.minecraft.util.hit.BlockHitResult r = mc.world.raycast(new RaycastContext(eye, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
			if (r != null && r.getType() != HitResult.Type.MISS && dist > bwr * bwr) return true;
		}
		return false;
	}

	private boolean placeRangeCheck(BlockPos pos) {
		double pr = placeRange.getValue();
		double pwr = placeWallRange.getValue();
		Vec3d playerPos = mc.player.getPos();
		double dist = playerPos.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (dist > pr * pr) return true;
		Vec3d to = Vec3d.of(pos).add(0.5, 2.7, 0.5);
		BlockHitResult r = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
		if (r != null && r.getType() == HitResult.Type.BLOCK && !r.getBlockPos().equals(pos) && dist > pwr * pwr) return true;
		return false;
	}

	private boolean canPlaceCrystal(BlockPos pos) {
		BlockState state = mc.world.getBlockState(pos);
		if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return false;
		return CrystalUtils.canPlaceCrystalClientAssumeObsidian(pos);
	}

	private Vec3d crystalVec(BlockPos pos) {
		return Vec3d.of(pos).add(0.5, 1.0, 0.5);
	}

	private EndCrystalEntity getCrystalAt(BlockPos pos) {
		for (Entity e : mc.world.getOtherEntities(null, new Box(pos))) {
			if (e instanceof EndCrystalEntity ce) return ce;
		}
		return null;
	}

	private boolean isValidTarget(Entity e) {
		return (e instanceof PlayerEntity && players.getValue())
				|| (e instanceof net.minecraft.entity.mob.HostileEntity && monsters.getValue())
				|| (e instanceof net.minecraft.entity.passive.AnimalEntity && animals.getValue());
	}

	private Hand getCrystalHand() {
		if (mc.player.getOffHandStack().getItem() instanceof EndCrystalItem) return Hand.OFF_HAND;
		if (mc.player.getMainHandStack().getItem() instanceof EndCrystalItem) return Hand.MAIN_HAND;
		return null;
	}

	private boolean isHoldingCrystal() {
		return getCrystalHand() != null || (swapMode.getMode() == SwapMode.Silent && getCrystalSlot() != -1);
	}

	private int getCrystalSlot() {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getStack(i).getItem() instanceof EndCrystalItem) return i;
		}
		return -1;
	}

	private List<BlockPos> getSphere(double radius) {
		List<BlockPos> list = new ArrayList<>();
		Vec3d origin = mc.player.getPos();
		int r = (int) Math.ceil(radius);
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos p = BlockPos.ofFloored(origin.x + x, origin.y + y, origin.z + z);
					list.add(p);
				}
			}
		}
		return list;
	}

	private static class DamageData<T> {
		private final T damageData;
		private final Entity attackTarget;
		private final double damage;
		private final double selfDamage;
		private final BlockPos blockPos;
		private final boolean antiSurround;

		DamageData(T data, Entity target, double damage, double selfDamage, BlockPos blockPos, boolean antiSurround) {
			this.damageData = data;
			this.attackTarget = target;
			this.damage = damage;
			this.selfDamage = selfDamage;
			this.blockPos = blockPos;
			this.antiSurround = antiSurround;
		}

		DamageData(BlockPos pos, Entity target, double damage, double selfDamage, boolean antiSurround) {
			this.damageData = (T) pos;
			this.attackTarget = target;
			this.damage = damage;
			this.selfDamage = selfDamage;
			this.blockPos = pos;
			this.antiSurround = antiSurround;
		}

		T getDamageData() { return damageData; }
		Entity getAttackTarget() { return attackTarget; }
		double getDamage() { return damage; }
		double getSelfDamage() { return selfDamage; }
		BlockPos getBlockPos() { return blockPos; }
	}
}
