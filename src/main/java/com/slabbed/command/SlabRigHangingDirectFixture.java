package com.slabbed.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure absolute-coordinate adapter for RIG-3B3A's four production painting pages.
 *
 * <p>This class owns no command, world write, entity lookup, persistence, or cleanup. It accepts only the
 * reviewed route 6143 / topology 42 / selector-page 1..4 plan and converts every relative planner cell into
 * one immutable absolute ownership envelope. Production execution and tests therefore consume the same
 * geometry instead of independently reconstructing a board from bounds or visual assumptions.
 */
public final class SlabRigHangingDirectFixture {

    public static final int ROUTE_INDEX = 6143;
    public static final int TOPOLOGY_INDEX = 42;
    private static final int MAX_X_SIZE = 40;
    private static final int MAX_Y_SIZE = 20;
    private static final int MAX_Z_SIZE = 40;

    private SlabRigHangingDirectFixture() {
    }

    public record AbsoluteCell(SlabRigHangingPaintingPlan.CellPlan plan, BlockPos pos) {
        public AbsoluteCell {
            Objects.requireNonNull(plan, "plan");
            pos = Objects.requireNonNull(pos, "pos").immutable();
        }
    }

    public record AbsoluteCase(SlabRigHangingPaintingPlan.CasePlan plan,
                               List<AbsoluteCell> topologyCells,
                               List<AbsoluteCell> backingCells,
                               List<BlockPos> supportCells,
                               List<BlockPos> reservedCells,
                               BlockPos clicked,
                               BlockPos anchor,
                               Vec3 hitVector,
                               AABB reservedBounds) {
        public AbsoluteCase {
            Objects.requireNonNull(plan, "plan");
            topologyCells = List.copyOf(topologyCells);
            backingCells = List.copyOf(backingCells);
            supportCells = immutablePositions(supportCells);
            reservedCells = immutablePositions(reservedCells);
            clicked = Objects.requireNonNull(clicked, "clicked").immutable();
            anchor = Objects.requireNonNull(anchor, "anchor").immutable();
            Objects.requireNonNull(hitVector, "hitVector");
            Objects.requireNonNull(reservedBounds, "reservedBounds");
        }

        public List<AbsoluteCell> authoredCells() {
            List<AbsoluteCell> cells = new ArrayList<>(topologyCells.size() + backingCells.size());
            cells.addAll(topologyCells);
            cells.addAll(backingCells);
            return List.copyOf(cells);
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Bounds of(Set<BlockPos> positions) {
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("cannot derive bounds from an empty position set");
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public int xSize() {
            return maxX - minX + 1;
        }

        public int ySize() {
            return maxY - minY + 1;
        }

        public int zSize() {
            return maxZ - minZ + 1;
        }

        public AABB aabb() {
            return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        }
    }

    public record AbsolutePage(SlabRigHangingPaintingPlan.PagePlan plan,
                               BlockPos origin,
                               List<AbsoluteCase> cases,
                               List<BlockPos> reservedCells,
                               List<AbsoluteCell> clearOwnedCells,
                               List<BlockPos> entityAirCells,
                               Bounds bounds) {
        public AbsolutePage {
            Objects.requireNonNull(plan, "plan");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            cases = List.copyOf(cases);
            reservedCells = immutablePositions(reservedCells);
            clearOwnedCells = List.copyOf(clearOwnedCells);
            entityAirCells = immutablePositions(entityAirCells);
            Objects.requireNonNull(bounds, "bounds");
        }

        public Set<BlockPos> clearOwnedPositionSet() {
            LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
            clearOwnedCells.forEach(cell -> positions.add(cell.pos()));
            return Set.copyOf(positions);
        }
    }

    /** Converts the exact reviewed relative page into one complete absolute ownership plan. */
    public static AbsolutePage adapt(SlabRigHangingPaintingPlan.Universe universe,
                                     SlabRigHangingPaintingPlan.PagePlan page,
                                     BlockPos origin) {
        Objects.requireNonNull(universe, "universe");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(origin, "origin");
        SlabRigHangingPaintingPlan.validatePage(universe, page);
        if (page.routeIndex() != ROUTE_INDEX || page.topologyIndex() != TOPOLOGY_INDEX) {
            throw new IllegalArgumentException("direct fixture accepts only route6143/topology42");
        }
        int caseCount = page.cases().size();

        BlockPos immutableOrigin = origin.immutable();
        List<AbsoluteCase> absoluteCases = new ArrayList<>(caseCount);
        LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>();
        LinkedHashMap<BlockPos, AbsoluteCell> clearOwned = new LinkedHashMap<>();
        for (SlabRigHangingPaintingPlan.CasePlan planned : page.cases()) {
            List<AbsoluteCell> topology = planned.topologyCells().stream()
                    .map(cell -> adaptCell(immutableOrigin, cell)).toList();
            List<AbsoluteCell> backing = planned.backingCells().stream()
                    .map(cell -> adaptCell(immutableOrigin, cell)).toList();
            List<BlockPos> support = planned.supportCells().stream()
                    .map(immutableOrigin::offset).map(BlockPos::immutable).toList();
            List<BlockPos> caseReserved = planned.reservedCells().stream()
                    .map(immutableOrigin::offset).map(BlockPos::immutable).toList();
            for (BlockPos pos : caseReserved) {
                if (!reserved.add(pos)) {
                    throw new IllegalArgumentException("absolute planner reservations overlap at " + pos);
                }
            }
            for (SlabRigHangingPaintingPlan.CellPlan cell : planned.clearOwnedCells()) {
                AbsoluteCell adapted = adaptCell(immutableOrigin, cell);
                if (clearOwned.putIfAbsent(adapted.pos(), adapted) != null) {
                    throw new IllegalArgumentException("absolute clear ownership overlaps at " + adapted.pos());
                }
            }

            BlockPos clicked = immutableOrigin.offset(planned.clicked()).immutable();
            BlockPos anchor = immutableOrigin.offset(planned.anchor()).immutable();
            if (!anchor.equals(clicked.relative(planned.clickedFace()))) {
                throw new IllegalArgumentException("absolute painting anchor is not clicked+face");
            }
            Vec3 hit = Vec3.atCenterOf(clicked).add(
                    planned.clickedFace().getStepX() * 0.5,
                    planned.clickedFace().getStepY() * 0.5,
                    planned.clickedFace().getStepZ() * 0.5);
            absoluteCases.add(new AbsoluteCase(planned, topology, backing, support, caseReserved,
                    clicked, anchor, hit, Bounds.of(Set.copyOf(caseReserved)).aabb()));
        }

        LinkedHashSet<BlockPos> entityAir = new LinkedHashSet<>(reserved);
        entityAir.removeAll(clearOwned.keySet());
        if (clearOwned.size() != caseCount * 52 || entityAir.size() != caseCount * 16
                || reserved.size() != caseCount * 68) {
            throw new IllegalStateException("reviewed page ownership counts drifted: clear="
                    + clearOwned.size() + " entityAir=" + entityAir.size()
                    + " reserved=" + reserved.size());
        }
        Bounds bounds = Bounds.of(Set.copyOf(reserved));
        if (bounds.xSize() > MAX_X_SIZE || bounds.ySize() > MAX_Y_SIZE
                || bounds.zSize() > MAX_Z_SIZE) {
            throw new IllegalStateException("direct fixture escaped finite 40x20x40 envelope: " + bounds);
        }
        return new AbsolutePage(page, immutableOrigin, absoluteCases,
                sortedPositions(reserved), List.copyOf(clearOwned.values()),
                sortedPositions(entityAir), bounds);
    }

    private static AbsoluteCell adaptCell(BlockPos origin,
                                          SlabRigHangingPaintingPlan.CellPlan cell) {
        return new AbsoluteCell(cell, origin.offset(cell.relativePos()));
    }

    public static Item itemForRecipe(String recipe) {
        return switch (recipe) {
            case "minecraft:stone" -> Items.STONE;
            case "minecraft:stone_slab[type=bottom]" -> Items.STONE_SLAB;
            case "minecraft:smooth_stone_slab[type=bottom]" -> Items.SMOOTH_STONE_SLAB;
            default -> throw new IllegalArgumentException("unsupported direct fixture item recipe " + recipe);
        };
    }

    public static BlockState expectedState(String recipe) {
        return switch (recipe) {
            case "minecraft:stone" -> Blocks.STONE.defaultBlockState();
            case "minecraft:stone_slab[type=bottom]" -> Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case "minecraft:smooth_stone_slab[type=bottom]" ->
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            default -> throw new IllegalArgumentException("unsupported direct fixture state recipe " + recipe);
        };
    }

    private static List<BlockPos> sortedPositions(Iterable<BlockPos> positions) {
        List<BlockPos> sorted = new ArrayList<>();
        positions.forEach(pos -> sorted.add(pos.immutable()));
        sorted.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ()));
        return List.copyOf(sorted);
    }

    private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
        return positions.stream().map(BlockPos::immutable).toList();
    }
}
