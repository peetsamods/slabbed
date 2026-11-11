package com.slabbed.client.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;
import org.apache.commons.lang3.tuple.Pair;

/** Only closed vanilla model trees without another offset owner may use the CTM adapter. */
public final class ContinuityModelAdmission {
    private ContinuityModelAdmission() {
    }

    public static boolean canTranslate(BakedModel delegate) {
        return canTranslate(delegate, ContinuityModelAdmission::children);
    }

    static boolean canTranslate(BakedModel delegate, Function<BakedModel, List<BakedModel>> inspector) {
        try {
            return visit(delegate, inspector, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (RuntimeException | LinkageError refused) {
            return false;
        }
    }

    private static boolean visit(BakedModel model, Function<BakedModel, List<BakedModel>> inspector,
                                 Set<BakedModel> active) {
        if (model == null || active.size() >= 64 || !active.add(model)) {
            return false;
        }
        try {
            if (model.getClass() == SimpleBakedModel.class) {
                return true;
            }
            if (model.getClass() != WeightedBakedModel.class && model.getClass() != MultiPartBakedModel.class) {
                return false;
            }
            List<BakedModel> children = inspector.apply(model);
            if (children == null || children.isEmpty()) {
                return false;
            }
            for (BakedModel child : children) {
                if (!visit(child, inspector, active)) {
                    return false;
                }
            }
            return true;
        } finally {
            active.remove(model);
        }
    }

    private static List<BakedModel> children(BakedModel model) {
        List<BakedModel> result = new ArrayList<>();
        if (model.getClass() == WeightedBakedModel.class) {
            Object entries = field(model, "list");
            if (!(entries instanceof List<?> list)) {
                return List.of();
            }
            for (Object entry : list) {
                if (!(entry instanceof WeightedEntry.Wrapper<?> weighted)
                        || !(weighted.data() instanceof BakedModel child)) {
                    return List.of();
                }
                result.add(child);
            }
            result.add((BakedModel) field(model, "wrapped"));
        } else if (model.getClass() == MultiPartBakedModel.class) {
            Object entries = field(model, "selectors");
            if (!(entries instanceof List<?> list)) {
                return List.of();
            }
            for (Object entry : list) {
                if (!(entry instanceof Pair<?, ?> pair) || !(pair.getRight() instanceof BakedModel child)) {
                    return List.of();
                }
                result.add(child);
            }
            result.add((BakedModel) field(model, "defaultModel"));
        }
        return result;
    }

    private static Object field(BakedModel model, String name) {
        try {
            Field field = model.getClass().getDeclaredField(name);
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Model tree is not inspectable");
            }
            return field.get(model);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Model tree is not inspectable", exception);
        }
    }
}
