/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.alignment.spec.stack;

import java.util.List;

import mpicbg.trakem2.transform.AffineModel2D;

import org.janelia.alignment.spec.Bounds;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link HierarchicalStack} class.
 *
 * @author Eric Trautman
 */
public class HierarchicalStackTest {

    @Test
    public void testEmptyConstructor() throws Exception {
        final HierarchicalStack stack = new HierarchicalStack();
        Assert.assertNotNull("null stack", stack);
    }

    @Test
    public void testGetters() throws Exception {

        final int tier = 2;
        final Integer tierRow = 1;
        final Integer tierColumn = 2;
        final Integer totalTierRowCount = 3;
        final Integer totalTierColumnCount = 4;
        final Double scale = 0.1;
        final Bounds fullScaleBounds = new Bounds(22.0, 33.0, 1.0, 44.0, 55.0, 9.0);

        final StackId roughTilesStackId = new StackId("testOwner", "tilesProject", "roughTiles");
        final StackId parentTierStackId = new StackId("testOwner", "tilesProject", "roughTiles_tier_1_warp");
        final StackId warpTilesStackId = new StackId("testOwner", "tilesProject", "roughTiles_tier_2_warp");
        final StackId splitStackId = new StackId("testOwner", "tilesProject_roughTiles_tier_2", "0003x0004_000005");

        final HierarchicalStack tier2Stack =
                new HierarchicalStack(roughTilesStackId,
                                      tier,
                                      tierRow,
                                      tierColumn,
                                      totalTierRowCount,
                                      totalTierColumnCount,
                                      scale,
                                      fullScaleBounds);

        checkStackId("invalid rough stack", roughTilesStackId, tier2Stack.getRoughTilesStackId());
        checkStackId("invalid parent stack", parentTierStackId, tier2Stack.getParentTierStackId());
        checkStackId("invalid warp stack", warpTilesStackId, tier2Stack.getWarpTilesStackId());

        Assert.assertEquals("invalid tier", new Integer(tier), tier2Stack.getTier());
        Assert.assertEquals("invalid row", tierRow.intValue(), tier2Stack.getTierRow());
        Assert.assertEquals("invalid column", tierColumn.intValue(), tier2Stack.getTierColumn());
        Assert.assertEquals("invalid row count", totalTierRowCount.intValue(), tier2Stack.getTotalTierRowCount());
        Assert.assertEquals("invalid column count",
                            totalTierColumnCount.intValue(), tier2Stack.getTotalTierColumnCount());
        Assert.assertEquals("invalid scale", scale, tier2Stack.getScale(), DOUBLE_DELTA);

        checkBounds("invalid bounds", fullScaleBounds, tier2Stack.getFullScaleBounds());

        final StackId actualSplitStackId = tier2Stack.getSplitStackId();
        Assert.assertEquals("invalid split stack project", splitStackId.getProject(), actualSplitStackId.getProject());
        Assert.assertEquals("invalid split stack", splitStackId.getStack(), actualSplitStackId.getStack());

        final Long savedMatchPairCount = 22L;
        tier2Stack.setSavedMatchPairCount(savedMatchPairCount);
        Assert.assertEquals("invalid match pair count", savedMatchPairCount, tier2Stack.getSavedMatchPairCount());

        final Double quality = 66.0;
        tier2Stack.setAlignmentQuality(quality);
        Assert.assertEquals("invalid quality", quality, tier2Stack.getAlignmentQuality(), DOUBLE_DELTA);

        Assert.assertNotNull("invalid box path", tier2Stack.getBoxPathForZ(1.0));
        Assert.assertNotNull("invalid model", tier2Stack.getRelativeModel(new AffineModel2D(), 0.0, 0.0));
    }

    public void foo(final double v) {
        System.out.println("ceil " + v + " is " + Math.ceil(v));
    }

    @Test
    public void testSplitTier() throws Exception {

        foo(1.1);
        foo(1.4);
        foo(1.5);
        foo(1.6);
        foo(2.0);
        foo(2.1);
        foo(2.4);
        foo(2.5);
        foo(2.6);

        final StackId roughTilesStackId = new StackId("testOwner", "tilesProject", "roughTiles");
        final StackId warpTilesStackId = new StackId("testOwner", "tilesProject", "roughTiles_tier_1_warp");
        final Bounds parentStackBounds = new Bounds(54954.0, 58314.0, 1.0, 69539.0, 76856.0, 3.0);
        final int maxPixesPerDimension = 4096;
        final Integer tier = 1;

        final List<HierarchicalStack> splitStacks = HierarchicalStack.splitTier(roughTilesStackId,
                                                                                parentStackBounds,
                                                                                maxPixesPerDimension,
                                                                                tier);

        Assert.assertEquals("invalid number of stacks", 9, splitStacks.size());

        final HierarchicalStack splitStack1 = splitStacks.get(1);

        checkStackId("invalid rough stack", roughTilesStackId, splitStack1.getRoughTilesStackId());
        checkStackId("invalid parent stack", roughTilesStackId, splitStack1.getParentTierStackId());
        checkStackId("invalid warp stack", warpTilesStackId, splitStack1.getWarpTilesStackId());

        Assert.assertEquals("invalid tier", tier, splitStack1.getTier());
        Assert.assertEquals("invalid row", 0, splitStack1.getTierRow());
        Assert.assertEquals("invalid column", 1, splitStack1.getTierColumn());
        Assert.assertEquals("invalid row count", 3, splitStack1.getTotalTierRowCount());
        Assert.assertEquals("invalid column count", 3, splitStack1.getTotalTierColumnCount());
        Assert.assertEquals("invalid scale", 0.662711681588, splitStack1.getScale(), DOUBLE_DELTA);

        // cellWidth = 4862, cellHeight = 6181
        final Bounds expectedSplitStack1Bounds = new Bounds(59816.0, 58314.0, 64678.0, 64495.0);
        checkBounds("invalid bounds", expectedSplitStack1Bounds, splitStack1.getFullScaleBounds());
    }

    private void checkStackId(final String message,
                              final StackId expected,
                              final StackId actual) {
        if (expected.compareTo(actual) != 0) {
            Assert.fail(message + ", expected " + expected + " but was " + actual);
        }
    }

    private void checkBounds(final String message,
                             final Bounds expected,
                             final Bounds actual) {
        Assert.assertEquals(message + " minX", expected.getMinX(), actual.getMinX(), DOUBLE_DELTA);
        Assert.assertEquals(message + " minY", expected.getMinY(), actual.getMinY(), DOUBLE_DELTA);
        Assert.assertEquals(message + " maxX", expected.getMaxX(), actual.getMaxX(), DOUBLE_DELTA);
        Assert.assertEquals(message + " maxY", expected.getMaxY(), actual.getMaxY(), DOUBLE_DELTA);
    }

    private final double DOUBLE_DELTA = 0.0001;
}