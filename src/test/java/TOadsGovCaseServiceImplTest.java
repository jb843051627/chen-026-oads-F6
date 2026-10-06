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
import com.fc.v2.service.govcase.GovCaseStep;
import com.fc.v2.service.impl.TOadsGovCaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 监管办件四步流程三大块＋并发幂等逐条核对（mapper 全 Mock，内存账台逐笔记账，不落真库）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TOadsGovCaseServiceImplTest {

    @Mock private TOadsGovCaseMapper caseMapper;
    @Mock private TOadsGovCaseFlowLogMapper flowLogMapper;
    @Mock private TOadsGovCaseReportMapper reportMapper;
    @Mock private TOadsPermitBillMapper permitBillMapper;
    @Mock private TOadsAreaBookMapper areaBookMapper;

    private TOadsGovCaseServiceImpl service;

    private final Map<Long, TOadsGovCaseReport> reports = new HashMap<Long, TOadsGovCaseReport>();
    private final Map<Long, TOadsGovCase> cases = new HashMap<Long, TOadsGovCase>();
    private final List<TOadsGovCaseFlowLog> logs = new ArrayList<TOadsGovCaseFlowLog>();
    private final List<TOadsPermitBill> sealedPermits = new ArrayList<TOadsPermitBill>();
    private final List<TOadsAreaBook> areas = new ArrayList<TOadsAreaBook>();
    private long idSeq = 5000;

    @BeforeEach
    public void wireHarness() {
        service = new TOadsGovCaseServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "caseMapper", caseMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "flowLogMapper", flowLogMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "permitBillMapper", permitBillMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "areaBookMapper", areaBookMapper);

        when(reportMapper.insert(any())).thenAnswer(i -> {
            TOadsGovCaseReport r = i.getArgument(0);
            if (r.getId() == null) r.setId(++idSeq);
            reports.put(r.getId(), r);
            return 1;
        });
        when(reportMapper.selectById(any())).thenAnswer(i -> reports.get(i.getArgument(0)));
        when(reportMapper.updateById(any())).thenAnswer(i -> {
            reports.put(((TOadsGovCaseReport) i.getArgument(0)).getId(), i.getArgument(0));
            return 1;
        });
        when(reportMapper.selectByReqNo(any())).thenAnswer(i -> {
            String no = i.getArgument(0);
            for (TOadsGovCaseReport r : reports.values()) if (no.equals(r.getReqNo())) return r;
            return null;
        });
        when(reportMapper.selectOpenBySpot(any())).thenAnswer(i -> {
            String spot = i.getArgument(0);
            for (TOadsGovCaseReport r : reports.values())
                if (spot.equals(r.getOpenToken()) && r.getDelFlag() == 0) return r;
            return null;
        });

        when(caseMapper.insert(any())).thenAnswer(i -> {
            TOadsGovCase c = i.getArgument(0);
            if (c.getId() == null) c.setId(++idSeq);
            cases.put(c.getId(), c);
            return 1;
        });
        when(caseMapper.selectById(any())).thenAnswer(i -> cases.get(i.getArgument(0)));
        when(caseMapper.selectByIdForUpdate(any())).thenAnswer(i -> cases.get(i.getArgument(0)));
        when(caseMapper.updateById(any())).thenAnswer(i -> {
            cases.put(((TOadsGovCase) i.getArgument(0)).getId(), i.getArgument(0));
            return 1;
        });
        when(caseMapper.selectByBizNo(any())).thenAnswer(i -> {
            String no = i.getArgument(0);
            for (TOadsGovCase c : cases.values()) if (no.equals(c.getBizNo()) && c.getDelFlag() == 0) return c;
            return null;
        });
        when(caseMapper.selectByReportId(any())).thenAnswer(i -> {
            Long rid = i.getArgument(0);
            for (TOadsGovCase c : cases.values()) if (rid.equals(c.getReportId()) && c.getDelFlag() == 0) return c;
            return null;
        });
        when(caseMapper.selectOpenBySpot(any())).thenAnswer(i -> {
            String spot = i.getArgument(0);
            for (TOadsGovCase c : cases.values())
                if (spot.equals(c.getLiveToken()) && c.getDelFlag() == 0) return c;
            return null;
        });
        when(caseMapper.countOpen()).thenAnswer(i -> {
            long n = 0;
            for (TOadsGovCase c : cases.values())
                if (c.getDelFlag() == 0 && c.getStatus() == GovCaseStep.ST_RUNNING) n++;
            return n;
        });
        when(caseMapper.selectAllNormal()).thenAnswer(i -> new ArrayList<TOadsGovCase>(cases.values()));

        when(flowLogMapper.insert(any())).thenAnswer(i -> {
            TOadsGovCaseFlowLog l = i.getArgument(0);
            if (l.getId() == null) l.setId(++idSeq);
            logs.add(l);
            return 1;
        });
        when(flowLogMapper.listByCase(any())).thenAnswer(i -> {
            Long cid = i.getArgument(0);
            List<TOadsGovCaseFlowLog> l = new ArrayList<TOadsGovCaseFlowLog>();
            for (TOadsGovCaseFlowLog g : logs) if (cid.equals(g.getCaseId())) l.add(g);
            l.sort(Comparator.comparing(TOadsGovCaseFlowLog::getActedAt)
                    .thenComparing(TOadsGovCaseFlowLog::getSeqNo).thenComparing(TOadsGovCaseFlowLog::getId));
            return l;
        });
        when(flowLogMapper.listActiveRound(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(i -> {
                    Long cid = i.getArgument(0); int st = i.getArgument(1); int rd = i.getArgument(2);
                    List<TOadsGovCaseFlowLog> l = new ArrayList<TOadsGovCaseFlowLog>();
                    for (TOadsGovCaseFlowLog g : logs)
                        if (cid.equals(g.getCaseId()) && g.getStepNo() == st && g.getRoundNo() == rd && g.getActive() == 1) l.add(g);
                    l.sort(Comparator.comparing(TOadsGovCaseFlowLog::getActedAt).thenComparing(TOadsGovCaseFlowLog::getSeqNo));
                    return l;
                });
        when(flowLogMapper.deactivateRound(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(i -> {
                    Long cid = i.getArgument(0); int st = i.getArgument(1); int rd = i.getArgument(2);
                    int n = 0;
                    for (TOadsGovCaseFlowLog g : logs)
                        if (cid.equals(g.getCaseId()) && g.getStepNo() == st && g.getRoundNo() == rd && g.getActive() == 1) {
                            g.setActive(0); n++;
                        }
                    return n;
                });
        when(flowLogMapper.selectByReqNo(any())).thenAnswer(i -> {
            String no = i.getArgument(0);
            for (TOadsGovCaseFlowLog g : logs) if (no.equals(g.getReqNo())) return g;
            return null;
        });
        when(flowLogMapper.listLandedBetween(any(), any())).thenAnswer(i -> {
            Date s = i.getArgument(0), e = i.getArgument(1);
            List<TOadsGovCaseFlowLog> l = new ArrayList<TOadsGovCaseFlowLog>();
            for (TOadsGovCaseFlowLog g : logs)
                if (!g.getActedAt().before(s) && g.getActedAt().before(e)) l.add(g);
            l.sort(Comparator.comparing(TOadsGovCaseFlowLog::getActedAt).thenComparing(TOadsGovCaseFlowLog::getSeqNo));
            return l;
        });

        when(permitBillMapper.selectList(any())).thenAnswer(i -> new ArrayList<TOadsPermitBill>(sealedPermits));
        when(areaBookMapper.selectList(any())).thenAnswer(i -> new ArrayList<TOadsAreaBook>(areas));
    }

    // ------------------------------------------------ 夹具

    private GovCaseReportForm form(String spot, String area, String qty, String score) {
        GovCaseReportForm f = new GovCaseReportForm();
        f.setSpotNo(spot);
        f.setAreaNo(area);
        f.setQtyText(qty);
        f.setCheckScoreText(score);
        return f;
    }

    private TOadsGovCase openCase(String spot) {
        TOadsGovCaseReport r = service.report(form(spot, "DU00", "1", "80"), "值守甲");
        return service.establish(r.getId(), "值守甲");
    }

    private void permitSealed(String spot) {
        TOadsPermitBill b = new TOadsPermitBill();
        b.setSpotNo(spot);
        b.setStatus(1);
        b.setDelFlag(0);
        sealedPermits.add(b);
    }

    private void areaRevoked(String areaNo) {
        TOadsAreaBook a = new TOadsAreaBook();
        a.setAreaNo(areaNo);
        a.setStatus(1);
        a.setAreaName("已划归邻段的" + areaNo);
        a.setDelFlag(0);
        areas.add(a);
    }

    private GovCaseSubmitForm submitForm(Long caseId, Integer claim, String note) {
        GovCaseSubmitForm f = new GovCaseSubmitForm();
        f.setCaseId(caseId);
        f.setClaimStep(claim);
        f.setNote(note);
        return f;
    }

    /** 推进到设置（step3）：须有封存许可。返回在 step3 的件。 */
    private TOadsGovCase reachSetup(String spot) {
        TOadsGovCase c = openCase(spot);
        permitSealed(spot);
        service.advance(c.getId(), null, null, "值守甲"); // 申报 -> 核准
        service.advance(c.getId(), null, null, "值守甲"); // 核准 -> 设置
        return cases.get(c.getId());
    }

    /** 推进到验收（step4）：设置核收同意。 */
    private TOadsGovCase reachAccept(String spot) {
        TOadsGovCase c = reachSetup(spot);
        GovCaseSubmitForm setup = submitForm(c.getId(), GovCaseStep.SETUP, "设置核收");
        setup.setAgree(GovCaseStep.AGREE);
        service.submit(setup, "设置岗");
        service.advance(c.getId(), null, null, "值守甲"); // 设置 -> 验收
        return cases.get(c.getId());
    }

    // ------------------------------------------------ 第一块：构成与建立

    @Test
    public void missingEachInputNamedSeparately() {
        GovCaseRuleException e1 = assertThrows(GovCaseRuleException.class,
                () -> service.report(form(null, "DU00", "1", "80"), "甲"));
        assertTrue(e1.getMessage().contains("点位编码"));

        GovCaseRuleException e2 = assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", null, "80"), "甲"));
        assertTrue(e2.getMessage().contains("处数"));

        GovCaseRuleException e3 = assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", "1", null), "甲"));
        assertTrue(e3.getMessage().contains("校核分值"));
        assertFalse(e1.getMessage().contains("信息不全"));

        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", "1.5", "80"), "甲")).getMessage().contains("整数"));
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", "0", "80"), "甲")).getMessage().contains("正整数"));
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", "1", "120"), "甲")).getMessage().contains("0..100"));
    }

    @Test
    public void establishLandsFirstStepAndCounts() {
        assertEquals(0, service.openCount());
        TOadsGovCase c = openCase("SP1");
        assertEquals(Integer.valueOf(GovCaseStep.DECLARE), c.getStepNo());
        assertEquals(Integer.valueOf(GovCaseStep.ST_RUNNING), c.getStatus());
        assertEquals(1, service.openCount(), "正式立起加一");
        assertEquals(Integer.valueOf(GovCaseStep.DECLARE), service.currentStep(c.getId()), "步次按留痕重数");
    }

    @Test
    public void oneSpotOnlyOneOpenCase() {
        openCase("SP1");
        // 报备阶段先挡
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.report(form("SP1", "DU00", "1", "80"), "甲")).getMessage().contains("在办"));
    }

    @Test
    public void cancelBeforeEstablishNotInLedger() {
        TOadsGovCaseReport r = service.report(form("SP1", "DU00", "1", "80"), "甲");
        assertEquals(0, service.openCount(), "报备未立不计入尚未走完");
        service.cancelReport(r.getId(), "不办了", "甲");
        assertEquals(0, cases.size(), "立前撤销不进办件主档");
        assertEquals(0, service.openCount());
        // 撤销后同点位可重新报备
        assertNotNull(service.report(form("SP1", "DU00", "1", "80"), "甲"));
        // 已立起的不能走立前撤销
        TOadsGovCaseReport r2 = service.report(form("SP2", "DU00", "1", "80"), "甲");
        service.establish(r2.getId(), "甲");
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.cancelReport(r2.getId(), "x", "甲")).getMessage().contains("立前"));
    }

    @Test
    public void claimStepIsReferenceOnlySkipDoesNotMove() {
        TOadsGovCase c = openCase("SP1");
        // 报文想从申报直接送到设置（跳过核准）：那一步不生效，整件留在申报
        GovCaseFlowOutcome o = service.advance(c.getId(), GovCaseStep.SETUP, "R-skip", "甲");
        assertEquals(GovCaseFlowOutcome.DISCARDED, o.getOutcome());
        assertEquals(Integer.valueOf(GovCaseStep.DECLARE), service.currentStep(c.getId()));
        // 被丢弃的这一步库里一条不添（只有立件那笔 REACH）
        assertEquals(1, logs.size());
    }

    // ------------------------------------------------ 各步准入

    @Test
    public void approveToSetupNeedsPermitThreeStages() {
        TOadsGovCase c = openCase("SP1");
        service.advance(c.getId(), null, null, "甲"); // 申报->核准
        // 没有封存的设置许可，进不了设置
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.advance(c.getId(), null, null, "甲")).getMessage().contains("三段签署"));
        permitSealed("SP1");
        GovCaseFlowOutcome o = service.advance(c.getId(), null, null, "甲");
        assertEquals(GovCaseFlowOutcome.ADVANCED, o.getOutcome());
        assertEquals(Integer.valueOf(GovCaseStep.SETUP), service.currentStep(c.getId()));
    }

    @Test
    public void setupAcceptRequiredToOpenAccept() {
        TOadsGovCase c = reachSetup("SP1");
        GovCaseSubmitForm no = submitForm(c.getId(), GovCaseStep.SETUP, "核收未同意");
        no.setAgree(GovCaseStep.DISAGREE);
        service.submit(no, "设置岗");
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.advance(c.getId(), null, null, "甲")).getMessage().contains("核收未记为同意"));
        // 第二回核收保留首次，不能靠改同意翻过来
        GovCaseSubmitForm yes = submitForm(c.getId(), GovCaseStep.SETUP, "想改同意");
        yes.setAgree(GovCaseStep.AGREE);
        assertTrue(assertThrows(GovCaseRuleException.class, () -> service.submit(yes, "设置岗")).getMessage().contains("首次"));
    }

    @Test
    public void acceptNeedsTwoWitnessesAndVoucherThenSeals() {
        TOadsGovCase c = reachAccept("SP1");
        assertEquals(1, service.openCount(), "验收未走完仍在办");

        GovCaseSubmitForm w1 = submitForm(c.getId(), GovCaseStep.ACCEPT, "见证甲意见");
        w1.setWitnessPost("W-A");
        assertEquals(GovCaseFlowOutcome.LANDED, service.submit(w1, "见证甲").getOutcome());

        // 同一见证岗第二回：保留首次
        GovCaseSubmitForm w1again = submitForm(c.getId(), GovCaseStep.ACCEPT, "见证甲又一句");
        w1again.setWitnessPost("W-A");
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.submit(w1again, "见证甲")).getMessage().contains("已提交过"));

        // 凭证先回写，还差一名见证，不封存
        GovCaseSubmitForm v = submitForm(c.getId(), GovCaseStep.ACCEPT, null);
        v.setRecheckVoucher("YZ-88");
        assertEquals(GovCaseFlowOutcome.LANDED, service.submit(v, "验收岗").getOutcome());
        assertEquals(1, service.openCount());

        GovCaseSubmitForm w2 = submitForm(c.getId(), GovCaseStep.ACCEPT, "见证乙意见");
        w2.setWitnessPost("W-B");
        GovCaseFlowOutcome sealed = service.submit(w2, "见证乙");
        assertEquals(GovCaseFlowOutcome.SEALED, sealed.getOutcome());
        assertEquals(Integer.valueOf(GovCaseStep.ST_SEALED), cases.get(c.getId()).getStatus());
        assertEquals("YZ-88", cases.get(c.getId()).getRecheckVoucher());
        assertEquals(0, service.openCount(), "封存减一");
        assertNull(cases.get(c.getId()).getLiveToken(), "封存腾名额");
    }

    @Test
    public void sealedAcceptsNoWriteActions() {
        TOadsGovCase c = reachAccept("SP1");
        GovCaseSubmitForm w1 = submitForm(c.getId(), GovCaseStep.ACCEPT, "甲");
        w1.setWitnessPost("W-A");
        service.submit(w1, "见证甲");
        GovCaseSubmitForm w2 = submitForm(c.getId(), GovCaseStep.ACCEPT, "乙");
        w2.setWitnessPost("W-B");
        w2.setRecheckVoucher("YZ-1");
        service.submit(w2, "见证乙");

        assertThrows(GovCaseRuleException.class,
                () -> service.submit(submitForm(c.getId(), GovCaseStep.ACCEPT, "封存后补意见"), "甲"));
        assertThrows(GovCaseRuleException.class, () -> service.advance(c.getId(), null, null, "甲"));
        assertThrows(GovCaseRuleException.class, () -> service.returnStep(c.getId(), "x", null, "甲"));
        assertThrows(GovCaseRuleException.class, () -> service.withdraw(c.getId(), "x", "甲"));
    }

    // ------------------------------------------------ 第二块：移动与封存

    @Test
    public void secondOpinionOnSameStepKeepsFirst() {
        TOadsGovCase c = openCase("SP1");
        service.submit(submitForm(c.getId(), GovCaseStep.DECLARE, "首次那句"), "甲");
        GovCaseRuleException e = assertThrows(GovCaseRuleException.class,
                () -> service.submit(submitForm(c.getId(), GovCaseStep.DECLARE, "第二句"), "甲"));
        assertTrue(e.getMessage().contains("首次"));
        List<TOadsGovCaseFlowLog> round = flowLogMapper.listActiveRound(c.getId(), GovCaseStep.DECLARE, 1);
        long opinions = round.stream().filter(g -> GovCaseStep.ACTION_OPINION.equals(g.getAction())).count();
        assertEquals(1, opinions, "不覆盖不追加");
    }

    @Test
    public void returnOneStepMovesRoundToTraceTailAndRecounts() {
        TOadsGovCase c = reachSetup("SP1"); // 停在设置，核收尚未在此提交
        GovCaseSubmitForm agree = submitForm(c.getId(), GovCaseStep.SETUP, "设置同意");
        agree.setAgree(GovCaseStep.AGREE);
        service.submit(agree, "设置岗");
        assertEquals(Integer.valueOf(1), cases.get(c.getId()).getSetupAccept());

        GovCaseFlowOutcome o = service.returnStep(c.getId(), "设置材料要补", null, "甲");
        assertEquals(GovCaseFlowOutcome.RETURNED, o.getOutcome());
        assertEquals(Integer.valueOf(GovCaseStep.APPROVE), service.currentStep(c.getId()), "退到核准");
        assertEquals(Integer.valueOf(0), cases.get(c.getId()).getSetupAccept(), "被退步当轮攒的同意清掉，重走重累计");
        // 设置那一轮在档行退出但行保留在时序尾部
        long inactiveSetup = logs.stream()
                .filter(g -> g.getCaseId().equals(c.getId()) && g.getStepNo() == GovCaseStep.SETUP && g.getActive() == 0)
                .count();
        assertTrue(inactiveSetup >= 2, "REACH 与同意意见都留在尾部可查");

        // 第一步不再后退：核准还能退到申报，退到申报后再退才拦
        service.returnStep(c.getId(), "核准也退回", null, "甲"); // 核准 -> 申报
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.returnStep(c.getId(), "申报无可退", null, "甲")).getMessage().contains("第一步"));
    }

    @Test
    public void withdrawAndReassignAndVoidAreThreeClosings() throws Exception {
        // 撤回报备（申报之后）
        TOadsGovCase c1 = openCase("SP1");
        service.withdraw(c1.getId(), "撤回", "甲");
        assertEquals(Integer.valueOf(GovCaseStep.ST_WITHDRAWN), cases.get(c1.getId()).getStatus());
        assertEquals(0, service.openCount());
        TOadsGovCase c1b = openCase("SP1");
        assertEquals(1, service.openCount(), "收尾后同点位可另立");
        service.withdraw(c1b.getId(), "再撤回", "甲");
        assertEquals(0, service.openCount());

        // 划段：目录未划转不收
        TOadsGovCase c2 = openCase("SP2");
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.reassignClose(c2.getId(), "x", "甲")).getMessage().contains("划归邻段"));
        areaRevoked("DU00");
        service.reassignClose(c2.getId(), "路段划走", "甲");
        assertEquals(Integer.valueOf(GovCaseStep.ST_REASSIGNED), cases.get(c2.getId()).getStatus());
        assertEquals(0, service.openCount());

        // 作废只从验收：设置步发起被拦、件仍在办
        TOadsGovCase c3 = reachSetup("SP3");
        assertTrue(assertThrows(GovCaseRuleException.class,
                () -> service.voidCase(c3.getId(), "x", "甲")).getMessage().contains("验收"));
        assertEquals(1, service.openCount(), "被拦的作废不动件，仍在办");

        // 走到验收发起作废：收尾减一
        TOadsGovCase c4 = reachAccept("SP4");
        service.voidCase(c4.getId(), "验收阶段作废", "甲");
        assertEquals(Integer.valueOf(GovCaseStep.ST_VOID), cases.get(c4.getId()).getStatus());
        assertEquals(1, service.openCount(), "只剩 SP3 那条仍在设置步");
    }

    // ------------------------------------------------ 第三块：追溯、计数、并发幂等

    @Test
    public void timelineRegisterMatchesEveryLandingIncludingReturnedRounds() {
        TOadsGovCase c = reachSetup("SP1");
        GovCaseSubmitForm agree = submitForm(c.getId(), GovCaseStep.SETUP, "同意");
        agree.setAgree(GovCaseStep.AGREE);
        service.submit(agree, "设置岗");
        service.returnStep(c.getId(), "退一回", null, "甲");

        List<TOadsGovCaseFlowLog> today = service.acceptanceRegister(new Date());
        // 立件REACH + 2次推进REACH + 设置OPINION + RETURN = 5 笔；RETURN 虽 active=0 也入当日流水
        assertEquals(5, today.size());
        // 时序按 seq 递增，不并笔不丢笔
        for (int i = 1; i < today.size(); i++) {
            assertTrue(today.get(i).getSeqNo() > today.get(i - 1).getSeqNo());
        }
        long returns = today.stream().filter(g -> GovCaseStep.ACTION_RETURN.equals(g.getAction())).count();
        assertEquals(1, returns, "后退那笔也算当日受理流水的一笔");
    }

    @Test
    public void twoChannelsSameInstantAreTwoRows() {
        TOadsGovCase c = reachAccept("SP1");
        // 两名见证几乎同时各送一笔：两条都在，不并成一条
        GovCaseSubmitForm w1 = submitForm(c.getId(), GovCaseStep.ACCEPT, "甲");
        w1.setWitnessPost("W-A");
        GovCaseSubmitForm w2 = submitForm(c.getId(), GovCaseStep.ACCEPT, "乙");
        w2.setWitnessPost("W-B");
        service.submit(w1, "见证甲");
        service.submit(w2, "见证乙");
        List<TOadsGovCaseFlowLog> rd = flowLogMapper.listActiveRound(c.getId(), GovCaseStep.ACCEPT, 1);
        long witnesses = rd.stream().filter(g -> GovCaseStep.ACTION_WITNESS.equals(g.getAction())).count();
        assertEquals(2, witnesses, "同刻两条通道各一笔，不并笔");
        assertNotEquals(rd.get(0).getSeqNo(), rd.get(1).getSeqNo());
    }

    @Test
    public void concurrentSubmitIsIdempotentByReqNo() {
        // 报备同请求号两回：同一件
        GovCaseReportForm f = form("SP9", "DU00", "1", "80");
        f.setReqNo("REQ-REPORT-1");
        TOadsGovCaseReport r1 = service.report(f, "甲");
        TOadsGovCaseReport r2 = service.report(f, "甲");
        assertEquals(r1.getId(), r2.getId());
        assertEquals(1, reports.values().stream().filter(r -> "SP9".equals(r.getSpotNo())).count());

        // 送意见同请求号两回：第二条幂等命中，库里只一条
        TOadsGovCase c = service.establish(r1.getId(), "甲");
        GovCaseSubmitForm o1 = submitForm(c.getId(), GovCaseStep.DECLARE, "幂等意见");
        o1.setReqNo("REQ-OP-1");
        GovCaseFlowOutcome first = service.submit(o1, "甲");
        assertNotEquals(GovCaseFlowOutcome.DEDUPED, first.getOutcome());
        GovCaseSubmitForm o2 = submitForm(c.getId(), GovCaseStep.DECLARE, "幂等意见");
        o2.setReqNo("REQ-OP-1");
        GovCaseFlowOutcome second = service.submit(o2, "甲");
        assertEquals(GovCaseFlowOutcome.DEDUPED, second.getOutcome());
        long opinions = logs.stream()
                .filter(g -> g.getCaseId().equals(c.getId()) && GovCaseStep.ACTION_OPINION.equals(g.getAction()))
                .count();
        assertEquals(1, opinions, "同号第二回不重复落、也不丢一条");
    }

    @Test
    public void reconcileBalancedAndNamesStepWhenDrift() {
        TOadsGovCase c1 = reachAccept("SP1");
        GovCaseSubmitForm w1 = submitForm(c1.getId(), GovCaseStep.ACCEPT, "甲");
        w1.setWitnessPost("W-A");
        service.submit(w1, "甲");
        GovCaseSubmitForm w2 = submitForm(c1.getId(), GovCaseStep.ACCEPT, "乙");
        w2.setWitnessPost("W-B");
        w2.setRecheckVoucher("V1");
        service.submit(w2, "乙"); // 封存

        TOadsGovCase c2 = openCase("SP2"); // 在办停在申报
        GovCaseLedgerCheck ok = service.reconcile();
        assertTrue(ok.isBalanced());
        assertEquals(1, ok.getOpenCount());

        // 人为把账面步次拨到验收，留痕仍在申报：对账指名
        c2.setStepNo(GovCaseStep.ACCEPT);
        GovCaseLedgerCheck bad = service.reconcile();
        assertFalse(bad.isBalanced());
        assertTrue(bad.getMismatches().stream().anyMatch(m -> m.contains(c2.getBizNo()) && m.contains("步")));
        assertTrue(bad.getMismatches().stream().noneMatch(m -> m.contains(c1.getBizNo())));
        // 服务端 currentStep 仍按留痕，不被账面拨乱
        assertEquals(Integer.valueOf(GovCaseStep.DECLARE), service.currentStep(c2.getId()));
    }

    @Test
    public void detailAssemblesStepsAndHidesOpsWhenSealed() {
        TOadsGovCase c = reachAccept("SP1");
        GovCaseDetail d0 = service.detail(c.getId());
        assertTrue(d0.getSteps().get(3).isCurrent(), "当前停在验收");
        assertFalse(d0.isSealed());

        GovCaseSubmitForm w1 = submitForm(c.getId(), GovCaseStep.ACCEPT, "甲");
        w1.setWitnessPost("W-A");
        service.submit(w1, "甲");
        GovCaseSubmitForm w2 = submitForm(c.getId(), GovCaseStep.ACCEPT, "乙");
        w2.setWitnessPost("W-B");
        w2.setRecheckVoucher("V9");
        service.submit(w2, "乙");

        GovCaseDetail d = service.detail(c.getId());
        assertTrue(d.isSealed());
        assertEquals(4, d.getSteps().size());
        assertTrue(d.getSteps().stream().noneMatch(GovCaseDetail.Step::isCurrent), "封存后无当前步、不给操作入口");
        assertFalse(d.getTimeline().isEmpty(), "留痕串照常可查");
    }
}
