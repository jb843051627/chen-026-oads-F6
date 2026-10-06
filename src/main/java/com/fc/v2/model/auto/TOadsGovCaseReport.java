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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 监管办件报备登记对象 t_oads_gov_case_report。
 *
 * <p>只承接“正式立起来之前那一小段”：报备四项输入收在这里，立前撤销也只改这张表，
 * 被撤掉的报备不进办件主档、不占“尚未走完”的位置。正式立起后 status=1 并关联办件本。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_oads_gov_case_report")
@ApiModel(value = "TOadsGovCaseReport", description = "监管办件报备登记")
public class TOadsGovCaseReport implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 报备幂等请求号（同一批报备两回认成同一件，可空） */
    @TableField("req_no")
    @ApiModelProperty(value = "报备幂等请求号")
    private String reqNo;

    /** 点位编码 */
    @TableField("spot_no")
    @ApiModelProperty(value = "点位编码")
    private String spotNo;

    /** 所属路段代号 */
    @TableField("area_no")
    @ApiModelProperty(value = "所属路段代号")
    private String areaNo;

    /** 处数（整数） */
    @TableField("qty")
    @ApiModelProperty(value = "处数")
    private Integer qty;

    /** 拟判定的校核分值 */
    @TableField("check_score")
    @ApiModelProperty(value = "拟判定的校核分值")
    private BigDecimal checkScore;

    /** 报备情形 0报备中 1已正式立起 6立前撤销 */
    @TableField("status")
    @ApiModelProperty(value = "报备情形")
    private Integer status;

    /** 正式立起后对应的办件本主键 */
    @TableField("case_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "对应办件本主键")
    private Long caseId;

    /** 报备占用令牌=点位编码；立起/撤销置空 */
    @TableField("open_token")
    @ApiModelProperty(value = "报备占用令牌")
    private String openToken;

    /** 立前撤销交代（原文） */
    @TableField("cancel_note")
    @ApiModelProperty(value = "立前撤销交代")
    private String cancelNote;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public String getSpotNo() {
        return spotNo;
    }

    public void setSpotNo(String spotNo) {
        this.spotNo = spotNo;
    }

    public String getAreaNo() {
        return areaNo;
    }

    public void setAreaNo(String areaNo) {
        this.areaNo = areaNo;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public BigDecimal getCheckScore() {
        return checkScore;
    }

    public void setCheckScore(BigDecimal checkScore) {
        this.checkScore = checkScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getOpenToken() {
        return openToken;
    }

    public void setOpenToken(String openToken) {
        this.openToken = openToken;
    }

    public String getCancelNote() {
        return cancelNote;
    }

    public void setCancelNote(String cancelNote) {
        this.cancelNote = cancelNote;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
