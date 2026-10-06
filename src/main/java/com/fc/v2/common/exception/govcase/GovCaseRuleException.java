package com.fc.v2.common.exception.govcase;

/**
 * 监管办件四步流程口径违例：建件缺项、步子跨了、准入没过、封存后还想写、同点位已有在途等。
 *
 * <p>每种不让办的情形各有各的提示语，缺项要指名缺哪一项，消息原样回到页面，
 * 不许合并成一句“信息不全”“数据有误”。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class GovCaseRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GovCaseRuleException(String message) {
        super(message);
    }
}
