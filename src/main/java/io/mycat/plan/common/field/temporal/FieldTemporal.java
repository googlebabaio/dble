package io.mycat.plan.common.field.temporal;

import io.mycat.plan.common.field.Field;
import io.mycat.plan.common.item.Item.ItemResult;
import io.mycat.plan.common.time.MySQLTime;

import java.math.BigDecimal;

/**
 * Abstract class for TIME, DATE, DATETIME, TIMESTAMP with and without
 * fractional part.
 *
 * @author ActionTech
 */
public abstract class FieldTemporal extends Field {
    protected MySQLTime ltime = new MySQLTime();

    public FieldTemporal(String name, String table, int charsetIndex, int fieldLength, int decimals, long flags) {
        super(name, table, charsetIndex, fieldLength, decimals, flags);
    }

    @Override
    public ItemResult resultType() {
        return ItemResult.STRING_RESULT;
    }

    @Override
    public ItemResult cmpType() {
        return ItemResult.INT_RESULT;
    }

    @Override
    public BigDecimal valReal() {
        return new BigDecimal(valInt());
    }

    @Override
    public BigDecimal valDecimal() {
        return isNull() ? null : new BigDecimal(valInt());
    }

    @Override
    public int compareTo(final Field other) {
        if (other == null || !(other instanceof FieldTemporal))
            return 1;
        FieldTemporal other2 = (FieldTemporal) other;
        this.internalJob();
        other2.internalJob();
        MySQLTime ltime2 = other2.ltime;
        return ltime.getCompareResult(ltime2);
    }

}
