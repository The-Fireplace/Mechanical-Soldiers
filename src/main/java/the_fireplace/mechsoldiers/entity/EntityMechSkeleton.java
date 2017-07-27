package the_fireplace.mechsoldiers.entity;

import com.google.common.collect.Lists;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import the_fireplace.mechsoldiers.MechSoldiers;
import the_fireplace.mechsoldiers.registry.PartRegistry;
import the_fireplace.mechsoldiers.util.EnumPartType;
import the_fireplace.mechsoldiers.util.ICPU;
import the_fireplace.overlord.Overlord;
import the_fireplace.overlord.entity.EntityArmyMember;
import the_fireplace.overlord.network.PacketDispatcher;
import the_fireplace.overlord.network.packets.RequestAugmentMessage;
import the_fireplace.overlord.registry.AugmentRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

/**
 * @author The_Fireplace
 */
@ParametersAreNonnullByDefault
public class EntityMechSkeleton extends EntityArmyMember {
	public boolean cachedClientParts = false;
	/*
	0: Main hand
	1: Off hand
	2: Augment
	 */
	public final InventoryBasic equipInventory;
	/*
	0: Skeleton
	1: Joints
	2: CPU
	 */
	public final InventoryBasic partInventory;

	public EntityMechSkeleton(World world) {
		this(world, null);
	}

	public EntityMechSkeleton(World world, @Nullable UUID owner) {
		super(world, owner);
		this.equipInventory = new InventoryBasic("Equipment", false, 3) {
			@Override
			public boolean isItemValidForSlot(int index, ItemStack stack) {
				return index >= 0 && index < 2 || index == 2 && AugmentRegistry.getAugment(stack) != null;
			}

			@Override
			public void setInventorySlotContents(int index, @Nullable ItemStack stack) {
				super.setInventorySlotContents(index, stack);
				if (EntityMechSkeleton.this.world.isRemote && index == 2) {
					PacketDispatcher.sendToServer(new RequestAugmentMessage(EntityMechSkeleton.this));
				}
			}

			@Nullable
			@Override
			public ItemStack removeStackFromSlot(int index) {
				ItemStack stack = super.removeStackFromSlot(index);
				if (EntityMechSkeleton.this.world.isRemote && index == 2) {
					PacketDispatcher.sendToServer(new RequestAugmentMessage(EntityMechSkeleton.this));
				}

				return stack;
			}

			@Nullable
			@Override
			public ItemStack decrStackSize(int index, int count) {
				ItemStack stack = super.decrStackSize(index, count);
				if (EntityMechSkeleton.this.world.isRemote && index == 2) {
					PacketDispatcher.sendToServer(new RequestAugmentMessage(EntityMechSkeleton.this));
				}

				return stack;
			}
		};

		this.partInventory = new InventoryBasic("Parts", false, 3) {
			@Override
			public boolean isItemValidForSlot(int index, ItemStack stack) {
				return (index == 0 && PartRegistry.isPartOfType(stack, EnumPartType.SKELETON)) || (index == 1 && PartRegistry.isPartOfType(stack, EnumPartType.JOINTS) || (index == 2 && PartRegistry.isPartOfType(stack, EnumPartType.CPU)));
			}
		};
	}

	private DamageSource actualDamageSource = DamageSource.generic;

	@Override
	public void addMovementTasks() {
		if (getCPU() != null) {
			ICPU cpu = PartRegistry.getCPU(getCPU());
			if (cpu != null)
				cpu.addMovementAi(this, getMovementMode());
		}
	}

	@Override
	public void addAttackTasks() {
		if (getCPU() != null) {
			ICPU cpu = PartRegistry.getCPU(getCPU());
			if (cpu != null)
				cpu.addAttackAi(this, getAttackMode());
		}
	}

	@Override
	public void addTargetTasks() {
		if (getCPU() != null) {
			ICPU cpu = PartRegistry.getCPU(getCPU());
			if (cpu != null)
				cpu.addTargetAi(this, getAttackMode());
		}
	}

	@Override
	public void onLivingUpdate() {
		if (getJoints() == null || getSkeleton() == null || getJoints().stackSize <= 0 || getSkeleton().stackSize <= 0)
			kill();

		super.onLivingUpdate();
	}

