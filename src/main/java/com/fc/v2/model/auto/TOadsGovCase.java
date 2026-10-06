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
 * 监管办件本对象 t_oads_gov_case。
 *
 * <p>一处在册点位一条办件，四步固定：申报→核准→设置→验收。step_no 只作服务端按留痕
 * 重数后的落账与回显，报文里带的步次不作数；同一点位任意时刻只许一条尚未走完。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_oads_gov_case")
@ApiModel(value = "TOadsGovCase", description = "监管办件本")
public class TOadsGovCase implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 监管办件单号（正式立起时服务端发） */
    @TableField("biz_no")
    @ApiModelProperty(value = "监管办件单号")
    private String bizNo;

    /** 来源报备登记主键（办件凭报备立起） */
    @TableField("report_id")
    @ApiModelProperty(value = "来源报备登记主键")
    private Long reportId;

    /** 点位编码（建件四项输入之一） */
    @TableField("spot_no")
    @ApiModelProperty(value = "点位编码")
    private String spotNo;

    /** 所属路段代号（建件四项输入之一） */
    @TableField("area_no")
    @ApiModelProperty(value = "所属路段代号")
    private String areaNo;

    /** 处数（建件四项输入之一） */
    @TableField("qty")
    @ApiModelProperty(value = "处数")
    private Integer qty;

    /** 拟判定的校核分值（建件四项输入之一） */
    @TableField("check_score")
    @ApiModelProperty(value = "拟判定的校核分值")
    private BigDecimal checkScore;

    /** 当前步 1申报 2核准 3设置 4验收（服务端按留痕重数落账） */
    @TableField("step_no")
    @ApiModelProperty(value = "当前步")
    private Integer stepNo;

    /** 情形 1在办 2已封存 3撤回 4划转 5作废（正式立起才进本表） */
    @TableField("status")
    @ApiModelProperty(value = "情形")
    private Integer status;

    /** 设置核收是否记为同意 0否 1是 */
    @TableField("setup_accept")
    @ApiModelProperty(value = "设置核收是否同意")
    private Integer setupAccept;

    /** 验收本轮两名见证岗已落笔数 */
    @TableField("witness_count")
    @ApiModelProperty(value = "见证已落笔数")
    private Integer witnessCount;

    /** 复验凭证编码（验收回写本行） */
    @TableField("recheck_voucher")
    @ApiModelProperty(value = "复验凭证编码")
    private String recheckVoucher;

    /** 收尾种类 WITHDRAW/REASSIGN/VOID */
    @TableField("closed_kind")
    @ApiModelProperty(value = "收尾种类")
    private String closedKind;

    /** 验收走完封存那一刻 */
    @TableField("sealed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    @ApiModelProperty(value = "封存时刻")
    private Date sealedAt;

    /** 收尾达成那一刻 */
    @TableField("closed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    @ApiModelProperty(value = "收尾时刻")
    private Date closedAt;

    /** 在途去重令牌 报备/在办=点位编码，收尾置空 */
    @TableField("live_token")
    @ApiModelProperty(value = "在途去重令牌")
    private String liveToken;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
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

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSetupAccept() {
        return setupAccept;
    }

    public void setSetupAccept(Integer setupAccept) {
        this.setupAccept = setupAccept;
    }

    public Integer getWitnessCount() {
        return witnessCount;
    }

    public void setWitnessCount(Integer witnessCount) {
        this.witnessCount = witnessCount;
    }

    public String getRecheckVoucher() {
        return recheckVoucher;
    }

    public void setRecheckVoucher(String recheckVoucher) {
        this.recheckVoucher = recheckVoucher;
    }

    public String getClosedKind() {
        return closedKind;
    }

    public void setClosedKind(String closedKind) {
        this.closedKind = closedKind;
    }

    public Date getSealedAt() {
        return sealedAt;
    }

    public void setSealedAt(Date sealedAt) {
        this.sealedAt = sealedAt;
    }

    public Date getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Date closedAt) {
        this.closedAt = closedAt;
    }

    public String getLiveToken() {
        return liveToken;
    }

    public void setLiveToken(String liveToken) {
        this.liveToken = liveToken;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
