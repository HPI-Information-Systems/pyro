package de.hpi.isg.pyro.model;

import de.hpi.isg.mdms.domain.constraints.PartialFunctionalDependency;
import de.hpi.isg.mdms.model.targets.Table;
import de.hpi.isg.mdms.model.util.IdUtils;
import de.metanome.algorithm_integration.results.FunctionalDependency;
import de.metanome.algorithm_integration.results.RelaxedFunctionalDependency;

import java.util.Comparator;

/**
 * This class models a partial functional dependencies.
 */
public class PartialFD {

    public final double error;

    public final Vertical lhs;

    public final Column rhs;

    public double score;

    public PartialFD(Vertical lhs, Column rhs, double error) {
        this(lhs, rhs, error, Double.NaN);
    }

    public PartialFD(Vertical lhs, Column rhs, double error, double score) {
        this.error = error;
        this.lhs = lhs;
        this.rhs = rhs;
        this.score = score;
    }

    public static Comparator<PartialFD> scoreComparator = Comparator.comparing(fd -> fd.score);


    @Override
    public String toString() {
        return String.format("%s~>%s (err=%.3f, scr=%.2f)",
                this.lhs, this.rhs, this.error, this.score
        );
    }

    public double getError() {
        return error;
    }

    public int getArity() {
        return lhs.getColumns().length;
    }

    /**
     * Convert this instance into a {@link de.metanome.algorithm_integration.results.FunctionalDependency}.
     *
     * @return the converted instance
     */
    public FunctionalDependency toMetanomeFunctionalDependency() {
        return new FunctionalDependency(
                this.lhs.toMetanomeColumnCombination(),
                this.rhs.toMetanomeColumnIdentifier()
        );
    }

    public RelaxedFunctionalDependency toMetanomeReFunctionalDependency() {
        return new RelaxedFunctionalDependency(
                this.lhs.toMetanomeColumnCombination(),
                this.rhs.toMetanomeColumnIdentifier(),
                score
        );
    }

    /**
     * Converts this instance into a {@link PartialFunctionalDependency}.
     *
     * @param idUtils to resolve IDs for the {@link PartialFunctionalDependency}
     * @param table   in which the {@link PartialFunctionalDependency} resides
     * @return the {@link PartialFunctionalDependency}
     */
    public PartialFunctionalDependency toPartialFunctionalDependency(IdUtils idUtils, Table table) {
        int localSchemaId = idUtils.getLocalSchemaId(table.getId());
        int localTableId = idUtils.getLocalTableId(table.getId());
        int[] lhs = new int[this.lhs.getArity()];
        for (int index = this.lhs.getColumnIndices().nextSetBit(0), i = 0;
             index != -1;
             index = this.lhs.getColumnIndices().nextSetBit(index + 1), i++) {
            lhs[i] = idUtils.createGlobalId(localSchemaId, localTableId, index + idUtils.getMinColumnNumber());
        }
        int rhs = idUtils.createGlobalId(localSchemaId, localTableId, this.rhs.getIndex() + idUtils.getMinColumnNumber());
        return new PartialFunctionalDependency(lhs, rhs, this.error, this.score);
    }
}
