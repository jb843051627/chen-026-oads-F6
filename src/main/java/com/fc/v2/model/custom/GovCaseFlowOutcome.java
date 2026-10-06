package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TOadsGovCase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 监管办件一次动作（送意见/推进/后退/收尾）落库后的交代。
 *
 * <p>这一笔落不落、整件停在哪一步、是推进还是封存、跨步丢弃还是并发幂等命中，
 * 都由服务端按步序留痕点出，不由报文自称。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "GovCaseFlowOutcome", description = "监管办件动作结果")
public class GovCaseFlowOutcome implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 意见/见证已落在当前步，但步还没走完，整件留在本步 */
    public static final int LANDED = 0;
    /** 准入满足，整件推进到紧邻下一步 */
    public static final int ADVANCED = 1;
    /** 后退一步：当轮转留痕尾部，重走重新累计 */
    public static final int RETURNED = 2;
    /** 验收走完封存 */
    public static final int SEALED = 3;
    /** 收尾达成（撤回报备/划归邻段/作废） */
    public static final int CLOSED = 4;
    /** 跨步/步次对不上：一笔不落，整件留在原地 */
    public static final int DISCARDED = 5;
    /** 并发幂等命中：认出是同一笔，不重复落、不丢笔 */
    public static final int DEDUPED = 6;

    @ApiModelProperty(value = "结果 0落步 1推进 2后退 3封存 4收尾 5丢弃 6幂等")
    private int outcome;

    @ApiModelProperty(value = "服务端交代原话")
    private String message;

    @ApiModelProperty(value = "处理后的办件（步次为服务端按留痕重算值）")
    private TOadsGovCase govCase;

    public static GovCaseFlowOutcome of(int outcome, String message, TOadsGovCase govCase) {
        GovCaseFlowOutcome r = new GovCaseFlowOutcome();
        r.outcome = outcome;
        r.message = message;
        r.govCase = govCase;
        return r;
    }

    public int getOutcome() {
        return outcome;
    }

    public void setOutcome(int outcome) {
        this.outcome = outcome;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TOadsGovCase getGovCase() {
        return govCase;
    }

    public void setGovCase(TOadsGovCase govCase) {
        this.govCase = govCase;
    }
}
