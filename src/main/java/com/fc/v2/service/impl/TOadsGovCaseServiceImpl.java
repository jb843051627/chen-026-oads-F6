package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.govcase.GovCaseRuleException;
import com.fc.v2.mapper.auto.TOadsAreaBookMapper;
import com.fc.v2.mapper.auto.TOadsGovCaseFlowLogMapper;
import com.fc.v2.mapper.auto.TOadsGovCaseMapper;
import com.fc.v2.mapper.auto.TOadsGovCaseReportMapper;
import com.fc.v2.mapper.auto.TOadsPermitBillMapper;
import com.fc.v2.model.auto.TOadsAreaBook;
import com.fc.v2.model.auto.TOadsGovCase;
import com.fc.v2.model.auto.TOadsGovCaseFlowLog;
import com.fc.v2.model.auto.TOadsGovCaseReport;
import com.fc.v2.model.auto.TOadsPermitBill;
import com.fc.v2.model.custom.GovCaseDetail;
import com.fc.v2.model.custom.GovCaseFlowOutcome;
import com.fc.v2.model.custom.GovCaseLedgerCheck;
import com.fc.v2.model.custom.GovCaseReportForm;
import com.fc.v2.model.custom.GovCaseSubmitForm;
import com.fc.v2.service.ITOadsGovCaseService;
import com.fc.v2.service.govcase.GovCaseStep;
import com.fc.v2.util.SnowflakeIdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监管办件四步流程唯一裁决处实现。
 *
 * <p>一处在册点位一条办件：申报→核准→设置→验收，先后不可调换。这条办件停在哪一步，
 * 只按 {@code t_oads_gov_case_flow_log} 在档 REACH 逐行重数；报文自带步次只作参考，
 * 与服务端点出的那一步对不上，这一笔一条不落、整件留在原地。办件本与留痕同一事务联动。
 *
 * <p>准入只认三条：核准看过了才进设置（设置许可三段签署走完）；设置核收记为同意才开验收；
 * 验收两名见证岗各一笔＋复验凭证回写，两样齐同一次处理封存。封存后只查不写。
 *
 * <p>旧 {@code TOadsGovFlowServiceImpl} 那套三跳骨架不添不改名；四步推进全部在本套承接。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TOadsGovCaseServiceImpl implements ITOadsGovCaseService {

    private static final int DEL_OK = 0;
    /** 段册已撤出（整段划归邻段） */
    private static final int AREA_REVOKED = 1;
    /** 核准单已点齐（三段签署走完封存） */
    private static final int PERMIT_SEALED = 1;
    private static final int SCORE_MAX = 100;

    @javax.annotation.Resource
    private TOadsGovCaseMapper caseMapper;
    @javax.annotation.Resource
    private TOadsGovCaseFlowLogMapper flowLogMapper;
    @javax.annotation.Resource
    private TOadsGovCaseReportMapper reportMapper;
    @javax.annotation.Resource
    private TOadsPermitBillMapper permitBillMapper;
    @javax.annotation.Resource
    private TOadsAreaBookMapper areaBookMapper;

    // ================================================================ 报备 / 立起 / 立前撤销

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TOadsGovCaseReport report(GovCaseReportForm form, String operator) {
        if (form == null) {
            throw new GovCaseRuleException("未收到报备材料，四项输入一项都没拿到，无从报备");
        }
        // 并发幂等：同一批并着报备两回，凭请求号认成同一件，不另立一条。
        String reqNo = trimToNull(form.getReqNo());
        if (reqNo != null) {
            TOadsGovCaseReport same = reportMapper.selectByReqNo(reqNo);
            if (same != null) {
                return same;
            }
        }

        // -------- 四项输入逐项点名：缺哪项说哪项，不丢一句“信息不全” --------
        String spotNo = trimToNull(form.getSpotNo());
        String areaNo = trimToNull(form.getAreaNo());
        Integer qty = GovCaseReportForm.parseQty(form.getQtyText());
        BigDecimal score = GovCaseReportForm.parseScore(form.getCheckScoreText());
        List<String> missing = new ArrayList<String>();
        if (spotNo == null) {
            missing.add("点位编码");
        }
        if (areaNo == null) {
            missing.add("所属路段代号");
        }
        if (form.getQtyText() == null || form.getQtyText().trim().isEmpty()) {
            missing.add("处数");
        }
        if (form.getCheckScoreText() == null || form.getCheckScoreText().trim().isEmpty()) {
            missing.add("拟判定的校核分值");
        }
        if (!missing.isEmpty()) {
            throw new GovCaseRuleException("建件四项输入缺：" + join(missing, "、") + "；缺项不予报备，逐项补齐再来");
        }
        if (qty == null) {
            throw new GovCaseRuleException("建件四项输入之「处数」不是整数（" + form.getQtyText().trim()
                    + "），本件报备不起来");
        }
        if (qty <= 0) {
            throw new GovCaseRuleException("建件四项输入之「处数」须为正整数（" + qty + "），本件报备不起来");
        }
        if (score == null) {
            throw new GovCaseRuleException("建件四项输入之「拟判定的校核分值」不是数（"
                    + form.getCheckScoreText().trim() + "），本件报备不起来");
        }
        if (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(SCORE_MAX)) > 0) {
            throw new GovCaseRuleException("建件四项输入之「拟判定的校核分值」须在 0.." + SCORE_MAX + " 之间（"
                    + score.stripTrailingZeros().toPlainString() + "），本件报备不起来");
        }

        // -------- 一处点位任意时刻只一条尚未走完：报备中占着，在办也占着 --------
        if (reportMapper.selectOpenBySpot(spotNo) != null) {
            throw new GovCaseRuleException("点位 " + spotNo + " 已有一条报备尚未正式立起，第二条报备收不进来");
        }
        if (caseMapper.selectOpenBySpot(spotNo) != null) {
            throw new GovCaseRuleException("点位 " + spotNo + " 已有一条在办办件尚未走完，第二条建不起来；"
                    + "待其封存或收尾后，同点位才可另报");
        }

        Date now = new Date();
        TOadsGovCaseReport r = new TOadsGovCaseReport();
        r.setReqNo(reqNo);
        r.setSpotNo(spotNo);
        r.setAreaNo(areaNo);
        r.setQty(qty);
        r.setCheckScore(score);
        r.setStatus(GovCaseStep.REPORT_OPEN);
        r.setOpenToken(spotNo);
        r.setDelFlag(DEL_OK);
        r.setCreateBy(operator);
        r.setCreateTime(now);
        reportMapper.insert(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TOadsGovCase establish(Long reportId, String operator) {
        if (reportId == null) {
            throw new GovCaseRuleException("正式立件须点出哪一条报备");
        }
        TOadsGovCaseReport r = reportMapper.selectById(reportId);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != DEL_OK)) {
            throw new GovCaseRuleException("报备登记上查无这一条：" + reportId);
        }
        if (r.getStatus() != null && r.getStatus() == GovCaseStep.REPORT_CANCELLED) {
            throw new GovCaseRuleException("点位 " + r.getSpotNo() + " 这条报备已在立起之前撤销，不进主档，不能再立");
        }
        if (r.getStatus() != null && r.getStatus() == GovCaseStep.REPORT_ESTABLISHED) {
            // 一条报备只立一件：重复立件返回已立那条，幂等不另建。
            TOadsGovCase existed = caseMapper.selectByReportId(r.getId());
            if (existed != null) {
                return existed;
            }
        }
        // 双保险：同点位在办名额（报备令牌即将让位给办件令牌）。
        if (caseMapper.selectOpenBySpot(r.getSpotNo()) != null) {
            throw new GovCaseRuleException("点位 " + r.getSpotNo() + " 已有一条在办办件，这条报备立不成第二件");
        }

        Date now = new Date();
        TOadsGovCase c = new TOadsGovCase();
        c.setBizNo("JC" + SnowflakeIdWorker.getUUID());
        c.setReportId(r.getId());
        c.setSpotNo(r.getSpotNo());
        c.setAreaNo(r.getAreaNo());
        c.setQty(r.getQty());
        c.setCheckScore(r.getCheckScore());
        // 正式立起落第一步：申报。步由系统按留痕数，不由报文点。
        c.setStepNo(GovCaseStep.DECLARE);
        c.setStatus(GovCaseStep.ST_RUNNING);
        c.setSetupAccept(0);
        c.setWitnessCount(0);
        c.setLiveToken(r.getSpotNo());
        c.setDelFlag(DEL_OK);
        c.setCreateBy(operator);
        c.setCreateTime(now);
        caseMapper.insert(c);

        appendLog(c.getId(), c.getBizNo(), GovCaseStep.DECLARE, 1, GovCaseStep.ACTION_REACH,
                "正式立起，落第一步「申报」（点位" + c.getSpotNo() + "／路段" + c.getAreaNo()
                        + "／处数" + c.getQty() + "／校核分值"
                        + c.getCheckScore().stripTrailingZeros().toPlainString() + "）",
                null, null, operator, null, now);

        // 报备让位：立起后不再占报备名额，办件本 live_token 接过来占“尚未走完”。
        r.setStatus(GovCaseStep.REPORT_ESTABLISHED);
        r.setCaseId(c.getId());
        r.setOpenToken(null);
        r.setUpdateBy(operator);
        r.setUpdateTime(now);
        reportMapper.updateById(r);
        return c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TOadsGovCaseReport cancelReport(Long reportId, String note, String operator) {
        if (reportId == null) {
            throw new GovCaseRuleException("立前撤销须点出哪一条报备");
        }
        TOadsGovCaseReport r = reportMapper.selectById(reportId);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != DEL_OK)) {
            throw new GovCaseRuleException("报备登记上查无这一条：" + reportId);
        }
        if (r.getStatus() != null && r.getStatus() == GovCaseStep.REPORT_ESTABLISHED) {
            throw new GovCaseRuleException("点位 " + r.getSpotNo() + " 这条报备已正式立成办件，过了立前那一小段，"
                    + "不能按立前撤销处理（要收尾走申报之后撤回报备）");
        }
        if (r.getStatus() != null && r.getStatus() == GovCaseStep.REPORT_CANCELLED) {
            return r; // 幂等：重复撤销返回原状
        }
        Date now = new Date();
        r.setStatus(GovCaseStep.REPORT_CANCELLED);
        r.setOpenToken(null);
        r.setCancelNote(clip(note));
        r.setUpdateBy(operator);
        r.setUpdateTime(now);
        reportMapper.updateById(r);
        // 不进办件主档、不占尚未走完名额，到此为止。
        return r;
    }

    // ================================================================ 当前步送一笔（意见/核收/见证/凭证）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome submit(GovCaseSubmitForm form, String operator) {
        if (form == null) {
            throw new GovCaseRuleException("未收到送审材料，无从落笔");
        }
        TOadsGovCase c = lockCase(form.getCaseId(), form.getBizNo());
        Recount rc = recount(c.getId());
        // 幂等命中：同一批并着送两回，第二回认出是同一笔，原样返回、不重复落、也不丢。
        String reqNo = trimToNull(form.getReqNo());
        if (reqNo != null) {
            TOadsGovCaseFlowLog first = flowLogMapper.selectByReqNo(reqNo);
            if (first != null) {
                return GovCaseFlowOutcome.of(GovCaseFlowOutcome.DEDUPED,
                        "本笔与请求号 " + reqNo + " 头一笔是同一笔，不重复落一条、也不另丢一条", c);
            }
        }
        rejectIfTerminal(c, rc, "送意见");

        int step = rc.step == null ? GovCaseStep.DECLARE : rc.step;
        // 报文自称步次只作参考：与当前停的这一步对不上，整笔丢弃，整件留在原地。
        if (form.getClaimStep() != null && form.getClaimStep() != step) {
            return GovCaseFlowOutcome.of(GovCaseFlowOutcome.DISCARDED,
                    "报文称第" + form.getClaimStep() + "步「" + GovCaseStep.nameOf(form.getClaimStep())
                            + "」，本件实际停在第" + step + "步「" + GovCaseStep.nameOf(step)
                            + "」；这一笔不予采信、一条不落，整件仍留在「" + GovCaseStep.nameOf(step) + "」", c);
        }
        int round = activeRound(rc, step);
        Date now = new Date();
        List<TOadsGovCaseFlowLog> activeHere = flowLogMapper.listActiveRound(c.getId(), step, round);

        if (step == GovCaseStep.ACCEPT) {
            return submitAccept(c, rc, round, activeHere, form, reqNo, operator, now);
        }
        if (step == GovCaseStep.SETUP) {
            return submitSetup(c, step, round, activeHere, form, reqNo, operator, now);
        }
        // 申报 / 核准：送一句意见。同一步第二次送，保留首次，不覆盖不追加。
        if (hasAction(activeHere, GovCaseStep.ACTION_OPINION)) {
            throw new GovCaseRuleException("「" + GovCaseStep.nameOf(step) + "」这一步本轮已有一句在档意见，"
                    + "保留首次那句，第二句既不覆盖也不追加");
        }
        String note = trimToNull(form.getNote());
        if (note == null) {
            throw new GovCaseRuleException("「" + GovCaseStep.nameOf(step) + "」送意见须带一句批语，空句不收");
        }
        appendLog(c.getId(), c.getBizNo(), step, round, GovCaseStep.ACTION_OPINION, note,
                null, null, operator, reqNo, now);
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.LANDED,
                "「" + GovCaseStep.nameOf(step) + "」意见已落（第" + round + "轮），整件仍停在这一步；"
                        + "准入满足后由紧邻推进进下一步", c);
    }

    /** 设置步：核收意见须给同意/不同意；未记同意不得开验收。 */
    private GovCaseFlowOutcome submitSetup(TOadsGovCase c, int step, int round,
                                           List<TOadsGovCaseFlowLog> activeHere, GovCaseSubmitForm form,
                                           String reqNo, String operator, Date now) {
        if (hasAction(activeHere, GovCaseStep.ACTION_OPINION)) {
            throw new GovCaseRuleException("「设置」这一步本轮核收意见已落，保留首次那句，第二回既不覆盖也不追加");
        }
        Integer agree = form.getAgree();
        if (agree == null || (agree != GovCaseStep.AGREE && agree != GovCaseStep.DISAGREE)) {
            throw new GovCaseRuleException("「设置」核收须明记 1同意 / 2不同意，没记同意不得开验收");
        }
        String note = trimToNull(form.getNote());
        if (note == null) {
            note = agree == GovCaseStep.AGREE ? "设置核收记为同意" : "设置核收未记同意";
        }
        appendLog(c.getId(), c.getBizNo(), step, round, GovCaseStep.ACTION_OPINION, note,
                null, agree, operator, reqNo, now);
        c.setSetupAccept(agree == GovCaseStep.AGREE ? 1 : 0);
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.LANDED,
                agree == GovCaseStep.AGREE
                        ? "「设置」核收已记为同意（第" + round + "轮），可紧邻推进开「验收」"
                        : "「设置」核收未记同意，验收不得开始；要改只能后退重来", c);
    }

    /** 验收步：两名见证岗各一笔＋复验凭证回写本行，两样齐同一次处理封存。 */
    private GovCaseFlowOutcome submitAccept(TOadsGovCase c, Recount rc, int round,
                                            List<TOadsGovCaseFlowLog> activeHere, GovCaseSubmitForm form,
                                            String reqNo, String operator, Date now) {
        String witnessPost = trimToNull(form.getWitnessPost());
        String voucher = trimToNull(form.getRecheckVoucher());
        if (witnessPost == null && voucher == null) {
            throw new GovCaseRuleException("「验收」这一步只接两种落笔：见证岗意见（两名各一笔）或复验凭证回写，"
                    + "两样都没带，这笔不收");
        }
        if (witnessPost != null) {
            String note = trimToNull(form.getNote());
            if (note == null) {
                throw new GovCaseRuleException("见证岗 " + witnessPost + " 须各提交一次意见，空意见不收");
            }
            for (TOadsGovCaseFlowLog l : activeHere) {
                if (GovCaseStep.ACTION_WITNESS.equals(l.getAction())
                        && witnessPost.equals(trimToNull(l.getWitnessPost()))) {
                    throw new GovCaseRuleException("见证岗 " + witnessPost + " 本轮已提交过一次意见，"
                            + "保留首次那句，第二回既不覆盖也不追加");
                }
            }
            long witnessN = countWitness(activeHere);
            if (witnessN >= 2) {
                throw new GovCaseRuleException("「验收」两名见证岗已各落一笔，不该再有第三名见证岗的意见进来");
            }
            appendLog(c.getId(), c.getBizNo(), GovCaseStep.ACCEPT, round, GovCaseStep.ACTION_WITNESS, note,
                    witnessPost, null, operator, reqNo, now);
        }
        if (voucher != null) {
            if (trimToNull(c.getRecheckVoucher()) != null) {
                throw new GovCaseRuleException("复验凭证编码已回写为「" + c.getRecheckVoucher()
                        + "」，保留首次那串，第二回既不覆盖也不追加");
            }
            c.setRecheckVoucher(voucher);
        }

        List<TOadsGovCaseFlowLog> fresh = flowLogMapper.listActiveRound(c.getId(), GovCaseStep.ACCEPT, round);
        int witnesses = (int) countWitness(fresh);
        boolean voucherOk = trimToNull(c.getRecheckVoucher()) != null;
        c.setWitnessCount(witnesses);
        if (witnesses >= 2 && voucherOk) {
            return seal(c, round, operator, now);
        }
        touch(c, operator, now);
        List<String> waiting = new ArrayList<String>();
        if (witnesses < 2) {
            waiting.add("见证岗意见还差" + (2 - witnesses) + "名");
        }
        if (!voucherOk) {
            waiting.add("复验凭证编码未回写");
        }
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.LANDED,
                "「验收」已落见证 " + witnesses + "/2；" + join(waiting, "、") + "，两样齐才走完封存", c);
    }

    // ================================================================ 前进 / 后退

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome advance(Long caseId, Integer claimStep, String reqNo, String operator) {
        TOadsGovCase c = lockCase(caseId, null);
        Recount rc = recount(c.getId());
        if (reqNo != null && !reqNo.trim().isEmpty()) {
            TOadsGovCaseFlowLog first = flowLogMapper.selectByReqNo(reqNo.trim());
            if (first != null) {
                return GovCaseFlowOutcome.of(GovCaseFlowOutcome.DEDUPED,
                        "本笔推进与请求号 " + reqNo.trim() + " 头一笔是同一笔，不重复走、也不丢一步", c);
            }
        }
        rejectIfTerminal(c, rc, "推进");
        int current = rc.step == null ? GovCaseStep.DECLARE : rc.step;
        if (current >= GovCaseStep.ACCEPT) {
            throw new GovCaseRuleException("点位 " + c.getSpotNo() + " 已到最后一步「验收」，后面没有步可推；"
                    + "两名见证＋复验凭证齐即封存");
        }
        int target = current + 1;
        // 前进只跨紧邻下一步；报文想跨过中间某步直接送进来，那一步不生效，整件留在原地。
        if (claimStep != null && claimStep != target) {
            return GovCaseFlowOutcome.of(GovCaseFlowOutcome.DISCARDED,
                    "报文本想送到第" + claimStep + "步「" + GovCaseStep.nameOf(claimStep)
                            + "」，紧邻下一步只能是第" + target + "步「" + GovCaseStep.nameOf(target)
                            + "」；跨步这一步不生效，整件仍留在「" + GovCaseStep.nameOf(current) + "」", c);
        }

        // 各步准入：只按这一趟点出的那一步判，不借邻步旧结论。
        String gate = entryGateBlocked(c, target);
        if (gate != null) {
            throw new GovCaseRuleException(gate);
        }

        int round = maxRound(c.getId(), target) + 1;
        Date now = new Date();
        appendLog(c.getId(), c.getBizNo(), target, round, GovCaseStep.ACTION_REACH,
                "由「" + GovCaseStep.nameOf(current) + "」紧邻推进到「" + GovCaseStep.nameOf(target)
                        + "」（第" + round + "轮），系统按步序留痕数步",
                null, null, operator, trimToNull(reqNo), now);
        c.setStepNo(target);
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.ADVANCED,
                "已走到「" + GovCaseStep.nameOf(target) + "」（第" + round + "轮），下一步准入："
                        + GovCaseStep.entryOf(target), c);
    }

    /** 进入 target 这一步的准入没过时返回阻断原话；过了返回 null。 */
    private String entryGateBlocked(TOadsGovCase c, int target) {
        if (target == GovCaseStep.SETUP) {
            // 核准这一步要看设置许可三段签署走完，未走完不得进入设置。
            if (!permitSealedForSpot(c.getSpotNo())) {
                return "进入「设置」的准入没过：点位 " + c.getSpotNo()
                        + " 的设置许可三段签署（窗口受理/区局核定/市局备案）尚未走完，不得进入设置";
            }
        } else if (target == GovCaseStep.ACCEPT) {
            // 设置一步的核收未记为同意，验收不得开始。
            if (c.getSetupAccept() == null || c.getSetupAccept() != 1) {
                return "进入「验收」的准入没过：「设置」一步的核收未记为同意，验收不得开始";
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome returnStep(Long caseId, String note, String reqNo, String operator) {
        TOadsGovCase c = lockCase(caseId, null);
        Recount rc = recount(c.getId());
        if (trimToNull(reqNo) != null) {
            TOadsGovCaseFlowLog first = flowLogMapper.selectByReqNo(reqNo.trim());
            if (first != null) {
                return GovCaseFlowOutcome.of(GovCaseFlowOutcome.DEDUPED,
                        "本笔后退与请求号 " + reqNo.trim() + " 头一笔是同一笔，不重复退、也不丢一步", c);
            }
        }
        rejectIfTerminal(c, rc, "后退");
        int current = rc.step == null ? GovCaseStep.DECLARE : rc.step;
        if (current <= GovCaseStep.DECLARE) {
            throw new GovCaseRuleException("点位 " + c.getSpotNo() + " 已在第一步「申报」，第一步不再后退");
        }
        int round = activeRound(rc, current);
        Date now = new Date();
        // 被退那一步此前留下的批语：当轮在档行 active 置 0，行不删、按原先后留在追溯记录里。
        flowLogMapper.deactivateRound(c.getId(), current, round);
        appendLog(c.getId(), c.getBizNo(), current, round, GovCaseStep.ACTION_RETURN,
                "由「" + GovCaseStep.nameOf(current) + "」后退一步到「" + GovCaseStep.nameOf(current - 1)
                        + "」；上一轮(" + round + ")批语转入追溯尾部，重走从这一步重新累计",
                null, null, operator, trimToNull(reqNo), now);

        int backTo = current - 1;
        c.setStepNo(backTo);
        // 被清掉的是哪一步，这一步攒下的东西同步归零，重走另起一轮重新累计。
        if (current == GovCaseStep.SETUP) {
            c.setSetupAccept(0);
        }
        if (current == GovCaseStep.ACCEPT) {
            c.setWitnessCount(0);
            c.setRecheckVoucher(null);
        }
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.RETURNED,
                "已由「" + GovCaseStep.nameOf(current) + "」后退一步到「" + GovCaseStep.nameOf(backTo)
                        + "」；上一轮批语留在追溯尾部、不与后补并在一起，重走重新累计", c);
    }

    // ================================================================ 收尾：撤回 / 划转 / 作废 / 封存

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome withdraw(Long caseId, String note, String operator) {
        TOadsGovCase c = lockCase(caseId, null);
        Recount rc = recount(c.getId());
        rejectIfTerminal(c, rc, "撤回报备");
        return close(c, GovCaseStep.ST_WITHDRAWN, GovCaseStep.CLOSE_WITHDRAW,
                "申报之后撤回报备，本件收尾、不必再往下走", note, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome reassignClose(Long caseId, String note, String operator) {
        TOadsGovCase c = lockCase(caseId, null);
        Recount rc = recount(c.getId());
        rejectIfTerminal(c, rc, "划段收尾");
        TOadsAreaBook area = findArea(c.getAreaNo());
        if (area == null || area.getStatus() == null || area.getStatus() != AREA_REVOKED) {
            throw new GovCaseRuleException("路段 " + c.getAreaNo() + " 在目录里还没有划归邻段（未登记为撤出），"
                    + "不能凭这句话收尾；目录确已划转才收尾");
        }
        return close(c, GovCaseStep.ST_REASSIGNED, GovCaseStep.CLOSE_REASSIGN,
                "所属路段 " + c.getAreaNo() + " 已从目录划归邻段（" + area.getAreaName() + "），本件收尾",
                note, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GovCaseFlowOutcome voidCase(Long caseId, String note, String operator) {
        TOadsGovCase c = lockCase(caseId, null);
        Recount rc = recount(c.getId());
        rejectIfTerminal(c, rc, "作废");
        int step = rc.step == null ? GovCaseStep.DECLARE : rc.step;
        if (step != GovCaseStep.ACCEPT) {
            throw new GovCaseRuleException("作废只从最后一步「验收」发起；本件现停在第" + step + "步「"
                    + GovCaseStep.nameOf(step) + "」，不到作废那一步");
        }
        return close(c, GovCaseStep.ST_VOID, GovCaseStep.CLOSE_VOID, "作废自最后一步「验收」发起，本件作废收尾",
                note, operator);
    }

    /** 收尾共用：落 CLOSE 留痕、置终态、腾在途名额（撤回报备/划转/作废）。 */
    private GovCaseFlowOutcome close(TOadsGovCase c, int status, String kind, String reason, String note,
                                     String operator) {
        Date now = new Date();
        int step = c.getStepNo() == null ? GovCaseStep.DECLARE : c.getStepNo();
        String text = reason + (trimToNull(note) == null ? "" : "；交代：" + note.trim());
        appendLog(c.getId(), c.getBizNo(), step, Math.max(1, maxRound(c.getId(), step)),
                GovCaseStep.ACTION_CLOSE, text, null, null, operator, null, now);
        c.setStatus(status);
        c.setClosedKind(kind);
        c.setClosedAt(now);
        c.setLiveToken(null);
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.CLOSED, reason + "；在途名额腾出，同点位可另报新件", c);
    }

    /** 验收两样齐：同一次处理落 SEAL、封存、腾名额；此后只查不写。 */
    private GovCaseFlowOutcome seal(TOadsGovCase c, int round, String operator, Date now) {
        appendLog(c.getId(), c.getBizNo(), GovCaseStep.ACCEPT, round, GovCaseStep.ACTION_SEAL,
                "两名见证岗各提交一次意见、复验凭证编码「" + c.getRecheckVoucher()
                        + "」已回写本行，两样齐，验收走完封存",
                null, null, operator, null, now);
        c.setStatus(GovCaseStep.ST_SEALED);
        c.setSealedAt(now);
        c.setLiveToken(null);
        touch(c, operator, now);
        return GovCaseFlowOutcome.of(GovCaseFlowOutcome.SEALED,
                "验收走完，本件封存；封存后可查不可写，新增意见/更正/抽回整条三种动作都不受理", c);
    }

    // ================================================================ 回看 / 计数 / 对账 / 流水

    @Override
    public Integer currentStep(Long caseId) {
        if (caseId == null) {
            return null;
        }
        return recount(caseId).step;
    }

    @Override
    public GovCaseDetail detail(Long caseId) {
        TOadsGovCase c = requireCase(caseId);
        List<TOadsGovCaseFlowLog> all = flowLogMapper.listByCase(caseId);
        Recount rc = recount(caseId, all);
        GovCaseDetail d = new GovCaseDetail();
        d.setGovCase(c);
        boolean sealed = isSealedStatus(c) || rc.sealed;
        boolean closedStatus = isTerminalStatus(c);
        d.setSealed(sealed);
        d.setClosed(closedStatus);
        d.setTimeline(all);

        int current = rc.step == null ? 0 : rc.step;
        for (int step = GovCaseStep.DECLARE; step <= GovCaseStep.ACCEPT; step++) {
            GovCaseDetail.Step s = new GovCaseDetail.Step();
            s.setStepNo(step);
            s.setStepName(GovCaseStep.nameOf(step));
            int round = rc.rounds.containsKey(step) ? rc.rounds.get(step) : 0;
            List<TOadsGovCaseFlowLog> logs = new ArrayList<TOadsGovCaseFlowLog>();
            for (TOadsGovCaseFlowLog l : all) {
                if (l.getStepNo() != null && l.getStepNo() == step && l.getActive() != null && l.getActive() == 1
                        && l.getRoundNo() != null && l.getRoundNo() == round) {
                    logs.add(l);
                }
            }
            s.setRoundNo(round);
            s.setLogs(logs);
            s.setLandedCount(logs.size());
            s.setReached(rc.step != null && step <= rc.step);
            // 封存/收尾后没有“当前步”，不再给任何操作入口；在办才点当前。
            s.setCurrent(!closedStatus && step == current);
            s.setEntryRule(GovCaseStep.entryOf(step));
            d.getSteps().add(s);
        }
        return d;
    }

    @Override
    public List<TOadsGovCase> selectCaseList(Wrapper<TOadsGovCase> queryWrapper) {
        return caseMapper.selectList(queryWrapper);
    }

    @Override
    public long openCount() {
        // 页首数字只认后端这一次查询，不取前端缓存，也不在别处另算一套。
        return caseMapper.countOpen();
    }

    @Override
    public GovCaseLedgerCheck reconcile() {
        GovCaseLedgerCheck check = new GovCaseLedgerCheck();
        List<TOadsGovCase> all = caseMapper.selectAllNormal();
        long tableOpen = 0;
        long recountedOpen = 0;
        for (TOadsGovCase c : all) {
            boolean tableRunning = c.getStatus() != null && c.getStatus() == GovCaseStep.ST_RUNNING;
            if (tableRunning) {
                tableOpen++;
            }
            Recount rc = recount(c.getId());
            boolean runningByLog = !rc.sealed && !rc.closed && rc.step != null;
            if (runningByLog) {
                recountedOpen++;
            }
            String name = c.getBizNo() == null ? String.valueOf(c.getId()) : c.getBizNo();
            // 账面在办与否要与留痕重数对得上，差了逐件指名，不并成一个差数。
            if (tableRunning != runningByLog) {
                check.getMismatches().add("办件 " + name + "：账面" + (tableRunning ? "在办" : statusName(c.getStatus()))
                        + "，留痕重算为" + (runningByLog ? "在办" : (rc.sealed ? "已封存" : "已收尾/未起步")));
            }
            // 两边都在办的，账面步次须与留痕重数停的那一步一致。
            if (tableRunning && runningByLog) {
                int tableStep = c.getStepNo() == null ? 0 : c.getStepNo();
                int logStep = rc.step == null ? 0 : rc.step;
                if (tableStep != logStep) {
                    check.getMismatches().add("办件 " + name + "：账面停在第" + tableStep + "步「"
                            + GovCaseStep.nameOf(tableStep) + "」，留痕重数停在第" + logStep + "步「"
                            + GovCaseStep.nameOf(logStep) + "」");
                }
            }
        }
        check.setOpenCount(tableOpen);
        check.setRecountedCount(recountedOpen);
        check.setBalanced(tableOpen == recountedOpen && check.getMismatches().isEmpty());
        return check;
    }

    @Override
    public List<TOadsGovCaseFlowLog> acceptanceRegister(Date day) {
        if (day == null) {
            throw new GovCaseRuleException("须点出哪一日的受理流水");
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date end = cal.getTime();
        // 逐笔吻合：后退退出的老轮也是当日落过的一笔，照常入簿；同秒两通道各一笔不并笔。
        return flowLogMapper.listLandedBetween(start, end);
    }

    // ================================================================ 内部：取件 / 重数 / 落痕

    private TOadsGovCase lockCase(Long id, String bizNo) {
        TOadsGovCase c;
        if (id != null) {
            c = caseMapper.selectByIdForUpdate(id);
        } else {
            String no = trimToNull(bizNo);
            if (no == null) {
                throw new GovCaseRuleException("须点出哪一条办件（caseId 与 bizNo 至少给一个）");
            }
            c = caseMapper.selectByBizNo(no);
        }
        if (c == null || (c.getDelFlag() != null && c.getDelFlag() != DEL_OK)) {
            throw new GovCaseRuleException("监管办件查无此条：" + (id != null ? id : bizNo));
        }
        return c;
    }

    private TOadsGovCase requireCase(Long id) {
        if (id == null) {
            throw new GovCaseRuleException("须点出哪一条办件");
        }
        TOadsGovCase c = caseMapper.selectById(id);
        if (c == null || (c.getDelFlag() != null && c.getDelFlag() != DEL_OK)) {
            throw new GovCaseRuleException("监管办件查无此条：" + id);
        }
        return c;
    }

    /** 终态（封存/撤回/划转/作废）一律不再接写动作，三种动作都不受理。 */
    private void rejectIfTerminal(TOadsGovCase c, Recount rc, String action) {
        if (isSealedStatus(c) || rc.sealed) {
            throw new GovCaseRuleException("办件 " + c.getBizNo() + " 已封存：可查不可写，"
                    + action + "、更正内容、抽回整条三种动作都不受理");
        }
        if (isClosedStatus(c) || rc.closed) {
            throw new GovCaseRuleException("办件 " + c.getBizNo() + " 已收尾（" + statusName(c.getStatus())
                    + "），不再接任何动作");
        }
    }

    /**
     * 这条办件停在哪一步，只按在档 REACH 连续到达重数：从申报起连续到达的最高一步。
     * 同时点出封存（在档 SEAL）、收尾（在档 CLOSE）与每步当前轮。旧结论一律不借。
     */
    private Recount recount(Long caseId) {
        return recount(caseId, flowLogMapper.listByCase(caseId));
    }

    private Recount recount(Long caseId, List<TOadsGovCaseFlowLog> logs) {
        Recount rc = new Recount();
        if (logs == null || logs.isEmpty()) {
            return rc;
        }
        for (TOadsGovCaseFlowLog l : logs) {
            if (l.getActive() == null || l.getActive() != 1) {
                continue;
            }
            if (GovCaseStep.ACTION_SEAL.equals(l.getAction())) {
                rc.sealed = true;
            }
            if (GovCaseStep.ACTION_CLOSE.equals(l.getAction())) {
                rc.closed = true;
            }
        }
        boolean anyReach = false;
        for (int step = GovCaseStep.DECLARE; step <= GovCaseStep.ACCEPT; step++) {
            int round = 0;
            boolean reached = false;
            for (TOadsGovCaseFlowLog l : logs) {
                if (l.getStepNo() == null || l.getStepNo() != step || l.getActive() == null || l.getActive() != 1) {
                    continue;
                }
                if (GovCaseStep.ACTION_REACH.equals(l.getAction())) {
                    reached = true;
                    anyReach = true;
                    if (l.getRoundNo() != null && l.getRoundNo() > round) {
                        round = l.getRoundNo();
                    }
                }
            }
            if (!reached) {
                break;
            }
            rc.step = step;
            rc.rounds.put(step, round);
        }
        if (!anyReach) {
            rc.step = null;
        }
        return rc;
    }

    private int maxRound(Long caseId, int step) {
        int max = 0;
        for (TOadsGovCaseFlowLog l : flowLogMapper.listByCase(caseId)) {
            if (l.getStepNo() != null && l.getStepNo() == step && l.getRoundNo() != null && l.getRoundNo() > max) {
                max = l.getRoundNo();
            }
        }
        return max;
    }

    private int activeRound(Recount rc, int step) {
        Integer r = rc.rounds.get(step);
        return r == null ? 1 : r;
    }

    private void appendLog(Long caseId, String bizNo, int step, int round, String action, String note,
                           String witnessPost, Integer agree, String operator, String reqNo, Date at) {
        int seq = flowLogMapper.listByCase(caseId).size() + 1;
        TOadsGovCaseFlowLog l = new TOadsGovCaseFlowLog();
        l.setCaseId(caseId);
        l.setBizNo(bizNo);
        l.setStepNo(step);
        l.setRoundNo(round);
        l.setSeqNo(seq);
        l.setAction(action);
        l.setNote(clip(note));
        l.setWitnessPost(witnessPost);
        l.setAgree(agree);
        l.setOperator(operator);
        l.setReqNo(reqNo);
        // RETURN 只把当轮退出：它自身不留在档（不参与重数），但行保留、追溯可见。
        l.setActive(GovCaseStep.ACTION_RETURN.equals(action) ? 0 : 1);
        l.setActedAt(at);
        l.setCreateBy(operator);
        l.setCreateTime(at);
        flowLogMapper.insert(l);
    }

    private long countWitness(List<TOadsGovCaseFlowLog> activeAccept) {
        Set<String> posts = new HashSet<String>();
        for (TOadsGovCaseFlowLog l : activeAccept) {
            if (GovCaseStep.ACTION_WITNESS.equals(l.getAction()) && trimToNull(l.getWitnessPost()) != null) {
                posts.add(l.getWitnessPost().trim());
            }
        }
        return posts.size();
    }

    private boolean hasAction(List<TOadsGovCaseFlowLog> logs, String action) {
        for (TOadsGovCaseFlowLog l : logs) {
            if (action.equals(l.getAction())) {
                return true;
            }
        }
        return false;
    }

    private boolean permitSealedForSpot(String spotNo) {
        List<TOadsPermitBill> bills = permitBillMapper.selectList(new QueryWrapper<TOadsPermitBill>()
                .eq("spot_no", spotNo).eq("status", PERMIT_SEALED).eq("del_flag", DEL_OK));
        return bills != null && !bills.isEmpty();
    }

    private TOadsAreaBook findArea(String areaNo) {
        if (areaNo == null) {
            return null;
        }
        List<TOadsAreaBook> list = areaBookMapper.selectList(new QueryWrapper<TOadsAreaBook>()
                .eq("area_no", areaNo).eq("del_flag", DEL_OK));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private void touch(TOadsGovCase c, String operator, Date now) {
        c.setUpdateBy(operator);
        c.setUpdateTime(now);
        caseMapper.updateById(c);
    }

    private boolean isSealedStatus(TOadsGovCase c) {
        return c.getStatus() != null && c.getStatus() == GovCaseStep.ST_SEALED;
    }

    private boolean isClosedStatus(TOadsGovCase c) {
        int s = c.getStatus() == null ? 0 : c.getStatus();
        return s == GovCaseStep.ST_WITHDRAWN || s == GovCaseStep.ST_REASSIGNED || s == GovCaseStep.ST_VOID;
    }

    private boolean isTerminalStatus(TOadsGovCase c) {
        return isSealedStatus(c) || isClosedStatus(c);
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未立";
        }
        switch (status) {
            case GovCaseStep.ST_RUNNING:
                return "在办";
            case GovCaseStep.ST_SEALED:
                return "已封存";
            case GovCaseStep.ST_WITHDRAWN:
                return "申报后撤回报备";
            case GovCaseStep.ST_REASSIGNED:
                return "路段划归邻段";
            case GovCaseStep.ST_VOID:
                return "作废";
            default:
                return "未知情形";
        }
    }

    private static class Recount {
        private Integer step;
        private final Map<Integer, Integer> rounds = new HashMap<Integer, Integer>();
        private boolean sealed;
        private boolean closed;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String join(List<?> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String clip(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
