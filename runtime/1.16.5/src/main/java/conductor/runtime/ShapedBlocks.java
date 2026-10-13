package conductor.runtime;

import conductor.contract.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 1.16.5's form of the block classes for shapes vanilla has no plain class for: cubes that face the player,
 * and blocks shaped by boxes. Same behaviour as the 1.21 classes; the method signatures are 1.16's.
 */
final class ShapedBlocks {

    private ShapedBlocks() {}

    /** A cube facing any of the six directions; it faces the player who places it, like an observer. */
    static final class Facing extends Block {
        static final DirectionProperty FACING = BlockStateProperties.FACING;

        Facing(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    /** A cube facing north, east, south or west; it faces the player who places it, like a furnace. */
    static final class HorizontalFacing extends Block {
        static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        HorizontalFacing(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    /**
     * A block shaped by boxes (given facing north), turned with the block; it can hold water and lets light through,
     * like a lantern.
     */
    static final class Model extends Block implements SimpleWaterloggedBlock {
        static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

        private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

        Model(Properties props, List<BlockBox> boxes) {
            super(props);
            for (Direction d : Arrays.asList(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                VoxelShape shape = Shapes.empty();
                for (BlockBox b : boxes) {
                    BlockBox t = b.facing(d.getSerializedName());
                    shape = Shapes.or(shape, Block.box(t.x1(), t.y1(), t.z1(), t.x2(), t.y2(), t.z2()));
                }
                shapes.put(d, shape.optimize());
            }
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, WATERLOGGED);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, water);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            VoxelShape shape = shapes == null ? null : shapes.get(state.getValue(FACING));
            return shape != null ? shape : Shapes.block();
        }

        @Override
        public FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                      BlockPos pos, BlockPos neighbourPos) {
            if (state.getValue(WATERLOGGED)) {
                level.getLiquidTicks().scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return !state.getValue(WATERLOGGED);
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }
}
