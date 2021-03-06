package de.hpi.isg.pyro.core;

import de.hpi.isg.pyro.model.Column;
import de.hpi.isg.pyro.model.Vertical;
import de.hpi.isg.pyro.util.AgreeSetSample;
import de.hpi.isg.pyro.util.ConfidenceInterval;
import de.hpi.isg.pyro.util.PartialFdScoring;
import de.hpi.isg.pyro.util.PositionListIndex;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import static de.hpi.isg.pyro.util.PositionListIndex.singletonValueId;

/**
 * {@link DependencyStrategy} implementation for partial FDs.
 */
public class FdG1Strategy extends DependencyStrategy {

    private final Column rhs;

    public FdG1Strategy(Column rhs, double maxError, double deviation) {
        super(maxError, deviation);
        this.rhs = rhs;
    }

    @Override
    synchronized public void ensureInitialized(SearchSpace searchSpace) {
        // We better do this thread-safe just in case.
        if (searchSpace.isInitialized) return;

        // We only add a candidate for the 0-ary FD []->RHS.
        final long startNanos = System.nanoTime();
        double zeroFdError = this.calculateError(this.context.relationData.getSchema().emptyVertical);
        this.context.profilingData.errorCalculationNanos.addAndGet(System.nanoTime() - startNanos);
        this.context.profilingData.numErrorCalculations.incrementAndGet();
        searchSpace.addLaunchPad(new DependencyCandidate(
                this.context.relationData.getSchema().emptyVertical,
                new ConfidenceInterval(zeroFdError),
                true
        ));

        searchSpace.isInitialized = true;
    }

    @Override
    double calculateError(Vertical lhs) {
        final long startNanos = System.nanoTime();
        final double error;
        // Special case: Check 0-ary FD.
        if (lhs.getArity() == 0) {
            PositionListIndex rhsPli = this.context.pliCache.get(this.rhs);
            assert rhsPli != null;
            error = this.calculateG1(rhsPli.getNip());
        } else {
            PositionListIndex lhsPli = this.context.pliCache.getOrCreateFor(lhs, this.context);
            PositionListIndex jointPli = this.context.pliCache.get(lhs.union(rhs));
            error = jointPli == null ? this.calculateG1(lhsPli) : this.calculateG1(lhsPli.getNepAsLong() - jointPli.getNepAsLong());
        }

        this.context.profilingData.errorCalculationNanos.addAndGet(System.nanoTime() - startNanos);
        this.context.profilingData.numErrorCalculations.incrementAndGet();
        return error;
    }

    private double calculateG1(PositionListIndex lhsPli) {
        long pliNanos = System.nanoTime();
        long numViolations = 0L;
        final Int2IntOpenHashMap valueCounts = new Int2IntOpenHashMap();
        valueCounts.defaultReturnValue(0);
        final int[] probingTable = this.context.relationData.getColumnData(this.rhs.getIndex()).getProbingTable();

        // Do the actual probing cluster by cluster.
        for (IntArrayList cluster : lhsPli.getIndex()) {
            valueCounts.clear();
            for (IntIterator iterator = cluster.iterator(); iterator.hasNext(); ) {

                // Probe the position.
                final int position = iterator.nextInt();
                final int probingTableValueId = probingTable[position];

                // Count the probed position if it is not a singleton.
                if (probingTableValueId != singletonValueId) {
                    valueCounts.addTo(probingTableValueId, 1);
                }
            }

            // Count the violations within the cluster.
            long numViolationsInCluster = cluster.size() * (cluster.size() - 1L) >> 1;
            ObjectIterator<Int2IntMap.Entry> valueCountIterator = valueCounts.int2IntEntrySet().fastIterator();
            while (valueCountIterator.hasNext()) {
                int refinedClusterSize = valueCountIterator.next().getIntValue();
                numViolationsInCluster -= refinedClusterSize * (refinedClusterSize - 1L) >> 1;
            }
            numViolations += numViolationsInCluster;
        }
        // TODO: Where is the profiling data?
//        pliNanos = System.nanoTime() - pliNanos;
//        this.context._profilingData.probingNanos.addAndGet(pliNanos);
//        this.context._profilingData.numProbings.incrementAndGet();

        return this.calculateG1(numViolations);
    }

    private double calculateG1(double numViolatingTuplePairs) {
        long numTuplePairs = this.context.relationData.getNumTuplePairs();
        if (numTuplePairs == 0) return 0d;
        double g1 = numViolatingTuplePairs / numTuplePairs;
        // We truncate some precision here to avoid small numerical flaws to affect the result.
        return PartialFdScoring.round(g1);
    }

    private ConfidenceInterval calculateG1(ConfidenceInterval numViolations) {
        return new ConfidenceInterval(
                this.calculateG1(numViolations.getMin()),
                this.calculateG1(numViolations.getMean()),
                this.calculateG1(numViolations.getMax())
        );
    }

    @Override
    DependencyCandidate createDependencyCandidate(Vertical vertical) {
        if (this.context.agreeSetSamples == null) {
            return new DependencyCandidate(vertical, new ConfidenceInterval(0, .5, 1), false);
        }

        // Find the best available correlation provider.
        final long startNanos = System.nanoTime();
        AgreeSetSample agreeSetSample = this.context.getAgreeSetSample(vertical);
        ConfidenceInterval numViolatingTuplePairs = agreeSetSample
                .estimateMixed(vertical, this.rhs, this.context.configuration.estimateConfidence)
                .multiply(this.context.relationData.getNumTuplePairs());
        ConfidenceInterval g1 = this.calculateG1(numViolatingTuplePairs);
        this.context.profilingData.errorEstimationNanos.addAndGet(System.nanoTime() - startNanos);
        this.context.profilingData.numErrorEstimations.incrementAndGet();
        return new DependencyCandidate(vertical, g1, false);
    }

    @Override
    String format(Vertical vertical) {
        return String.format("%s\u2192%s", vertical, this.rhs);
    }

    @Override
    void registerDependency(Vertical vertical, double error, DependencyConsumer discoveryUnit) {
        // TODO: Calculate score.
        this.context.profilingData.numDependencies.incrementAndGet();
        this.context.profilingData.dependencyArity.addAndGet(vertical.getArity());
        discoveryUnit.registerFd(vertical, this.rhs, error, this.context.rateFdScore(vertical, this.rhs));
    }

    @Override
    boolean isIrrelevantColumn(int columnIndex) {
        return this.rhs.getIndex() == columnIndex;
    }

    @Override
    int getNumIrrelevantColumns() {
        return 1;
    }

    @Override
    public Vertical getIrrelevantColumns() {
        return this.rhs;
    }

    @Override
    public String toString() {
        return String.format("FD[RHS=%s, g1\u2264(%.3f..%.3f)]", this.rhs.getName(), this.minNonDependencyError, this.maxDependencyError);
    }
}
