package org.janelia.alignment.transform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.Point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.Cursor;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RealCursor;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.Sampler;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.composite.RealComposite;

/**
 * Utility for building an {@link AffineWarpField} of arbitrary (specified) resolution from consensus match point sets.
 *
 * The {@link #build} method uses Stephan Saalfeld's
 * <a href="https://github.com/axtimwalde/experiments/blob/master/src/main/java/mpicbg/ij/plugin/SIFT_ExtractMultiplePointRois.java#L174-L188">
 *     nearest neighbor logic
 * </a>
 * to assign affine parameters to warp field cells.
 *
 * The {@link #toIndexGridString} method provides an "ASCII Voronoi diagram" of the mapped consensus sets.
 *
 * @author Eric Trautman
 */
public class ConsensusWarpFieldBuilder {

    private final double width;
    private final double height;
    private final int rowCount;
    private final int columnCount;

    private final double pixelsPerRow;
    private final double pixelsPerColumn;
    private final List<Affine2D> consensusSetModelList;
    private final RealPointSampleList<ARGBType> consensusSetIndexSamples;

    /**
     * Sets up a field with the specified dimensions.
     *
     * @param  width                pixel width of the warp field.
     * @param  height               pixel height of the warp field.
     * @param  rowCount             number of affine rows in the warp field.
     * @param  columnCount          number of affine columns in the warp field.
     */
    public ConsensusWarpFieldBuilder(final double width,
                                     final double height,
                                     final int rowCount,
                                     final int columnCount) {
        this.width = width;
        this.height = height;
        this.rowCount = rowCount;
        this.columnCount = columnCount;

        this.pixelsPerRow = height / rowCount;
        this.pixelsPerColumn = width / columnCount;
        this.consensusSetModelList = new ArrayList<>();
        this.consensusSetIndexSamples = new RealPointSampleList<>(2);
    }

    /**
     * @return number of cells in the warp field grid.
     */
    public int getNumberOfCells() {
        return rowCount * columnCount;
    }

    /**
     * @return number of consensus sets remaining in the grid after nearest neighbor analysis is completed.
     */
    public int getNumberOfConsensusSetsInGrid() {
        return getNumberOfConsensusSets(buildModelIndexGrid());
    }

    /**
     * Adds the specified consensus set data to this builder so that it can be
     * used by the {@link #build} method later.
     *
     * @param  alignmentModel  alignment model for this consensus point set.
     * @param  points          set of points associated with the model.
     */
    public void addConsensusSetData(final Affine2D alignmentModel,
                                    final List<Point> points) {

        final ARGBType consensusSetIndex = new ARGBType(consensusSetModelList.size());
        consensusSetModelList.add(alignmentModel);

        double[] local;
        double x;
        double y;
        for (final Point point : points) {

            local = point.getL();
            x = local[0] / pixelsPerColumn;
            y = local[1] / pixelsPerRow;

            consensusSetIndexSamples.add(new RealPoint(x, y), consensusSetIndex);
        }

    }

    /**
     * @return a warp field built from this builder's consensus set data.
     *         The returned field will utilize the default interpolator factory.
     */
    public AffineWarpField build() {
        return build(AffineWarpField.getDefaultInterpolatorFactory());
    }

    /**
     * @param  interpolatorFactory  factory to include in the returned warp field.
     *
     * @return a warp field built from this builder's consensus set data.
     *         The returned field will utilize the specified interpolator factory.
     */
    public AffineWarpField build(final InterpolatorFactory<RealComposite<DoubleType>, RandomAccessible<RealComposite<DoubleType>>> interpolatorFactory) {

        final int[] modelIndexGrid = buildModelIndexGrid();

        final AffineWarpField affineWarpField =
                new AffineWarpField(width, height, rowCount, columnCount, interpolatorFactory);

        for (int row = 0; row < rowCount; row++) {
            for (int column = 0; column < columnCount; column++) {

                final int gridIndex = (row * rowCount) + column;
                final int modelIndex = modelIndexGrid[gridIndex];
                final Affine2D model = consensusSetModelList.get(modelIndex);

                final double[] affineMatrixElements = new double[6];
                model.toArray(affineMatrixElements);

                affineWarpField.set(row, column, affineMatrixElements);
            }
        }

        return affineWarpField;
    }

    /**
     * @return ASCII Voronoi diagram of the consensus set indexes for each warp field cell.
     */
    public String toIndexGridString() {

        final int numberOfCells = getNumberOfCells();
        final int[] modelIndexGrid = buildModelIndexGrid();

        final int indexPlusSpaceWidth = String.valueOf(consensusSetModelList.size() - 1).length() + 1;
        final String format = "%" + indexPlusSpaceWidth + "d";
        final StringBuilder sb = new StringBuilder();

        sb.append(rowCount).append('x').append(columnCount).append(" grid with ");
        sb.append(getNumberOfConsensusSets(modelIndexGrid)).append(" distinct sets:\n");

        for (int i = 0; i < numberOfCells; i++) {
            if (i % columnCount == 0) {
                sb.append('\n');
            }
            sb.append(String.format(format, modelIndexGrid[i]));
        }

        return sb.toString();
    }

