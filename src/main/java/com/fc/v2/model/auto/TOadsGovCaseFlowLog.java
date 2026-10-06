package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 监管办件步序留痕对象 t_oads_gov_case_flow_log（append-only）。
 *
 * <p>这一条办件停在哪一步由系统按本表在档 REACH 逐行重数，报文自称的步次不算数。
 * 后退只把被退那一步当轮在档行 active 置 0，行不删、留在时序尾部；重走另起一轮重新累计。
 * 同一时刻两条通道各送一笔：各记各的 seq/时刻，不并笔、不丢笔。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_oads_gov_case_flow_log")
@ApiModel(value = "TOadsGovCaseFlowLog", description = "监管办件步序留痕")
public class TOadsGovCaseFlowLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 哪一条监管办件 */
    @TableField("case_id")
    @ApiModelProperty(value = "哪一条监管办件")
    private Long caseId;

    /** 办件单号（落账当时冗余） */
    @TableField("biz_no")
    @ApiModelProperty(value = "办件单号")
    private String bizNo;

    /** 步序 1申报 2核准 3设置 4验收（服务端点出） */
    @TableField("step_no")
    @ApiModelProperty(value = "步序")
    private Integer stepNo;

    /** 该步轮次 后退一次加一轮 */
    @TableField("round_no")
    @ApiModelProperty(value = "该步轮次")
    private Integer roundNo;

    /** 本件内笔序（逐笔累加，同秒也不并笔） */
    @TableField("seq_no")
    @ApiModelProperty(value = "笔序")
    private Integer seqNo;

    /** 动作 REACH/OPINION/WITNESS/RETURN/CLOSE */
    @TableField("action")
    @ApiModelProperty(value = "动作")
    private String action;

    /** 这一步这句批语/交代（原文，后退不抹） */
    @TableField("note")
    @ApiModelProperty(value = "批语/交代")
    private String note;

    /** 见证岗代号（仅 WITNESS：两名各一笔） */
    @TableField("witness_post")
    @ApiModelProperty(value = "见证岗代号")
    private String witnessPost;

    /** 本笔判断 1同意 2不同意（设置核收意见） */
    @TableField("agree")
    @ApiModelProperty(value = "本笔判断")
    private Integer agree;

    /** 经办岗（只记是谁，步不由他点） */
    @TableField("operator")
    @ApiModelProperty(value = "经办岗")
    private String operator;

    /** 并发幂等请求号（同号第二回认出是同一笔） */
    @TableField("req_no")
    @ApiModelProperty(value = "并发幂等请求号")
    private String reqNo;

    /** 本轮是否在档 1当前轮 0随后退退出（不删行） */
    @TableField("active")
    @ApiModelProperty(value = "本轮是否在档")
    private Integer active;

    /** 落账时刻（毫秒；同秒两笔看 seq 排先后） */
    @TableField("acted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    @ApiModelProperty(value = "落账时刻")
    private Date actedAt;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getWitnessPost() {
        return witnessPost;
    }

    public void setWitnessPost(String witnessPost) {
        this.witnessPost = witnessPost;
    }

    public Integer getAgree() {
        return agree;
    }

    public void setAgree(Integer agree) {
        this.agree = agree;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public Date getActedAt() {
        return actedAt;
    }

    public void setActedAt(Date actedAt) {
        this.actedAt = actedAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
