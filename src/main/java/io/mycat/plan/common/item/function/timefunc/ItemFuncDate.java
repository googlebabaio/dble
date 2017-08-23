package io.mycat.plan.common.item.function.timefunc;

import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.function.ItemFunc;
import io.mycat.plan.common.time.MySQLTime;

import java.util.List;


public class ItemFuncDate extends ItemDateFunc {

    public ItemFuncDate(List<Item> args) {
        super(args);
    }

    @Override
    public final String funcName() {
        return "date";
    }

    @Override
    public boolean getDate(MySQLTime ltime, long fuzzyDate) {
        if (nullValue = args.get(0).nullValue) {
            return true;
        }
        nullValue = getArg0Date(ltime, fuzzyDate);
        return nullValue;
    }

    @Override
    public ItemFunc nativeConstruct(List<Item> realArgs) {
        return new ItemFuncDate(realArgs);
    }
}
