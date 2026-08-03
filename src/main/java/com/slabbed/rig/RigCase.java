package com.slabbed.rig;

import com.slabbed.placement.LandingResolution;
import com.slabbed.placement.PlacementAim;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One deterministic rig case: declared scaffolding plus real-use-on subject actions. */
public record RigCase(
        String id,
        List<FixtureCell> fixtures,
        List<SubjectPlacement> subjects) {

    public RigCase {
        id = requireId(id);
        fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        if (fixtures.isEmpty() && subjects.isEmpty()) {
            throw new IllegalArgumentException("rig case must declare at least one cell");
        }
    }

    /**
     * Declared non-state truth authored only for fixture scaffolding. Subject placement never uses
     * this door. The source is required only for the compound-visible side-slab relationship.
     */
    public record FixtureAuthorship(Kind kind, BlockPos sourcePos, long storedDyBits) {
        public FixtureAuthorship {
            kind = Objects.requireNonNull(kind, "kind");
            if (kind == Kind.COMPOUND_VISIBLE_SIDE_LOWER_SLAB) {
                sourcePos = Objects.requireNonNull(sourcePos, "sourcePos").immutable();
            } else if (sourcePos != null) {
                throw new IllegalArgumentException(
                        "only compound-visible side fixtures may name a source cell");
            }
            if (kind == Kind.STORED_DY
                    && !Double.isFinite(Double.longBitsToDouble(storedDyBits))) {
                throw new IllegalArgumentException("stored fixture dy must be finite");
            } else if (kind != Kind.STORED_DY && storedDyBits != 0L) {
                throw new IllegalArgumentException(
                        "only stored-dy fixtures may carry authored dy bits");
            }
        }

        public static FixtureAuthorship none() {
            return new FixtureAuthorship(Kind.NONE, null, 0L);
        }

        public static FixtureAuthorship compoundFullBlock() {
            return new FixtureAuthorship(Kind.COMPOUND_FULL_BLOCK, null, 0L);
        }

        public static FixtureAuthorship compoundVisibleSideLowerSlab(BlockPos sourcePos) {
            return new FixtureAuthorship(
                    Kind.COMPOUND_VISIBLE_SIDE_LOWER_SLAB, sourcePos, 0L);
        }

        public static FixtureAuthorship storedDy(double dy) {
            if (!Double.isFinite(dy)) {
                throw new IllegalArgumentException("stored fixture dy must be finite");
            }
            return new FixtureAuthorship(
                    Kind.STORED_DY, null, Double.doubleToRawLongBits(dy));
        }

        public enum Kind {
            NONE,
            COMPOUND_FULL_BLOCK,
            COMPOUND_VISIBLE_SIDE_LOWER_SLAB,
            STORED_DY
        }
    }

    public record FixtureCell(
            BlockPos pos,
            BlockState state,
            FixtureAuthorship authorship) {
        public FixtureCell(BlockPos pos, BlockState state) {
            this(pos, state, FixtureAuthorship.none());
        }

        public FixtureCell {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            state = Objects.requireNonNull(state, "state");
            authorship = Objects.requireNonNull(authorship, "authorship");
            if (state.isAir()) {
                throw new IllegalArgumentException("fixture state must not be air");
            }
            if (authorship.sourcePos() != null && authorship.sourcePos().equals(pos)) {
                throw new IllegalArgumentException("fixture source must be a different cell");
            }
        }
    }

    public record SubjectPlacement(
            PlacementAim aim,
            Block expectedBlock,
            LandingResolution.Lane expectedLane) {
        public SubjectPlacement {
            aim = Objects.requireNonNull(aim, "aim");
            expectedBlock = Objects.requireNonNull(expectedBlock, "expectedBlock");
            expectedLane = Objects.requireNonNull(expectedLane, "expectedLane");
        }
    }

    private static String requireId(String value) {
        String id = Objects.requireNonNull(value, "id").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        return id;
    }
}