    public ConsensusWarpFieldBuilder mergeBuilders(final ConsensusWarpFieldBuilder otherBuilder) {

        validateConsistency("rowCount", rowCount, otherBuilder.rowCount);
        validateConsistency("columnCount", columnCount, otherBuilder.columnCount);
        validateConsistency("width", width, otherBuilder.width);
        validateConsistency("height", height, otherBuilder.height);

        final Map<Integer, List<Point>> cellToPointsMap = new LinkedHashMap<>();
        mapCellsToPoints(cellToPointsMap, consensusSetIndexSamples.cursor());
        mapCellsToPoints(cellToPointsMap, otherBuilder.consensusSetIndexSamples.cursor());

        final int[] modelIndexGrid = buildModelIndexGrid();
        final int[] otherModelIndexGrid = otherBuilder.buildModelIndexGrid();

        LOG.info("mergeBuilder: mapped points to {} cells", cellToPointsMap.size());

        final Map<String, List<Point>> setPairToPointsMap = new LinkedHashMap<>();
        List<Point> pointList;
        for (int i = 0; i < modelIndexGrid.length; i++) {
            final String setPair = modelIndexGrid[i] + "::" + otherModelIndexGrid[i];
            pointList = setPairToPointsMap.get(setPair);
            if (pointList == null) {
                pointList = new ArrayList<>();
                setPairToPointsMap.put(setPair, pointList);
            }
            final List<Point> cellPoints = cellToPointsMap.get(i);
            if (cellPoints != null) {
                pointList.addAll(cellPoints);
            }
        }

        LOG.info("mergeBuilder: merged result contains {} consensus sets", setPairToPointsMap.size());

        final ConsensusWarpFieldBuilder mergedBuilder =
                new ConsensusWarpFieldBuilder(width, height, rowCount, columnCount);

        for (final List<Point> setPoints : setPairToPointsMap.values()) {
            mergedBuilder.addConsensusSetData(new AffineModel2D(), setPoints);
        }

        return mergedBuilder;
    }

    private void mapCellsToPoints(final Map<Integer, List<Point>> cellToPointsMap,
                                  final RealCursor<ARGBType> cursor) {

        List<Point> pointList;

        while (cursor.hasNext()) {

            cursor.fwd();

            final double x = cursor.getDoublePosition(0) * pixelsPerColumn;
            final double y = cursor.getDoublePosition(1) * pixelsPerRow;
            final int row = (int) ((y / height) * rowCount);
            final int column = (int) ((x / width) * columnCount);
            final int gridIndex = (row * rowCount) + column;

            pointList = cellToPointsMap.get(gridIndex);
            if (pointList == null) {
                pointList = new ArrayList<>();
                cellToPointsMap.put(gridIndex, pointList);
            }

            pointList.add(new Point(new double[] {x, y}));
        }
    }

    private void validateConsistency(final String context,
                                     final Object expectedValue,
                                     final Object actualValue)
            throws IllegalArgumentException {
        if (! expectedValue.equals(actualValue)) {
            throw new IllegalArgumentException(
                    context + " is inconsistent, expected " + expectedValue + " but was " + actualValue);
        }
    }

    private int[] buildModelIndexGrid() {

        final int[] targetCellIndexes = new int[getNumberOfCells()];

        final ArrayImg<ARGBType, IntArray> target = ArrayImgs.argbs(targetCellIndexes, columnCount, rowCount);
        final KDTree<ARGBType> kdTree = new KDTree<>(consensusSetIndexSamples);
        final NearestNeighborSearch<ARGBType> nnSearchSamples = new NearestNeighborSearchOnKDTree<>(kdTree);

        final Cursor<ARGBType> targetCursor = target.localizingCursor();

        Sampler<ARGBType> sampler;
        ARGBType sampleItem;
        ARGBType targetItem;
        while (targetCursor.hasNext()) {

            targetCursor.fwd();
            nnSearchSamples.search(targetCursor);
            sampler = nnSearchSamples.getSampler();

            sampleItem = sampler.get();
            targetItem = targetCursor.get();
            targetItem.set(sampleItem);
        }

        return targetCellIndexes;
    }

    private int getNumberOfConsensusSets(final int[] modelIndexGrid) {
        final Set<Integer> distinctModelIndexes = new HashSet<>();
        for (final int modelIndex : modelIndexGrid) {
            distinctModelIndexes.add(modelIndex);
        }
        return distinctModelIndexes.size();

    }

    private static final Logger LOG = LoggerFactory.getLogger(ConsensusWarpFieldBuilder.class);

}
