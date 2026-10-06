package com.fc.v2.model.custom;

/**
 * 监管办件“当前步送一笔”的统一入参，按当前步各取所需。
 *
 * <ul>
 *   <li>申报/核准：送一句意见 note（同一步第二次送，保留首次，不覆盖不追加）；</li>
 *   <li>设置：除 note 外须给核收判断 agree（1同意 2不同意），未记同意不得进验收；</li>
 *   <li>验收：见证岗落笔给 witnessPost＋note（两名见证岗各一笔），复验凭证给 recheckVoucher。</li>
 * </ul>
 *
 * <p>{@code claimStep} 是报文自称的步次，只作参考：与服务端按留痕重数的当前步对不上，
 * 这一笔不予采信、一条不落，整件留在原地。{@code reqNo} 是并发幂等请求号。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class GovCaseSubmitForm {

    /** 办件本主键（与 bizNo 二选一点出哪一条） */
    private Long caseId;

    /** 办件单号（与 caseId 二选一） */
    private String bizNo;

    /** 并发幂等请求号：同一批并着送两回，凭它认成同一笔 */
    private String reqNo;

    /** 报文自称的步次（只作参考，不采信记账） */
    private Integer claimStep;

    /** 这一步这句意见/交代（原文留痕） */
    private String note;

    /** 设置核收判断 1同意 2不同意（仅设置步） */
    private Integer agree;

    /** 见证岗代号（仅验收步：两名见证岗各落一笔） */
    private String witnessPost;

    /** 复验凭证编码（验收回写本行） */
    private String recheckVoucher;

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

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public Integer getClaimStep() {
        return claimStep;
    }

    public void setClaimStep(Integer claimStep) {
        this.claimStep = claimStep;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getAgree() {
        return agree;
    }

    public void setAgree(Integer agree) {
        this.agree = agree;
    }

    public String getWitnessPost() {
        return witnessPost;
    }

    public void setWitnessPost(String witnessPost) {
        this.witnessPost = witnessPost;
    }

    public String getRecheckVoucher() {
        return recheckVoucher;
    }

    public void setRecheckVoucher(String recheckVoucher) {
        this.recheckVoucher = recheckVoucher;
    }
}
