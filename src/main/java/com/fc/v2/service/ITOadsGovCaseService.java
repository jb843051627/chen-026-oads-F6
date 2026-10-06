package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TOadsGovCase;
import com.fc.v2.model.auto.TOadsGovCaseFlowLog;
import com.fc.v2.model.auto.TOadsGovCaseReport;
import com.fc.v2.model.custom.GovCaseDetail;
import com.fc.v2.model.custom.GovCaseFlowOutcome;
import com.fc.v2.model.custom.GovCaseLedgerCheck;
import com.fc.v2.model.custom.GovCaseReportForm;
import com.fc.v2.model.custom.GovCaseSubmitForm;

import java.util.Date;
import java.util.List;

/**
 * 监管办件四步流程唯一裁决处（申报→核准→设置→验收）。
 *
 * <p>一条办件当下停在第几步、下一步准入满没满、封存后接不接新意见、同一步第二次批语怎么存，
 * 答案只从本处按 append-only 留痕点出。办件本（t_oads_gov_case）与留痕
 * （t_oads_gov_case_flow_log）说的是同一件事，同一事务联动落账。报文自带步次一律只作参考，
 * 不采信记账；不先定死步数再回头找路径配。
 *
 * <p>与旧 {@code ITOadsGovFlowService} 各走各的：旧那套不添不改名，四步推进由本套等价承接。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITOadsGovCaseService {

    /** 报备（正式立起来之前那一小段）。四项输入齐才收，缺项逐项点名；同点位只许一条。 */
    TOadsGovCaseReport report(GovCaseReportForm form, String operator);

    /** 正式立起：落第一步申报、计入尚未走完。一条报备只立一件。 */
    TOadsGovCase establish(Long reportId, String operator);

    /** 立前撤销：只在正式立起来之前，不进主档、不占尚未走完名额。 */
    TOadsGovCaseReport cancelReport(Long reportId, String note, String operator);

    /**
     * 当前步送一笔：申报/核准送意见，设置送核收同意，验收送见证岗意见或回写复验凭证。
     * 同一步第二次送保留首次；报文字称步次只作参考，对不上一笔不落。
     * 验收两名见证岗＋凭证齐，同一次处理封存。
     */
    GovCaseFlowOutcome submit(GovCaseSubmitForm form, String operator);

    /** 前进紧邻下一步（跨步送进来的那一步不生效，整件留在原地）。 */
    GovCaseFlowOutcome advance(Long caseId, Integer claimStep, String reqNo, String operator);

    /** 后退一步：被退那步当轮批语转留痕尾部，重走重新累计；第一步不退。 */
    GovCaseFlowOutcome returnStep(Long caseId, String note, String reqNo, String operator);

    /** 申报之后撤回报备：收尾达成，不必再往下走。 */
    GovCaseFlowOutcome withdraw(Long caseId, String note, String operator);

    /** 所属路段从目录划归邻段：目录确已撤出/划转才收尾。 */
    GovCaseFlowOutcome reassignClose(Long caseId, String note, String operator);

    /** 作废：只从最后一步（验收）发起。 */
    GovCaseFlowOutcome voidCase(Long caseId, String note, String operator);

    /** 整件回看：单头＋按步分节＋时序留痕串（封存/收尾照常可查）。 */
    GovCaseDetail detail(Long caseId);

    /** 当前停在第几步（服务端按留痕重数，不读报文、不读页面缓存）。 */
    Integer currentStep(Long caseId);

    /** 台账查询（分页由控制器 PageHelper 接管）。 */
    List<TOadsGovCase> selectCaseList(Wrapper<TOadsGovCase> queryWrapper);

    /** 尚未走完数量：立一条加一、封/收一条减一，后端一次查询出数。 */
    long openCount();

    /** 台账对账：顶部数与逐件留痕重算同一次核对，对不上逐件指名。 */
    GovCaseLedgerCheck reconcile();

    /** 当日受理流水：当日全部落笔按时序排（含后退退出的老轮），逐笔吻合。 */
    List<TOadsGovCaseFlowLog> acceptanceRegister(Date day);
}
