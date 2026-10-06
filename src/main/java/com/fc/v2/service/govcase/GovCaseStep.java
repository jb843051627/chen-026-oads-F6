package com.fc.v2.service.govcase;

/**
 * 监管办件四步步号与动作的唯一取号处。
 *
 * <p>只认四步，先后不可调换：{@link #DECLARE}申报 → {@link #APPROVE}核准 →
 * {@link #SETUP}设置 → {@link #ACCEPT}验收。前进只跨紧邻下一步，跨步不生效；
 * 这一条办件停在哪一步由系统按步序留痕自己数，报文里带的步次只作参考、不采信。
 *
 * <p>与旧 {@code TOadsGovFlow} 的 0..3 三跳骨架各走各的：本处不回头改那一套，
 * 需要四步推进就由这一套等价承接。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class GovCaseStep {

    private GovCaseStep() {
    }

    /** 申报（建件落的第一步） */
    public static final int DECLARE = 1;
    /** 核准（要看设置许可三段签署走完） */
    public static final int APPROVE = 2;
    /** 设置（核收须记为同意才进验收） */
    public static final int SETUP = 3;
    /** 验收（两名见证岗各一笔＋复验凭证回写齐才走完封存） */
    public static final int ACCEPT = 4;

    /** 走到（一次紧邻推进） */
    public static final String ACTION_REACH = "REACH";
    /** 当前步送一句意见（首条为准，第二次不覆盖不追加） */
    public static final String ACTION_OPINION = "OPINION";
    /** 验收见证岗落笔（两名各一笔） */
    public static final String ACTION_WITNESS = "WITNESS";
    /** 后退一步（当轮转留痕尾部，重走重新累计） */
    public static final String ACTION_RETURN = "RETURN";
    /** 验收走完封存（两见证＋凭证齐的同一次处理落一笔） */
    public static final String ACTION_SEAL = "SEAL";
    /** 收尾达成（撤回报备/划归邻段/作废） */
    public static final String ACTION_CLOSE = "CLOSE";

    /** 设置核收：记为同意 */
    public static final int AGREE = 1;
    /** 设置核收：未记同意 */
    public static final int DISAGREE = 2;

    /** 在办（停在四步中的某一步，尚未走完） */
    public static final int ST_RUNNING = 1;
    /** 已封存：验收走完，可查不可写 */
    public static final int ST_SEALED = 2;
    /** 申报后撤回报备（收尾，不占在途名额） */
    public static final int ST_WITHDRAWN = 3;
    /** 所属路段划归邻段（收尾） */
    public static final int ST_REASSIGNED = 4;
    /** 作废（只从最后一步发起） */
    public static final int ST_VOID = 5;

    /** 撤回报备 */
    public static final String CLOSE_WITHDRAW = "WITHDRAW";
    /** 划归邻段 */
    public static final String CLOSE_REASSIGN = "REASSIGN";
    /** 作废 */
    public static final String CLOSE_VOID = "VOID";

    /** 报备登记：报备中（正式立起来之前那一小段） */
    public static final int REPORT_OPEN = 0;
    /** 报备登记：已正式立起 */
    public static final int REPORT_ESTABLISHED = 1;
    /** 报备登记：立前撤销（不进主档、不占尚未走完） */
    public static final int REPORT_CANCELLED = 6;

    public static String nameOf(Integer step) {
        if (step == null) {
            return "未起步";
        }
        switch (step) {
            case DECLARE:
                return "申报";
            case APPROVE:
                return "核准";
            case SETUP:
                return "设置";
            case ACCEPT:
                return "验收";
            default:
                return "未知步";
        }
    }

    /** 这一步进入下一步的准入条件原话，供回显与报错同一句，不另立说法。 */
    public static String entryOf(int step) {
        switch (step) {
            case DECLARE:
                return "建件四项输入齐（点位编码、所属路段代号、处数、校核分值）";
            case APPROVE:
                return "设置许可三段签署走完（窗口受理/区局核定/市局备案）";
            case SETUP:
                return "核准已通过";
            case ACCEPT:
                return "设置一步的核收已记为同意";
            default:
                return "无此步";
        }
    }
}
