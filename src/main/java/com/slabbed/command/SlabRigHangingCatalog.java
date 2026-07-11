package com.slabbed.command;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * RIG-3A's pure runtime hanging/attachment catalog and lazy case index.
 *
 * <p>This class is deliberately world-free. It derives membership from live registry objects and
 * state-bearing block classes, partitions every runtime item into exactly one included/excluded row,
 * expands state-specific placement routes, and binds them to RIG-2's exact 64 S/B topologies. It does
 * not place blocks, summon/remove entities, or claim that a proxy route is player-authored proof.
 * Mapping-dependent Java class names never enter the artifact identity.
 */
public final class SlabRigHangingCatalog {

    public static final String SCHEMA = "slabbed-rig-hanging-catalog-v1";
    public static final String EXECUTION_CONTRACT = "rig3a-runtime-class-state-partition-v1;"
            + "origins=useon-vs-placement-secondary-vs-neighbor-vs-bonemeal-vs-random-vs-generated-v2;"
            + "topologies=rig2-exact-64-v1;chain-lengths=0-as-direct,1,2,3,5,16;"
            + "chain-orientations=down,up,north,east,south,west;"
            + "chain-materials=all-runtime-chain-block-items;mixed=sorted-cycle-v1;"
            + "case-index=hashed-axis-order-and-page-geometry-v1;"
            + "case-id=semantic-no-page-ordinal-v1;player-proof=absent-catalog-only";
    public static final int PAGE_SIZE = 16;

