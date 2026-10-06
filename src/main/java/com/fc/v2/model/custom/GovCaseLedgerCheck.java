package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 监管办件台账对账结果。
 *
 * <p>页面顶部“尚未走完”那个数与逐件按留痕重数的结果出自同一次后端查询/计算；
 * 不取前端缓存值，也不在别处另算一套。两边一旦对不上，这里把对应办件单号与毛病逐一指名。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "GovCaseLedgerCheck", description = "监管办件台账对账结果")
public class GovCaseLedgerCheck implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 账面尚未走完数（status=1 一次查询） */
    @ApiModelProperty(value = "账面尚未走完数")
    private long openCount;

    /** 逐件按留痕重算应在办的数量 */
    @ApiModelProperty(value = "留痕重算的在办数")
    private long recountedCount;

    /** 两边是否一致 */
    @ApiModelProperty(value = "是否账账相符")
    private boolean balanced;

    /** 对不上的办件：单号加毛病说明 */
    @ApiModelProperty(value = "对不上的办件指名")
    private List<String> mismatches = new ArrayList<String>();

    public long getOpenCount() {
        return openCount;
    }

    public void setOpenCount(long openCount) {
        this.openCount = openCount;
    }

    public long getRecountedCount() {
        return recountedCount;
    }

    public void setRecountedCount(long recountedCount) {
        this.recountedCount = recountedCount;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public List<String> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<String> mismatches) {
        this.mismatches = mismatches;
    }
}
