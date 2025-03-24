package de.hpi.isg.pyro.model;

import de.hpi.isg.mdms.domain.constraints.PartialUniqueColumnCombination;
import de.hpi.isg.mdms.model.targets.Table;
import de.hpi.isg.mdms.model.util.IdUtils;
import de.metanome.algorithm_integration.results.UniqueColumnCombination;
import de.metanome.algorithm_integration.results.RelaxedUniqueColumnCombination;

import java.util.Comparator;

/**
 * This class models a partial keys.
 */
public class PartialKey {

    public final double error;

    public final Vertical vertical;

    public double score;

    public PartialKey(Vertical vertical, double error) {
        this(vertical, error, Double.NaN);
    }

    public PartialKey(Vertical vertical, double error, double score) {
        this.vertical = vertical;
        this.error = error;
        this.score = score;
    }

    public static Comparator<PartialKey> scoreComparator = Comparator.comparing(fd -> fd.score);

    @Override
    public String toString() {
        return String.format("%s (err=%.2f, scr=%.2f)",
                this.vertical, this.error, this.score
        );
    }

    /**
     * Convert this instance to a {@link UniqueColumnCombination}.
     *
     * @return the converted instance
     */
    public UniqueColumnCombination toMetanomeUniqueColumnCobination() {
        return new UniqueColumnCombination(this.vertical.toMetanomeColumnCombination());
    }

    public RelaxedUniqueColumnCombination toMetanomeRelaxedUniqueColumnCobination() {
        return new RelaxedUniqueColumnCombination(this.vertical.toMetanomeColumnCombination(), this.score);
    }

    /**
     * Converts this instance into a {@link PartialUniqueColumnCombination}.
     *
     * @param idUtils to resolve IDs for the {@link PartialUniqueColumnCombination}
     * @param table   in which the {@link PartialUniqueColumnCombination} resides
     * @return the {@link PartialUniqueColumnCombination}
     */
    public PartialUniqueColumnCombination toPartialUniqueColumnCombination(IdUtils idUtils, Table table) {
        int localSchemaId = idUtils.getLocalSchemaId(table.getId());
        int localTableId = idUtils.getLocalTableId(table.getId());
        int[] columnIds = new int[this.vertical.getArity()];
        for (int index = this.vertical.getColumnIndices().nextSetBit(0), i = 0;
             index != -1;
             index = this.vertical.getColumnIndices().nextSetBit(index + 1), i++) {
            columnIds[i] = idUtils.createGlobalId(localSchemaId, localTableId, index + idUtils.getMinColumnNumber());
        }
        return new PartialUniqueColumnCombination(columnIds, this.error, this.score);
    }
}