    private static final String CASE_INDEX_CONTRACT_VERSION = "rig3-case-index-v1";
    private static final List<String> CASE_KIND_ORDER =
            List.of("DIRECT", "CHAIN_ONLY", "CHAIN_TERMINAL");
    private static final List<Integer> CHAIN_LENGTHS = List.of(1, 2, 3, 5, 16);
    private static final List<String> CHAIN_ORIENTATIONS =
            List.of("down", "up", "north", "east", "south", "west");
    private static final List<String> ORIENTATIONS = List.of("north", "east", "south", "west");
    private static final List<String> ROTATIONS_16 = BlockStateProperties.ROTATION_16
            .getPossibleValues().stream().sorted().map(value -> "rotation=" + value).toList();
    private static final List<Direction> HORIZONTAL =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final List<Direction> ALL_DIRECTIONS =
            List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.EAST,
                    Direction.SOUTH, Direction.WEST);
    private static final List<String> FLOOR_FRAMES =
            List.of("FLOOR_FULL", "FLOOR_BOTTOM_SLAB", "FLOOR_TOP_SLAB", "FLOOR_DOUBLE_SLAB");
    private static final List<String> CEILING_FRAMES =
            List.of("CEILING_FULL", "CEILING_BOTTOM_SLAB", "CEILING_TOP_SLAB", "CEILING_DOUBLE_SLAB");
    private static final List<String> WALL_FRAMES =
            List.of("WALL_FULL", "WALL_BOTTOM_SLAB", "WALL_TOP_SLAB", "WALL_DOUBLE_SLAB");
    private static final List<String> WALL_SIGN_CLICKED_SUPPORT_OPTIONS =
            List.of("FULL", "BOTTOM_SLAB", "TOP_SLAB", "DOUBLE_SLAB",
                    "WALL_HANGING_SIGN_SAME_FACING", "WALL_HANGING_SIGN_OPPOSITE_FACING");
    private static final List<String> WALL_SIGN_OTHER_SUPPORT_OPTIONS =
            List.of("AIR", "FULL", "BOTTOM_SLAB", "TOP_SLAB", "DOUBLE_SLAB",
                    "WALL_HANGING_SIGN_SAME_FACING", "WALL_HANGING_SIGN_OPPOSITE_FACING");

    private SlabRigHangingCatalog() {
    }

    /** One primary, non-overlapping runtime-item family. */
    public enum Family {
        ENTITY_HANGING,
        HANGING_SIGN,
        CHAIN,
        LANTERN,
        FACE_ATTACHED,
        BELL,
        STANDING_AND_WALL,
        TRAPDOOR,
        ROD,
        AMETHYST_CLUSTER,
        MULTIFACE,
        SPELEOTHEM,
        CEILING_GROWTH,
        WALL_ATTACHMENT,
        WALL_CARPET_STATE,
        GENERATED_HANGING_STATE,
        SHELF,
        LEASH_KNOT_ENTITY
    }

    public enum ActionKind {
        PLAYER_USEON_BLOCK,
        PLAYER_USEON_BLOCK_SEQUENCE,
        PLAYER_USEON_ENTITY_EFFECT,
        PLAYER_PLACEMENT_DERIVED_SECONDARY,
        PLAYER_DERIVED_NEIGHBOR_UPDATE,
        BONEMEAL_DERIVED_SECONDARY,
        RANDOM_TICK_DERIVED_SECONDARY,
        GENERATED_STATE_ONLY
    }

    public enum EffectKind {
        EXACT_BLOCK_CELLS,
        PLACEMENT_ATTEMPT,
        EXACT_ENTITY,
        PRIMARY_AND_DERIVED_BLOCK_CELLS,
        DERIVED_BLOCK_CELLS,
        GENERATED_BLOCK_CELLS
    }

    public enum CaseKind {
        DIRECT,
        CHAIN_ONLY,
        CHAIN_TERMINAL
    }

    public record CatalogItem(int index, String id, Family family, boolean blockItem,
                              List<String> tags) {
        public CatalogItem {
            tags = List.copyOf(tags);
        }
    }

    private static void addWallHangingSignRoutes(List<Route> routes, String subjectId,
                                                  Family family) {
        for (Direction facing : HORIZONTAL) {
            Direction lookDirection = facing.getOpposite();
            Direction clockwise = facing.getClockWise();
            Direction counterClockwise = facing.getCounterClockWise();
            for (Direction clickedFace : HORIZONTAL) {
                if (clickedFace.getAxis() == facing.getAxis()) {
                    continue;
                }
                Direction clickedSupportDirection = clickedFace.getOpposite();
                Direction otherSupportDirection = clickedSupportDirection == clockwise
                        ? counterClockwise : clockwise;
                for (String clickedSupport : WALL_SIGN_CLICKED_SUPPORT_OPTIONS) {
                    for (String otherSupport : WALL_SIGN_OTHER_SUPPORT_OPTIONS) {
                        boolean signSequence = clickedSupport.contains("HANGING_SIGN")
                                || otherSupport.contains("HANGING_SIGN");
                        ActionKind action = signSequence
                                ? ActionKind.PLAYER_USEON_BLOCK_SEQUENCE
                                : ActionKind.PLAYER_USEON_BLOCK;
                        String frame = "WALL_SIGN_CLICKED_" + clickedSupport
                                + "_OTHER_" + otherSupport;
                        String state = "wall_hanging_sign;clicked_face=" + faceName(clickedFace)
                                + ";look_direction=" + faceName(lookDirection)
                                + ";facing=" + faceName(facing)
                                + ";clicked_support_direction=" + faceName(clickedSupportDirection)
                                + ";other_support_direction=" + faceName(otherSupportDirection)
                                + ";clicked_support=" + clickedSupport.toLowerCase(Locale.ROOT)
                                + ";other_support=" + otherSupport.toLowerCase(Locale.ROOT)
                                + ";waterlogged=observe";
                        addRoute(routes, subjectId, family, action, EffectKind.EXACT_BLOCK_CELLS,
                                "wall_lateral", faceName(clickedFace), frame, state, false, true,
                                Set.of("paired_outcome=wall_hanging_sign_block",
                                        "support_rule=either_lateral_full_or_compatible_sign",
                                        "donor_sign_axis=must_match_candidate_facing_axis",
                                        signSequence ? "composition=wall_sign_under_or_beside_sign"
                                                : "composition=direct_lateral_support"));
                    }
                }
            }
        }
    }

    private static void addCeilingHangingSignRoutes(List<Route> routes, String subjectId,
                                                     Family family) {
        for (String frame : CEILING_FRAMES) {
            for (int inputRotation = 0; inputRotation < 16; inputRotation++) {
                for (boolean secondaryUse : List.of(false, true)) {
                    boolean topSlab = "CEILING_TOP_SLAB".equals(frame);
                    boolean vanillaAttached = topSlab || secondaryUse;
                    boolean attached = topSlab ? false : vanillaAttached;
                    Direction playerCardinal = playerDirectionForInputRotation(inputRotation);
                    int resultRotation = hangingSignResultRotation(
                            inputRotation, vanillaAttached, playerCardinal);
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.EXACT_BLOCK_CELLS, "ceiling", "down", frame,
                            "ceiling_hanging_sign;aim_rotation=" + inputRotation
                                    + ";input_rotation=" + inputRotation
                                    + ";context_yaw_segment=" + inputRotation
                                    + ";player_cardinal=" + faceName(playerCardinal)
                                    + ";rotation=" + resultRotation
                                    + ";secondary_use=" + secondaryUse
                                    + ";vanilla_pre_mixin_attached=" + vanillaAttached
                                    + ";attached=" + attached + ";waterlogged=observe",
                            true, true, Set.of("paired_outcome=ceiling_hanging_sign_block",
                                    "top_slab_attached_override=false"));
                }
            }
        }
    }

    private static void addHangingSignColumnRoutes(List<Route> routes, String subjectId,
                                                    Family family) {
        for (Direction donorFacing : HORIZONTAL) {
            Direction.Axis donorAxis = donorFacing.getAxis();
            for (boolean secondaryUse : List.of(false, true)) {
                for (int inputRotation = 0; inputRotation < 16; inputRotation++) {
                    addHangingSignEdgeRoute(routes, subjectId, family, "wall",
                            faceName(donorFacing), "not_applicable", donorAxis,
                            secondaryUse, inputRotation);
                }
            }
        }
        for (int donorRotation = 0; donorRotation < 16; donorRotation++) {
            Direction.Axis donorAxis = RotationSegment.convertToDirection(donorRotation)
                    .map(Direction::getAxis).orElse(null);
            for (boolean donorAttached : List.of(false, true)) {
                for (boolean secondaryUse : List.of(false, true)) {
                    for (int inputRotation = 0; inputRotation < 16; inputRotation++) {
                        addHangingSignEdgeRoute(routes, subjectId, family, "ceiling",
                                Integer.toString(donorRotation), Boolean.toString(donorAttached),
                                donorAxis, secondaryUse, inputRotation);
                    }
                }
            }
        }
        for (int length : List.of(2, 3, 5, 16)) {
            for (String rootFrame : CEILING_FRAMES) {
                for (boolean secondaryUse : List.of(false, true)) {
                    for (int inputRotation = 0; inputRotation < 16; inputRotation++) {
                        boolean topSlab = "CEILING_TOP_SLAB".equals(rootFrame);
                        boolean vanillaAttached = topSlab || secondaryUse;
                        boolean attached = topSlab ? false : vanillaAttached;
                        Direction playerCardinal = playerDirectionForInputRotation(inputRotation);
                        int rotation = hangingSignResultRotation(
                                inputRotation, vanillaAttached, playerCardinal);
                        StringBuilder attachedTrace = new StringBuilder(Boolean.toString(attached));
                        StringBuilder rotationTrace = new StringBuilder(Integer.toString(rotation));
                        for (int step = 2; step <= length; step++) {
                            Direction.Axis donorAxis = RotationSegment.convertToDirection(rotation)
                                    .map(Direction::getAxis).orElse(null);
                            attached = hangingSignAttachedForDonor(
                                    secondaryUse, donorAxis, playerCardinal);
                            rotation = hangingSignResultRotation(
                                    inputRotation, attached, playerCardinal);
                            attachedTrace.append(',').append(attached);
                            rotationTrace.append(',').append(rotation);
                        }
                        addRoute(routes, subjectId, family,
                                ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                                EffectKind.EXACT_BLOCK_CELLS, "ceiling_column", "down", rootFrame,
                                "sequence=hanging_sign_column;length=" + length
                                        + ";donor_kind=recursive_ceiling_sign"
                                        + ";aim_rotation=" + inputRotation
                                        + ";input_rotation=" + inputRotation
                                        + ";context_yaw_segment=" + inputRotation
                                        + ";player_cardinal=" + faceName(playerCardinal)
                                        + ";secondary_use=" + secondaryUse
                                        + ";attached=" + attached
                                        + ";rotation=" + rotation
                                        + ";attached_trace=" + attachedTrace
                                        + ";rotation_trace=" + rotationTrace
                                        + ";recursion=donor_equals_previous_result",
                                true, true, Set.of(
                                        "composition=sign_under_sign",
                                        "column_materialization=deterministic_recursive"));
                    }
                }
            }
        }
    }

    private static void addHangingSignEdgeRoute(List<Route> routes, String subjectId,
                                                Family family, String donorKind,
                                                String donorOrientation, String donorAttached,
                                                Direction.Axis donorAxis, boolean secondaryUse,
                                                int inputRotation) {
        Direction playerCardinal = playerDirectionForInputRotation(inputRotation);
        boolean attached = hangingSignAttachedForDonor(secondaryUse, donorAxis, playerCardinal);
        int resultRotation = hangingSignResultRotation(inputRotation, attached, playerCardinal);
        addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                EffectKind.EXACT_BLOCK_CELLS, "hanging_sign_edge", "down",
                "DONOR_" + donorKind.toUpperCase(Locale.ROOT),
                "sequence=hanging_sign_edge;donor_kind=" + donorKind
                        + ";donor_orientation=" + donorOrientation
                        + ";donor_attached=" + donorAttached
                        + ";donor_axis=" + (donorAxis == null ? "none"
                        : donorAxis.name().toLowerCase(Locale.ROOT))
                        + ";aim_rotation=" + inputRotation
                        + ";input_rotation=" + inputRotation
                        + ";context_yaw_segment=" + inputRotation
                        + ";player_cardinal=" + faceName(playerCardinal)
                        + ";secondary_use=" + secondaryUse
                        + ";attached=" + attached + ";rotation=" + resultRotation,
                false, true, Set.of(
                        "composition=sign_under_sign",
                        "edge_transition=exact_mapped_get_state_for_placement"));
    }

    private static boolean hangingSignAttachedForDonor(boolean secondaryUse,
                                                       Direction.Axis donorAxis,
                                                       Direction playerCardinal) {
        return secondaryUse || donorAxis == null || donorAxis != playerCardinal.getAxis();
    }

    private static int hangingSignResultRotation(int inputRotation, boolean attached,
                                                 Direction playerCardinal) {
        return attached ? Math.floorMod(inputRotation + 8, 16)
                : RotationSegment.convertToSegment(playerCardinal.getOpposite());
    }

    private static void addBellRoutes(List<Route> routes, String subjectId, Family family) {
        for (Direction heading : HORIZONTAL) {
            for (String frame : FLOOR_FRAMES) {
                addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, "floor", "up", frame,
                        "bell_attachment=floor;player_heading=" + faceName(heading)
                                + ";facing=" + faceName(heading), false, true,
                        Set.of("exception=NORMAL_FROZEN"));
            }
            for (String frame : CEILING_FRAMES) {
                addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, "ceiling", "down", frame,
                        "bell_attachment=ceiling;player_heading=" + faceName(heading)
                                + ";facing=" + faceName(heading), false, true,
                        Set.of("exception=NORMAL_FROZEN"));
            }
        }
        for (Direction clickedFace : HORIZONTAL) {
            Direction facing = clickedFace.getOpposite();
            List<String> sturdySides = List.of("WALL_FULL", "WALL_DOUBLE_SLAB");
            List<String> weakSides = List.of("WALL_BOTTOM_SLAB", "WALL_TOP_SLAB");
            List<String> allOppositeSides = List.of(
                    "AIR", "WALL_FULL", "WALL_BOTTOM_SLAB", "WALL_TOP_SLAB",
                    "WALL_DOUBLE_SLAB");
            for (String clickedOwner : sturdySides) {
                for (String oppositeOwner : sturdySides) {
                    addBellWallOutcomeRoute(routes, subjectId, family, clickedFace, facing,
                            clickedOwner, oppositeOwner, "wall_double", "double_wall");
                }
                for (String oppositeOwner : List.of(
                        "AIR", "WALL_BOTTOM_SLAB", "WALL_TOP_SLAB")) {
                    addBellWallOutcomeRoute(routes, subjectId, family, clickedFace, facing,
                            clickedOwner, oppositeOwner, "wall_single", "single_wall");
                }
            }
            for (String clickedOwner : weakSides) {
                for (String oppositeOwner : allOppositeSides) {
                    for (String belowOwner : FLOOR_FRAMES) {
                        addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                                EffectKind.EXACT_BLOCK_CELLS, "floor_fallback_from_wall_click",
                                faceName(clickedFace), "BELL_CLICKED_" + clickedOwner
                                        + "_OPPOSITE_" + oppositeOwner + "_BELOW_" + belowOwner,
                                bellFallbackState(clickedFace, facing, clickedOwner,
                                        oppositeOwner, belowOwner, "not_evaluated",
                                        "floor"), false, true,
                                Set.of("placement_branch=wall_click_floor_fallback"));
                    }
                    for (String aboveOwner : CEILING_FRAMES) {
                        addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                                EffectKind.EXACT_BLOCK_CELLS, "ceiling_fallback_from_wall_click",
                                faceName(clickedFace), "BELL_CLICKED_" + clickedOwner
                                        + "_OPPOSITE_" + oppositeOwner + "_BELOW_AIR_ABOVE_"
                                        + aboveOwner,
                                bellFallbackState(clickedFace, facing, clickedOwner,
                                        oppositeOwner, "AIR", aboveOwner,
                                        "ceiling"), false, true,
                                Set.of("placement_branch=wall_click_ceiling_fallback"));
                    }
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.PLACEMENT_ATTEMPT, "wall_refusal",
                            faceName(clickedFace), "BELL_CLICKED_" + clickedOwner
                                    + "_OPPOSITE_" + oppositeOwner + "_BELOW_AIR_ABOVE_AIR",
                            bellFallbackState(clickedFace, facing, clickedOwner,
                                    oppositeOwner, "AIR", "AIR", "refusal"),
                            false, true, Set.of("placement_branch=wall_click_refusal",
                                    "expected_outcome=vanilla_null_placement_state"));
                }
            }
        }
    }

    private static void addBellWallOutcomeRoute(List<Route> routes, String subjectId,
                                                Family family, Direction clickedFace,
                                                Direction facing, String clickedOwner,
                                                String oppositeOwner, String mount,
                                                String result) {
        addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                EffectKind.EXACT_BLOCK_CELLS, mount, faceName(clickedFace),
                "BELL_CLICKED_" + clickedOwner + "_OPPOSITE_" + oppositeOwner,
                "attempt=horizontal_bell_use;clicked_face=" + faceName(clickedFace)
                        + ";facing=" + faceName(facing)
                        + ";clicked_support=" + clickedOwner.toLowerCase(Locale.ROOT)
                        + ";opposite_support=" + oppositeOwner.toLowerCase(Locale.ROOT)
                        + ";double_wall_predicate=both_opposed_axis_faces_sturdy"
                        + ";single_wall_predicate=clicked_side_survives"
                        + ";result=" + result,
                false, true, Set.of("exception=NORMAL_FROZEN"));
    }

    private static String bellFallbackState(Direction clickedFace, Direction facing,
                                            String clickedOwner, String oppositeOwner,
                                            String belowOwner, String aboveOwner,
                                            String result) {
        return "attempt=horizontal_bell_use;clicked_face=" + faceName(clickedFace)
                + ";facing=" + faceName(facing)
                + ";clicked_support=" + clickedOwner.toLowerCase(Locale.ROOT)
                + ";opposite_support=" + oppositeOwner.toLowerCase(Locale.ROOT)
                + ";below_support=" + belowOwner.toLowerCase(Locale.ROOT)
                + ";above_support=" + aboveOwner.toLowerCase(Locale.ROOT)
                + ";double_wall_predicate=both_opposed_axis_faces_sturdy"
                + ";single_wall_predicate=clicked_side_survives"
                + ";fallback_order=floor_if_below_sturdy_else_ceiling_if_above_sturdy_else_refusal"
                + ";result=" + result;
    }

    private static void addCocoaRoutes(List<Route> routes, String subjectId, Family family) {
        for (Direction supportDirection : HORIZONTAL) {
            Direction clickedFace = supportDirection.getOpposite();
            addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                    EffectKind.EXACT_BLOCK_CELLS, "wall_tagged_support", faceName(clickedFace),
                    "COCOA_TAGGED_SUPPORT", "facing=" + faceName(supportDirection)
                            + ";clicked_face=" + faceName(clickedFace) + ";age=0",
                    false, true, Set.of("required_tag=minecraft:supports_cocoa"));
            for (int age = 1; age <= 2; age++) {
                addRoute(routes, subjectId, family, ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "wall_tagged_support", "none",
                        "COCOA_TAGGED_SUPPORT", "facing=" + faceName(supportDirection)
                                + ";age=" + age, false, true,
                        Set.of("required_tag=minecraft:supports_cocoa", "transition=advance_age"));
                addRoute(routes, subjectId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "wall_tagged_support", "none",
                        "COCOA_TAGGED_SUPPORT", "facing=" + faceName(supportDirection)
                                + ";age=" + age, false, true,
                        Set.of("required_tag=minecraft:supports_cocoa", "transition=advance_age"));
            }
        }
    }

    private static void addShelfRoutes(List<Route> routes, String subjectId, Family family) {
        for (Direction clickedFace : ALL_DIRECTIONS) {
            String frame = "ORIENTATION_ONLY_NO_REQUIRED_SUPPORT";
            for (Direction playerHeading : HORIZONTAL) {
                    Direction facing = playerHeading.getOpposite();
                    String base = "clicked_face=" + faceName(clickedFace)
                            + ";player_heading=" + faceName(playerHeading)
                            + ";facing=" + faceName(facing) + ";waterlogged=observe";
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=false;side_chain=unconnected;sequence=single_direct",
                            false, true, Set.of("survival=orientation_only"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=unconnected;sequence=single_then_power",
                            false, true, Set.of("survival=orientation_only"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=right;sequence=pair;neighbor=clockwise",
                            false, true, Set.of("max_side_chain_length=3",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=left;sequence=pair;neighbor=counterclockwise",
                            false, true, Set.of("max_side_chain_length=3",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=left;sequence=triple;role=clockwise_end",
                            false, true, Set.of("max_side_chain_length=3",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=center;sequence=triple;role=center",
                            false, true, Set.of("max_side_chain_length=3",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=right;sequence=triple;role=counterclockwise_end",
                            false, true, Set.of("max_side_chain_length=3",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=unconnected;sequence=fourth_refused_by_cap;attempt_side=clockwise",
                            false, true, Set.of("max_side_chain_length=3",
                                    "expected_outcome=vanilla_refusal_to_join_existing_three",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=unconnected;sequence=fourth_refused_by_cap;attempt_side=counterclockwise",
                            false, true, Set.of("max_side_chain_length=3",
                                    "expected_outcome=vanilla_refusal_to_join_existing_three",
                                    "neighbor_material=any_minecraft_wooden_shelves"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=unconnected;sequence=unpowered_neighbor_no_connect",
                            false, true, Set.of("negative_neighbor=unpowered"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=true;side_chain=unconnected;sequence=different_facing_neighbor_no_connect",
                            false, true, Set.of("negative_neighbor=different_facing"));
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.EXACT_BLOCK_CELLS, "orientation_only",
                            faceName(clickedFace), frame,
                            base + ";powered=false;side_chain=unconnected;sequence=power_down_disconnects_neighbor",
                            false, true, Set.of("transition=power_down_disconnect"));
                }
        }
    }

    public record ExcludedItem(int index, String id, String reason, String route) {
    }

    /** One executable or explicitly generated state route. */
    public record Route(int index, String id, String subjectId, Family family,
                        ActionKind actionKind, EffectKind effectKind, String actionOrigin,
                        String mount, String clickedFace, String supportFrame,
                        String stateContract, boolean chainTerminal,
                        boolean delayedObservation, List<String> tags) {
        public Route {
            tags = List.copyOf(tags);
        }
    }

    public record ChainMaterial(int index, String itemId) {
    }

    public record ChainPattern(int index, String id, String orientation, String axis,
                               int length, boolean mixed,
                               List<String> materialIds) {
        public ChainPattern {
            materialIds = List.copyOf(materialIds);
        }
    }

    public record Snapshot(String schema, String catalogHash, String topologyCatalogHash,
                           int runtimeItemCount, List<CatalogItem> items,
                           List<ExcludedItem> excludedItems, List<Route> routes,
                           List<Route> chainTerminalRoutes, List<ChainMaterial> chainMaterials,
                           List<Integer> chainLengths, List<ChainPattern> chainPatterns,
                           List<ChainPattern> terminalChainPatterns,
                           List<SlabRigCaseCatalog.Topology> topologies,
                           long totalCases, int pageCount) {
        public Snapshot {
            items = List.copyOf(items);
            excludedItems = List.copyOf(excludedItems);
            routes = List.copyOf(routes);
            chainTerminalRoutes = List.copyOf(chainTerminalRoutes);
            chainMaterials = List.copyOf(chainMaterials);
            chainLengths = List.copyOf(chainLengths);
            chainPatterns = List.copyOf(chainPatterns);
            terminalChainPatterns = List.copyOf(terminalChainPatterns);
            topologies = List.copyOf(topologies);
        }
    }

    public record CaseDefinition(long index, String id, CaseKind kind, Route route,
                                 ChainPattern chainPattern, String chainSupportFrame,
                                 SlabRigCaseCatalog.Topology topology) {
    }

    public record CasePage(int page, int pageCount, long firstCaseIndex, long lastCaseIndex,
                           List<CaseDefinition> cases) {
        public CasePage {
            cases = List.copyOf(cases);
        }
    }

    /** Rebuilds the catalog from the live registries; no cached/static item list is authoritative. */
    public static Snapshot snapshot() {
        List<Item> runtimeItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            runtimeItems.add(item);
        }
        runtimeItems.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));

        List<CatalogItem> included = new ArrayList<>();
        List<ExcludedItem> excluded = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        for (Item item : runtimeItems) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            Family family = primaryFamily(item);
            if (family == null) {
                excluded.add(new ExcludedItem(excluded.size(), itemId, exclusionReason(item),
                        "not_in_rig3_hanging_attachment_union"));
                continue;
            }
            boolean blockItem = item instanceof BlockItem;
            included.add(new CatalogItem(included.size(), itemId, family, blockItem,
                    itemTags(itemId, family, blockItem)));
            addItemRoutes(routes, item, itemId, family);
        }
        addDerivedBlockRoutes(routes);
        routes = normalizeRoutes(routes);

        List<Route> chainTerminals = routes.stream().filter(Route::chainTerminal).toList();
        List<ChainMaterial> chainMaterials = included.stream()
                .filter(item -> item.family() == Family.CHAIN)
                .map(item -> item.id())
                .sorted()
                .map(id -> new ChainMaterial(0, id))
                .toList();
        List<ChainMaterial> indexedMaterials = new ArrayList<>();
        for (int i = 0; i < chainMaterials.size(); i++) {
            indexedMaterials.add(new ChainMaterial(i, chainMaterials.get(i).itemId()));
        }
        List<ChainPattern> patterns = chainPatterns(indexedMaterials);
        List<ChainPattern> terminalPatterns = patterns.stream()
                .filter(pattern -> "down".equals(pattern.orientation())).toList();

        SlabRigCaseCatalog.Snapshot topologySnapshot = SlabRigCaseCatalog.snapshot();
        List<SlabRigCaseCatalog.Topology> topologies = topologySnapshot.topologies();
        long directCases = Math.multiplyExact((long) routes.size(), (long) topologies.size());
        long chainOnlyCases = Math.multiplyExact(
                Math.multiplyExact((long) patterns.size(), (long) supportFramesPerChainPattern()),
                (long) topologies.size());
        long terminalCases = Math.multiplyExact(
                Math.multiplyExact(Math.multiplyExact((long) chainTerminals.size(),
                        (long) terminalPatterns.size()), (long) terminalSupportFrames().size()),
                (long) topologies.size());
        long totalCases = Math.addExact(directCases, Math.addExact(chainOnlyCases, terminalCases));
        long pages = (totalCases + PAGE_SIZE - 1L) / PAGE_SIZE;
        if (pages > Integer.MAX_VALUE) {
            throw new IllegalStateException("RIG-3 page count exceeds Brigadier integer range: " + pages);
        }

        String body = canonicalBody(topologySnapshot.catalogHash(), runtimeItems.size(), included,
                excluded, routes, chainTerminals, indexedMaterials, patterns, terminalPatterns,
                topologies, totalCases, (int) pages);
        return new Snapshot(SCHEMA, sha256(body), topologySnapshot.catalogHash(), runtimeItems.size(),
                included, excluded, routes, chainTerminals, indexedMaterials, CHAIN_LENGTHS, patterns,
                terminalPatterns, topologies, totalCases, (int) pages);
    }

    /** Content-addressed, mapping-stable catalog text for the later command/artifact layer. */
    public static String catalogTsv(Snapshot snapshot) {
        String body = canonicalBody(snapshot.topologyCatalogHash(), snapshot.runtimeItemCount(),
                snapshot.items(), snapshot.excludedItems(), snapshot.routes(),
                snapshot.chainTerminalRoutes(), snapshot.chainMaterials(), snapshot.chainPatterns(),
                snapshot.terminalChainPatterns(), snapshot.topologies(), snapshot.totalCases(),
                snapshot.pageCount());
        int firstLineEnd = body.indexOf('\n') + 1;
        return body.substring(0, firstLineEnd)
                + "catalog_hash\t" + snapshot.catalogHash() + "\n"
                + body.substring(firstLineEnd);
    }

    public static List<String> ceilingSupportFrames() {
        return CEILING_FRAMES;
    }

    public static List<String> chainSupportFrames(String orientation) {
        return switch (orientation) {
            case "down" -> CEILING_FRAMES;
            case "up" -> FLOOR_FRAMES;
            case "north", "east", "south", "west" -> WALL_FRAMES;
            default -> throw new IllegalArgumentException("unknown chain orientation " + orientation);
        };
    }

    /** Lazy semantic case lookup; the complete matrix is never materialized in memory. */
    public static CaseDefinition caseAt(Snapshot snapshot, long index) {
        if (index < 0L || index >= snapshot.totalCases()) {
            throw new IllegalArgumentException("RIG-3 case index out of range: " + index);
        }
        int topologyCount = snapshot.topologies().size();
        long directCount = Math.multiplyExact((long) snapshot.routes().size(), topologyCount);
        if (index < directCount) {
            int routeIndex = Math.toIntExact(index / topologyCount);
            int topologyIndex = Math.toIntExact(index % topologyCount);
            Route route = snapshot.routes().get(routeIndex);
            SlabRigCaseCatalog.Topology topology = snapshot.topologies().get(topologyIndex);
            return caseDefinition(index, CaseKind.DIRECT, route, null, "none", topology);
        }

        long remainder = index - directCount;
        long chainOnlyCount = Math.multiplyExact(
                Math.multiplyExact((long) snapshot.chainPatterns().size(), supportFramesPerChainPattern()),
                topologyCount);
        if (remainder < chainOnlyCount) {
            long patternStride = Math.multiplyExact((long) supportFramesPerChainPattern(), topologyCount);
            int patternIndex = Math.toIntExact(remainder / patternStride);
            long withinPattern = remainder % patternStride;
            int frameIndex = Math.toIntExact(withinPattern / topologyCount);
            int topologyIndex = Math.toIntExact(withinPattern % topologyCount);
            ChainPattern pattern = snapshot.chainPatterns().get(patternIndex);
            return caseDefinition(index, CaseKind.CHAIN_ONLY, null,
                    pattern, chainSupportFrames(pattern.orientation()).get(frameIndex),
                    snapshot.topologies().get(topologyIndex));
        }

        remainder -= chainOnlyCount;
        List<String> terminalFrames = terminalSupportFrames();
        long patternAndFrameCount = Math.multiplyExact(
                (long) snapshot.terminalChainPatterns().size(), (long) terminalFrames.size());
        long terminalStride = Math.multiplyExact(patternAndFrameCount, topologyCount);
        int terminalIndex = Math.toIntExact(remainder / terminalStride);
        long withinTerminal = remainder % terminalStride;
        int patternAndFrameIndex = Math.toIntExact(withinTerminal / topologyCount);
        int patternIndex = patternAndFrameIndex / terminalFrames.size();
        int frameIndex = patternAndFrameIndex % terminalFrames.size();
        int topologyIndex = Math.toIntExact(withinTerminal % topologyCount);
        Route terminal = snapshot.chainTerminalRoutes().get(terminalIndex);
        return caseDefinition(index, CaseKind.CHAIN_TERMINAL, terminal,
                snapshot.terminalChainPatterns().get(patternIndex), terminalFrames.get(frameIndex),
                snapshot.topologies().get(topologyIndex));
    }

    public static CasePage page(Snapshot snapshot, int page) {
        if (page < 1 || page > snapshot.pageCount()) {
            throw new IllegalArgumentException("RIG-3 page must be 1.." + snapshot.pageCount()
                    + ", got " + page);
        }
        long first = Math.multiplyExact((long) page - 1L, PAGE_SIZE);
        long lastExclusive = Math.min(snapshot.totalCases(), first + PAGE_SIZE);
        List<CaseDefinition> cases = new ArrayList<>((int) (lastExclusive - first));
        for (long index = first; index < lastExclusive; index++) {
            cases.add(caseAt(snapshot, index));
        }
        return new CasePage(page, snapshot.pageCount(), first, lastExclusive - 1L, cases);
    }

    private static CaseDefinition caseDefinition(long index, CaseKind kind, Route route,
                                                 ChainPattern pattern, String chainSupportFrame,
                                                 SlabRigCaseCatalog.Topology topology) {
        String id = semanticCaseId(kind, route, pattern, chainSupportFrame, topology);
        return new CaseDefinition(index, id, kind, route, pattern, chainSupportFrame, topology);
    }

    /** Pure semantic ID seam: page and ordinal are deliberately absent. */
    public static String semanticCaseId(CaseKind kind, Route route, ChainPattern pattern,
                                        String chainSupportFrame,
                                        SlabRigCaseCatalog.Topology topology) {
        String routeId = route == null ? "none" : route.id();
        String patternId = pattern == null ? "none" : pattern.id();
        String semantic = SCHEMA + '\0' + kind.name() + '\0' + routeId + '\0' + patternId
                + '\0' + chainSupportFrame + '\0' + topology.id();
        return "case-v1:sha256:" + sha256(semantic);
    }

    private static Family primaryFamily(Item item) {
        if (item instanceof HangingEntityItem) {
            return Family.ENTITY_HANGING;
        }
        if (item instanceof LeadItem) {
            return Family.LEASH_KNOT_ENTITY;
        }
        if (!(item instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        if (item instanceof HangingSignItem) {
            return Family.HANGING_SIGN;
        }
        if (block instanceof ChainBlock) {
            return Family.CHAIN;
        }
        if (block instanceof LanternBlock) {
            return Family.LANTERN;
        }
        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            return Family.FACE_ATTACHED;
        }
        if (block instanceof BellBlock) {
            return Family.BELL;
        }
        if (item instanceof StandingAndWallBlockItem) {
            return Family.STANDING_AND_WALL;
        }
        if (block instanceof TrapDoorBlock) {
            return Family.TRAPDOOR;
        }
        if (block instanceof RodBlock) {
            return Family.ROD;
        }
        if (block instanceof AmethystClusterBlock) {
            return Family.AMETHYST_CLUSTER;
        }
        if (block instanceof MultifaceBlock) {
            return Family.MULTIFACE;
        }
        if (block instanceof SpeleothemBlock) {
            return Family.SPELEOTHEM;
        }
        if (block instanceof HangingRootsBlock || block instanceof SporeBlossomBlock
                || block instanceof HangingMossBlock || block instanceof WeepingVinesBlock
                || block instanceof CaveVinesBlock) {
            return Family.CEILING_GROWTH;
        }
        if (block instanceof VineBlock || block instanceof CocoaBlock || block instanceof LadderBlock
                || block instanceof TripWireHookBlock) {
            return Family.WALL_ATTACHMENT;
        }
        if (block instanceof MossyCarpetBlock) {
            return Family.WALL_CARPET_STATE;
        }
        if (block instanceof MangrovePropaguleBlock) {
            return Family.GENERATED_HANGING_STATE;
        }
        if (block instanceof ShelfBlock) {
            return Family.SHELF;
        }
        return null;
    }

    private static String exclusionReason(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return "non_block_item_without_hanging_or_attachment_route";
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)
                || state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.hasProperty(BlockStateProperties.AXIS)) {
            return "orientation_property_without_rig3_support_attachment_semantics";
        }
        return "no_hanging_or_support_attachment_route_in_rig3";
    }

    private static List<String> itemTags(String id, Family family, boolean blockItem) {
        TreeSet<String> tags = new TreeSet<>();
        tags.add(blockItem ? "universe:block_item" : "universe:non_block_attachment_item");
        int colon = id.indexOf(':');
        tags.add("namespace:" + (colon < 0 ? "unknown" : id.substring(0, colon)));
        tags.add("family:" + familyTag(family));
        tags.add("classification:runtime_class_and_state");
        return List.copyOf(tags);
    }

    private static void addItemRoutes(List<Route> routes, Item item, String itemId, Family family) {
        Block block = item instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        switch (family) {
            case ENTITY_HANGING -> {
                if (item instanceof ItemFrameItem) {
                    addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_ENTITY_EFFECT,
                            EffectKind.EXACT_ENTITY, List.of("none"), "entity_facing=up", false,
                            true, Set.of("entity_effect=uuid_type_aabb_facing_attachment_nbt"));
                    addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_ENTITY_EFFECT,
                            EffectKind.EXACT_ENTITY, List.of("none"), "entity_facing=down", true,
                            true, Set.of("entity_effect=uuid_type_aabb_facing_attachment_nbt"));
                }
                Set<String> extra = item instanceof ItemFrameItem
                        ? Set.of("entity_effect=uuid_type_aabb_facing_attachment_nbt")
                        : Set.of("entity_effect=uuid_type_aabb_facing_attachment_nbt",
                        "dynamic_subcatalog=minecraft:painting_variant#placeable",
                        "dynamic_axes=variant_id,width,height,item_stack_component,backing_plane",
                        "executor_obligation=exact_server_registry_snapshot");
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_ENTITY_EFFECT,
                        EffectKind.EXACT_ENTITY, "entity_facing=clicked_face", false, true, extra);
            }
            case HANGING_SIGN -> {
                addCeilingHangingSignRoutes(routes, itemId, family);
                addWallHangingSignRoutes(routes, itemId, family);
                addHangingSignColumnRoutes(routes, itemId, family);
            }
            case CHAIN -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "axis=y;waterlogged=false;post=waterlog", false, true, Set.of("exception=CHAIN_GRID"));
                addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "axis=y;waterlogged=false;post=waterlog", false, true,
                        Set.of("exception=CHAIN_GRID_OR_CHAIN_BRIDGE_GRID_0"));
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, "axis=from_clicked_face;waterlogged=false",
                        false, true, Set.of("exception=NON_CEILING_CHAIN_STATE"));
            }
            case LANTERN -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "hanging=false;waterlogged=false;post=waterlog", false, true,
                        Set.of("exception=NORMAL_FROZEN"));
                addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "hanging=true;waterlogged=false;post=waterlog", true, true,
                        Set.of("exception=NORMAL_FROZEN_OR_CHAIN_BRIDGE_GRID_0"));
            }
            case FACE_ATTACHED -> {
                String survival = block instanceof GrindstoneBlock
                        ? "orientation_only" : "support_required";
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, ORIENTATIONS,
                        "attach_face=floor;survival=" + survival, false, true, Set.of());
                addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, ORIENTATIONS,
                        "attach_face=ceiling;survival=" + survival, false, true, Set.of());
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, "attach_face=wall;survival=" + survival,
                        false, true, Set.of());
            }
            case BELL -> {
                addBellRoutes(routes, itemId, family);
            }
            case STANDING_AND_WALL -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, standingStateVariants(block),
                        "standing_variant", false, true,
                        Set.of("paired_outcome=standing_block"));
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS,
                        "wall_variant;result_state=runtime_get_state_for_placement",
                        false, true, Set.of("paired_outcome=wall_block"));
            }
            case TRAPDOOR -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, ORIENTATIONS,
                        "half=bottom;open=false;post=toggle_open_and_power_and_waterlog",
                        false, true, Set.of());
                addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, ORIENTATIONS,
                        "half=top;open=false;post=toggle_open_and_power_and_waterlog",
                        false, true, Set.of());
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS,
                        "hit_band=low;half=bottom;open=false;post=toggle_open_and_power_and_waterlog",
                        false, true, Set.of());
                addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS,
                        "hit_band=high;half=top;open=false;post=toggle_open_and_power_and_waterlog",
                        false, true, Set.of());
            }
            case ROD -> {
                addAllFaceRoutes(routes, itemId, family,
                        "mode=independent;result_facing=clicked_face;waterlogged_if_supported",
                        false, true, Set.of());
                if (block instanceof EndRodBlock) {
                    addAllFaceRoutes(routes, itemId, family,
                            "mode=aligned_extension;existing_rod_facing=clicked_face;"
                                    + "result_facing=opposite_clicked_face",
                            false, true, Set.of("extension_rule=end_rod_flip"));
                }
            }
            case AMETHYST_CLUSTER -> addAllFaceRoutes(routes, itemId, family,
                    "facing=clicked_face;waterlogged_if_supported", false, true, Set.of());
            case MULTIFACE -> addMultifaceRoutes(routes, itemId, family, block);
            case SPELEOTHEM -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "tip_direction=up;thickness=observe", false, true,
                        Set.of("exception=NORMAL_FROZEN"));
                addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "tip_direction=down;thickness=observe", true, true,
                        Set.of("exception=NORMAL_FROZEN_OR_SPELEOTHEM_TOP_ROOT_GRID_0"));
                addCeilingSequences(routes, itemId, family, "downward_speleothem_column",
                        List.of("none"), true,
                        Set.of("state_axis=direction_and_thickness"));
            }
            case CEILING_GROWTH -> {
                String directState;
                if (block instanceof HangingMossBlock) {
                    directState = "ceiling_growth_tip;tip=true";
                } else if (block instanceof CaveVinesBlock) {
                    directState = "ceiling_growth_head;age=0;berries=false";
                } else if (block instanceof WeepingVinesBlock) {
                    directState = "ceiling_growth_head;age=0";
                } else {
                    directState = "ceiling_growth_tip;state=observe";
                }
                if (block instanceof CaveVinesBlock || block instanceof WeepingVinesBlock) {
                    addGrowingVineDirectRoutes(routes, itemId, family,
                            block instanceof CaveVinesBlock);
                } else {
                    addCeilingRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                            directState, true, true,
                            Set.of("exception=NORMAL_FROZEN"));
                }
                if (block instanceof HangingMossBlock) {
                    addHangingMossRoutes(routes, itemId, family);
                } else if (block instanceof CaveVinesBlock) {
                    addGrowingVinePlayerSequences(routes, itemId, family, true);
                    addCaveVinePlayerEdgeRoutes(routes, itemId, family);
                    addCaveVineHeadDerivedRoutes(routes, itemId, family);
                } else if (block instanceof WeepingVinesBlock) {
                    addGrowingVinePlayerSequences(routes, itemId, family, false);
                    addWeepingVineHeadDerivedRoutes(routes, itemId, family);
                }
            }
            case WALL_ATTACHMENT -> {
                if (block instanceof VineBlock) {
                    addVineFaceSubsets(routes, itemId, family);
                } else if (block instanceof CocoaBlock) {
                    addCocoaRoutes(routes, itemId, family);
                } else {
                    addWallRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.EXACT_BLOCK_CELLS,
                            "wall_attachment;result_facing=clicked_face;look_direction=opposite_clicked_face",
                            false, true, Set.of());
                }
            }
            case WALL_CARPET_STATE -> {
                addMossyCarpetRoutes(routes, itemId, family);
            }
            case GENERATED_HANGING_STATE -> {
                addFloorRoutes(routes, itemId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"),
                        "hanging=false;age=4;stage=0;waterlogged=observe", false, true,
                        Set.of("origin=item_placement_not_hanging"));
                for (int age = 0; age <= 4; age++) {
                    addRoute(routes, itemId, family, ActionKind.GENERATED_STATE_ONLY,
                            EffectKind.GENERATED_BLOCK_CELLS, "ceiling", "down",
                            "GENERATED_MANGROVE_LEAVES", "hanging=true;age=" + age,
                            false, true, Set.of("origin=worldgen_or_growth_not_item_use"));
                }
                addRoute(routes, itemId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "ceiling_generated", "down",
                        "MANGROVE_LEAVES_TAG", "hanging=true;age=0;source=mangrove_leaves",
                        false, true, Set.of("transition=create_hanging_propagule"));
                for (int age = 1; age <= 4; age++) {
                    addRoute(routes, itemId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                            EffectKind.DERIVED_BLOCK_CELLS, "ceiling_generated", "none",
                            "MANGROVE_LEAVES_TAG", "hanging=true;age=" + age,
                            false, true, Set.of("transition=advance_hanging_age"));
                    addRoute(routes, itemId, family, ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                            EffectKind.DERIVED_BLOCK_CELLS, "ceiling_generated", "none",
                            "MANGROVE_LEAVES_TAG", "hanging=true;age=" + age,
                            false, true, Set.of("transition=advance_hanging_age"));
                }
            }
            case SHELF -> addShelfRoutes(routes, itemId, family);
            case LEASH_KNOT_ENTITY -> addRoute(routes, itemId, family,
                    ActionKind.PLAYER_USEON_ENTITY_EFFECT, EffectKind.EXACT_ENTITY,
                    "block_attached_entity", "ignored_by_lead_item", "LEASH_FENCE_OWNER",
                    "owner=fence;requires_leashed_mob=true;result_entity=leash_knot",
                    false, true, Set.of("entity_effect=leash_knot_uuid_type_aabb_attachment_nbt"));
        }
    }

    private static void addHangingMossRoutes(List<Route> routes, String subjectId,
                                             Family family) {
        for (int length : List.of(2, 3, 5, 16)) {
            for (String rootFrame : CEILING_FRAMES) {
                addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                        EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS, "ceiling", "down",
                        rootFrame, "sequence=downward_growth_column;length=" + length
                                + ";tip_pattern=body_false_x" + (length - 1)
                                + ",terminal_true;tip_transition=old_true_to_false,new_true",
                        true, true, Set.of("derived_segments=explicit",
                                "growth_mechanism=player_placement_neighbor_update",
                                "boundary_length_sample=true"));
            }
        }
        for (int beforeLength : List.of(1, 2, 3, 5, 16)) {
            for (int targetedSegment = 0; targetedSegment < beforeLength; targetedSegment++) {
                for (String rootFrame : CEILING_FRAMES) {
                    addRoute(routes, subjectId, family,
                            ActionKind.BONEMEAL_DERIVED_SECONDARY,
                            EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                            "ceiling_growth", "none", rootFrame,
                            "before_length=" + beforeLength + ";targeted_segment="
                                    + targetedSegment + ";after_length=" + (beforeLength + 1)
                                    + ";tip_scan=target_to_bottom"
                                    + ";tip_transition=old_true_to_false,new_true"
                                    + ";growth_cells=exactly_one",
                            false, true, Set.of(
                                    "growth_mechanism=hanging_moss_bonemeal_any_segment",
                                    "boundary_length_sample=true"));
                }
            }
        }
        for (int length : List.of(1, 2, 3, 5, 16)) {
            addRoute(routes, subjectId, family, ActionKind.GENERATED_STATE_ONLY,
                    EffectKind.GENERATED_BLOCK_CELLS, "ceiling_generated", "none",
                    "GENERATED_PALE_OAK_CANOPY", "length=" + length
                            + ";tip_pattern=body_false_x" + Math.max(0, length - 1)
                            + ",terminal_true",
                    false, true, Set.of("origin=worldgen_or_structure_state",
                            "boundary_length_sample=true",
                            "unbounded_until_obstruction_or_build_height=true"));
        }
    }

    private static void addGrowingVineDirectRoutes(List<Route> routes, String subjectId,
                                                    Family family, boolean berries) {
        for (String rootFrame : CEILING_FRAMES) {
            for (int age = 0; age < 25; age++) {
                String state = "ceiling_growth_head;age=" + age;
                if (berries) {
                    state += ";berries=false";
                }
                addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, "ceiling", "down", rootFrame,
                        state, true, true, Set.of("exception=NORMAL_FROZEN",
                                "placement_age=random_0_24"));
            }
        }
    }

    private static void addGrowingVinePlayerSequences(List<Route> routes, String subjectId,
                                                       Family family, boolean berries) {
        for (int length : List.of(2, 3, 5, 16)) {
            for (String rootFrame : CEILING_FRAMES) {
                for (int terminalAge = 0; terminalAge < 25; terminalAge++) {
                    String state = "sequence=downward_growth_column;length=" + length
                            + ";body_count=" + (length - 1)
                            + ";terminal_head_age=" + terminalAge;
                    if (berries) {
                        state += ";body_berries=false;head_berries=false";
                    }
                    addRoute(routes, subjectId, family,
                            ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                            "ceiling", "down", rootFrame, state, true, true,
                            Set.of("growth_mechanism=player_placement_head_to_body_conversion",
                                    "placement_age=random_0_24"));
                }
            }
        }
    }

    private static void addCaveVineHeadDerivedRoutes(List<Route> routes, String subjectId,
                                                      Family family) {
        for (int age = 0; age <= 25; age++) {
            for (boolean berries : List.of(false, true)) {
                addRoute(routes, subjectId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "ceiling_growth", "none",
                        "CAVE_VINE_HEAD", "target=head;age=" + age + ";berries="
                                + (berries ? "true_noop" : "false_to_true")
                                + ";length_delta=0;age_delta=0",
                        false, true, Set.of(
                                berries ? "bonemeal_target=invalid_already_berried"
                                        : "growth_mechanism=cave_vines_bonemeal_sets_berries"));
            }
        }
        for (int age = 0; age < 25; age++) {
            for (boolean priorBerries : List.of(false, true)) {
                for (boolean newBerries : List.of(false, true)) {
                    for (String rootFrame : CEILING_FRAMES) {
                        addRoute(routes, subjectId, family,
                                ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                                EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                                "ceiling_growth", "none", rootFrame,
                                "old_head_age=" + age + ";old_head_berries=" + priorBerries
                                        + ";old_head_becomes_body=true;new_head_age=" + (age + 1)
                                        + ";new_head_berries=" + newBerries + ";length_delta=1",
                                false, true, Set.of(
                                        "growth_mechanism=random_tick_head_extension",
                                        "new_berries_probability=0.11"));
                    }
                }
            }
        }
        for (boolean berries : List.of(false, true)) {
            for (String rootFrame : CEILING_FRAMES) {
                addRoute(routes, subjectId, family,
                        ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "ceiling_growth", "none", rootFrame,
                        "old_head_age=25;old_head_berries=" + berries
                                + ";result=no_growth;length_delta=0",
                        false, true, Set.of(
                                "growth_mechanism=random_tick_max_age_noop"));
            }
        }
        for (String feature : List.of("cave_vine", "cave_vine_in_moss")) {
            int maximumLength = feature.equals("cave_vine") ? 20 : 8;
            for (int length = 1; length <= maximumLength; length++) {
                for (int headAge = 23; headAge <= 25; headAge++) {
                    for (boolean terminalBerries : List.of(false, true)) {
                        addRoute(routes, subjectId, family, ActionKind.GENERATED_STATE_ONLY,
                                EffectKind.GENERATED_BLOCK_CELLS,
                                "ceiling_generated", "none",
                                "GENERATED_CAVE_VINE_COLUMN",
                                "feature=" + feature + ";length=" + length
                                        + ";head_age=" + headAge
                                        + ";terminal_head_berries=" + terminalBerries
                                        + ";body_berries=independent_per_cell_probability_0.2",
                                false, true, Set.of(
                                        "origin=worldgen_configured_feature",
                                        "head_berries_probability=0.2",
                                        "body_berries_realizations=stratified_not_exhaustive"));
                    }
                }
            }
        }
    }

    private static void addCaveVinePlayerEdgeRoutes(List<Route> routes, String subjectId,
                                                     Family family) {
        for (boolean oldHeadBerries : List.of(false, true)) {
            for (int newHeadAge = 0; newHeadAge < 25; newHeadAge++) {
                for (String rootFrame : CEILING_FRAMES) {
                    addRoute(routes, subjectId, family,
                            ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                            "cave_vine_edge", "down", rootFrame,
                            "sequence=cave_vine_edge;old_head_berries=" + oldHeadBerries
                                    + ";old_head_becomes_body=true"
                                    + ";old_body_berries=" + oldHeadBerries
                                    + ";new_head_age=" + newHeadAge
                                    + ";new_head_berries=false;length_delta=1",
                            false, true, Set.of(
                                    "growth_mechanism=player_extension_head_to_body_conversion",
                                    "placement_age=random_0_24"));
                }
            }
        }
    }

    private static void addWeepingVineHeadDerivedRoutes(List<Route> routes, String subjectId,
                                                         Family family) {
        for (int beforeLength : List.of(1, 2, 3, 5, 16)) {
            for (int growthSample : List.of(1, 2, 3, 5, 16)) {
                for (String rootFrame : CEILING_FRAMES) {
                    addRoute(routes, subjectId, family,
                            ActionKind.BONEMEAL_DERIVED_SECONDARY,
                            EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                            "ceiling_growth", "none", rootFrame,
                            "before_length=" + beforeLength + ";growth_sample=" + growthSample
                                    + ";after_length=" + (beforeLength + growthSample)
                                    + ";head_age=incrementing_capped_25",
                            false, true, Set.of(
                                    "growth_mechanism=nether_vines_probabilistic_bonemeal",
                                    "boundary_length_sample=true",
                                    "executor_defer_if_effect_exceeds=16"));
                }
            }
        }
        for (int age = 0; age < 25; age++) {
            for (String rootFrame : CEILING_FRAMES) {
                addRoute(routes, subjectId, family,
                        ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                        EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS,
                        "ceiling_growth", "none", rootFrame,
                        "old_head_age=" + age + ";old_head_becomes_body=true;new_head_age="
                                + (age + 1) + ";length_delta=1",
                        false, true, Set.of("growth_mechanism=random_tick_head_extension"));
            }
        }
        for (String rootFrame : CEILING_FRAMES) {
            addRoute(routes, subjectId, family,
                    ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                    EffectKind.DERIVED_BLOCK_CELLS, "ceiling_growth", "none", rootFrame,
                    "old_head_age=25;result=no_growth;length_delta=0",
                    false, true, Set.of("growth_mechanism=random_tick_max_age_noop"));
        }
        for (int length = 1; length <= 17; length++) {
            for (int headAge = 17; headAge <= 25; headAge++) {
                addRoute(routes, subjectId, family, ActionKind.GENERATED_STATE_ONLY,
                        EffectKind.GENERATED_BLOCK_CELLS, "ceiling_generated", "none",
                        "GENERATED_WEEPING_VINE_COLUMN", "feature=weeping_vines"
                                + ";length=" + length + ";body_count="
                                + Math.max(0, length - 1) + ";terminal_head=true;head_age="
                                + headAge,
                        false, true, Set.of("origin=worldgen_configured_feature",
                                "provider_domain=length_1_17_head_age_17_25"));
            }
        }
    }

    private static void addDerivedBlockRoutes(List<Route> routes) {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            blocks.add(block);
        }
        blocks.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        for (Block block : blocks) {
            if (!(block instanceof CaveVinesPlantBlock) && !(block instanceof WeepingVinesPlantBlock)) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String sourceItem = block instanceof CaveVinesPlantBlock
                    ? "minecraft:glow_berries" : "minecraft:weeping_vines";
            for (int length : List.of(2, 3, 5, 16)) {
                addRoute(routes, id, Family.CEILING_GROWTH,
                        ActionKind.PLAYER_DERIVED_NEIGHBOR_UPDATE,
                        EffectKind.DERIVED_BLOCK_CELLS, "ceiling", "down", "DERIVED_COLUMN",
                        "derived_plant_segment;length=" + length
                                + ";source_item=" + sourceItem.replace(':', '_')
                                + ";cause=head_to_body_conversion", true, true,
                        Set.of("origin=player_useon_sequence_neighbor_conversion"));
            }
            if (block instanceof CaveVinesPlantBlock) {
                for (boolean berries : List.of(false, true)) {
                    addRoute(routes, id, Family.CEILING_GROWTH,
                            ActionKind.BONEMEAL_DERIVED_SECONDARY,
                            EffectKind.DERIVED_BLOCK_CELLS, "ceiling_growth", "none",
                            "DERIVED_COLUMN", "target=body;berries="
                                    + (berries ? "true_noop" : "false_to_true")
                                    + ";length_delta=0",
                            false, true, Set.of("source_item=" + sourceItem,
                                    berries ? "bonemeal_target=invalid_already_berried"
                                            : "growth_mechanism=cave_vines_bonemeal_sets_berries"));
                }
                for (boolean berries : List.of(false, true)) {
                    addRoute(routes, id, Family.CEILING_GROWTH,
                            ActionKind.GENERATED_STATE_ONLY,
                            EffectKind.GENERATED_BLOCK_CELLS, "ceiling_generated", "none",
                            "GENERATED_CAVE_VINE_COLUMN",
                            "derived_plant_segment;berries=" + berries,
                            false, true, Set.of("origin=worldgen_or_structure_state"));
                }
            } else {
                for (int length : List.of(1, 2, 3, 5, 16)) {
                    addRoute(routes, id, Family.CEILING_GROWTH,
                            ActionKind.BONEMEAL_DERIVED_SECONDARY,
                            EffectKind.DERIVED_BLOCK_CELLS, "ceiling_growth", "none",
                            "DERIVED_COLUMN", "derived_plant_segment;growth_sample=" + length
                                    + ";cause=bonemeal_delegates_to_head", false, true,
                            Set.of("source_item=" + sourceItem,
                                    "probabilistic_boundary_sample=true",
                                    "executor_defer_if_effect_exceeds=16"));
                }
                for (int columnLength = 2; columnLength <= 17; columnLength++) {
                    addRoute(routes, id, Family.CEILING_GROWTH,
                            ActionKind.GENERATED_STATE_ONLY,
                            EffectKind.GENERATED_BLOCK_CELLS, "ceiling_generated", "none",
                            "GENERATED_WEEPING_VINE_COLUMN",
                            "derived_plant_segment;feature=weeping_vines;column_length="
                                    + columnLength + ";body_subject_present=true"
                                    + ";head_age_domain=17_25",
                            false, true, Set.of("origin=worldgen_configured_feature",
                                    "provider_domain=column_length_2_17"));
                }
            }
        }
    }

    private static void addFloorRoutes(List<Route> routes, String subjectId, Family family,
                                       ActionKind actionKind, EffectKind effectKind,
                                       List<String> orientations, String state,
                                       boolean chainTerminal, boolean delayed,
                                       Set<String> extraTags) {
        for (String frame : FLOOR_FRAMES) {
            for (String orientation : orientations) {
                addRoute(routes, subjectId, family, actionKind, effectKind, "floor", "up", frame,
                        state + orientationSuffix(orientation), chainTerminal, delayed, extraTags);
            }
        }
    }

    private static void addCeilingRoutes(List<Route> routes, String subjectId, Family family,
                                         ActionKind actionKind, EffectKind effectKind,
                                         List<String> orientations, String state,
                                         boolean chainTerminal, boolean delayed,
                                         Set<String> extraTags) {
        for (String frame : CEILING_FRAMES) {
            for (String orientation : orientations) {
                addRoute(routes, subjectId, family, actionKind, effectKind, "ceiling", "down", frame,
                        state + orientationSuffix(orientation), chainTerminal, delayed, extraTags);
            }
        }
    }

    private static void addWallRoutes(List<Route> routes, String subjectId, Family family,
                                      ActionKind actionKind, EffectKind effectKind,
                                      String state, boolean chainTerminal, boolean delayed,
                                      Set<String> extraTags) {
        for (Direction face : HORIZONTAL) {
            for (String frame : WALL_FRAMES) {
                addRoute(routes, subjectId, family, actionKind, effectKind, "wall",
                        faceName(face), frame, state + ";clicked_face=" + faceName(face),
                        chainTerminal, delayed, extraTags);
            }
        }
    }

    private static void addAllFaceRoutes(List<Route> routes, String subjectId, Family family,
                                         String state, boolean chainTerminal, boolean delayed,
                                         Set<String> extraTags) {
        for (Direction face : ALL_DIRECTIONS) {
            if (face == Direction.UP) {
                addFloorRoutes(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"), state, chainTerminal,
                        delayed, extraTags);
            } else if (face == Direction.DOWN) {
                addCeilingRoutes(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.EXACT_BLOCK_CELLS, List.of("none"), state, chainTerminal,
                        delayed, extraTags);
            } else {
                for (String frame : WALL_FRAMES) {
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                            EffectKind.EXACT_BLOCK_CELLS, "wall", faceName(face), frame,
                            state + ";clicked_face=" + faceName(face), chainTerminal, delayed, extraTags);
                }
            }
        }
    }

    private static void addCeilingSequences(List<Route> routes, String subjectId, Family family,
                                            String sequence, List<String> stateVariants,
                                            boolean chainTerminal,
                                            Set<String> extraTags) {
        addCeilingSequences(routes, subjectId, family, sequence, stateVariants,
                EffectKind.EXACT_BLOCK_CELLS, chainTerminal, extraTags);
    }

    private static void addCeilingSequences(List<Route> routes, String subjectId, Family family,
                                            String sequence, List<String> stateVariants,
                                            EffectKind effectKind, boolean chainTerminal,
                                            Set<String> extraTags) {
        for (int length : List.of(2, 3, 5, 16)) {
            for (String frame : CEILING_FRAMES) {
                for (String stateVariant : stateVariants) {
                    addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK_SEQUENCE,
                            effectKind, "ceiling", "down", frame,
                            "sequence=" + sequence + ";length=" + length
                                    + orientationSuffix(stateVariant),
                            chainTerminal, true, extraTags);
                }
            }
        }
    }

    private static void addMultifaceRoutes(List<Route> routes, String subjectId, Family family,
                                           Block block) {
        List<Direction> supported = new ArrayList<>();
        BlockState state = block.defaultBlockState();
        for (Direction direction : ALL_DIRECTIONS) {
            if (state.hasProperty(MultifaceBlock.getFaceProperty(direction))) {
                supported.add(direction);
            }
        }
        int combinations = 1 << supported.size();
        for (int mask = 1; mask < combinations; mask++) {
            List<String> stateFaces = new ArrayList<>();
            List<String> clickedFaces = new ArrayList<>();
            for (int bit = 0; bit < supported.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    Direction stateFace = supported.get(bit);
                    stateFaces.add(faceName(stateFace));
                    clickedFaces.add(faceName(stateFace.getOpposite()));
                }
            }
            ActionKind kind = stateFaces.size() == 1
                    ? ActionKind.PLAYER_USEON_BLOCK : ActionKind.PLAYER_USEON_BLOCK_SEQUENCE;
            String mount = stateFaces.contains("up") ? "ceiling_multiface"
                    : stateFaces.contains("down") ? "floor_multiface" : "wall_multiface";
            String stateContract = "state_faces=" + String.join(",", stateFaces)
                    + ";ordered_clicked_faces=" + String.join(",", clickedFaces);
            addRoute(routes, subjectId, family, kind, EffectKind.EXACT_BLOCK_CELLS,
                    mount, String.join(">", clickedFaces), "MULTIFACE_ENCLOSURE",
                    stateContract, false, true,
                    Set.of("face_subset_count=" + stateFaces.size(),
                            "state_support_faces=" + String.join(",", stateFaces)));
            addRoute(routes, subjectId, family, ActionKind.GENERATED_STATE_ONLY,
                    EffectKind.GENERATED_BLOCK_CELLS, "multiface_generated", "none",
                    "MULTIFACE_ENCLOSURE", stateContract, false, true,
                    Set.of("face_subset_count=" + stateFaces.size(),
                            "origin=worldgen_or_spreader_state"));
            if (block instanceof BonemealableBlock) {
                addRoute(routes, subjectId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                        EffectKind.DERIVED_BLOCK_CELLS, "multiface_growth", "none",
                        "MULTIFACE_ENCLOSURE", stateContract, false, true,
                        Set.of("face_subset_count=" + stateFaces.size(),
                                "effect=multiface_spreader_growth"));
            }
        }
    }

    private static void addVineFaceSubsets(List<Route> routes, String subjectId, Family family) {
        List<Direction> faces = List.of(Direction.UP, Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST);
        for (int mask = 1; mask < (1 << faces.size()); mask++) {
            List<String> stateFaces = new ArrayList<>();
            List<String> clickedFaces = new ArrayList<>();
            for (int bit = 0; bit < faces.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    Direction stateFace = faces.get(bit);
                    stateFaces.add(faceName(stateFace));
                    clickedFaces.add(faceName(stateFace.getOpposite()));
                }
            }
            ActionKind kind = stateFaces.size() == 1
                    ? ActionKind.PLAYER_USEON_BLOCK : ActionKind.PLAYER_USEON_BLOCK_SEQUENCE;
            String mount = stateFaces.contains("up") ? "ceiling_vine" : "wall_vine";
            String stateContract = "state_faces=" + String.join(",", stateFaces)
                    + ";ordered_clicked_faces=" + String.join(",", clickedFaces);
            addRoute(routes, subjectId, family, kind, EffectKind.EXACT_BLOCK_CELLS,
                    mount, String.join(">", clickedFaces), "VINE_ENCLOSURE",
                    stateContract, false, true,
                    Set.of("face_subset_count=" + stateFaces.size(),
                            "state_support_faces=" + String.join(",", stateFaces)));
            addRoute(routes, subjectId, family, ActionKind.RANDOM_TICK_DERIVED_SECONDARY,
                    EffectKind.DERIVED_BLOCK_CELLS, "vine_growth", "none",
                    "VINE_ENCLOSURE", stateContract, false, true,
                    Set.of("face_subset_count=" + stateFaces.size(),
                            "effect=random_tick_spread_or_add_face"));
            addRoute(routes, subjectId, family, ActionKind.GENERATED_STATE_ONLY,
                    EffectKind.GENERATED_BLOCK_CELLS, "vine_generated", "none",
                    "VINE_ENCLOSURE", stateContract, false, true,
                    Set.of("face_subset_count=" + stateFaces.size(),
                            "origin=worldgen_or_structure_state"));
        }
    }

    private static void addMossyCarpetRoutes(List<Route> routes, String subjectId,
                                             Family family) {
        List<String> directions = List.of("north", "east", "south", "west");
        for (String frame : FLOOR_FRAMES) {
            for (int mask = 0; mask < 16; mask++) {
                List<String> supported = selectedNames(directions, mask);
                String lowState = mossyCarpetState(true, supported, "low");
                addRoute(routes, subjectId, family, ActionKind.PLAYER_USEON_BLOCK,
                        EffectKind.PRIMARY_AND_DERIVED_BLOCK_CELLS, "floor", "up", frame,
                        lowState + ";support_faces=" + joinedOrNone(supported)
                                + ";topper=probabilistic_supported_subset",
                        false, true, Set.of("placement_secondary=topper_if_nonempty"));
            }
        }
        for (int mask = 1; mask < 16; mask++) {
            List<String> supported = selectedNames(directions, mask);
            String topper = mossyCarpetState(false, supported, "low");
            addRoute(routes, subjectId, family,
                    ActionKind.PLAYER_PLACEMENT_DERIVED_SECONDARY,
                    EffectKind.DERIVED_BLOCK_CELLS, "wall_carpet_topper", "none",
                    "MOSSY_CARPET_ENCLOSURE", topper
                            + ";paired_bottom_sides=tall;source=set_placed_by",
                    false, true, Set.of("support_faces=" + joinedOrNone(supported)));
            addRoute(routes, subjectId, family, ActionKind.BONEMEAL_DERIVED_SECONDARY,
                    EffectKind.DERIVED_BLOCK_CELLS, "wall_carpet_topper", "none",
                    "MOSSY_CARPET_ENCLOSURE", topper
                            + ";paired_bottom_sides=tall;source=bonemeal",
                    false, true, Set.of("support_faces=" + joinedOrNone(supported)));
        }
        List<String> sides = List.of("none", "low", "tall");
        for (boolean base : List.of(false, true)) {
            for (String north : sides) {
                for (String east : sides) {
                    for (String south : sides) {
                        for (String west : sides) {
                            if (!base && north.equals("none") && east.equals("none")
                                    && south.equals("none") && west.equals("none")) {
                                continue;
                            }
                            String state = "base=" + base + ";north=" + north + ";east=" + east
                                    + ";south=" + south + ";west=" + west;
                            addRoute(routes, subjectId, family,
                                    ActionKind.PLAYER_DERIVED_NEIGHBOR_UPDATE,
                                    EffectKind.DERIVED_BLOCK_CELLS, "wall_carpet_state", "sequence",
                                    "MOSSY_CARPET_ENCLOSURE", state, false, true,
                                    Set.of("state_space=legal_base_x_wall_side_3pow4",
                                            "cause=neighbor_update"));
                            addRoute(routes, subjectId, family,
                                    ActionKind.GENERATED_STATE_ONLY,
                                    EffectKind.GENERATED_BLOCK_CELLS, "wall_carpet_generated", "none",
                                    "MOSSY_CARPET_ENCLOSURE", state, false, true,
                                    Set.of("state_space=legal_base_x_wall_side_3pow4",
                                            "origin=worldgen_place_at_or_structure_state"));
                        }
                    }
                }
            }
        }
    }

    private static void addRoute(List<Route> routes, String subjectId, Family family,
                                 ActionKind actionKind, EffectKind effectKind,
                                 String mount, String clickedFace, String supportFrame,
                                 String stateContract, boolean terminalHint,
                                 boolean delayedObservation, Set<String> extraTags) {
        String actionOrigin = switch (actionKind) {
            case GENERATED_STATE_ONLY -> "GENERATED_STATE_PROXY";
            case PLAYER_PLACEMENT_DERIVED_SECONDARY -> "PLAYER_PLACEMENT_SECONDARY_PROXY";
            case PLAYER_DERIVED_NEIGHBOR_UPDATE -> "PLAYER_NEIGHBOR_DERIVED_PROXY";
            case BONEMEAL_DERIVED_SECONDARY -> "BONEMEAL_DERIVED_PROXY";
            case RANDOM_TICK_DERIVED_SECONDARY -> "RANDOM_TICK_DERIVED_PROXY";
            default -> "AUTO_USEON_PROXY";
        };
        boolean chainTerminal = isChainTerminalRoute(family, actionKind, mount);
        TreeSet<String> tags = new TreeSet<>();
        tags.add("family=" + familyTag(family));
        tags.add("mount=" + mount);
        tags.add("clicked_face=" + clickedFace);
        tags.add("support_frame=" + supportFrame.toLowerCase(Locale.ROOT));
        tags.add("action_kind=" + actionKind.name().toLowerCase(Locale.ROOT));
        tags.add("effect_kind=" + effectKind.name().toLowerCase(Locale.ROOT));
        tags.add("chain_terminal=" + chainTerminal);
        tags.add("player_proof=absent_catalog_only");
        for (String stateToken : stateContract.split(";")) {
            if (stateToken.matches("[a-z_]+=[a-zA-Z0-9_.,+-]+")) {
                tags.add(stateToken);
            }
        }
        tags.addAll(extraTags);
        String routeId = semanticRouteId(subjectId, family, actionKind, effectKind, actionOrigin,
                mount, clickedFace, supportFrame, stateContract, chainTerminal,
                delayedObservation, List.copyOf(tags));
        routes.add(new Route(-1, routeId, subjectId, family,
                actionKind, effectKind, actionOrigin, mount, clickedFace, supportFrame,
                stateContract, chainTerminal, delayedObservation, List.copyOf(tags)));
    }

    private static boolean isChainTerminalRoute(Family family, ActionKind actionKind,
                                                String mount) {
        boolean executablePlayerRoute = actionKind == ActionKind.PLAYER_USEON_BLOCK
                || actionKind == ActionKind.PLAYER_USEON_BLOCK_SEQUENCE
                || actionKind == ActionKind.PLAYER_USEON_ENTITY_EFFECT;
        return executablePlayerRoute && family != Family.CHAIN && mount.startsWith("ceiling");
    }

    /** Pure route-ID seam; every execution/verdict-relevant field is load-bearing. */
    public static String semanticRouteId(String subjectId, Family family, ActionKind actionKind,
                                         EffectKind effectKind, String actionOrigin,
                                         String mount, String clickedFace, String supportFrame,
                                         String stateContract, boolean chainTerminal,
                                         boolean delayedObservation, List<String> tags) {
        String semantic = SCHEMA + '\0' + subjectId + '\0' + family.name() + '\0'
                + actionKind.name() + '\0' + effectKind.name() + '\0' + actionOrigin + '\0'
                + mount + '\0' + clickedFace + '\0' + supportFrame + '\0' + stateContract + '\0'
                + chainTerminal + '\0' + delayedObservation + '\0' + String.join(",", tags);
        return "route-v1:sha256:" + sha256(semantic);
    }

    private static List<Route> normalizeRoutes(List<Route> routes) {
        routes.sort(Comparator.comparing(Route::id));
        List<Route> normalized = new ArrayList<>(routes.size());
        String previous = null;
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            if (route.id().equals(previous)) {
                throw new IllegalStateException("duplicate semantic RIG-3 route: " + route.id());
            }
            previous = route.id();
            normalized.add(new Route(i, route.id(), route.subjectId(), route.family(),
                    route.actionKind(), route.effectKind(), route.actionOrigin(), route.mount(),
                    route.clickedFace(), route.supportFrame(), route.stateContract(),
                    route.chainTerminal(), route.delayedObservation(), route.tags()));
        }
        return List.copyOf(normalized);
    }

    private static List<ChainPattern> chainPatterns(List<ChainMaterial> materials) {
        List<ChainPattern> patterns = new ArrayList<>();
        for (String orientation : CHAIN_ORIENTATIONS) {
            String axis = axisForOrientation(orientation);
            for (int length : CHAIN_LENGTHS) {
                for (ChainMaterial material : materials) {
                    List<String> materialIds = new ArrayList<>(length);
                    for (int i = 0; i < length; i++) {
                        materialIds.add(material.itemId());
                    }
                    patterns.add(new ChainPattern(-1,
                            chainPatternId(orientation, axis, length, false, materialIds),
                            orientation, axis, length, false, materialIds));
                }
                if (length > 1) {
                    List<String> mixed = new ArrayList<>(length);
                    for (int i = 0; i < length; i++) {
                        mixed.add(materials.get(i % materials.size()).itemId());
                    }
                    patterns.add(new ChainPattern(-1,
                            chainPatternId(orientation, axis, length, true, mixed),
                            orientation, axis, length, true, mixed));
                }
            }
        }
        patterns.sort(Comparator.comparing(ChainPattern::id));
        List<ChainPattern> indexed = new ArrayList<>(patterns.size());
        for (int i = 0; i < patterns.size(); i++) {
            ChainPattern pattern = patterns.get(i);
            indexed.add(new ChainPattern(i, pattern.id(), pattern.orientation(), pattern.axis(),
                    pattern.length(), pattern.mixed(), pattern.materialIds()));
        }
        return List.copyOf(indexed);
    }

    private static String chainPatternId(String orientation, String axis, int length,
                                         boolean mixed, List<String> materials) {
        String semantic = SCHEMA + '\0' + "chain-pattern" + '\0' + orientation + '\0' + axis
                + '\0' + length + '\0' + mixed
                + '\0' + String.join(",", materials);
        return "chain-v1:sha256:" + sha256(semantic);
    }

    private static String canonicalBody(String topologyCatalogHash, int runtimeItemCount,
                                        List<CatalogItem> items, List<ExcludedItem> excluded,
                                        List<Route> routes, List<Route> chainTerminals,
                                        List<ChainMaterial> materials, List<ChainPattern> patterns,
                                        List<ChainPattern> terminalPatterns,
                                        List<SlabRigCaseCatalog.Topology> topologies,
                                        long totalCases, int pageCount) {
        StringBuilder out = new StringBuilder();
        out.append("schema\t").append(SCHEMA).append('\n');
        out.append("case_index_contract_version\t").append(CASE_INDEX_CONTRACT_VERSION).append('\n');
        out.append("page_size\t").append(PAGE_SIZE).append('\n');
        out.append("page_count\t").append(pageCount).append('\n');
        out.append("case_kind_order\t").append(String.join(",", CASE_KIND_ORDER)).append('\n');
        out.append("case_axes_DIRECT\troute,topology\n");
        out.append("case_axes_CHAIN_ONLY\tchain_pattern,chain_support_frame,topology\n");
        out.append("case_axes_CHAIN_TERMINAL\tterminal_route,terminal_pattern,terminal_support_frame,topology\n");
        for (String orientation : CHAIN_ORIENTATIONS) {
            out.append("chain_support_frames_").append(orientation).append('\t')
                    .append(String.join(",", chainSupportFrames(orientation))).append('\n');
        }
        out.append("terminal_support_frames\t")
                .append(String.join(",", terminalSupportFrames())).append('\n');
        out.append("terminal_route_count\t").append(chainTerminals.size()).append('\n');
        for (int index = 0; index < chainTerminals.size(); index++) {
            out.append("terminal_route\t").append(index).append('\t')
                    .append(chainTerminals.get(index).id()).append('\n');
        }
        out.append("terminal_pattern_count\t").append(terminalPatterns.size()).append('\n');
        for (int index = 0; index < terminalPatterns.size(); index++) {
            out.append("terminal_pattern\t").append(index).append('\t')
                    .append(terminalPatterns.get(index).id()).append('\n');
        }
        out.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        out.append("topology_catalog_hash\t").append(topologyCatalogHash).append('\n');
        out.append("runtime_item_count\t").append(runtimeItemCount).append('\n');
        out.append("included_item_count\t").append(items.size()).append('\n');
        out.append("excluded_item_count\t").append(excluded.size()).append('\n');
        out.append("route_count\t").append(routes.size()).append('\n');
        out.append("chain_material_count\t").append(materials.size()).append('\n');
        out.append("chain_pattern_count\t").append(patterns.size()).append('\n');
        out.append("topology_count\t").append(topologies.size()).append('\n');
        out.append("total_cases\t").append(totalCases).append('\n');
        for (CatalogItem item : items) {
            out.append("item\t").append(item.index()).append('\t').append(item.id()).append('\t')
                    .append(item.family()).append('\t').append(item.blockItem()).append('\t')
                    .append(String.join(",", item.tags())).append('\n');
        }
        for (ExcludedItem item : excluded) {
            out.append("excluded\t").append(item.index()).append('\t').append(item.id()).append('\t')
                    .append(item.reason()).append('\t').append(item.route()).append('\n');
        }
        for (Route route : routes) {
            out.append("route\t").append(route.index()).append('\t').append(route.id()).append('\t')
                    .append(route.subjectId()).append('\t').append(route.family()).append('\t')
                    .append(route.actionKind()).append('\t').append(route.effectKind()).append('\t')
                    .append(route.actionOrigin()).append('\t').append(route.mount()).append('\t')
                    .append(route.clickedFace()).append('\t').append(route.supportFrame()).append('\t')
                    .append(route.stateContract()).append('\t').append(route.chainTerminal()).append('\t')
                    .append(route.delayedObservation()).append('\t')
                    .append(String.join(",", route.tags())).append('\n');
        }
        for (ChainMaterial material : materials) {
            out.append("chain_material\t").append(material.index()).append('\t')
                    .append(material.itemId()).append('\n');
        }
        for (ChainPattern pattern : patterns) {
            out.append("chain_pattern\t").append(pattern.index()).append('\t').append(pattern.id())
                    .append('\t').append(pattern.orientation()).append('\t').append(pattern.axis())
                    .append('\t').append(pattern.length()).append('\t').append(pattern.mixed()).append('\t')
                    .append(String.join(",", pattern.materialIds())).append('\n');
        }
        for (SlabRigCaseCatalog.Topology topology : topologies) {
            out.append("topology\t").append(topology.index()).append('\t').append(topology.id())
                    .append('\t').append(topology.recipe()).append('\t').append(topology.control()).append('\n');
        }
        return out.toString();
    }

    private static String familyTag(Family family) {
        return family.name().toLowerCase(Locale.ROOT);
    }

    private static String faceName(Direction direction) {
        return direction.getName();
    }

    private static Direction playerDirectionForInputRotation(int inputRotation) {
        float contextRotation = inputRotation * 22.5F;
        return Direction.fromYRot(contextRotation);
    }

    private static String orientationSuffix(String orientation) {
        if ("none".equals(orientation)) {
            return "";
        }
        return orientation.contains("=") ? ";" + orientation : ";horizontal_facing=" + orientation;
    }

    private static String axisForOrientation(String orientation) {
        return switch (orientation) {
            case "up", "down" -> "y";
            case "east", "west" -> "x";
            case "north", "south" -> "z";
            default -> throw new IllegalArgumentException("unknown chain orientation " + orientation);
        };
    }

    private static int supportFramesPerChainPattern() {
        return 4;
    }

    private static List<String> terminalSupportFrames() {
        return CEILING_FRAMES;
    }

    private static List<String> selectedNames(List<String> names, int mask) {
        List<String> selected = new ArrayList<>();
        for (int bit = 0; bit < names.size(); bit++) {
            if ((mask & (1 << bit)) != 0) {
                selected.add(names.get(bit));
            }
        }
        return List.copyOf(selected);
    }

    private static String joinedOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(",", values);
    }

    private static String mossyCarpetState(boolean base, List<String> selected, String sideValue) {
        Set<String> chosen = Set.copyOf(selected);
        return "base=" + base
                + ";north=" + (chosen.contains("north") ? sideValue : "none")
                + ";east=" + (chosen.contains("east") ? sideValue : "none")
                + ";south=" + (chosen.contains("south") ? sideValue : "none")
                + ";west=" + (chosen.contains("west") ? sideValue : "none");
    }

    private static List<String> standingStateVariants(Block block) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            return ROTATIONS_16;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return ORIENTATIONS.stream().map(value -> "horizontal_facing=" + value).toList();
        }
        return List.of("none");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
