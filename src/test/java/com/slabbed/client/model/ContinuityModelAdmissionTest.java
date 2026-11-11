package com.slabbed.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuityModelAdmissionTest {
    static SimpleBakedModel simple() {
        return new SimpleBakedModel(List.of(), Map.of(), false, false, false,
                null, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
    }

    static WeightedBakedModel weighted(BakedModel... models) {
        return new WeightedBakedModel(java.util.Arrays.stream(models).map(m -> WeightedEntry.wrap(m, 1)).toList());
    }

    static MultiPartBakedModel multipart(BakedModel... models) {
        Predicate<BlockState> yes = state -> true;
        return new MultiPartBakedModel(java.util.Arrays.stream(models).map(m -> Pair.of(yes, m)).toList());
    }

    @Test void admitsOnlyCompleteExactVanillaTrees() {
        var leaf = simple();
        assertTrue(ContinuityModelAdmission.canTranslate(leaf));
        assertTrue(ContinuityModelAdmission.canTranslate(weighted(leaf, multipart(leaf, simple()))));
        assertTrue(ContinuityModelAdmission.canTranslate(multipart(weighted(leaf, simple()), leaf)));
        assertFalse(ContinuityModelAdmission.canTranslate(null));
        assertFalse(ContinuityModelAdmission.canTranslate(new BakedModelWrapper<>(leaf) {}));
        assertFalse(ContinuityModelAdmission.canTranslate(new SimpleBakedModel(List.of(), Map.of(), false,
                false, false, null, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY) {}));
    }

    @Test void refusesNativeOwnersAndUnknownsInEveryCompositeBranch() {
        var leaf = simple();
        var nativeOwner = new OffsetBlockStateModel(leaf);
        var foreign = new BakedModelWrapper<>(leaf) {};
        assertFalse(ContinuityModelAdmission.canTranslate(weighted(leaf, multipart(leaf, nativeOwner))));
        assertFalse(ContinuityModelAdmission.canTranslate(multipart(leaf, weighted(leaf, nativeOwner))));
        assertFalse(ContinuityModelAdmission.canTranslate(weighted(leaf, foreign)));
        assertFalse(ContinuityModelAdmission.canTranslate(multipart(leaf, foreign)));
        assertTrue(ContinuityModelAdmission.canTranslate(weighted(leaf, leaf)),
                "sharing a leaf is not a cycle");
    }

    @Test void cyclesAndInspectionFailuresRemainUnchanged() {
        var leaf = simple();
        List<WeightedEntry.Wrapper<BakedModel>> entries = new ArrayList<>();
        entries.add(WeightedEntry.wrap(leaf, 1));
        var cycle = new WeightedBakedModel(entries);
        entries.add(WeightedEntry.wrap(cycle, 1));
        assertFalse(ContinuityModelAdmission.canTranslate(cycle));
        assertFalse(ContinuityModelAdmission.canTranslate(weighted(leaf), model -> {
            throw new IllegalStateException("inspection unavailable");
        }));
        assertFalse(ContinuityModelAdmission.canTranslate(weighted(leaf), model -> List.of()));
        assertFalse(ContinuityModelAdmission.canTranslate(weighted(leaf), model -> null));
    }
}
