package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.govcase.GovCaseRuleException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TOadsGovCase;
import com.fc.v2.model.auto.TOadsGovCaseFlowLog;
import com.fc.v2.model.auto.TOadsGovCaseReport;
import com.fc.v2.model.custom.GovCaseDetail;
import com.fc.v2.model.custom.GovCaseFlowOutcome;
import com.fc.v2.model.custom.GovCaseLedgerCheck;
import com.fc.v2.model.custom.GovCaseReportForm;
import com.fc.v2.model.custom.GovCaseSubmitForm;
import com.fc.v2.service.ITOadsGovCaseService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.List;

/**
 * 监管办件 Controller——申报/核准/设置/验收四步流程的唯一门面。
 *
 * <p>所有判定都在 {@link ITOadsGovCaseService} 一处给出：控制器只收报文、回原句提示。
 * 步次不从报文记账（claimStep 仅作参考），封存/收尾行不接写动作。
 * 与旧 {@code OadsGovFlowController} 各走各的，不改旧那套。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "监管办件")
@Controller
@RequestMapping("/oadsGovCase")
public class OadsGovCaseController extends BaseController {

    private final String prefix = "admin/oadsGovCase";

    @Autowired
    private ITOadsGovCaseService govCaseService;

    @ApiOperation(value = "办件台账跳转", notes = "办件台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("oads:oadsGovCase:view")
    public String view(ModelMap model) {
        model.put("openCount", govCaseService.openCount());
        return prefix + "/list";
    }

    @Log(title = "监管办件台账", action = "list")
    @ApiOperation(value = "台账分页", notes = "在办/封存/收尾照常出册，封存行只给查")
    @GetMapping("/list")
    @RequiresPermissions("oads:oadsGovCase:list")
    @ResponseBody
    public ResultTable list(TOadsGovCase record) {
        QueryWrapper<TOadsGovCase> qw = new QueryWrapper<TOadsGovCase>()
                .eq("del_flag", 0)
                .orderByDesc("create_time");
        if (record.getBizNo() != null && !record.getBizNo().trim().isEmpty()) {
            qw.like("biz_no", record.getBizNo().trim());
        }
        if (record.getSpotNo() != null && !record.getSpotNo().trim().isEmpty()) {
            qw.eq("spot_no", record.getSpotNo().trim());
        }
        if (record.getAreaNo() != null && !record.getAreaNo().trim().isEmpty()) {
            qw.eq("area_no", record.getAreaNo().trim());
        }
        if (record.getStatus() != null) {
            qw.eq("status", record.getStatus());
        }
        startPage();
        com.github.pagehelper.PageInfo<TOadsGovCase> page =
                new com.github.pagehelper.PageInfo<TOadsGovCase>(govCaseService.selectCaseList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "尚未走完数量", notes = "立一条加一、封/收一条减一，后端一次查询出数")
    @GetMapping("/openCount")
    @RequiresPermissions("oads:oadsGovCase:list")
    @ResponseBody
    public AjaxResult openCount() {
        return AjaxResult.success("ok").put("count", govCaseService.openCount());
    }

    @ApiOperation(value = "整件跳转", notes = "单头＋按步分节＋时序留痕")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("oads:oadsGovCase:view")
    public String detail(@PathVariable("id") Long id, ModelMap model) {
        model.put("caseId", id);
        return prefix + "/detail";
    }

    @ApiOperation(value = "整件数据", notes = "当前步与下一步准入服务端算好；封存行不带操作入口")
    @GetMapping("/detailData/{id}")
    @RequiresPermissions("oads:oadsGovCase:view")
    @ResponseBody
    public AjaxResult detailData(@PathVariable("id") Long id) {
        GovCaseDetail detail = govCaseService.detail(id);
        return AjaxResult.success("ok").put("data", detail);
    }

    @Log(title = "监管办件报备", action = "report")
    @ApiOperation(value = "报备", notes = "四项输入齐才收；同点位只一条；可立前撤销")
    @PostMapping("/report")
    @RequiresPermissions("oads:oadsGovCase:create")
    @ResponseBody
    public AjaxResult report(GovCaseReportForm form) {
        try {
            TOadsGovCaseReport r = govCaseService.report(form, ShiroUtils.getLoginName());
            return AjaxResult.success("报备已收，待正式立起").put("data", r);
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件正式立件", action = "establish")
    @ApiOperation(value = "正式立件", notes = "落第一步申报、计入尚未走完")
    @PostMapping("/establish")
    @RequiresPermissions("oads:oadsGovCase:create")
    @ResponseBody
    public AjaxResult establish(@RequestParam("reportId") Long reportId) {
        try {
            TOadsGovCase c = govCaseService.establish(reportId, ShiroUtils.getLoginName());
            return AjaxResult.success("已正式立件 " + c.getBizNo() + "，落第一步「申报」").put("data", c);
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件立前撤销", action = "cancel")
    @ApiOperation(value = "立前撤销", notes = "只在正式立起之前；不进主档、不占名额")
    @PostMapping("/cancel")
    @RequiresPermissions("oads:oadsGovCase:close")
    @ResponseBody
    public AjaxResult cancel(@RequestParam("reportId") Long reportId,
                             @RequestParam(value = "note", required = false) String note) {
        try {
            TOadsGovCaseReport r = govCaseService.cancelReport(reportId, note, ShiroUtils.getLoginName());
            return AjaxResult.success("该报备已在立起前撤销，不进主档、不占尚未走完名额").put("data", r);
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件送意见/见证", action = "opinion")
    @ApiOperation(value = "当前步送一笔", notes = "意见/核收同意/见证岗/复验凭证；首条为准，跨步丢弃")
    @PostMapping("/opinion")
    @RequiresPermissions("oads:oadsGovCase:opinion")
    @ResponseBody
    public AjaxResult opinion(GovCaseSubmitForm form) {
        try {
            GovCaseFlowOutcome o = govCaseService.submit(form, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage())
                    .put("outcome", o.getOutcome())
                    .put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件推进一步", action = "advance")
    @PostMapping("/advance")
    @RequiresPermissions("oads:oadsGovCase:flow")
    @ResponseBody
    public AjaxResult advance(@RequestParam("caseId") Long caseId,
                              @RequestParam(value = "claimStep", required = false) Integer claimStep,
                              @RequestParam(value = "reqNo", required = false) String reqNo) {
        try {
            GovCaseFlowOutcome o = govCaseService.advance(caseId, claimStep, reqNo, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage()).put("outcome", o.getOutcome()).put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件后退一步", action = "returnStep")
    @PostMapping("/returnStep")
    @RequiresPermissions("oads:oadsGovCase:flow")
    @ResponseBody
    public AjaxResult returnStep(@RequestParam("caseId") Long caseId,
                                 @RequestParam(value = "note", required = false) String note,
                                 @RequestParam(value = "reqNo", required = false) String reqNo) {
        try {
            GovCaseFlowOutcome o = govCaseService.returnStep(caseId, note, reqNo, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage()).put("outcome", o.getOutcome()).put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件撤回报备", action = "withdraw")
    @PostMapping("/withdraw")
    @RequiresPermissions("oads:oadsGovCase:close")
    @ResponseBody
    public AjaxResult withdraw(@RequestParam("caseId") Long caseId,
                               @RequestParam(value = "note", required = false) String note) {
        try {
            GovCaseFlowOutcome o = govCaseService.withdraw(caseId, note, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage()).put("outcome", o.getOutcome()).put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件划段收尾", action = "reassignClose")
    @PostMapping("/reassignClose")
    @RequiresPermissions("oads:oadsGovCase:close")
    @ResponseBody
    public AjaxResult reassignClose(@RequestParam("caseId") Long caseId,
                                    @RequestParam(value = "note", required = false) String note) {
        try {
            GovCaseFlowOutcome o = govCaseService.reassignClose(caseId, note, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage()).put("outcome", o.getOutcome()).put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "监管办件作废", action = "void")
    @PostMapping("/void")
    @RequiresPermissions("oads:oadsGovCase:void")
    @ResponseBody
    public AjaxResult voidCase(@RequestParam("caseId") Long caseId,
                               @RequestParam(value = "note", required = false) String note) {
        try {
            GovCaseFlowOutcome o = govCaseService.voidCase(caseId, note, ShiroUtils.getLoginName());
            return AjaxResult.success(o.getMessage()).put("outcome", o.getOutcome()).put("data", o.getGovCase());
        } catch (GovCaseRuleException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation(value = "台账对账", notes = "顶部数与逐件留痕重算同一次核对，对不上逐件指名")
    @GetMapping("/reconcile")
    @RequiresPermissions("oads:oadsGovCase:list")
    @ResponseBody
    public AjaxResult reconcile() {
        GovCaseLedgerCheck check = govCaseService.reconcile();
        return AjaxResult.success(check.isBalanced() ? "账账相符" : "账面对不上，已逐件指名").put("data", check);
    }

    @ApiOperation(value = "当日受理流水", notes = "当日全部落笔按时序排，含后退退出的老轮")
    @GetMapping("/register")
    @RequiresPermissions("oads:oadsGovCase:list")
    @ResponseBody
    public AjaxResult register(
            @DateTimeFormat(pattern = "yyyy-MM-dd") @RequestParam(value = "day", required = false) Date day) {
        List<TOadsGovCaseFlowLog> rows =
                govCaseService.acceptanceRegister(day == null ? new Date() : day);
        return AjaxResult.success("ok").put("data", rows);
    }
}
