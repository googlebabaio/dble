package io.mycat.plan.common.item.function.sumfunc;

import io.mycat.net.mysql.RowDataPacket;
import io.mycat.plan.PlanNode;
import io.mycat.plan.common.context.NameResolutionContext;
import io.mycat.plan.common.context.ReferContext;
import io.mycat.plan.common.external.ResultStore;
import io.mycat.plan.common.field.Field;
import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.ItemResultField;
import io.mycat.plan.common.item.function.sumfunc.Aggregator.AggregatorType;
import io.mycat.plan.common.ptr.DoublePtr;
import io.mycat.plan.common.ptr.LongPtr;
import io.mycat.plan.util.PlanUtil;
import org.apache.commons.lang.StringUtils;

import java.util.List;


public abstract class ItemSum extends ItemResultField {
    /* 行fields集合 */
    protected List<Field> sourceFields;
    public boolean isPushDown;

    public static final int FALSE = 0;
    public static final int TRUE = 1;

    /**
     * Aggregator class instance. Not set initially. Allocated only after it is
     * determined if the incoming data are already distinct.
     */
    protected Aggregator aggr;

    /**
     * Indicates how the aggregate function was specified by the parser : 1 if
     * it was written as AGGREGATE(DISTINCT), 0 if it was AGGREGATE()
     */
    private boolean withDistinct = false;

    public final int getArgCount() {
        if (args == null)
            return 0;
        return args.size();
    }

    public List<Item> arguments() {
        return this.args;
    }

    public final boolean hasWithDistinct() {
        return withDistinct;
    }

    public enum Sumfunctype {
        COUNT_FUNC, COUNT_DISTINCT_FUNC, SUM_FUNC, SUM_DISTINCT_FUNC, AVG_FUNC, AVG_DISTINCT_FUNC, MIN_FUNC, MAX_FUNC, STD_FUNC, VARIANCE_FUNC, SUM_BIT_FUNC, UDF_SUM_FUNC, GROUP_CONCAT_FUNC
    }

    ;

    public void markAsSumFunc() {
        withSumFunc = true;
    }

    protected List<Item> args;

    public ItemSum(List<Item> args, boolean isPushDown, List<Field> fields) {
        this.isPushDown = isPushDown;
        this.args = args;
        this.sourceFields = fields;
        markAsSumFunc();
        initAggregator();
    }

    public final ItemType type() {
        return ItemType.SUM_FUNC_ITEM;
    }

    public abstract Sumfunctype sumType();

