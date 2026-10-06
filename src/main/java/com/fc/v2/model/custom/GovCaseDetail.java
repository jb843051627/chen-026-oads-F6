package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TOadsGovCase;
import com.fc.v2.model.auto.TOadsGovCaseFlowLog;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条监管办件的整件回看：办件本头＋按步分节＋一句一句按时序压在本件后面的留痕串。
 *
 * <p>当前停在哪一步、下一步准入满没满，由服务端按留痕重算后给；封存行照常组装，
 * 但不给任何操作入口（只查）。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "GovCaseDetail", description = "监管办件整件回看")
public class GovCaseDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 办件本头 */
    @ApiModelProperty(value = "办件本头")
    private TOadsGovCase govCase;

    /** 按步分节（四步固定） */
    @ApiModelProperty(value = "按步分节")
    private List<Step> steps = new ArrayList<Step>();

    /** 时序留痕串（每步那句意见＋提交时刻，含后退退出的老轮） */
    @ApiModelProperty(value = "时序留痕串")
    private List<TOadsGovCaseFlowLog> timeline = new ArrayList<TOadsGovCaseFlowLog>();

    /** 是否封存（封存只查不写，页面不给操作入口） */
    @ApiModelProperty(value = "是否封存")
    private boolean sealed;

    /** 是否收尾（撤回/划转/作废，也不再接动作） */
    @ApiModelProperty(value = "是否收尾")
    private boolean closed;

    public TOadsGovCase getGovCase() {
        return govCase;
    }

    public void setGovCase(TOadsGovCase govCase) {
        this.govCase = govCase;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public List<TOadsGovCaseFlowLog> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TOadsGovCaseFlowLog> timeline) {
        this.timeline = timeline;
    }

    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    /** 一步一节：步名、是否当前、本轮在档落笔、进入下一步的准入原话。 */
    @ApiModel(value = "GovCaseDetail.Step", description = "办件一步一节")
    public static class Step implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "步号 1..4")
        private int stepNo;

        @ApiModelProperty(value = "步名")
        private String stepName;

        @ApiModelProperty(value = "是否已走到")
        private boolean reached;

        @ApiModelProperty(value = "是否当前停在这一步")
        private boolean current;

        @ApiModelProperty(value = "当前轮次")
        private int roundNo;

        @ApiModelProperty(value = "本轮在档落笔数")
        private int landedCount;

        @ApiModelProperty(value = "进入这一步的准入原话")
        private String entryRule;

        @ApiModelProperty(value = "本轮在档落笔")
        private List<TOadsGovCaseFlowLog> logs = new ArrayList<TOadsGovCaseFlowLog>();

        public int getStepNo() {
            return stepNo;
        }

        public void setStepNo(int stepNo) {
            this.stepNo = stepNo;
        }

        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        public boolean isReached() {
            return reached;
        }

        public void setReached(boolean reached) {
            this.reached = reached;
        }

        public boolean isCurrent() {
            return current;
        }

        public void setCurrent(boolean current) {
            this.current = current;
        }

        public int getRoundNo() {
            return roundNo;
        }

        public void setRoundNo(int roundNo) {
            this.roundNo = roundNo;
        }

        public int getLandedCount() {
            return landedCount;
        }

        public void setLandedCount(int landedCount) {
            this.landedCount = landedCount;
        }

        public String getEntryRule() {
            return entryRule;
        }

        public void setEntryRule(String entryRule) {
            this.entryRule = entryRule;
        }

        public List<TOadsGovCaseFlowLog> getLogs() {
            return logs;
        }

        public void setLogs(List<TOadsGovCaseFlowLog> logs) {
            this.logs = logs;
        }
    }
}
