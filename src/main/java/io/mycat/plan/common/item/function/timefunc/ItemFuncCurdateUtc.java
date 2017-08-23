package io.mycat.plan.common.item.function.timefunc;

import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.function.ItemFunc;
import io.mycat.plan.common.time.MySQLTime;

import java.util.List;


public class ItemFuncCurdateUtc extends ItemDateFunc {

    public ItemFuncCurdateUtc(List<Item> args) {
        super(args);
    }

    @Override
    public final String funcName() {
        return "utc_date";
    }

    @Override
    public boolean getDate(MySQLTime ltime, long fuzzyDate) {
        java.util.Calendar cal = getUTCTime();
        ltime.year = cal.get(java.util.Calendar.YEAR);
        ltime.month = cal.get(java.util.Calendar.MONTH) + 1;
        ltime.day = cal.get(java.util.Calendar.DAY_OF_MONTH) + 1;
        ltime.hour = ltime.minute = ltime.second = ltime.secondPart = 0;
        return false;
    }

    @Override
    public ItemFunc nativeConstruct(List<Item> realArgs) {
        return new ItemFuncCurdateUtc(realArgs);
    }
}