	@Override
	protected void damageEntity(DamageSource damageSrc, float damageAmount) {
		if (ticksExisted <= 3)
			initEntityAI();//Refreshes the AI after the CPU has loaded
		if (!this.isEntityInvulnerable(damageSrc)) {
			if (damageSrc != DamageSource.outOfWorld)
				actualDamageSource = damageSrc;
			else
				setHealth(0);
			damageAmount = net.minecraftforge.common.ForgeHooks.onLivingHurt(this, damageSrc, damageAmount);
			if (damageAmount <= 0) return;
			damageAmount = this.applyArmorCalculations(damageSrc, damageAmount);
			damageAmount = this.applyPotionDamageCalculations(damageSrc, damageAmount);
			float f = damageAmount;
			damageAmount = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);
			this.setAbsorptionAmount(this.getAbsorptionAmount() - (f - damageAmount));

			if (damageAmount != 0.0F) {
				float f1 = this.getHealth();
				damageComponents(damageSrc, damageAmount);
				this.getCombatTracker().trackDamage(damageSrc, f1, damageAmount);
				this.setAbsorptionAmount(this.getAbsorptionAmount() - damageAmount);
			}
		}
	}

	protected void damageComponents(DamageSource damageSrc, float damageAmount) {
		if (damageSrc == DamageSource.anvil) {
			setCPU(PartRegistry.damagePart(getCPU(), damageSrc, damageAmount, this));
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, damageAmount / 4, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, damageAmount / 4, this));
		} else if (damageSrc == DamageSource.dragonBreath) {
			setCPU(PartRegistry.damagePart(getCPU(), damageSrc, damageAmount / 4, this));
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, damageAmount / 3, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, damageAmount / 3, this));
		} else if (damageSrc == DamageSource.fall) {
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, damageAmount / 5, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, damageAmount, this));
		} else if (damageSrc == DamageSource.lightningBolt) {
			setCPU(PartRegistry.damagePart(getCPU(), damageSrc, damageAmount, this));
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, damageAmount / 2, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, damageAmount / 2, this));
		} else if (damageSrc.isFireDamage() || damageSrc.isExplosion()) {
			setCPU(PartRegistry.damagePart(getCPU(), damageSrc, damageAmount / 3, this));
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, damageAmount / 3, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, damageAmount / 3, this));
		} else {
			setCPU(PartRegistry.damagePart(getCPU(), damageSrc, damageAmount / 12, this));
			setSkeleton(PartRegistry.damagePart(getSkeleton(), damageSrc, 3 * damageAmount / 4, this));
			setJoints(PartRegistry.damagePart(getJoints(), damageSrc, 3 * damageAmount / 7, this));
		}
	}

	public ItemStack getCPU() {
		if (partInventory == null)
			return null;
		return partInventory.getStackInSlot(2);
	}

	public EntityMechSkeleton setCPU(ItemStack cpu) {
		if (partInventory == null)
			return this;
		partInventory.setInventorySlotContents(2, cpu);
		initEntityAI();
		return this;
	}

	public ItemStack getJoints() {
		if (partInventory == null)
			return null;
		return partInventory.getStackInSlot(1);
	}

	public EntityMechSkeleton setJoints(ItemStack joints) {
		if (partInventory == null)
			return this;
		partInventory.setInventorySlotContents(1, joints);
		return this;
	}

	public ItemStack getSkeleton() {
		if (partInventory == null)
			return null;
		return partInventory.getStackInSlot(0);
	}

	public EntityMechSkeleton setSkeleton(ItemStack skeleton) {
		if (partInventory == null)
			return this;
		partInventory.setInventorySlotContents(0, skeleton);
		return this;
	}

	@Override
	public void onDeath(@Nonnull DamageSource cause) {
		super.onDeath(actualDamageSource);

		if (!this.world.isRemote) {
			int i;
			EntityItem entityitem;
			for (i = 0; i < this.partInventory.getSizeInventory(); ++i) {
				if (this.partInventory.getStackInSlot(i) != null) {
					entityitem = new EntityItem(this.world, this.posX, this.posY, this.posZ, this.partInventory.getStackInSlot(i));
					entityitem.setDefaultPickupDelay();
					this.world.spawnEntity(entityitem);
				}
			}

			for (i = 0; i < this.equipInventory.getSizeInventory(); ++i) {
				if (this.equipInventory.getStackInSlot(i) != null) {
					entityitem = new EntityItem(this.world, this.posX, this.posY, this.posZ, this.equipInventory.getStackInSlot(i));
					entityitem.setDefaultPickupDelay();
					this.world.spawnEntity(entityitem);
				}
			}
		}
	}

	@Override
	public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
		super.readEntityFromNBT(compound);

		NBTTagList mainInv = (NBTTagList) compound.getTag("SkeletonParts");
		if (mainInv != null) {
			for (int i = 0; i < mainInv.tagCount(); i++) {
				NBTTagCompound item = (NBTTagCompound) mainInv.get(i);
				int slot = item.getByte("SlotSkeletonParts");
				if (slot >= 0 && slot < partInventory.getSizeInventory()) {
					partInventory.setInventorySlotContents(slot, ItemStack.loadItemStackFromNBT(item));
				}
			}
		} else {
			Overlord.logWarn("List was null when reading Mechanical Skeleton's Inventory");
		}
		NBTTagList armorInv = (NBTTagList) compound.getTag("SkeletonEquipment");
		if (armorInv != null) {
			for (int i = 0; i < armorInv.tagCount(); i++) {
				NBTTagCompound item = (NBTTagCompound) armorInv.get(i);
				int slot = item.getByte("SlotSkeletonEquipment");
				if (slot >= 0 && slot < equipInventory.getSizeInventory()) {
					equipInventory.setInventorySlotContents(slot, ItemStack.loadItemStackFromNBT(item));
				}
			}
		} else {
			Overlord.logWarn("List was null when reading Mechanical Skeleton's Equipment");
		}
	}

	@Override
	public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
		super.writeEntityToNBT(compound);

		NBTTagList mainInv = new NBTTagList();
		for (int i = 0; i < partInventory.getSizeInventory(); i++) {
			ItemStack is = partInventory.getStackInSlot(i);
			if (is != null) {
				NBTTagCompound item = new NBTTagCompound();

				item.setByte("SlotSkeletonParts", (byte) i);
				is.writeToNBT(item);

				mainInv.appendTag(item);
			}
		}
		compound.setTag("SkeletonParts", mainInv);

		NBTTagList armorInv = new NBTTagList();
		for (int i = 0; i < equipInventory.getSizeInventory(); i++) {
			ItemStack is = equipInventory.getStackInSlot(i);
			if (is != null) {
				NBTTagCompound item = new NBTTagCompound();

				item.setByte("SlotSkeletonEquipment", (byte) i);
				is.writeToNBT(item);

				armorInv.appendTag(item);
			}
		}
		compound.setTag("SkeletonEquipment", armorInv);
	}

	@Override
	public float getEyeHeight() {
		return 1.84F;
	}

	@Override
	public double getYOffset() {
		return -0.35D;
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
		this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
		this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.0D);
		this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(1.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(999.0D);
	}

	@Override
	public boolean processInteract(EntityPlayer player, EnumHand hand, ItemStack stack) {
		if (this.getOwner() != null) {
			if (this.getOwner().equals(player)) {
				if (!player.isSneaking()) {
					FMLNetworkHandler.openGui(player, MechSoldiers.instance, hashCode(), world, (int) this.posX, (int) this.posY, (int) this.posZ);
					return true;
				}
			}
		}
		return super.processInteract(player, hand, stack);
	}

	@Override
	@Nullable
	public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn)
	{
		return slotIn == EntityEquipmentSlot.MAINHAND ? equipInventory.getStackInSlot(0) : (slotIn == EntityEquipmentSlot.OFFHAND ? equipInventory.getStackInSlot(1) : null);
	}

	@Override
	public void setItemStackToSlot(EntityEquipmentSlot slotIn, @Nullable ItemStack stack)
	{
		if (slotIn == EntityEquipmentSlot.MAINHAND)
		{
			this.playEquipSound(stack);
			this.equipInventory.setInventorySlotContents(0, stack);
			initEntityAI();
		}
		else if (slotIn == EntityEquipmentSlot.OFFHAND)
		{
			this.playEquipSound(stack);
			this.equipInventory.setInventorySlotContents(1, stack);
		}
	}

	@Override
	@Nonnull
	public Iterable<ItemStack> getHeldEquipment()
	{
		return Lists.newArrayList(this.getHeldItemMainhand(), this.getHeldItemOffhand());
	}

	@Override
	@Nullable
	public ItemStack getHeldItemMainhand()
	{
		if(equipInventory == null)
			return null;
		return equipInventory.getStackInSlot(0);
	}

	@Override
	@Nullable
	public ItemStack getHeldItemOffhand()
	{
		if(equipInventory == null)
			return null;
		return equipInventory.getStackInSlot(1);
	}

	@Override
	@Nullable
	public ItemStack getHeldItem(EnumHand hand)
	{
		if (hand == EnumHand.MAIN_HAND)
		{
			return getHeldItemMainhand();
		}
		else if (hand == EnumHand.OFF_HAND)
		{
			return getHeldItemOffhand();
		}
		else
		{
			throw new IllegalArgumentException("Invalid hand " + hand);
		}
	}

	@Override
	public void setHeldItem(EnumHand hand, @Nullable ItemStack stack)
	{
		if (hand == EnumHand.MAIN_HAND)
		{
			this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack);
		}
		else
		{
			if (hand != EnumHand.OFF_HAND)
			{
				throw new IllegalArgumentException("Invalid hand " + hand);
			}

			this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, stack);
		}
	}
}
