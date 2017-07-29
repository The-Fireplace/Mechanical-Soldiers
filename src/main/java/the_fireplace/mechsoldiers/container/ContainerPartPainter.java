package the_fireplace.mechsoldiers.container;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import the_fireplace.overlord.container.SlotOutput;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ContainerPartPainter extends Container {
	private final IInventory tileConstructor;
	private int redValue;
	private int greenValue;
	private int blueValue;

	public ContainerPartPainter(InventoryPlayer playerInventory, IInventory furnaceInventory) {
		this.tileConstructor = furnaceInventory;
		this.addSlotToContainer(new Slot(furnaceInventory, 0, 8, 8));
		this.addSlotToContainer(new Slot(furnaceInventory, 1, 152, 18));
		this.addSlotToContainer(new Slot(furnaceInventory, 2, 152, 40));
		this.addSlotToContainer(new Slot(furnaceInventory, 3, 152, 62));
		this.addSlotToContainer(new SlotOutput(furnaceInventory, 4, 8, 62));

		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 9; ++j) {
				this.addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}

		for (int k = 0; k < 9; ++k) {
			this.addSlotToContainer(new Slot(playerInventory, k, 8 + k * 18, 142));
		}
	}

	@Override
	public void addListener(IContainerListener listener) {
		super.addListener(listener);
		listener.sendAllWindowProperties(this, this.tileConstructor);
	}

	@Override
	public void detectAndSendChanges() {
		super.detectAndSendChanges();

		for (int i = 0; i < this.listeners.size(); ++i) {
			IContainerListener icontainerlistener = this.listeners.get(i);

			if (this.redValue != this.tileConstructor.getField(0)) {
				icontainerlistener.sendWindowProperty(this, 0, this.tileConstructor.getField(0));
			}

			if (this.blueValue != this.tileConstructor.getField(2)) {
				icontainerlistener.sendWindowProperty(this, 2, this.tileConstructor.getField(2));
			}

			if (this.greenValue != this.tileConstructor.getField(1)) {
				icontainerlistener.sendWindowProperty(this, 1, this.tileConstructor.getField(1));
			}
		}

		this.redValue = this.tileConstructor.getField(0);
		this.blueValue = this.tileConstructor.getField(2);
		this.greenValue = this.tileConstructor.getField(1);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void updateProgressBar(int id, int data) {
		this.tileConstructor.setField(id, data);
	}

	@Override
	public boolean canInteractWith(EntityPlayer playerIn) {
		return this.tileConstructor.isUsableByPlayer(playerIn);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int i) {
		Slot slot = getSlot(i);
		if (slot != null && slot.getHasStack()) {
			ItemStack is = slot.getStack();
			ItemStack result = is.copy();

			if (i >= 36) {
				if (!mergeItemStack(is, 0, 36, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!mergeItemStack(is, 36, 36 + tileConstructor.getSizeInventory(), false)) {
				return ItemStack.EMPTY;
			}
			if (is.isEmpty()) {
				slot.putStack(ItemStack.EMPTY);
			} else {
				slot.onSlotChanged();
			}
			slot.onTake(player, is);
			return result;
		}
		return ItemStack.EMPTY;
	}
}