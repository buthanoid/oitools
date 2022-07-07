/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oitools.processing;

import fr.jmmc.oitools.fits.FitsTable;
import java.util.List;
import java.util.logging.Logger;

/**
 * Abstract Filter on any FitsTable based on 1 column and a list of accepted values
 * @param <K> type of accepted values
 */
public abstract class FitsTableFilter<K> {

    /** logger */
    protected final static Logger logger = Logger.getLogger(FitsTableFilter.class.getName());

    public enum FilterState {
        INVALID,
        MASK,
        FULL
    }

    // members:
    protected final String columnName;
    protected final List<K> acceptedValues;

    FitsTableFilter(final String columnName, final List<K> acceptedValues) {
        this.columnName = columnName;
        this.acceptedValues = acceptedValues;
    }

    void reset() {
        // no-op by default
    }

    public abstract FilterState prepare(final FitsTable fitsTable);

    public abstract boolean accept(final int row, final int col);

    public String getColumnName() {
        return columnName;
    }

    public List<K> getAcceptedValues() {
        return acceptedValues;
    }

    public boolean is2D() {
        return false; // by default
    }

    @Override
    public String toString() {
        return "FitsTableFilter{" + "columnName=" + columnName + ", acceptedValues=" + acceptedValues
                + ", is2D= " + is2D() + '}';
    }

}
