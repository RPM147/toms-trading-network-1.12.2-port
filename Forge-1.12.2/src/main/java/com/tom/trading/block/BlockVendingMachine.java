package com.tom.trading.block;

import com.tom.trading.BuildInfo;
import com.tom.trading.container.MachineGuiHandler;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraftforge.items.ItemStackHandler;

public final class BlockVendingMachine extends BlockContainer {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public BlockVendingMachine() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        setTranslationKey(BuildInfo.MOD_ID + ".vending_machine");
        setCreativeTab(TradingNetworkMod.CREATIVE_TAB);
        setHardness(5.0F);
        setResistance(6_000_000.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(8.0F / 15.0F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileVendingMachine();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileVendingMachine) || player.isSpectator()) return false;
        TileVendingMachine machine = (TileVendingMachine) tile;
        if (machine.isAutomationQuarantined()) {
            player.sendStatusMessage(new TextComponentTranslation("gui.toms_trading_network.automation_quarantined"), false);
            return true;
        }
        if (!machine.isDataVersionSupported()) return false;
        int gui = machine.canConfigure(player)
                && (!machine.isCreativeMode() || player.capabilities.isCreativeMode)
                ? MachineGuiHandler.CONFIG : MachineGuiHandler.TRADING;
        player.openGui(TradingNetworkMod.instance, gui, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public IBlockState getStateForPlacement(
            World world,
            BlockPos pos,
            EnumFacing facing,
            float hitX,
            float hitY,
            float hitZ,
            int meta,
            EntityLivingBase placer,
            EnumHand hand
    ) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }

    @Override
    public void onBlockPlacedBy(
            World worldIn,
            BlockPos pos,
            IBlockState state,
            EntityLivingBase placer,
            ItemStack stack
    ) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        TileEntity tileEntity = worldIn.getTileEntity(pos);
        if (!(tileEntity instanceof TileVendingMachine)) {
            return;
        }

        TileVendingMachine vendingMachine = (TileVendingMachine) tileEntity;
        // Item BlockEntityTag may carry an old UUID. A new placement is a new physical machine.
        vendingMachine.renewMachineIdentity();
        if (placer instanceof EntityPlayer) {
            vendingMachine.setOwner((EntityPlayer) placer);
        }
        if (stack.hasDisplayName()) {
            vendingMachine.setCustomName(stack.getDisplayName());
        }
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void onBlockExploded(World world, BlockPos pos, Explosion explosion) {
        // Explosions have no owner-authorized BreakEvent. Keep both the block and tile data.
        // Also protects against a forced affected-block entry despite the blast resistance.
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        if (!worldIn.isRemote) {
            TileEntity tileEntity = worldIn.getTileEntity(pos);
            if (tileEntity instanceof TileVendingMachine) {
                TileVendingMachine vendingMachine = (TileVendingMachine) tileEntity;
                dropContents(worldIn, pos, vendingMachine.getSaleStock());
                dropContents(worldIn, pos, vendingMachine.getEarnings());
                ItemStack remainder = vendingMachine.takeAutomationRemainderForDrop();
                if (!remainder.isEmpty()) {
                    InventoryHelper.spawnItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), remainder);
                }
                worldIn.updateComparatorOutputLevel(pos, this);
            }
        }

        super.breakBlock(worldIn, pos, state);
    }

    private static void dropContents(World world, BlockPos pos, ItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack dropped = stack.copy();
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), dropped);
        }
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }
}
