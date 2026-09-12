package com.slabbed.placement;

import java.util.List;

/** Shared context supplied by the declaring collision-sweeper class. */
public interface StoredCollisionSource {
    List<PlacementCollisionShapes.Contact> slabbed$storedContacts();
}