    @Override
    public int hashCode() {
        int prime = 10;
        int hashCode = funcName().hashCode();
        hashCode = hashCode * prime;
        for (int index = 0; index < getArgCount(); index++) {
            hashCode += args.get(index).hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof ItemSum))
            return false;
        ItemSum other = (ItemSum) obj;
        if (!sumType().equals(other.sumType()))
            return false;
        if (getArgCount() != other.getArgCount())
            return false;
        return StringUtils.equals(getItemName(), other.getItemName());
    }

    /**
     * Resets the aggregate value to its default and aggregates the current
     * value of its attribute(s).
     */
    public boolean resetAndAdd(RowDataPacket row, Object transObj) {
        aggregatorClear();
        return aggregatorAdd(row, transObj);
    }

    ;

    /**
     * 局部聚合生成的中间结果(transitional)
     *
     * @return notice:不能返回null，因为Group By的第一条数据的transAggObj是null，
     * 如果聚合过的也返回null的话，将无法区分是聚合过的还是第一条数据
     */
    public abstract Object getTransAggObj();

    /**
     * 局部聚合结果的byte数组的预估大小
     *
     * @return
     */
    public abstract int getTransSize();

    public void fixLengthAndDec() {
        maybeNull = true;
        nullValue = true;
    }

    @Override
    public boolean isNull() {
        return nullValue;
    }

    public void fixNumLengthAndDec() {
        decimals = 0;
        for (int i = 0; i < getArgCount(); i++)
            decimals = Math.max(decimals, args.get(i).decimals);
        maxLength = floatLength(decimals);
    }

    /**
     * Mark an aggregate as having no rows.
     * <p>
     * This function is called by the execution engine to assign 'NO ROWS FOUND'
     * value to an aggregate item, when the underlying result set has no rows.
     * Such value, in a general case, may be different from the default value of
     * the item after 'clear()': e.g. a numeric item may be initialized to 0 by
     * clear() and to NULL by no_rows_in_result().
     */
    public void noRowsInResult() {
        setAggregator(withDistinct ? AggregatorType.DISTINCT_AGGREGATOR : AggregatorType.SIMPLE_AGGREGATOR, null);
        aggregatorClear();
    }

    public Item getArg(int i) {
        return args.get(i);
    }

    public Item setArg(int i, Item newVal) {
        args.set(i, newVal);
        return newVal;
    }

    /* Initialization of distinct related members */
    public void initAggregator() {
        aggr = null;
        withDistinct = false;
    }

    /**
     * Called to initialize the aggregator.
     */

    public boolean aggregatorSetup() {
        return aggr.setup();
    }

    /**
     * Called to cleanup the aggregator.
     */

    public void aggregatorClear() {
        aggr.clear();
    }

    /**
     * Called to add value to the aggregator.
     */

    public boolean aggregatorAdd(RowDataPacket row, Object transObj) {
        return aggr.add(row, transObj);
    }

    ;

    /* stores the declared DISTINCT flag (from the parser) */
    public void setDistinct(boolean distinct) {
        withDistinct = distinct;
    }

    /*
     * Set the type of aggregation : DISTINCT or not.
     *
     * May be called multiple times.
     */

    public int setAggregator(AggregatorType aggregator, ResultStore store) {
        /*
         * Dependent subselects may be executed multiple times, making
         * set_aggregator to be called multiple times. The aggregator type will
         * be the same, but it needs to be reset so that it is reevaluated with
         * the new dependent data. This function may also be called multiple
         * times during query optimization. In this case, the type may change,
         * so we delete the old aggregator, and create a new one.
         */
        if (aggr != null && aggregator == aggr.aggrType()) {
            aggr.clear();
            return FALSE;
        }

        aggr = null;
        if (aggregator == AggregatorType.DISTINCT_AGGREGATOR) {
            aggr = new AggregatorDistinct(this, store);

        } else if (aggregator == AggregatorType.SIMPLE_AGGREGATOR) {
            aggr = new AggregatorSimple(this);

        }
        ;
        return aggr != null ? FALSE : TRUE;
    }

    public abstract void clear();

    /**
     * 函数不下发时候的add方法，和mysql原生代码基本一致
     *
     * @return
     */
    protected abstract boolean add(RowDataPacket row, Object transObj);

    /**
     * 聚合函数被下发时的add方法
     *
     * @return
     */
    protected abstract boolean pushDownAdd(RowDataPacket row);

    public boolean setup() {
        return false;
    }

    /**
     * Variance implementation for floating-point implementations, without
     * catastrophic cancellation, from Knuth's _TAoCP_, 3rd ed, volume 2, pg232.
     * This alters the value at m, s, and increments count.
     */

    /*
     * These two functions are used by the Item_sum_variance and the
     * Item_variance_field classes, which are unrelated, and each need to
     * calculate variance. The difference between the two classes is that the
     * first is used for a mundane SELECT, while the latter is used in a
     * GROUPing SELECT.
     */
    protected static void varianceFpRecurrenceNext(DoublePtr m, DoublePtr s, LongPtr count, double nr) {
        count.incre();

        if (count.get() == 1) {
            m.set(nr);
            s.set(0);
        } else {
            double kMinusOne = m.get();
            m.set(kMinusOne + (nr - kMinusOne) / (double) count.get());
            s.set(s.get() + (nr - kMinusOne) * (nr - m.get()));
        }
    }

    protected static double varianceFpRecurrenceResult(double s, long count, boolean isSampleVariance) {
        if (count == 1)
            return 0.0;

        if (isSampleVariance)
            return s / (count - 1);

        /* else, is a population variance */
        return s / count;
    }

    @Override
    public final ItemSum fixFields(NameResolutionContext context) {
        getReferTables().clear();
        for (int index = 0; index < getArgCount(); index++) {
            Item arg = args.get(index);
            Item fixedArg = arg.fixFields(context);
            if (fixedArg == null)
                return null;
            args.set(index, fixedArg);
            getReferTables().addAll(fixedArg.getReferTables());
            withIsNull = withIsNull || fixedArg.withIsNull;
            withSubQuery = withSubQuery || fixedArg.withSubQuery;
        }
        return this;
    }

    @Override
    public final void fixRefer(ReferContext context) {
        PlanNode planNode = context.getPlanNode();
        planNode.addSelToReferedMap(planNode, this);
        boolean needAddArgToRefer = true;
        if (context.isPushDownNode() && !planNode.existUnPushDownGroup()) {
            boolean isUnpushSum = PlanUtil.isUnPushDownSum(this);
            if (isUnpushSum) {// 这个函数是非下推函数
                planNode.setExistUnPushDownGroup(true);
                needAddArgToRefer = true;
                // 补上sunfuncs里面的arg参数
                for (ItemSum sumfunc : planNode.sumFuncs) {
                    for (Item sumArg : sumfunc.args) {
                        sumArg.fixRefer(context);
                    }
                }
            } else {
                needAddArgToRefer = false;
            }
        }
        if (needAddArgToRefer) {
            for (Item arg : this.args) {
                arg.fixRefer(context);
            }
        }
        planNode.sumFuncs.add(this);
    }

}
