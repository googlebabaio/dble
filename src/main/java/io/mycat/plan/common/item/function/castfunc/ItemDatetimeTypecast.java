package io.mycat.plan.common.item.function.castfunc;

import com.alibaba.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCastExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import io.mycat.plan.common.field.Field;
import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.function.timefunc.ItemDatetimeFunc;
import io.mycat.plan.common.time.MySQLTime;
import io.mycat.plan.common.time.MySQLTimestampType;
import io.mycat.plan.common.time.MyTime;

import java.util.ArrayList;
import java.util.List;


public class ItemDatetimeTypecast extends ItemDatetimeFunc {
    public ItemDatetimeTypecast(Item a) {
        super(new ArrayList<Item>());
        args.add(a);
    }

    public ItemDatetimeTypecast(Item a, int decArg) {
        super(new ArrayList<Item>());
        args.add(a);
        this.decimals = decArg;
    }

    @Override
    public final String funcName() {
        return "cast_as_datetime";
    }

    @Override
    public void fixLengthAndDec() {
        maybeNull = true;
    }

    @Override
    public boolean getDate(MySQLTime ltime, long fuzzyDate) {
        if ((nullValue = args.get(0).getDate(ltime, fuzzyDate | MyTime.TIME_NO_DATE_FRAC_WARN)))
            return true;
        assert (ltime.timeType != MySQLTimestampType.MYSQL_TIMESTAMP_TIME);
        ltime.timeType = MySQLTimestampType.MYSQL_TIMESTAMP_DATETIME; // In
        // case
        // it
        // was
        // DATE
        return (nullValue = MyTime.myDatetimeRound(ltime, decimals));
    }

    @Override
    public SQLExpr toExpression() {
        SQLCastExpr cast = new SQLCastExpr();
        cast.setExpr(args.get(0).toExpression());
        SQLDataTypeImpl dataType = new SQLDataTypeImpl("DATETIME");
        if (decimals != NOT_FIXED_DEC) {
            dataType.addArgument(new SQLIntegerExpr(decimals));
        }
        cast.setDataType(dataType);
        return cast;
    }

    @Override
    protected Item cloneStruct(boolean forCalculate, List<Item> calArgs, boolean isPushDown, List<Field> fields) {
        List<Item> newArgs = null;
        if (!forCalculate)
            newArgs = cloneStructList(args);
        else
            newArgs = calArgs;
        return new ItemDatetimeTypecast(newArgs.get(0), this.decimals);
    }
}
